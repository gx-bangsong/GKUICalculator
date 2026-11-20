/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.graphics.Insets;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/** Calculator/science/kinship only. Keep original pad groups and keys; scroll when they cannot fit. */
final class RemainingKeypadLayout {
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final LinearLayout stack;
    private final ViewGroup input,advanced;
    private final List<Label> labels=new ArrayList<>();
    private ScrollView viewport;
    private LinearLayout body;
    private ViewGroup.LayoutParams inputParams,advancedParams;
    private ConstraintSet normal,inputHome,advancedHome;
    private String lastDecision="";
    // A fit decision must not be recomputed from the grid it just mutated.
    private final int inputMarginX,inputMarginY,advancedMarginX,advancedMarginY;
    private String fittedGeometry="";
    private boolean enabled,compact,scientific;
    private int lastWidth,lastHeight,lastLeft,lastRight;
    private static final class Label {
        final TextView view;final float size;
        Label(TextView v){view=v;size=v.getTextSize();}
    }
    RemainingKeypadLayout(Calculator a) {
        activity=a;motion=a.findViewById(R.id.main_calculator);stack=a.findViewById(R.id.pad_stack);
        input=a.findViewById(R.id.input_pad);advanced=a.findViewById(R.id.advanced_pad);
        inputMarginX=measuredMargin(input,true);inputMarginY=measuredMargin(input,false);
        advancedMarginX=measuredMargin(advanced,true);advancedMarginY=measuredMargin(advanced,false);
        motion.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->schedule());
        stack.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(compact)sizeViewport();});
    }
    void setEnabled(boolean value,boolean science) {
        if(enabled==value&&scientific==science)return;
        if(scientific!=science)restore();
        fittedGeometry="";
        enabled=value;scientific=science;motion.removeCallbacks(fitTask);
        if(value)schedule();else restore();
    }
    private final Runnable fitTask=this::fit;
    private void schedule(){if(enabled){motion.removeCallbacks(fitTask);motion.post(fitTask);}}
    private final View.OnLayoutChangeListener labelListener=(v,l,t,r,b,ol,ot,or,ob)->{if(compact)style((TextView)v);};
    private void style(TextView v) {
        boolean science=v.getParent()==advanced;
        float px=activity.getResources().getDimension(science?R.dimen.remaining_scientific_label_size:R.dimen.remaining_numeric_label_size);
        if(Math.abs(px-v.getTextSize())>.1f)v.setTextSize(TypedValue.COMPLEX_UNIT_PX,px);
    }
    private int columns(ViewGroup pad) {
        return compact?4:originalColumns(pad);
    }
    private int originalColumns(ViewGroup pad) {
        return pad==input?(input.findViewById(R.id.columnGuide4)==null?4:5):(advanced.findViewById(R.id.columnGuide3)==null?3:4);
    }
    private static final int[] NUMERIC={R.id.clr,R.id.paren,R.id.op_pct,R.id.op_div,
        R.id.digit_7,R.id.digit_8,R.id.digit_9,R.id.op_mul,R.id.digit_4,R.id.digit_5,R.id.digit_6,R.id.op_sub,
        R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.op_add,R.id.digit_0,R.id.dec_point,R.id.del,R.id.eq};
    private static final int[] SCIENCE={R.id.op_sqrt,R.id.const_pi,R.id.op_pow,R.id.op_fact,
        R.id.toggle_mode,R.id.fun_sin,R.id.fun_cos,R.id.fun_tan,R.id.toggle_inv,R.id.const_e,R.id.fun_ln,R.id.fun_log};
    private static final int[] INVERSE={R.id.op_sqr,R.id.fun_arcsin,R.id.fun_arccos,R.id.fun_arctan,R.id.fun_exp,R.id.fun_10pow};
    private static final int[] INVERSE_CELLS={0,5,6,7,10,11};
    private int compactWidth(ViewGroup pad) {
        return 4*((int)Math.ceil(48*activity.getResources().getDisplayMetrics().density)+margin(pad,true)+1)
            +pad.getPaddingLeft()+pad.getPaddingRight();
    }
    private int compactHeight(ViewGroup pad) {
        return (pad==input?5:3)*(cellHeight(pad)+margin(pad,false)+1)+pad.getPaddingTop()+pad.getPaddingBottom();
    }
    private void gridCell(ConstraintSet state,ViewGroup pad,int id,int cell,int width) {
        View key=pad.findViewById(id);ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)key.getLayoutParams();
        int pitch=(width-pad.getPaddingLeft()-pad.getPaddingRight())/4;
        int row=cellHeight(pad)+margin(pad,false)+1;
        state.clear(id);state.constrainWidth(id,pitch-p.leftMargin-p.rightMargin);
        state.constrainHeight(id,row-p.topMargin-p.bottomMargin);
        state.connect(id,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,cell%4*pitch+p.leftMargin);
        state.connect(id,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP,cell/4*row+p.topMargin);
        state.setVisibilityMode(id,ConstraintSet.VISIBILITY_MODE_IGNORE);
    }
    private void compactGrid(ViewGroup pad,int width) {
        ConstraintSet state=new ConstraintSet();state.clone((ConstraintLayout)pad);
        int[] keys=pad==input?NUMERIC:SCIENCE;
        for(int n=0;n<keys.length;n++)gridCell(state,pad,keys[n],n,width);
        if(pad==advanced)for(int n=0;n<INVERSE.length;n++)gridCell(state,pad,INVERSE[n],INVERSE_CELLS[n],width);
        state.applyTo((ConstraintLayout)pad);
    }
    private int margin(ViewGroup pad,boolean horizontal) {
        return pad==input?(horizontal?inputMarginX:inputMarginY):(horizontal?advancedMarginX:advancedMarginY);
    }
    private int measuredMargin(ViewGroup pad,boolean horizontal) {
        int max=0;
        for(int n=0;n<pad.getChildCount();n++)if(pad.getChildAt(n) instanceof HapticButton) {
            ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)pad.getChildAt(n).getLayoutParams();
            max=Math.max(max,horizontal?p.leftMargin+p.rightMargin:p.topMargin+p.bottomMargin);
        }
        return max;
    }
    private int cellHeight(ViewGroup pad) {
        float px=activity.getResources().getDimension(pad==advanced?R.dimen.remaining_scientific_label_size:R.dimen.remaining_numeric_label_size);
        android.graphics.Paint paint=new android.graphics.Paint();paint.setTextSize(px);
        return Math.max((int)Math.ceil(48*activity.getResources().getDisplayMetrics().density),paint.getFontMetricsInt(null));
    }
    private int padHeight(ViewGroup pad) {
        int rows=(pad==input?20:12)/originalColumns(pad);
        return rows*(cellHeight(pad)+margin(pad,false)+1)+pad.getPaddingTop()+pad.getPaddingBottom();
    }
    private int padWidth(ViewGroup pad) {
        int cell=(int)Math.ceil(48*activity.getResources().getDisplayMetrics().density);
        return originalColumns(pad)*(cell+margin(pad,true))+pad.getPaddingLeft()+pad.getPaddingRight();
    }
    private void mount() {
        Log.i("RemainingWindow","KEYPAD_MOUNT science="+scientific);
        normal=new ConstraintSet();normal.clone(motion.getConstraintSet(R.id.start_state));
        inputParams=input.getLayoutParams();advancedParams=advanced.getLayoutParams();
        inputHome=new ConstraintSet();inputHome.clone((ConstraintLayout)input);
        advancedHome=new ConstraintSet();advancedHome.clone((ConstraintLayout)advanced);
        for(int id:NUMERIC)inputHome.setVisibilityMode(id,ConstraintSet.VISIBILITY_MODE_IGNORE);
        for(int id:SCIENCE)advancedHome.setVisibilityMode(id,ConstraintSet.VISIBILITY_MODE_IGNORE);
        for(int id:INVERSE)advancedHome.setVisibilityMode(id,ConstraintSet.VISIBILITY_MODE_IGNORE);
        viewport=new ScrollView(activity);viewport.setTag("remaining_keypad_viewport");
        viewport.setFillViewport(false);viewport.setVerticalScrollBarEnabled(true);viewport.setScrollbarFadingEnabled(false);
        body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        stack.removeView(advanced);stack.removeView(input);
        body.addView(advanced,new LinearLayout.LayoutParams(-1,scientific?compactHeight(advanced):0));
        body.addView(input,new LinearLayout.LayoutParams(-1,compactHeight(input)));
        viewport.addView(body,new ScrollView.LayoutParams(-1,-2));stack.addView(viewport,new LinearLayout.LayoutParams(-1,-1));
        labels.clear();
        for(ViewGroup pad:new ViewGroup[]{input,advanced})for(int n=0;n<pad.getChildCount();n++) {
            View v=pad.getChildAt(n);if(!(v instanceof HapticButton))continue;
            labels.add(new Label((TextView)v));v.addOnLayoutChangeListener(labelListener);
        }
        compact=true;
    }
    void refreshVisibleKeys() {
        if(!compact)return;
        // Inverse controls were GONE with zero measured bounds. Re-measure the retained pad
        // subtree even when the outer MotionLayout/ScrollView dimensions have not changed.
        advanced.forceLayout();input.forceLayout();body.forceLayout();viewport.forceLayout();
        viewport.requestLayout();motion.requestLayout();schedule();
    }
    private void sizeViewport() {
        if(viewport==null)return;
        ViewGroup.LayoutParams p=viewport.getLayoutParams();
        if(p.width!=stack.getWidth()||p.height!=stack.getHeight()) {
            p.width=stack.getWidth();p.height=stack.getHeight();viewport.setLayoutParams(p);
        }
    }
    private void fit() {
        if(!enabled||!motion.isAttachedToWindow()||motion.getRootWindowInsets()==null||motion.getCurrentState()!=R.id.start_state)return;
        float d=activity.getResources().getDisplayMetrics().density;
        int h=motion.getHeight()-motion.getPaddingTop()-motion.getPaddingBottom();
        Insets bars=motion.getRootWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        Rect window=activity.getWindowManager().getCurrentWindowMetrics().getBounds();int[] raw=new int[2];motion.getLocationOnScreen(raw);
        int left=Math.max(0,window.left+bars.left-raw[0]),right=Math.max(0,raw[0]+motion.getWidth()-(window.right-bars.right));
        int w=motion.getWidth()-motion.getPaddingLeft()-motion.getPaddingRight()-left-right;
        if(w<=0||h<=0)return;
        String geometry=w+":"+h+":"+left+":"+right+":"+scientific;
        if(geometry.equals(fittedGeometry)){sizeViewport();return;}
        fittedGeometry=geometry;
        TypedValue ratio=new TypedValue();activity.getResources().getValue(R.dimen.layout_pads_ratio,ratio,true);
        int normalHeight=Math.round(h*ratio.getFloat());
        boolean vertical=stack.getOrientation()==LinearLayout.VERTICAL;
        int neededHeight=scientific?(vertical?padHeight(input)+padHeight(advanced):Math.max(padHeight(input),padHeight(advanced))):padHeight(input);
        int neededWidth=scientific&&!vertical?Math.max(3*padWidth(advanced),(padWidth(input)*3+1)/2):padWidth(input);
        if(normalHeight>=neededHeight&&w>=neededWidth){restore();return;}
        int barHeight=activity.getResources().getDimensionPixelSize(R.dimen.mode_segment_height)
                +activity.findViewById(R.id.tool_bar).getPaddingTop()+activity.findViewById(R.id.tool_bar).getPaddingBottom();
        int keyboardWidth=Math.max(compactWidth(input),scientific?compactWidth(advanced):0);
        boolean side=w>=keyboardWidth+Math.ceil(128*d);
        String decision="availablePx="+w+"x"+h+" availableDp="+(w/d)+"x"+(h/d)
            +" originalKeyboardWidthDp="+(Math.max(padWidth(input),scientific?padWidth(advanced):0)/d)
            +" compactKeyboardWidthDp="+(keyboardWidth/d)+" displayMinimumDp=128 branch="+(side?"LEFT_DISPLAY_RIGHT_KEYPAD":"INSUFFICIENT_WIDTH")
            +" science="+scientific;
        if(!decision.equals(lastDecision)){Log.i("RemainingWindow",decision);lastDecision=decision;}
        // Do not silently replace the requested short-window structure with a squeezed vertical pad.
        if(!side){restore();return;}
        int displayHeight=h-barHeight;
        if(displayHeight<=0)return; // No fictitious 48dp guarantee in an impossibly small task.
        if(!compact)mount();
        if(lastWidth!=w||lastHeight!=h){compactGrid(input,keyboardWidth);compactGrid(advanced,keyboardWidth);}
        for(Label label:labels)style(label.view);
        if(lastWidth==w&&lastHeight==h&&lastLeft==left&&lastRight==right){sizeViewport();return;}
        ConstraintSet state=new ConstraintSet();state.clone(normal);
        for(int id:new int[]{R.id.display,R.id.tool_bar,R.id.pad_stack})
            for(int anchor:new int[]{ConstraintSet.TOP,ConstraintSet.BOTTOM,ConstraintSet.LEFT,ConstraintSet.RIGHT,ConstraintSet.START,ConstraintSet.END})state.clear(id,anchor);
        int displayWidth=side?w-keyboardWidth:w;
        state.constrainWidth(R.id.display,displayWidth);state.constrainHeight(R.id.display,displayHeight);
        state.connect(R.id.display,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
        state.connect(R.id.display,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
        state.constrainWidth(R.id.tool_bar,displayWidth);state.constrainHeight(R.id.tool_bar,barHeight);
        state.connect(R.id.tool_bar,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
        state.connect(R.id.tool_bar,ConstraintSet.TOP,R.id.display,ConstraintSet.BOTTOM);
        state.constrainWidth(R.id.pad_stack,side?keyboardWidth:w);state.constrainHeight(R.id.pad_stack,side?h:h-displayHeight-barHeight);
        state.connect(R.id.pad_stack,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left+(side?displayWidth:0));
        state.connect(R.id.pad_stack,ConstraintSet.TOP,side?ConstraintSet.PARENT_ID:R.id.tool_bar,side?ConstraintSet.TOP:ConstraintSet.BOTTOM);
        lastWidth=w;lastHeight=h;lastLeft=left;lastRight=right;
        motion.updateState(R.id.start_state,state);motion.post(this::sizeViewport);
    }
    private void restore() {
        if(!compact)return;
        Log.i("RemainingWindow","KEYPAD_RESTORE science="+scientific);
        compact=false;lastWidth=lastHeight=0;
        inputHome.applyTo((ConstraintLayout)input);advancedHome.applyTo((ConstraintLayout)advanced);
        body.removeView(advanced);body.removeView(input);stack.removeView(viewport);
        stack.addView(advanced,0,advancedParams);stack.addView(input,1,inputParams);
        for(Label label:labels){label.view.removeOnLayoutChangeListener(labelListener);label.view.setTextSize(TypedValue.COMPLEX_UNIT_PX,label.size);}
        labels.clear();viewport=null;body=null;motion.updateState(R.id.start_state,normal);
    }
}
