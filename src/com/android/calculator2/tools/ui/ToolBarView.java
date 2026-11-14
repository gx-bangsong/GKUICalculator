/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * The persistent mode strip shown between the display and the pads (collapsed state).
 * <p>
 * Unlike the earlier chip-only bar, the calculator is no longer one chip among many. The bar is
 * led by a segmented control modelled on Google Calculator v10:
 * <ul>
 *   <li>{@code 123} — the standard keypad (and the calculator itself)</li>
 *   <li>{@code fx} — the scientific pad, without leaving the calculator</li>
 *   <li>{@code ⇄} — the converters (units / currency)</li>
 * </ul>
 * The tool pills are only inflated while a life-calculation tool is active, so the calculator
 * state shows a single control instead of a scrolling list, and the trailing "more" button opens
 * the grid of remaining tools.
 */
public class ToolBarView extends LinearLayout {

    /**
     * Callbacks fired by user interaction with the bar. The 123 / fx / ⇄ segments are wired with
     * {@code android:onClick="onModeSegmentClick"} in the layout, because only the activity knows
     * both the active tool and the pad state.
     */
    public interface Listener {
        void onToolChipClicked(@NonNull String toolId);

        void onMoreClicked();
    }

    private LinearLayout mChipsContainer;
    private ViewGroup mChipsScroll;
    private MaterialButton mMoreButton;
    private TextView mSegmentBasic;
    private TextView mSegmentScientific;
    private TextView mSegmentConvert;

    @Nullable
    private Listener mListener;
    private boolean mPanelExpanded;

    public ToolBarView(Context context) {
        this(context, null);
    }

    public ToolBarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mChipsContainer = findViewById(R.id.tool_chips_container);
        mChipsScroll = findViewById(R.id.tool_chips_scroll);
        mMoreButton = findViewById(R.id.tool_more_button);
        mMoreButton.setOnClickListener(v -> {
            Log.d("ToolDebug", "more button clicked");
            if (mListener != null) {
                mListener.onMoreClicked();
            }
        });
        mSegmentBasic = findViewById(R.id.mode_segment_basic);
        mSegmentScientific = findViewById(R.id.mode_segment_scientific);
        mSegmentConvert = findViewById(R.id.mode_segment_convert);
        updateMoreButton(/* animate */ false);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Rebuild the tool pills from the supplied list and reflect the panel state. Pills are only
     * inflated for real tools: in calculator mode Google shows no pills at all, and the
     * calculator itself is the {@code 123} segment rather than a pill.
     *
     * @param items         pinned tools (may include the calculator, which is skipped)
     * @param activeId      id of the active tool, or null in calculator mode
     * @param panelExpanded whether the "more" panel is open
     */
    public void render(@NonNull List<ToolMode> items, @Nullable String activeId,
            boolean panelExpanded) {
        mPanelExpanded = panelExpanded;
        updateMoreButton(/* animate */ true);

        final Context context = getContext();
        mChipsContainer.removeAllViews();
        final boolean toolActive = activeId != null && !ToolId.CALCULATOR.equals(activeId);
        mChipsScroll.setVisibility(toolActive ? VISIBLE : GONE);
        final int spacing = context.getResources()
                .getDimensionPixelOffset(R.dimen.tool_chip_spacing);
        for (ToolMode mode : items) {
            if (ToolId.CALCULATOR.equals(mode.getId())) {
                // The calculator is the 123 segment, never a pill.
                continue;
            }
            Chip chip = (Chip) LayoutInflater.from(context)
                    .inflate(R.layout.tool_chip, mChipsContainer, false);
            chip.setText(context.getString(mode.getNameRes()));
            chip.setChecked(mode.getId().equals(activeId));
            chip.setOnClickListener(v -> {
                Log.d("ToolDebug", "chip clicked: " + mode.getId());
                if (mListener != null) {
                    mListener.onToolChipClicked(mode.getId());
                }
            });
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) chip.getLayoutParams();
            lp.setMarginStart(spacing);
            lp.setMarginEnd(spacing);
            chip.setLayoutParams(lp);
            mChipsContainer.addView(chip);
        }
    }

    /**
     * Reflects which surface is on screen. The activity owns this state because only it knows
     * both the active tool and whether the scientific pad is actually visible.
     */
    public void setModeSelection(boolean basic, boolean scientific, boolean convert) {
        setSegmentSelected(mSegmentBasic, basic);
        setSegmentSelected(mSegmentScientific, scientific);
        setSegmentSelected(mSegmentConvert, convert);
    }

    /** Selection drives both the segment background and its label colour. */
    private void setSegmentSelected(@Nullable TextView segment, boolean checked) {
        if (segment != null && segment.isSelected() != checked) {
            segment.setSelected(checked);
        }
    }

    private void updateMoreButton(boolean animate) {
        mMoreButton.setContentDescription(getContext().getString(
                mPanelExpanded ? R.string.tool_more_close : R.string.tool_more));
        final float target = mPanelExpanded ? 180f : 0f;
        if (animate) {
            mMoreButton.animate().rotation(target)
                    .setDuration(getResources().getInteger(
                            android.R.integer.config_shortAnimTime))
                    .start();
        } else {
            mMoreButton.setRotation(target);
        }
    }
}
