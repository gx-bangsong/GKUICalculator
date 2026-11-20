/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** Window-local presentation only; never reactivates a tool or replaces the history fragment. */
final class AdaptiveHistoryPane {
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final FrameLayout root;
    private final FrameLayout modalHost;
    private final FrameLayout sidebar;
    private final View divider;
    private boolean wide;

    AdaptiveHistoryPane(Calculator activity) {
        this.activity = activity;
        motion = activity.findViewById(R.id.main_calculator);
        root = (FrameLayout) motion.getParent();
        modalHost = activity.findViewById(R.id.history_frame);
        sidebar = new FrameLayout(activity);
        sidebar.setTag("persistent_history_host");
        sidebar.setBackgroundColor(activity.getColor(R.color.history_background_color));
        sidebar.setVisibility(View.GONE);
        root.addView(sidebar, 0, new FrameLayout.LayoutParams(1, 1));
        divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.history_background_color));
        divider.setVisibility(View.GONE);
        root.addView(divider, new FrameLayout.LayoutParams(1, 1));
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> scheduleFit());
        scheduleFit();
    }

    boolean isWide() { return wide; }

    void scheduleFit() {
        root.removeCallbacks(fitTask);
        root.post(fitTask);
    }

    private final Runnable fitTask = this::fit;

    void placeHistoryView() {
        HistoryFragment fragment = activity.getHistoryFragment();
        if (fragment == null || fragment.getView() == null) return;
        View view = fragment.getView();
        FrameLayout target = wide ? sidebar : modalHost;
        if (view.getParent() == target) return;
        fragment.captureScroll();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        // Keep history_frame in MotionLayout: its cached constraint widgets must never refer to
        // a child whose LayoutParams have been converted to a different parent's type.
        target.addView(view, new FrameLayout.LayoutParams(-1, -1));
        fragment.restoreScroll();
    }

    private void fit() {
        if (root.getWidth() == 0 || root.getHeight() == 0
                || activity.isFinishing() || activity.isDestroyed()) return;
        Insets bars = activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets()
                .getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int width = root.getWidth() - bars.left - bars.right;
        int height = root.getHeight() - bars.top - bars.bottom;
        float density = root.getResources().getDisplayMetrics().density;
        boolean target = width >= Math.ceil(900 * density) && height >= Math.ceil(600 * density);
        if (target != wide) {
            HistoryFragment fragment = activity.getHistoryFragment();
            if (fragment != null) fragment.captureScroll();
            wide = target;
            motion.jumpToState(R.id.start_state);
            activity.onHistoryDockChanged();
        }
        int side = wide ? Math.min(Math.round(360 * density), Math.round(width * .32f)) : 0;
        int gap = wide ? Math.max(1, Math.round(density)) : 0;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) motion.getLayoutParams();
        int left = wide ? bars.left + side + gap : 0;
        int right = wide ? bars.right : 0;
        if (params.leftMargin != left || params.rightMargin != right) {
            params.leftMargin = left;
            params.rightMargin = right;
            motion.setLayoutParams(params);
        }
        if (wide) {
            frame(sidebar, side, height, bars.left, bars.top);
            frame(divider, gap, height, bars.left + side, bars.top);
            sidebar.setVisibility(View.VISIBLE);
            divider.setVisibility(View.VISIBLE);
            activity.ensureDockedHistory();
        } else {
            placeHistoryView();
            sidebar.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }
    }

    private void frame(View view, int width, int height, int left, int top) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (params.width != width || params.height != height
                || params.leftMargin != left || params.topMargin != top) {
            params.width = width;
            params.height = height;
            params.leftMargin = left;
            params.topMargin = top;
            view.setLayoutParams(params);
        }
    }
}
