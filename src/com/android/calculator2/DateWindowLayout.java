/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.graphics.Insets;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintSet;

/** Date-only presentation. Its numeric pad has no date actions; never move other modes' keys. */
final class DateWindowLayout {
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final LinearLayout display,content,shared;
    private final View toolbar,bar,pad,formulaContainer;
    private final TextView formula,result;
    private final ViewGroup.LayoutParams contentParams,sharedParams,formulaParams,textParams,resultParams;
    private final int formulaMinimum,resultLines;
    private ScrollView viewport;
    private ConstraintSet original;
    private boolean enabled,compact;
    private int lastWidth,lastHeight,lastLeft,lastRight;

    DateWindowLayout(Calculator a) {
        activity=a;motion=a.findViewById(R.id.main_calculator);display=a.findViewById(R.id.display);
        content=a.findViewById(R.id.tool_display_content);shared=a.findViewById(R.id.shared_display_rows);
        toolbar=a.findViewById(R.id.toolbar);bar=a.findViewById(R.id.tool_bar);pad=a.findViewById(R.id.pad_stack);
        formulaContainer=a.findViewById(R.id.formula_scroll_view);formula=a.findViewById(R.id.formula);result=a.findViewById(R.id.result);
        contentParams=content.getLayoutParams();sharedParams=shared.getLayoutParams();formulaParams=formulaContainer.getLayoutParams();
        textParams=formula.getLayoutParams();resultParams=result.getLayoutParams();
        formulaMinimum=formulaContainer.getMinimumHeight();resultLines=result.getMaxLines();
        motion.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->schedule());
        content.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->schedule());
    }
    void setEnabled(boolean value) {
        enabled=value;motion.removeCallbacks(fitTask);
        if(value)schedule();else restore();
    }
    private final Runnable fitTask=this::fit;
    private void schedule(){if(enabled){motion.removeCallbacks(fitTask);motion.post(fitTask);}}
    private void fit() {
        if(!enabled||motion.getCurrentState()!=R.id.start_state||motion.getWidth()==0)return;
        int h=motion.getHeight()-motion.getPaddingTop()-motion.getPaddingBottom();
        int w=motion.getWidth()-motion.getPaddingLeft()-motion.getPaddingRight();
        Insets insets=motion.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        Rect window=activity.getWindowManager().getCurrentWindowMetrics().getBounds();int[] origin=new int[2];motion.getLocationOnScreen(origin);
        int left=Math.max(0,window.left+insets.left-origin[0]),right=Math.max(0,origin[0]+motion.getWidth()-(window.right-insets.right));
        w-=left+right;
        android.util.TypedValue ratio=new android.util.TypedValue();activity.getResources().getValue(R.dimen.layout_pads_ratio,ratio,true);
        float d=activity.getResources().getDisplayMetrics().density;
        int barHeight=activity.getResources().getDimensionPixelSize(R.dimen.mode_segment_height)+bar.getPaddingTop()+bar.getPaddingBottom();
        int handle=activity.findViewById(R.id.drag_handle_view).getHeight()+2*activity.getResources().getDimensionPixelSize(R.dimen.drag_handle_margin);
        int required=toolbar.getHeight()+Math.round(48*d)+formula.getLineHeight()+result.getLineHeight()+handle;
        android.util.TypedValue barRatio=new android.util.TypedValue();activity.getResources().getValue(R.dimen.layout_tool_bar_ratio,barRatio,true);
        int normalBar=activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT
                ?Math.round(h*barRatio.getFloat()):barHeight;
        int normalDisplay=h-Math.round(h*ratio.getFloat())-normalBar;
        if(normalDisplay>=required){restore();return;}
        if(w<=0||h<=barHeight)return;
        if(!compact) {
            original=new ConstraintSet();original.clone(motion.getConstraintSet(R.id.start_state));
            viewport=new ScrollView(activity);viewport.setTag("field_tool_viewport");
            viewport.setFillViewport(false);viewport.setVerticalScrollBarEnabled(true);viewport.setScrollbarFadingEnabled(false);
            display.removeView(content);content.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            shared.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            formulaContainer.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            ViewGroup.LayoutParams text=new ViewGroup.LayoutParams(textParams);text.height=-2;formula.setLayoutParams(text);
            ((CalculatorFormula)formula).setWrapToolOutput(true);((CalculatorScrollView)formulaContainer).setWrapChildToViewport(true);
            formulaContainer.setMinimumHeight(formula.getLineHeight()+formula.getPaddingTop()+formula.getPaddingBottom());
            result.setSingleLine(false);result.setMaxLines(Integer.MAX_VALUE);result.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            viewport.addView(content,new ScrollView.LayoutParams(-1,-2));
            display.addView(viewport,1,new LinearLayout.LayoutParams(-1,0,1));
            compact=true;pad.setVisibility(View.GONE);
        }
        if(w==lastWidth&&h==lastHeight&&left==lastLeft&&right==lastRight)return;
        ConstraintSet state=new ConstraintSet();state.clone(original);
        for(int id:new int[]{R.id.display,R.id.tool_bar,R.id.pad_stack})
            for(int side:new int[]{ConstraintSet.TOP,ConstraintSet.BOTTOM,ConstraintSet.LEFT,ConstraintSet.RIGHT,ConstraintSet.START,ConstraintSet.END})state.clear(id,side);
        state.constrainWidth(R.id.display,w);state.constrainHeight(R.id.display,h-barHeight);
        state.connect(R.id.display,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
        state.connect(R.id.display,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
        state.constrainWidth(R.id.tool_bar,w);state.constrainHeight(R.id.tool_bar,barHeight);
        state.connect(R.id.tool_bar,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
        state.connect(R.id.tool_bar,ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM);
        state.setVisibility(R.id.pad_stack,View.GONE);state.constrainHeight(R.id.pad_stack,0);
        lastWidth=w;lastHeight=h;lastLeft=left;lastRight=right;
        motion.updateState(R.id.start_state,state);
    }
    private void restore() {
        if(!compact)return;
        compact=false;lastWidth=lastHeight=0;
        viewport.removeView(content);display.removeView(viewport);viewport=null;
        display.addView(content,1,contentParams);shared.setLayoutParams(sharedParams);
        formulaContainer.setLayoutParams(formulaParams);formula.setLayoutParams(textParams);result.setLayoutParams(resultParams);
        formulaContainer.setMinimumHeight(formulaMinimum);
        ((CalculatorFormula)formula).setWrapToolOutput(false);((CalculatorScrollView)formulaContainer).setWrapChildToViewport(false);
        result.setSingleLine(true);result.setMaxLines(resultLines);
        pad.setVisibility(View.VISIBLE);motion.updateState(R.id.start_state,original);
    }
}
