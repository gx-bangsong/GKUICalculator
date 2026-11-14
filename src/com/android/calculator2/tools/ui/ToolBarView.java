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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;

import com.google.android.material.button.MaterialButton;

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

    private MaterialButton mMoreButton;
    private TextView mSegmentBasic;
    private TextView mSegmentScientific;
    private TextView mSegmentConvert;
    private LinearLayout mToolSegments;

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
        mToolSegments = findViewById(R.id.tool_segments_container);
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
     * Rebuild the tool segments and the trailing button state.
     * <p>
     * Every registered tool becomes an <em>icon-only</em> segment in the same strip as
     * {@code 123 / fx / ⇄}, mirroring how Google Calculator v10 keeps one mode control for the
     * whole app instead of a separate chip row. The three core segments are declared in
     * {@code tool_bar.xml}; everything else — the programmer calculator and the life-calculation
     * tools (BMI, income tax, mortgage, date, relatives) — is built here from
     * {@link ToolMode#getIconRes()}. {@code Units} and {@code Currency} are the {@code ⇄}
     * segment, so they are not repeated.
     *
     * @param items         registered tools (the calculator is skipped — it *is* the 123 segment)
     * @param activeId      id of the active tool, or null in calculator mode
     * @param panelExpanded whether the "more" panel is open
     */
    public void render(@NonNull List<ToolMode> items, @Nullable String activeId,
            boolean panelExpanded) {
        mPanelExpanded = panelExpanded;
        updateMoreButton(/* animate */ true);

        final Context context = getContext();
        mToolSegments.removeAllViews();
        for (ToolMode mode : items) {
            final String id = mode.getId();
            if (ToolId.CALCULATOR.equals(id) || ToolId.UNIT.equals(id)
                    || ToolId.CURRENCY.equals(id)) {
                // The 123, fx and two-way-arrow segments already stand for these.
                continue;
            }
            final int iconRes = mode.getIconRes();
            final TextView segment = (TextView) LayoutInflater.from(context)
                    .inflate(R.layout.tool_segment, mToolSegments, false);
            segment.setText("");
            segment.setContentDescription(context.getString(mode.getNameRes()));
            if (iconRes != 0) {
                segment.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        iconRes, 0, 0, 0);
                // compound drawables are centred by gravity; size them like the fixed segments
                final int inset = context.getResources()
                        .getDimensionPixelSize(R.dimen.mode_segment_icon_inset);
                segment.setCompoundDrawablePadding(0);
                segment.setPadding(inset, inset, inset, inset);
            }
            segment.setSelected(id.equals(activeId));
            final String toolId = id;
            segment.setOnClickListener(v -> {
                Log.d("ToolDebug", "tool segment clicked: " + toolId);
                if (mListener != null) {
                    mListener.onToolChipClicked(toolId);
                }
            });
            mToolSegments.addView(segment);
        }

        // Hide the whole group (and the gap it would leave) when there is nothing to show.
        final View scroll = findViewById(R.id.tool_segments_scroll);
        if (scroll != null) {
            scroll.setVisibility(mToolSegments.getChildCount() > 0 ? VISIBLE : GONE);
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
