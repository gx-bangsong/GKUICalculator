/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Programmer presentation only: reuse the actual keys, listeners and enabled states. */
final class ProgrammerKeypadLayout {
    static final int[] ORDER={R.id.digit_7,R.id.digit_8,R.id.digit_9,R.id.del,R.id.clr,
        R.id.digit_4,R.id.digit_5,R.id.digit_6,R.id.op_mul,R.id.op_div,
        R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.op_add,R.id.op_sub,
        R.id.op_sqrt,R.id.const_pi,R.id.op_pow,R.id.digit_0,R.id.eq,
        R.id.op_fact,R.id.toggle_mode,R.id.fun_sin,R.id.fun_cos,R.id.fun_tan,
        R.id.toggle_inv,R.id.const_e,R.id.fun_ln,R.id.fun_log};
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final LinearLayout stack;
    private final View input,advanced;
    private final List<KeyHome> homes=new ArrayList<>();
    private ScrollView viewport;
    private KeyGrid grid;
    private ConstraintSet normal;
    private boolean enabled,compact;
    private int oldOrientation,oldInputVisibility,oldAdvancedVisibility,lastWidth,lastHeight,lastLeft,lastRight;

    private static final class KeyHome {
        final TextView key;
        final ViewGroup parent;
        final int index,lines;
        final int[] padding;
        final ViewGroup.LayoutParams params;
        final float size;
        KeyHome(TextView v) {
            key=v;parent=(ViewGroup)v.getParent();index=parent.indexOfChild(v);params=v.getLayoutParams();
            lines=v.getMaxLines();size=v.getTextSize();
            padding=new int[]{v.getPaddingLeft(),v.getPaddingTop(),v.getPaddingRight(),v.getPaddingBottom()};
        }
    }

    ProgrammerKeypadLayout(Calculator a) {
        activity=a;motion=a.findViewById(R.id.main_calculator);stack=a.findViewById(R.id.pad_stack);
        input=a.findViewById(R.id.input_pad);advanced=a.findViewById(R.id.advanced_pad);
        motion.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(enabled)motion.post(this::fit);});
        stack.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(enabled)sizeViewport(r-l,b-t);
        });
    }

    void setEnabled(boolean value) {
        if(!value) {
            enabled=false;restore();
            if(normal!=null)motion.updateState(R.id.start_state,normal);
            normal=null;lastWidth=lastHeight=0;return;
        }
        if(!enabled) {
            enabled=true;oldOrientation=stack.getOrientation();
            oldInputVisibility=input.getVisibility();oldAdvancedVisibility=advanced.getVisibility();
            // An Activity restored during rotation has not resolved its start constraints yet.
            normal=null;lastWidth=lastHeight=0;
        }
        // Radix changes update these original pads too. Hide them only while reparented.
        if(viewport!=null) {input.setVisibility(View.GONE);advanced.setVisibility(View.GONE);}
        motion.post(this::fit);
    }

    private void mountCompactKeys() {
        viewport=new ScrollView(activity);viewport.setTag("programmer_keypad_viewport");
        viewport.setContentDescription(activity.getString(R.string.programmer_keypad_scroll));
        viewport.setFillViewport(false);viewport.setVerticalScrollBarEnabled(true);viewport.setScrollbarFadingEnabled(false);
        grid=new KeyGrid();grid.setTag("programmer_keypad_grid");
        homes.clear();
        for(int id:ORDER)homes.add(new KeyHome(activity.findViewById(id)));
        for(KeyHome home:homes) {
            home.parent.removeView(home.key);grid.addView(home.key);
            home.key.setSingleLine(true);home.key.setEllipsize(null);home.key.setPadding(0,0,0,0);
            home.key.addOnLayoutChangeListener(labelListener);
        }
        viewport.addView(grid,new ScrollView.LayoutParams(-1,-2));
        stack.addView(viewport,new LinearLayout.LayoutParams(-1,-1));
        viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(enabled)sizeKeys();});
        input.setVisibility(View.GONE);advanced.setVisibility(View.GONE);
        // A single visible child needs no orientation change. In particular keep the
        // landscape pad weights intact when Calculator refreshes the radix/enabled keys.
    }

    /** Minimum legibility budget of the accepted original XML, not the compact grid's
     * 48dp budget. Requiring 48dp for all eight original rows would replace normal full
     * screens too, recreating the regression this adapter is meant to avoid. */
    private boolean needsCompact(int width,int padHeight,float density) {
        int cell=(int)Math.ceil(40*density)+2*activity.getResources().getDimensionPixelSize(R.dimen.button_vertical_margin);
        if(oldOrientation==LinearLayout.VERTICAL) {
            return padHeight<8*cell+input.getPaddingTop()+input.getPaddingBottom()+advanced.getPaddingTop()+advanced.getPaddingBottom()
                    ||width<4*cell+Math.max(input.getPaddingLeft()+input.getPaddingRight(),advanced.getPaddingLeft()+advanced.getPaddingRight());
        }
        int minHeight=4*cell+Math.max(input.getPaddingTop()+input.getPaddingBottom(),advanced.getPaddingTop()+advanced.getPaddingBottom());
        int advancedMin=3*cell+advanced.getPaddingLeft()+advanced.getPaddingRight();
        int inputMin=5*cell+input.getPaddingLeft()+input.getPaddingRight();
        return padHeight<minHeight||width<Math.max(3*advancedMin,(inputMin*3+1)/2);
    }

    private final View.OnLayoutChangeListener labelListener=(v,l,t,r,b,ol,ot,or,ob)->{
        if(enabled&&viewport!=null)styleLabel((TextView)v);
    };

    private void styleLabel(TextView key) {
        int id=key.getId();
        boolean word=id==R.id.fun_cos||id==R.id.fun_tan||id==R.id.toggle_inv||id==R.id.const_e;
        float sp=word?18f:22f;
        float px=sp*activity.getResources().getDisplayMetrics().scaledDensity;
        if(Math.abs(key.getTextSize()-px)>.1f)key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,sp);
    }

    private void sizeViewport(int width,int height) {
        if(viewport==null||width<=0||height<=0)return;
        ViewGroup.LayoutParams p=viewport.getLayoutParams();
        // MotionLayout can otherwise retain the child's previous full-width measurement
        // while the outer stack already has its new narrow bounds.
        if(p.width!=width||p.height!=height) {
            p.width=width;p.height=height;viewport.setLayoutParams(p);
        }
    }

    private void sizeKeys() {
        if(!enabled||viewport==null)return;
        for(KeyHome home:homes)styleLabel(home.key);
    }

    /** Exact cells: no intrinsic button minimum or GridLayout constraint may widen a column. */
    private final class KeyGrid extends ViewGroup {
        int columns,cellHeight;
        KeyGrid(){super(activity);}
        @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
            return new ViewGroup.LayoutParams(-1,-1);
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int width=MeasureSpec.getSize(widthSpec);
            float d=getResources().getDisplayMetrics().density;
            columns=width>=Math.ceil(640*d)?10:Math.min(5,Math.max(1,width/(int)Math.ceil(48*d)));
            int rows=(getChildCount()+columns-1)/columns;
            int available=Math.max(0,viewport.getLayoutParams().height);
            cellHeight=Math.max((int)Math.ceil(48*d),available/rows);
            for(int i=0;i<getChildCount();i++) {
                int col=i%columns;
                int cellWidth=(col+1)*width/columns-col*width/columns;
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(cellHeight,MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width,rows*cellHeight);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            int width=r-l;
            for(int i=0;i<getChildCount();i++) {
                int col=i%columns,row=i/columns;
                getChildAt(i).layout(col*width/columns,row*cellHeight,
                        (col+1)*width/columns,(row+1)*cellHeight);
            }
        }
    }

    private void fit() {
        if(!enabled||motion.getCurrentState()!=R.id.start_state||motion.getWidth()==0||motion.getHeight()==0)return;
        if(normal==null) {
            normal=new ConstraintSet();normal.clone(motion.getConstraintSet(R.id.start_state));
        }
        float d=activity.getResources().getDisplayMetrics().density;
        android.graphics.Insets bars=activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets()
                .getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
        int left=Math.max(0,bars.left-motion.getPaddingLeft()),right=Math.max(0,bars.right-motion.getPaddingRight());
        int width=motion.getWidth()-motion.getPaddingLeft()-motion.getPaddingRight()-left-right;
        int height=motion.getHeight()-motion.getPaddingTop()-motion.getPaddingBottom();
        int keyboardWidth=(int)Math.ceil(240*d),contentMin=(int)Math.ceil(140*d);
        android.util.TypedValue ratio=new android.util.TypedValue();
        activity.getResources().getValue(R.dimen.layout_pads_ratio,ratio,true);
        boolean rearrange=needsCompact(width,Math.round(height*ratio.getFloat()),d);
        boolean changed=rearrange!=(viewport!=null);
        if(changed) {
            if(rearrange)mountCompactKeys();else restore();
        }
        boolean target=rearrange&&width>=keyboardWidth+contentMin&&width<2*keyboardWidth+contentMin
                &&height*ratio.getFloat()<Math.ceil(6*48*d);
        if(changed||target!=compact||width!=lastWidth||height!=lastHeight||left!=lastLeft||right!=lastRight) {
            ConstraintSet state=new ConstraintSet();state.clone(normal);
            if(target) {
                View bar=activity.findViewById(R.id.tool_bar);
                int barHeight=activity.getResources().getDimensionPixelSize(R.dimen.mode_segment_height)+bar.getPaddingTop()+bar.getPaddingBottom();
                for(int id:new int[]{R.id.display,R.id.tool_bar,R.id.pad_stack})
                    for(int anchor:new int[]{ConstraintSet.TOP,ConstraintSet.BOTTOM,ConstraintSet.START,ConstraintSet.END,ConstraintSet.LEFT,ConstraintSet.RIGHT})state.clear(id,anchor);
                state.constrainWidth(R.id.pad_stack,keyboardWidth);state.constrainHeight(R.id.pad_stack,height);
                state.connect(R.id.pad_stack,ConstraintSet.RIGHT,ConstraintSet.PARENT_ID,ConstraintSet.RIGHT,right);
                state.connect(R.id.pad_stack,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
                state.constrainWidth(R.id.tool_bar,width-keyboardWidth);state.constrainHeight(R.id.tool_bar,barHeight);
                state.connect(R.id.tool_bar,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
                state.connect(R.id.tool_bar,ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM);
                state.constrainWidth(R.id.display,width-keyboardWidth);state.constrainHeight(R.id.display,Math.max(1,height-barHeight));
                state.connect(R.id.display,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
                state.connect(R.id.display,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
            }
            if(!target&&(left>0||right>0)) {
                // The activity is edge-to-edge. Keep this mode's actual touch cells out of
                // side navigation/cutouts, without changing other modes or global insets.
                for(int id:new int[]{R.id.display,R.id.tool_bar,R.id.pad_stack}) {
                    for(int anchor:new int[]{ConstraintSet.LEFT,ConstraintSet.RIGHT,ConstraintSet.START,ConstraintSet.END})state.clear(id,anchor);
                    state.constrainWidth(id,width);
                    state.connect(id,ConstraintSet.LEFT,ConstraintSet.PARENT_ID,ConstraintSet.LEFT,left);
                    state.connect(id,ConstraintSet.RIGHT,ConstraintSet.PARENT_ID,ConstraintSet.RIGHT,right);
                }
            }
            compact=target;lastWidth=width;lastHeight=height;lastLeft=left;lastRight=right;
            motion.updateState(R.id.start_state,state);
        }
        sizeViewport(target?keyboardWidth:width,target?height:Math.round(height*ratio.getFloat()));
        sizeKeys();
    }

    private void restore() {
        if(viewport==null)return;
        stack.removeView(viewport);
        // Insert in ascending original index for each parent, not programmer grid order.
        List<KeyHome> sorted=new ArrayList<>(homes);
        sorted.sort(Comparator.comparingInt(h->h.index));
        for(KeyHome home:sorted) {
            grid.removeView(home.key);home.key.removeOnLayoutChangeListener(labelListener);
            home.parent.addView(home.key,Math.min(home.index,home.parent.getChildCount()),home.params);
            home.key.setSingleLine(false);home.key.setMaxLines(home.lines);
            home.key.setPadding(home.padding[0],home.padding[1],home.padding[2],home.padding[3]);
            home.key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,home.size);
        }
        input.setVisibility(oldInputVisibility);advanced.setVisibility(oldAdvancedVisibility);
        stack.setOrientation(oldOrientation);
        viewport=null;grid=null;compact=false;homes.clear();
    }
}
