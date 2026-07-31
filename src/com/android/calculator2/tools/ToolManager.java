/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.KeyMaps;
import com.android.calculator2.R;
import com.android.calculator2.tools.ui.ToolBarView;
import com.android.calculator2.tools.ui.ToolPanelOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and controller for tool modes.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Hold registered {@link ToolMode}s and the currently active one.</li>
 *   <li>Switch modes (with value carry-over and display hand-off via {@link ToolHost}).</li>
 *   <li>Order the toolbar chips by usage frequency (SharedPreferences) plus first-run defaults.</li>
 *   <li>Drive the overlay panel (expand/collapse) and route pad input to the active tool.</li>
 * </ul>
 * The calculator's own mode is the default; while it is active this manager stays out of the way
 * and the existing engine handles input/display.
 */
public class ToolManager {

    private static final String PREFS_NAME = "calc_tools";
    private static final String FREQ_PREFIX = "freq_";
    private static final int PINNED_COUNT = 3;

    private final Context mAppContext;
    private final ToolHost mHost;
    private final ToolBarView mToolBar;
    private final ToolPanelOverlay mOverlay;
    private final SharedPreferences mPrefs;

    private final LinkedHashMap<String, ToolMode> mModes = new LinkedHashMap<>();
    @Nullable
    private ToolMode mActive;
    private boolean mPanelExpanded;

    public ToolManager(@NonNull Context context, @NonNull ToolHost host,
            @Nullable ToolBarView toolBar, @Nullable ToolPanelOverlay overlay) {
        mAppContext = context.getApplicationContext();
        mHost = host;
        mToolBar = toolBar;
        mOverlay = overlay;
        mPrefs = mAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (mToolBar != null) {
            mToolBar.setListener(new ToolBarView.Listener() {
                @Override
                public void onToolChipClicked(@NonNull String toolId) {
                    activate(toolId);
                }

                @Override
                public void onMoreClicked() {
                    togglePanel();
                }
            });
        }
        if (mOverlay != null) {
            mOverlay.setListener(toolId -> activate(toolId));
        }
    }

    /** Register a tool. Call before {@link #init()}. */
    public void register(@NonNull ToolMode mode) {
        mModes.put(mode.getId(), mode);
    }

    /** Finalize setup: select the calculator mode as active and render the UI. */
    public void init() {
        mActive = mModes.get(ToolId.CALCULATOR);
        Log.d("ToolDebug", "init: active=" + (mActive == null ? "null" : mActive.getId())
                + " overlay=" + (mOverlay != null)
                + " overlayVis=" + (mOverlay == null ? "null" : mOverlay.getVisibility())
                + " panelExpanded=" + mPanelExpanded);
        refresh();
    }

    @Nullable
    public ToolMode getActive() {
        return mActive;
    }

    /** True when a non-calculator tool is active (pad input is routed to the tool). */
    public boolean isActive() {
        return mActive != null && !mActive.getId().equals(ToolId.CALCULATOR);
    }

    /** All registered tools except the calculator (shown in the "more" grid). */
    @NonNull
    public List<ToolMode> getMoreItems() {
        List<ToolMode> result = new ArrayList<>();
        for (ToolMode mode : mModes.values()) {
            if (!mode.getId().equals(ToolId.CALCULATOR)) {
                result.add(mode);
            }
        }
        return result;
    }

    /** Ordered toolbar items: calculator first, then the pinned (top-frequency) tools. */
    @NonNull
    public List<ToolMode> getToolbarItems() {
        List<ToolMode> items = new ArrayList<>();
        final ToolMode calc = mModes.get(ToolId.CALCULATOR);
        if (calc != null) {
            items.add(calc);
        }
        items.addAll(getPinned());
        return items;
    }

    private List<ToolMode> getPinned() {
        final Map<String, Integer> freq = readFrequencies();
        List<ToolMode> tools = getMoreItems();
        Collections.sort(tools, new Comparator<ToolMode>() {
            @Override
            public int compare(ToolMode a, ToolMode b) {
                return Integer.compare(score(b.getId(), freq), score(a.getId(), freq));
            }
        });
        return tools.subList(0, Math.min(PINNED_COUNT, tools.size()));
    }

    private int score(@NonNull String id, @NonNull Map<String, Integer> freq) {
        final int recorded = freq.containsKey(id) ? freq.get(id) : defaultFrequency(id);
        return recorded;
    }

    /** First-run seed so the bar is not empty before the user has used any tool. */
    private int defaultFrequency(@NonNull String id) {
        if (ToolId.UNIT.equals(id) || ToolId.CURRENCY.equals(id)) {
            return 1;
        }
        return 0;
    }

    private Map<String, Integer> readFrequencies() {
        final Map<String, Integer> freq = new LinkedHashMap<>();
        final Map<String, ?> all = mPrefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(FREQ_PREFIX) && entry.getValue() instanceof Integer) {
                freq.put(key.substring(FREQ_PREFIX.length()), (Integer) entry.getValue());
            }
        }
        return freq;
    }

    private void recordUse(@NonNull String id) {
        final int next = mPrefs.getInt(FREQ_PREFIX + id, defaultFrequency(id)) + 1;
        mPrefs.edit().putInt(FREQ_PREFIX + id, next).apply();
    }

    /** Switch to the given tool, carrying a value from the calculator display when applicable. */
    public void activate(@NonNull String id) {
        final ToolMode next = mModes.get(id);
        if (next == null) {
            return;
        }
        if (next == mActive) {
            return;
        }
        final ToolMode prev = mActive;

        // Carry a value only when leaving the calculator (a clean numeric context).
        final String carry = (prev == null || ToolId.CALCULATOR.equals(prev.getId()))
                ? mHost.getCurrentDisplayNumber()
                : null;

        if (prev != null) {
            prev.onDeactivate(mHost);
        }

        final boolean toCalc = ToolId.CALCULATOR.equals(id);

        // Grow the display (hide the scientific pad) BEFORE the tool mounts its controls and
        // writes its result, so they are laid out in the larger display and don't overflow.
        mHost.setExpandedDisplay(next.wantsExpandedDisplay());

        if (!toCalc) {
            mHost.prepareForToolDisplay();
        }

        mActive = next;
        next.onActivate(mHost, carry);

        if (toCalc) {
            mHost.restoreCalculatorDisplay();
        } else {
            recordUse(id);
        }

        collapsePanel();
        refresh();
    }

    public void backToCalculator() {
        activate(ToolId.CALCULATOR);
    }

    /**
     * Route a pad button press. Returns {@code true} if consumed by the active tool (i.e. a
     * non-calculator mode is active); {@code false} to let the calculator handle it normally.
     */
    public boolean handlePadClick(int viewId) {
        if (!isActive() || mActive == null) {
            return false;
        }
        final ToolMode mode = mActive;
        if (viewId == R.id.clr) {
            mode.onClear();
            return true;
        }
        if (viewId == R.id.del) {
            mode.onDelete();
            return true;
        }
        final int digit = KeyMaps.digVal(viewId);
        if (digit != KeyMaps.NOT_DIGIT) {
            mode.onDigit(digit);
            return true;
        }
        if (viewId == R.id.dec_point) {
            mode.onDecimalPoint();
            return true;
        }
        // Operators / equals / scientific keys are irrelevant in tool mode: consume silently.
        return true;
    }

    // ---- Overlay panel ----

    public void togglePanel() {
        Log.d("ToolDebug", "togglePanel: mPanelExpanded=" + mPanelExpanded);
        if (mPanelExpanded) {
            collapsePanel();
        } else {
            expandPanel();
        }
    }

    public void expandPanel() {
        Log.d("ToolDebug", "expandPanel: mPanelExpanded=" + mPanelExpanded
                + " overlay=" + (mOverlay != null));
        if (mPanelExpanded || mOverlay == null) {
            mPanelExpanded = true;
            refresh();
            return;
        }
        mPanelExpanded = true;
        mOverlay.expand();
        Log.d("ToolDebug", "expandPanel: after expand(), overlayVis=" + mOverlay.getVisibility());
        refresh();
    }

    public void collapsePanel() {
        Log.d("ToolDebug", "collapsePanel: mPanelExpanded=" + mPanelExpanded);
        if (!mPanelExpanded) {
            return;
        }
        mPanelExpanded = false;
        if (mOverlay != null) {
            mOverlay.collapse();
            Log.d("ToolDebug", "collapsePanel: after collapse(), overlayVis=" + mOverlay.getVisibility());
        }
        refresh();
    }

    public boolean isPanelExpanded() {
        return mPanelExpanded;
    }

    /** Consume back-press to close the panel first. Returns true if handled. */
    public boolean onBackPressed() {
        if (mPanelExpanded) {
            collapsePanel();
            return true;
        }
        return false;
    }

    private void refresh() {
        final String activeId = mActive == null ? null : mActive.getId();
        if (mToolBar != null) {
            mToolBar.render(getToolbarItems(), activeId, mPanelExpanded);
        }
        if (mOverlay != null) {
            mOverlay.render(getMoreItems());
        }
    }
}
