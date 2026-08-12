/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolMode;

import com.google.android.material.chip.Chip;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * The persistent tool bar shown between the display and the pads (collapsed state).
 * <p>
 * A horizontally scrollable row of chips (always led by the calculator chip, followed by the
 * most-frequently-used tools — or every tool on tablets/unfolded foldables) plus a trailing
 * "more" button that toggles the overlay panel.
 * The active mode's chip is highlighted; tapping the active chip returns to the calculator.
 */
public class ToolBarView extends LinearLayout {

    /** Callbacks fired by user interaction with the bar. */
    public interface Listener {
        void onToolChipClicked(@NonNull String toolId);

        void onMoreClicked();
    }

    private LinearLayout mChipsContainer;
    private MaterialButton mMoreButton;

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
        mMoreButton = findViewById(R.id.tool_more_button);
        mMoreButton.setOnClickListener(v -> {
            Log.d("ToolDebug", "more button clicked");
            if (mListener != null) {
                mListener.onMoreClicked();
            }
        });
        updateMoreButton(/* animate */ false);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * Rebuild the chips from the supplied ordered list and reflect the active mode + panel state.
     * The list is expected to start with the calculator mode.
     */
    public void render(@NonNull List<ToolMode> items, @Nullable String activeId,
            boolean panelExpanded) {
        mPanelExpanded = panelExpanded;
        updateMoreButton(/* animate */ true);

        final Context context = getContext();
        final int spacing = context.getResources()
                .getDimensionPixelOffset(R.dimen.tool_chip_spacing);
        mChipsContainer.removeAllViews();
        for (ToolMode mode : items) {
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
