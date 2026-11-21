/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/** A result-only, naturally wrapping scroll surface; GONE takes no input/preview space. */
public final class FractionResultView extends ScrollView {
    private boolean expandedContent;
    void setExpandedContent(boolean expanded) {
        if(expandedContent!=expanded){expandedContent=expanded;requestLayout();}
    }
    public FractionResultView(Context c,AttributeSet attrs){super(c,attrs);setFillViewport(false);}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        FractionScrollDiagnostics.event(this,"inner.dispatch.in",e,null);
        boolean handled=super.dispatchTouchEvent(e);
        FractionScrollDiagnostics.event(this,"inner.dispatch.out",e,handled);return handled;
    }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
        boolean intercept=super.onInterceptTouchEvent(e);
        FractionScrollDiagnostics.event(this,"inner.intercept",e,intercept);return intercept;
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        FractionScrollDiagnostics.event(this,"inner.touch.in",e,null);
        boolean handled=super.onTouchEvent(e);
        FractionScrollDiagnostics.event(this,"inner.touch.out",e,handled);return handled;
    }
    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        FractionScrollDiagnostics.disallow(this,"inner",disallow);super.requestDisallowInterceptTouchEvent(disallow);
    }
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY) {
        super.onScrollChanged(x,y,oldX,oldY);FractionScrollDiagnostics.scroll(this,"inner",oldY,y);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        View parent=(View)getParent();
        int available=parent.getHeight()-parent.getPaddingTop()-parent.getPaddingBottom();
        if(!expandedContent&&available>0) {
            android.widget.TextView text=(android.widget.TextView)getChildAt(0);
            int line=text.getLineHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom();
            int cap=Math.min(available,Math.max(line,available/3));
            if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(heightSpec));
            heightSpec=MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
