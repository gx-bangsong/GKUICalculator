/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** Window-local presentation only. The same history container/fragment moves between hosts. */
final class AdaptiveHistoryPane {
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final FrameLayout root;
    private final View history;
    private final ViewGroup.LayoutParams originalParams;
    private final int originalIndex;
    private final View divider;
    private boolean wide;

    AdaptiveHistoryPane(Calculator a) {
        activity=a;motion=a.findViewById(R.id.main_calculator);
        root=(FrameLayout)motion.getParent();history=a.findViewById(R.id.history_frame);
        originalParams=history.getLayoutParams();originalIndex=motion.indexOfChild(history);
        divider=new View(a);divider.setTag("history_column_divider");
        divider.setBackgroundColor(a.getColor(R.color.history_background_color));
        divider.setVisibility(View.GONE);root.addView(divider);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->scheduleFit());
        scheduleFit();
    }

    boolean isWide(){return wide;}
    void scheduleFit(){root.removeCallbacks(fitTask);root.post(fitTask);}
    private final Runnable fitTask=this::fit;

    private void fit() {
        if(root.getWidth()==0||root.getHeight()==0||activity.isFinishing()||activity.isDestroyed())return;
        Insets bars=activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets()
                .getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int width=root.getWidth()-bars.left-bars.right,height=root.getHeight()-bars.top-bars.bottom;
        float d=root.getResources().getDisplayMetrics().density;
        boolean target=width>=Math.ceil(900*d)&&height>=Math.ceil(600*d);
        if(target!=wide) {
            HistoryFragment fragment=activity.getHistoryFragment();
            if(fragment!=null)fragment.captureScroll();
            wide=target;
            // Close any modal motion before moving its host, without touching tool state.
            motion.jumpToState(R.id.start_state);
            ((ViewGroup)history.getParent()).removeView(history);
            if(wide)root.addView(history,0,new FrameLayout.LayoutParams(1,1));
            else motion.addView(history,originalIndex,originalParams);
            motion.rebuildScene();
            activity.onHistoryDockChanged();
        }
        int side=wide?Math.min(Math.round(360*d),Math.round(width*.32f)):0;
        int gap=wide?Math.max(1,Math.round(d)):0;
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)motion.getLayoutParams();
        int left=wide?bars.left+side+gap:0,right=wide?bars.right:0;
        if(p.leftMargin!=left||p.rightMargin!=right) {
            p.leftMargin=left;p.rightMargin=right;motion.setLayoutParams(p);
        }
        if(wide) {
            frame(history,side,height,bars.left,bars.top);
            frame(divider,gap,height,bars.left+side,bars.top);
            divider.setVisibility(View.VISIBLE);
            activity.ensureDockedHistory();
        } else divider.setVisibility(View.GONE);
    }

    private void frame(View view,int width,int height,int left,int top) {
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)view.getLayoutParams();
        if(p.width!=width||p.height!=height||p.leftMargin!=left||p.topMargin!=top) {
            p.width=width;p.height=height;p.leftMargin=left;p.topMargin=top;view.setLayoutParams(p);
        }
    }
}
