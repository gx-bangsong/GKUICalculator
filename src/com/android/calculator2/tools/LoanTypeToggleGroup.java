/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButtonToggleGroup;

/** The mortgage's loan categories, not its repayment-method control.
 * Measure the complete buttons before sharing space equally: summing label widths can
 * incorrectly fit three equal slots when the longest label/insets don't fit one slot.
 * Keep the very same button instances, checked IDs, text sizes and touch heights.
 */
public final class LoanTypeToggleGroup extends MaterialButtonToggleGroup {
    public LoanTypeToggleGroup(Context context) {this(context,null);}
    public LoanTypeToggleGroup(Context context,AttributeSet attrs) {super(context,attrs);}

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        if(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED) {
            int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            int largest=0,count=0;
            for(int i=0;i<getChildCount();i++) {
                View button=getChildAt(i);
                if(button.getVisibility()==GONE)continue;
                // Includes transformed text, compound icons, drawable minimums and Material
                // button insets/padding, unlike Layout.getDesiredWidth(label) alone.
                button.measure(MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)button.getLayoutParams();
                largest=Math.max(largest,button.getMeasuredWidth()
                        +Math.max(0,p.leftMargin)+Math.max(0,p.rightMargin));
                count++;
            }
            boolean vertical=(long)largest*count>available;
            int orientation=vertical?VERTICAL:HORIZONTAL;
            if(getOrientation()!=orientation) {
                setOrientation(orientation);
                for(int i=0;i<getChildCount();i++) {
                    View button=getChildAt(i);
                    LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)button.getLayoutParams();
                    p.width=vertical?ViewGroup.LayoutParams.MATCH_PARENT:0;
                    p.weight=vertical?0:1;
                    button.setLayoutParams(p);
                }
            }
        }
        // Remeasure children with the chosen real slot widths; Material's normal shape,
        // stroke overlap, selection and accessibility handling remains in this superclass.
        super.onMeasure(widthSpec,heightSpec);
    }
}
