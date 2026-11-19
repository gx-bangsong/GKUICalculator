/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

/** Local converter-only short-window arrangement. No content reparenting or extra scroller. */
final class ConverterKeypadLayout {
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final ConstraintLayout keys;
    private ConstraintSet normal, normalKeys;
    private final int[] vertical = new int[4], horizontal = new int[3];
    private final int left, top, right, bottom;
    private boolean enabled, compact;
    private int lastWidth, lastHeight;
    private final int[][] keyPadding=new int[20][4];
    private final int[] keyLines=new int[20];
    private final int[] order = {R.id.digit_7,R.id.digit_8,R.id.digit_9,R.id.del,R.id.clr,
        R.id.digit_4,R.id.digit_5,R.id.digit_6,R.id.op_mul,R.id.op_div,
        R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.op_add,R.id.op_sub,
        R.id.paren,R.id.digit_0,R.id.dec_point,R.id.op_pct,R.id.eq};

    ConverterKeypadLayout(Calculator a) {
        activity=a; motion=a.findViewById(R.id.main_calculator); keys=a.findViewById(R.id.input_pad);
        left=keys.getPaddingLeft();top=keys.getPaddingTop();right=keys.getPaddingRight();bottom=keys.getPaddingBottom();
        for(int i=0;i<vertical.length;i++)vertical[i]=View.generateViewId();
        for(int i=0;i<horizontal.length;i++)horizontal[i]=View.generateViewId();
        motion.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(enabled)motion.post(this::fit);});
        for(int i=0;i<order.length;i++) {
            TextView key=keys.findViewById(order[i]);
            keyPadding[i]=new int[]{key.getPaddingLeft(),key.getPaddingTop(),key.getPaddingRight(),key.getPaddingBottom()};
            keyLines[i]=key.getMaxLines();
        }
        for(int id:order) keys.findViewById(id).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(enabled && compact)((TextView)v).setTextSize(TypedValue.COMPLEX_UNIT_SP,22);
        });
    }

    void setEnabled(boolean value) {
        enabled=value;
        if(value)motion.post(this::fit);else restore();
    }

    private void restore() {
        if(!compact)return;
        compact=false;
        motion.updateState(R.id.start_state,normal);
        normalKeys.applyTo(keys);
        for(int i=0;i<order.length;i++) {
            TextView key=keys.findViewById(order[i]);int[] p=keyPadding[i];
            key.setSingleLine(false);key.setMaxLines(keyLines[i]);key.setPadding(p[0],p[1],p[2],p[3]);
        }
        keys.setPadding(left,top,right,bottom);
        // HapticButton recalculates its normal responsive label on the restored size change.
        keys.requestLayout();
    }

    private void fit() {
        if(!enabled || motion.getCurrentState()!=R.id.start_state)return;
        float density=activity.getResources().getDisplayMetrics().density;
        int width=motion.getWidth()-motion.getPaddingLeft()-motion.getPaddingRight();
        int height=motion.getHeight()-motion.getPaddingTop()-motion.getPaddingBottom();
        int keySize=(int)Math.ceil(48*density), contentMin=(int)Math.ceil(140*density);
        int keyboardWidth=5*keySize;
        // Use actual available pixels. Four rows need 192dp; five ordinary rows need 240dp.
        // A tall window restores the existing full-width layout rather than retaining columns.
        TypedValue ratio=new TypedValue();
        activity.getResources().getValue(R.dimen.layout_pads_ratio,ratio,true);
        int rowMargins=2*activity.getResources().getDimensionPixelSize(R.dimen.button_vertical_margin);
        boolean target=width>=keyboardWidth+contentMin && height>=4*keySize
            && height*ratio.getFloat()<5*(keySize+rowMargins)+top+bottom;
        if(!target){restore();return;}
        if(compact && width==lastWidth && height==lastHeight) {
            for(int id:order)if(((TextView)keys.findViewById(id)).getTextSize()!=22*activity.getResources().getDisplayMetrics().scaledDensity)
                ((TextView)keys.findViewById(id)).setTextSize(TypedValue.COMPLEX_UNIT_SP,22);
            return;
        }
        if(normal==null){normal=new ConstraintSet();normal.clone(motion.getConstraintSet(R.id.start_state));normalKeys=new ConstraintSet();normalKeys.clone(keys);}
        ConstraintSet state=new ConstraintSet();state.clone(normal);
        int contentWidth=width-keyboardWidth;
        View bar=activity.findViewById(R.id.tool_bar);
        int barHeight=activity.getResources().getDimensionPixelSize(R.dimen.mode_segment_height)+bar.getPaddingTop()+bar.getPaddingBottom();
        for(int id:new int[]{R.id.display,R.id.tool_bar,R.id.pad_stack}) {
            for(int anchor:new int[]{ConstraintSet.TOP,ConstraintSet.BOTTOM,ConstraintSet.START,ConstraintSet.END})state.clear(id,anchor);
        }
        state.constrainWidth(R.id.pad_stack,keyboardWidth);state.constrainHeight(R.id.pad_stack,height);
        state.connect(R.id.pad_stack,ConstraintSet.END,ConstraintSet.PARENT_ID,ConstraintSet.END);
        state.connect(R.id.pad_stack,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
        state.constrainWidth(R.id.tool_bar,contentWidth);state.constrainHeight(R.id.tool_bar,barHeight);
        state.connect(R.id.tool_bar,ConstraintSet.START,ConstraintSet.PARENT_ID,ConstraintSet.START);
        state.connect(R.id.tool_bar,ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM);
        state.constrainWidth(R.id.display,contentWidth);state.constrainHeight(R.id.display,height-barHeight);
        state.connect(R.id.display,ConstraintSet.START,ConstraintSet.PARENT_ID,ConstraintSet.START);
        state.connect(R.id.display,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
        ConstraintSet grid=new ConstraintSet();grid.clone(normalKeys);
        for(int i=0;i<4;i++){grid.create(vertical[i],ConstraintSet.VERTICAL);grid.setGuidelinePercent(vertical[i],(i+1)/5f);}
        for(int i=0;i<3;i++){grid.create(horizontal[i],ConstraintSet.HORIZONTAL);grid.setGuidelinePercent(horizontal[i],(i+1)/4f);}
        for(int i=0;i<order.length;i++) {
            int id=order[i],column=i%5,row=i/5;
            grid.clear(id);grid.constrainWidth(id,0);grid.constrainHeight(id,0);
            grid.connect(id,ConstraintSet.START,column==0?ConstraintSet.PARENT_ID:vertical[column-1],column==0?ConstraintSet.START:ConstraintSet.END,0);
            grid.connect(id,ConstraintSet.END,column==4?ConstraintSet.PARENT_ID:vertical[column],column==4?ConstraintSet.END:ConstraintSet.START,0);
            grid.connect(id,ConstraintSet.TOP,row==0?ConstraintSet.PARENT_ID:horizontal[row-1],row==0?ConstraintSet.TOP:ConstraintSet.BOTTOM,0);
            grid.connect(id,ConstraintSet.BOTTOM,row==3?ConstraintSet.PARENT_ID:horizontal[row],row==3?ConstraintSet.BOTTOM:ConstraintSet.TOP,0);
        }
        compact=true;lastWidth=width;lastHeight=height;
        keys.setPadding(0,0,0,0);grid.applyTo(keys);
        for(int id:order) {
            TextView key=keys.findViewById(id);key.setSingleLine(true);key.setEllipsize(null);key.setPadding(0,0,0,0);
        }
        motion.updateState(R.id.start_state,state);
        activity.findViewById(R.id.advanced_pad).setVisibility(View.GONE);
        motion.post(this::fit);
    }
}
