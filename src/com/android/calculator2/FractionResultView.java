/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/** A result-only, naturally wrapping scroll surface; GONE takes no input/preview space. */
public final class FractionResultView extends ScrollView {
    public FractionResultView(Context c,AttributeSet attrs){super(c,attrs);setFillViewport(false);}
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        View parent=(View)getParent();
        int available=parent.getHeight()-parent.getPaddingTop()-parent.getPaddingBottom();
        if(available>0) {
            int cap=Math.max(1,available/3);
            if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(heightSpec));
            heightSpec=MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
