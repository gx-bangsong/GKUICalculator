/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Local completed-fraction fallback. Never changes the display bounds, mode strip or keypad.
 * Original parents/params are restored when hidden or when the window has sufficient space.
 */
final class FractionDisplayScroll {
    private final LinearLayout display;
    private final View toolbar, content, handle, shared;
    private final CalculatorFormula formula;
    private final TextView decimal, fraction;
    private final FractionResultView fractionViewport;
    private final ViewGroup.LayoutParams toolbarParams, contentParams;
    private final int toolbarIndex, contentIndex;
    private final View.OnLayoutChangeListener resize;
    private ScrollView scroll;
    private LinearLayout body;
    private boolean enabled, includesToolbar;

    FractionDisplayScroll(Calculator a) {
        display=a.findViewById(R.id.display);
        toolbar=a.findViewById(R.id.toolbar);content=a.findViewById(R.id.tool_display_content);
        handle=a.findViewById(R.id.drag_handle_view);shared=a.findViewById(R.id.shared_display_rows);
        formula=a.findViewById(R.id.formula);decimal=a.findViewById(R.id.result);
        fraction=a.findViewById(R.id.fraction_result);fractionViewport=a.findViewById(R.id.fraction_viewport);
        toolbarParams=toolbar.getLayoutParams();contentParams=content.getLayoutParams();
        toolbarIndex=display.indexOfChild(toolbar);contentIndex=display.indexOfChild(content);
        resize=(v,l,t,r,b,ol,ot,or,ob) -> {if(enabled)display.post(this::update);};
        display.addOnLayoutChangeListener(resize);
    }
    void setEnabled(boolean value) {
        enabled=value;
        if(value)update();else unmount();
    }
    private static int outerHeight(View v) {
        int h=v.getMeasuredHeight();
        if(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)v.getLayoutParams();
            h+=p.topMargin+p.bottomMargin;
        }
        return h;
    }
    private void update() {
        if(!enabled)return;
        int width=content.getWidth();
        int available=display.getHeight()-display.getPaddingTop()-display.getPaddingBottom()-outerHeight(handle);
        if(width<=0||available<=0)return;
        // Use the existing main-result target typography, not a smaller compact font.
        Paint mainPaint=new Paint(decimal.getPaint());mainPaint.setTextSize(formula.getMaximumTextSize());
        int primaryLine=(int)Math.ceil(mainPaint.getFontSpacing())+formula.getCompoundPaddingBottom();
        int primaryRows=shared instanceof FrameLayout?primaryLine:2*primaryLine;
        int oneFractionLine=fraction.getLineHeight()+fraction.getCompoundPaddingTop()+fraction.getCompoundPaddingBottom();
        int belowToolbar=available-outerHeight(toolbar);
        if(belowToolbar>=primaryRows+oneFractionLine) {unmount();return;}
        // If even one fraction line does not fit below the toolbar, scroll the toolbar as
        // ordinary content too. Clipping stays enabled: nothing paints over toolbar/keys.
        boolean includeToolbar=belowToolbar<Math.max(oneFractionLine,primaryLine);
        fraction.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int required=primaryRows+fraction.getMeasuredHeight();
        if(scroll!=null&&includeToolbar!=includesToolbar)unmount();
        if(scroll==null)mount(includeToolbar,required);
        else if(content.getLayoutParams().height!=required) {
            ViewGroup.LayoutParams p=content.getLayoutParams();p.height=required;content.setLayoutParams(p);
        }
    }
    private void mount(boolean withToolbar,int required) {
        includesToolbar=withToolbar;
        scroll=new ScrollView(display.getContext()) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                // Only this completed-result surface owns these drags. The history handle
                // remains outside; do not change global display/history gestures.
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
                boolean handled=super.dispatchTouchEvent(e);
                if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)
                    getParent().requestDisallowInterceptTouchEvent(false);
                return handled;
            }
            @Override public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rect,boolean immediate) {
                return false; // hidden formula/selection must not auto-scroll a completed result
            }
        };
        scroll.setTag("fraction_display_scroll");scroll.setFocusable(false);scroll.setSmoothScrollingEnabled(false);
        display.removeView(content);
        if(withToolbar) {
            display.removeView(toolbar);
            body=new LinearLayout(display.getContext());body.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(body,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            body.addView(toolbar,new LinearLayout.LayoutParams(toolbarParams));
            body.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,required));
            display.addView(scroll,toolbarIndex,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        } else {
            scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,required));
            display.addView(scroll,contentIndex,new LinearLayout.LayoutParams((LinearLayout.LayoutParams)contentParams));
        }
        fractionViewport.setExpandedContent(true);
    }
    private void unmount() {
        if(scroll==null)return;
        display.removeView(scroll);
        ((ViewGroup)content.getParent()).removeView(content);
        if(includesToolbar) {
            body.removeView(toolbar);display.addView(toolbar,toolbarIndex,toolbarParams);
        }
        display.addView(content,contentIndex,contentParams);
        fractionViewport.setExpandedContent(false);
        scroll=null;body=null;
    }
    void close(){setEnabled(false);display.removeOnLayoutChangeListener(resize);}
}
