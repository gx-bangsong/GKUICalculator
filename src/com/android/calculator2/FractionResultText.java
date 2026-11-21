/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

/** Same aligned preview typography and long-click behavior; passive debug touch tracing only. */
public final class FractionResultText extends AlignedTextView {
    public FractionResultText(Context c,AttributeSet attrs){super(c,attrs);}
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        FractionScrollDiagnostics.event(this,"text.dispatch.in",e,null);
        boolean handled=super.dispatchTouchEvent(e);
        FractionScrollDiagnostics.event(this,"text.dispatch.out",e,handled);return handled;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        FractionScrollDiagnostics.event(this,"text.touch.in",e,null);
        boolean handled=super.onTouchEvent(e);
        FractionScrollDiagnostics.event(this,"text.touch.out",e,handled);return handled;
    }
}
