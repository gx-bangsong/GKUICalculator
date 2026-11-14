/*
 * SPDX-FileCopyrightText: 2006 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

/**
 * A basic Button that vibrates on finger down.
 * <p>
 * The label is sized from the key's <em>width</em>, then clamped by its height. Sizing from the
 * height alone made tall keys print digits that were ~1.7x larger than Google Calculator's,
 * because the height grows on taller screens while the width does not. Google's measured ink is
 * ~25 dp on a 78 dp-tall / 96 dp-wide digit key (0.26 of the width) and ~15 dp on a 48 dp-tall
 * scientific key of the same width (0.155). One width scale of 0.21 lands both close, and the
 * height clamp only ever kicks in on unusually short keys. A key that carries an icon keeps its
 * own text size.
 */
public class HapticButton extends MaterialButton {

    /** A label may never exceed this fraction of the key's height (two-line labels stay inside). */
    private static final float KEY_LABEL_MAX_HEIGHT_RATIO = 0.42f;

    private float mTextSizeScale = -1f;
    private float mMaxTextSizePx = -1f;

    public HapticButton(Context context) {
        super(context);
        initVibration();
    }

    public HapticButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVibration();
        readTextSizeAttributes(attrs);
    }

    public HapticButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVibration();
        readTextSizeAttributes(attrs);
    }

    private void initVibration() {
        setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            // Passthrough
            return false;
        });
    }

    private void readTextSizeAttributes(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HapticButton);
        try {
            mTextSizeScale = a.getFloat(R.styleable.HapticButton_keyLabelScale, -1f);
            mMaxTextSizePx = a.getDimension(R.styleable.HapticButton_keyLabelMaxTextSize, -1f);
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getIcon() == null) {
            float scale = mTextSizeScale;
            if (scale <= 0f) {
                scale = getResources().getDimension(R.dimen.dimen_key_label_scale);
            }
            // Width is the honest measure of a key's label: the digit keys are ~90 dp wide and
            // Google prints ~25 dp digits, while a scientific key is the same width but only half
            // as tall, and it prints ~15 dp labels. Sizing from the height alone made the digits
            // far too large; sizing from the smaller side would make the scientific labels tiny.
            // So: width x scale, then clamped to a fraction of the height and to a hard cap.
            float sizePx = w * scale;
            sizePx = Math.min(sizePx, h * KEY_LABEL_MAX_HEIGHT_RATIO);
            if (mMaxTextSizePx > 0f) {
                sizePx = Math.min(sizePx, mMaxTextSizePx);
            }
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        }
    }
}
