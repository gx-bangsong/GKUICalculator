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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;

import java.util.List;

/**
 * The persistent mode strip shown between the display and the pads (collapsed state).
 * <p>
 * The control is a single pill modelled on Google Calculator v10, led by three text segments:
 * <ul>
 *   <li>{@code 123} — the standard keypad (and the calculator itself)</li>
 *   <li>{@code fx} — the scientific pad, without leaving the calculator</li>
 *   <li>{@code ⇄} — the converters (units / currency)</li>
 * </ul>
 * The tool icons are appended to that same pill at runtime. The pill fills the strip and its
 * segments share the width equally, so every mode is visible inside it at once; nothing sits
 * outside the pill.
 */
public class ToolBarView extends LinearLayout {

    /** 123 / fx / ⇄ are declared in the layout; generated tool segments follow them. */
    private static final int CORE_SEGMENT_COUNT = 3;

    /**
     * Callbacks fired by user interaction with the bar. The 123 / fx / ⇄ segments are wired with
     * {@code android:onClick="onModeSegmentClick"} in the layout, because only the activity knows
     * both the active tool and the pad state.
     */
    public interface Listener {
        void onToolChipClicked(@NonNull String toolId);
    }

    private TextView mSegmentBasic;
    private TextView mSegmentScientific;
    private TextView mSegmentConvert;
    private LinearLayout mModeSegments;
    private HorizontalScrollView mModeSegmentsScroll;

    @Nullable
    private Listener mListener;

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
        mModeSegments = findViewById(R.id.mode_segments);
        mModeSegmentsScroll = findViewById(R.id.mode_segments_scroll);
        mSegmentBasic = findViewById(R.id.mode_segment_basic);
        mSegmentScientific = findViewById(R.id.mode_segment_scientific);
        mSegmentConvert = findViewById(R.id.mode_segment_convert);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Rebuild the tool segments.
     * <p>
     * Every registered tool becomes an <em>icon-only</em> segment appended to the one pill that
     * already holds {@code 123 / fx / ⇄}. The three core segments are declared in
     * {@code tool_bar.xml}; everything else — the programmer calculator and the life-calculation
     * tools (BMI, income tax, mortgage, date, relatives) — is built here from
     * {@link ToolMode#getIconRes()} and appended to the same {@code LinearLayout}, so all eight
     * modes live in a single track. {@code Units} and {@code Currency} are the {@code ⇄}
     * segment, so they are not repeated.
     *
     * @param items         registered tools (the calculator is skipped — it *is* the 123 segment)
     * @param activeId      id of the active tool, or null in calculator mode
     */
    public void render(@NonNull List<ToolMode> items, @Nullable String activeId) {
        final Context context = getContext();
        // Drop only the generated segments; the declared 123 / fx / ⇄ children stay put. They
        // are the first three children, so remove everything after them.
        while (mModeSegments.getChildCount() > CORE_SEGMENT_COUNT) {
            mModeSegments.removeViewAt(mModeSegments.getChildCount() - 1);
        }
        for (ToolMode mode : items) {
            final String id = mode.getId();
            if (ToolId.CALCULATOR.equals(id) || ToolId.UNIT.equals(id)
                    || ToolId.CURRENCY.equals(id)) {
                // The 123, fx and two-way-arrow segments already stand for these.
                continue;
            }
            final int iconRes = mode.getIconRes();
            final ImageView segment = (ImageView) LayoutInflater.from(context)
                    .inflate(R.layout.tool_segment, mModeSegments, false);
            segment.setContentDescription(context.getString(mode.getNameRes()));
            if (iconRes != 0) {
                segment.setImageResource(iconRes);
            }
            segment.setSelected(id.equals(activeId));
            final String toolId = id;
            segment.setOnClickListener(v -> {
                Log.d("ToolDebug", "tool segment clicked: " + toolId);
                if (mListener != null) {
                    mListener.onToolChipClicked(toolId);
                }
            });
            mModeSegments.addView(segment);
        }
        // Only matters on a window too narrow to fit every segment: keep the active one visible.
        scrollSelectionIntoView();
    }

    /**
     * Scrolls the pill so the selected segment is fully visible. Cheap and idempotent: the scroll
     * only moves when the selection is actually outside the viewport.
     */
    private void scrollSelectionIntoView() {
        if (mModeSegmentsScroll == null) {
            return;
        }
        final View selected = findSelectedSegment();
        if (selected == null) {
            return;
        }
        mModeSegmentsScroll.post(() -> {
            final int left = selected.getLeft();
            final int right = selected.getRight();
            final int viewport = mModeSegmentsScroll.getWidth()
                    - mModeSegmentsScroll.getPaddingStart()
                    - mModeSegmentsScroll.getPaddingEnd();
            if (viewport <= 0 || right - left > viewport) {
                return;
            }
            final int visibleLeft = mModeSegmentsScroll.getScrollX();
            final int visibleRight = visibleLeft + viewport;
            if (left < visibleLeft) {
                mModeSegmentsScroll.smoothScrollTo(left, 0);
            } else if (right > visibleRight) {
                mModeSegmentsScroll.smoothScrollTo(right - viewport, 0);
            }
        });
    }

    @Nullable
    private View findSelectedSegment() {
        for (int i = 0; i < mModeSegments.getChildCount(); i++) {
            final View child = mModeSegments.getChildAt(i);
            if (child.isSelected()) {
                return child;
            }
        }
        return null;
    }

    /**
     * Reflects which surface is on screen. The activity owns this state because only it knows
     * both the active tool and whether the scientific pad is actually visible.
     */
    public void setModeSelection(boolean basic, boolean scientific, boolean convert) {
        setSegmentSelected(mSegmentBasic, basic);
        setSegmentSelected(mSegmentScientific, scientific);
        setSegmentSelected(mSegmentConvert, convert);
        scrollSelectionIntoView();
    }

    /** Selection drives both the segment background and its label colour. */
    private void setSegmentSelected(@Nullable TextView segment, boolean checked) {
        if (segment != null && segment.isSelected() != checked) {
            segment.setSelected(checked);
        }
    }
}
