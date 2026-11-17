/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.KeyMaps;
import com.android.calculator2.R;
import com.android.calculator2.tools.ui.ToolBarView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Registry and controller for tool modes.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Hold registered {@link ToolMode}s and the currently active one.</li>
 *   <li>Switch modes (with value carry-over and display hand-off via {@link ToolHost}).</li>
 *   <li>Order the toolbar chips by usage frequency (SharedPreferences) plus first-run defaults.</li>
 *   <li>Route pad input to the active tool.</li>
 * </ul>
 * The calculator's own mode is the default; while it is active this manager stays out of the way
 * and the existing engine handles input/display.
 */
public class ToolManager {

    private final Context mAppContext;
    private final ToolHost mHost;
    private final ToolBarView mToolBar;

    private final LinkedHashMap<String, ToolMode> mModes = new LinkedHashMap<>();
    @Nullable
    private ToolMode mActive;

    public ToolManager(@NonNull Context context, @NonNull ToolHost host,
            @Nullable ToolBarView toolBar) {
        mAppContext = context.getApplicationContext();
        mHost = host;
        mToolBar = toolBar;

        if (mToolBar != null) {
            mToolBar.setListener(new ToolBarView.Listener() {
                @Override
                public void onToolChipClicked(@NonNull String toolId) {
                    activate(toolId);
                }
            });
        }
    }

    /** Register a tool. Call before {@link #init()}. */
    public void register(@NonNull ToolMode mode) {
        mModes.put(mode.getId(), mode);
    }

    /** Finalize setup: select the calculator mode as active and render the UI. */
    public void init() {
        mActive = mModes.get(ToolId.CALCULATOR);
        Log.d("ToolDebug", "init: active=" + (mActive == null ? "null" : mActive.getId()));
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

    /**
     * Mode-pill items: the calculator, then every registered tool in registration order. The
     * pill is the only tool switcher, so nothing is left out and the order never changes.
     */
    @NonNull
    public List<ToolMode> getToolbarItems() {
        return new ArrayList<>(mModes.values());
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
        // Publish the next mode before the host refreshes mode-dependent chrome/menu state.
        mActive = next;

        // Grow the display (hide the scientific pad) BEFORE the tool mounts its controls and
        // writes its result, so they are laid out in the larger display and don't overflow.
        mHost.setExpandedDisplay(next.wantsExpandedDisplay());

        if (!toCalc) {
            mHost.prepareForToolDisplay();
        }

        next.onActivate(mHost, carry);

        if (toCalc) {
            mHost.restoreCalculatorDisplay();
        }

        mHost.refreshOptionsMenu();
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
        if (mode.onPadKey(viewId)) {
            return true;
        }
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
        // Any operator / equals / scientific key not claimed by onPadKey is irrelevant to this
        // particular tool; consume it without leaking the press into the calculator evaluator.
        return true;
    }

    private void refresh() {
        final String activeId = mActive == null ? null : mActive.getId();
        if (mToolBar != null) {
            mToolBar.render(getToolbarItems(), activeId);
        }
    }
}
