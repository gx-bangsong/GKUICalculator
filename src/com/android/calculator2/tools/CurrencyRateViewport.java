/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

/** Existing currency scroll layer: background output updates must not reveal its old focus. */
public final class CurrencyRateViewport extends ScrollView {
    private boolean updatingRates;

    public CurrencyRateViewport(Context context, AttributeSet attrs) { super(context, attrs); }

    public void beginRateUpdate() {
        if (updatingRates) return;
        updatingRates = true;
        // TextView may request its focused selection rectangle during the next pre-draw,
        // not just synchronously inside setText. End the guard after that draw completes.
        getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            @Override public void onDraw() {
                post(() -> {
                    if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnDrawListener(this);
                    updatingRates = false;
                });
            }
        });
    }

    @Override public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (updatingRates && findFocus() instanceof ToolOutputTextView) return false;
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }
}
