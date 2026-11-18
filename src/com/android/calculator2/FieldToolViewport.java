/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.constraintlayout.widget.ConstraintSet;

/** BMI/mortgage only: natural-height controls AND outputs in one bounded viewport.
 * Never replace the editable views (or their text/selection). Calculator layouts are restored
 * verbatim on leaving these tools. The handle stays outside the scrolling content.
 */
final class FieldToolViewport {
    private final Calculator activity;
    private final DisplayMotionLayout motion;
    private final LinearLayout display, content;
    private final View toolbar, shared, formula, result, formulaText, pad, modeBar;
    private final ViewGroup sharedParent;
    private final ViewGroup.LayoutParams toolbarParams, contentParams, formulaParams, resultParams;
    private final int formulaHeight;
    private ScrollView viewport;
    private boolean enabled, compact;
    private int compactWidth,compactHeight;
    private ConstraintSet original;

    FieldToolViewport(Calculator a) {
        activity=a;motion=a.findViewById(R.id.main_calculator);
        display=a.findViewById(R.id.display);content=a.findViewById(R.id.tool_display_content);
        toolbar=a.findViewById(R.id.toolbar);shared=a.findViewById(R.id.shared_display_rows);
        formula=a.findViewById(R.id.formula_scroll_view);result=a.findViewById(R.id.result);
        formulaText=a.findViewById(R.id.formula);pad=a.findViewById(R.id.pad_stack);modeBar=a.findViewById(R.id.tool_bar);
        sharedParent=(ViewGroup)shared;
        toolbarParams=toolbar.getLayoutParams();contentParams=content.getLayoutParams();
        formulaParams=formula.getLayoutParams();resultParams=result.getLayoutParams();
        formulaHeight=formulaText.getLayoutParams().height;
        display.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if(enabled)motion.post(this::fitShortWindow);
        });
        motion.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if(enabled&&(r-l!=or-ol||b-t!=ob-ot))motion.post(this::fitShortWindow);
        });
    }

    void setEnabled(boolean value) {
        if(value==enabled) {if(value)motion.post(this::fitShortWindow);return;}
        enabled=value;
        if(value) {
            viewport=new ScrollView(activity);
            viewport.setTag("field_tool_viewport");
            viewport.setFillViewport(false);
            viewport.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
            display.removeView(toolbar);display.removeView(content);
            body.addView(toolbar,toolbarParams);
            body.addView(content,new LinearLayout.LayoutParams(-1,-2));
            sharedParent.removeView(formula);sharedParent.removeView(result);shared.setVisibility(View.GONE);
            content.addView(formula,new LinearLayout.LayoutParams(-1,-2));
            content.addView(result,new LinearLayout.LayoutParams(-1,-2));
            ViewGroup.LayoutParams textParams=formulaText.getLayoutParams();textParams.height=-2;formulaText.setLayoutParams(textParams);
            viewport.addView(body,new ScrollView.LayoutParams(-1,-2));
            display.addView(viewport,0,new LinearLayout.LayoutParams(-1,0,1));
            motion.post(this::fitShortWindow);
        } else {
            content.removeView(formula);content.removeView(result);
            sharedParent.addView(formula,formulaParams);sharedParent.addView(result,resultParams);
            ViewGroup.LayoutParams textParams=formulaText.getLayoutParams();textParams.height=formulaHeight;formulaText.setLayoutParams(textParams);
            ((ViewGroup)toolbar.getParent()).removeView(toolbar);((ViewGroup)content.getParent()).removeView(content);
            display.removeView(viewport);viewport=null;
            display.addView(toolbar,0,toolbarParams);display.addView(content,1,contentParams);
            shared.setVisibility(View.VISIBLE);
            if(compact&&original!=null)motion.updateState(R.id.start_state,original);
            compact=false;
        }
    }

    /** Five keypad rows need 32dp touch height plus their existing margins. When a vertical
     * stack cannot leave even one full output line above them, place the same two regions side
     * by side. This is based on CURRENT available pixels, not phone type or a new height ratio.
     */
    private void fitShortWindow() {
        if(!enabled||motion.getWidth()==0||motion.getCurrentState()!=R.id.start_state)return;
        float density=activity.getResources().getDisplayMetrics().density;
        int margin=activity.getResources().getDimensionPixelSize(R.dimen.button_vertical_margin);
        int minimumPad=5*(Math.round(32*density)+2*margin);
        int line=Math.max(((android.widget.TextView)result).getLineHeight(),((android.widget.TextView)formulaText).getLineHeight());
        int handle=activity.findViewById(R.id.drag_handle_view).getHeight()
            +2*activity.getResources().getDimensionPixelSize(R.dimen.drag_handle_margin);
        // Leave a readable viewport. Top action toolbar may scroll with the content.
        boolean needsCompact=motion.getHeight()<minimumPad+modeBar.getHeight()+line+handle;
        int keyboardWidth=motion.getWidth()/2;
        boolean roomForColumns=keyboardWidth>=Math.round(4*40*density);
        boolean target=needsCompact&&roomForColumns;
        int availableHeight=motion.getHeight()-modeBar.getHeight();
        if(target==compact&&(!target||(keyboardWidth==compactWidth&&availableHeight==compactHeight)))return;
        if(original==null) {original=new ConstraintSet();original.clone(motion.getConstraintSet(R.id.start_state));}
        ConstraintSet state=new ConstraintSet();state.clone(original);
        if(target) {
            state.clear(R.id.tool_bar,ConstraintSet.TOP);state.clear(R.id.tool_bar,ConstraintSet.BOTTOM);
            state.connect(R.id.tool_bar,ConstraintSet.BOTTOM,ConstraintSet.PARENT_ID,ConstraintSet.BOTTOM);
            state.constrainHeight(R.id.tool_bar,ViewGroup.LayoutParams.WRAP_CONTENT);
            state.clear(R.id.pad_stack,ConstraintSet.START);state.clear(R.id.pad_stack,ConstraintSet.TOP);
            state.clear(R.id.pad_stack,ConstraintSet.BOTTOM);
            state.connect(R.id.pad_stack,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
            state.connect(R.id.pad_stack,ConstraintSet.BOTTOM,R.id.tool_bar,ConstraintSet.TOP);
            state.connect(R.id.pad_stack,ConstraintSet.END,ConstraintSet.PARENT_ID,ConstraintSet.END);
            state.constrainWidth(R.id.pad_stack,keyboardWidth);
            // Explicit remaining pixels override the scene's percent-height mode; just changing
            // MATCH_CONSTRAINT's default leaves MotionLayout's old percentage in its model.
            state.constrainHeight(R.id.pad_stack,availableHeight);
            state.constrainWidth(R.id.display,motion.getWidth()-keyboardWidth);
            state.constrainHeight(R.id.display,availableHeight);
            state.clear(R.id.display,ConstraintSet.END);
            state.connect(R.id.display,ConstraintSet.END,R.id.pad_stack,ConstraintSet.START);
            state.connect(R.id.display,ConstraintSet.TOP,ConstraintSet.PARENT_ID,ConstraintSet.TOP);
            state.connect(R.id.display,ConstraintSet.BOTTOM,R.id.tool_bar,ConstraintSet.TOP);
        }
        compact=target;compactWidth=keyboardWidth;compactHeight=availableHeight;
        motion.updateState(R.id.start_state,state);
        // Restoring a scene must not resurrect a scientific child for a field-only tool.
        activity.findViewById(R.id.advanced_pad).setVisibility(View.GONE);
        pad.requestLayout();
    }
}
