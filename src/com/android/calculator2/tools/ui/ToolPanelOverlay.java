/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.gridlayout.widget.GridLayout;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolMode;

import java.util.List;

/**
 * The "more" panel: a grid of all tools that <b>covers the pad area</b> when expanded.
 * <p>
 * It is a sibling of the MotionLayout (a child of the root FrameLayout) and is laid out to span
 * the lower part of the screen (below a guideline), so that animating its translation Y slides it
 * over the pads. Selection of any item collapses the panel and activates the tool.
 */
public class ToolPanelOverlay extends ConstraintLayout {

    private static final String TAG = "ToolDebug";
    private static final long ANIM_DURATION_MS = 280L;

    /** Callback fired when a tool is selected from the panel. */
    public interface Listener {
        void onToolSelected(@NonNull String toolId);
    }

    private GridLayout mGrid;
    private TextView mHint;
    @Nullable
    private Listener mListener;
    private boolean mExpanded;

    public ToolPanelOverlay(Context context) {
        this(context, null);
    }

    public ToolPanelOverlay(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolPanelOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setVisibility(GONE);
        Log.d(TAG, "ToolPanelOverlay constructed; visibility set to GONE");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mGrid = findViewById(R.id.tool_panel_grid);
        mHint = findViewById(R.id.tool_panel_hint);
        Log.d(TAG, "ToolPanelOverlay onFinishInflate; grid=" + mGrid + " hint=" + mHint
                + " visibility=" + getVisibility());
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Populate the grid with all (non-calculator) tools. */
    public void render(@NonNull List<ToolMode> tools) {
        final Context context = getContext();
        mGrid.removeAllViews();
        final int columns = (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) ? 4 : 3;
        mGrid.setColumnCount(columns);

        if (tools.isEmpty()) {
            mHint.setVisibility(VISIBLE);
            mGrid.setVisibility(GONE);
            return;
        }
        mHint.setVisibility(GONE);
        mGrid.setVisibility(VISIBLE);

        for (ToolMode mode : tools) {
            View item = LayoutInflater.from(context)
                    .inflate(R.layout.tool_panel_item, mGrid, false);
            TextView text = item.findViewById(R.id.tool_panel_item_text);
            text.setText(context.getString(mode.getNameRes()));
            View icon = item.findViewById(R.id.tool_panel_item_icon);
            final int iconRes = mode.getIconRes();
            if (iconRes != 0) {
                icon.setVisibility(VISIBLE);
            } else {
                icon.setVisibility(GONE);
            }
            item.setContentDescription(context.getString(mode.getNameRes()));
            item.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onToolSelected(mode.getId());
                }
            });
            mGrid.addView(item);
        }
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    /** Slide the panel into view, covering the pads. */
    public void expand() {
        Log.d(TAG, "expand: called; mExpanded=" + mExpanded + " visibility=" + getVisibility());
        if (mExpanded) {
            return;
        }
        mExpanded = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(slideDistance());
        Log.d(TAG, "expand: set VISIBLE; animating in");
        animate().translationY(0f).alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(easing())
                .start();
    }

    /** Slide the panel back down off-screen and hide it. */
    public void collapse() {
        Log.d(TAG, "collapse: called; mExpanded=" + mExpanded + " visibility=" + getVisibility());
        if (!mExpanded) {
            return;
        }
        mExpanded = false;
        final float slide = slideDistance();
        animate().translationY(slide).alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .setInterpolator(easing())
                .withEndAction(() -> {
                    setVisibility(GONE);
                    Log.d(TAG, "collapse: endAction -> GONE; visibility=" + getVisibility());
                })
                .start();
    }

    private float slideDistance() {
        final float h = Math.max(getHeight(), getMeasuredHeight());
        return h > 0f ? h : getResources().getDimension(R.dimen.tool_panel_slide);
    }

    private Interpolator easing() {
        return android.view.animation.AnimationUtils.loadInterpolator(
                getContext(), android.R.interpolator.fast_out_slow_in);
    }
}
