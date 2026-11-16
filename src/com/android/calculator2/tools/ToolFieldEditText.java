/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.KeyListener;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * EditText subclass for tool input fields that reports selection changes to
 * {@link FieldToolMode} via {@link OnSelectionChangeListener}.
 *
 * <p>Programmatic calls to {@link #setText(CharSequence, android.text.TextView.BufferType)} and
 * {@link #setSelection(int, int)} are flagged via {@link #setProgrammaticUpdate(boolean)} so that
 * the listener can ignore selection changes caused by the application rather than by user touch.
 */
public class ToolFieldEditText extends EditText {

    /** Listener notified of selection changes that originate from user interaction. */
    public interface OnSelectionChangeListener {
        /** Called when the selection changes due to user touch, not programmatic setText/setSelection. */
        void onSelectionChanged(ToolFieldEditText view, int selStart, int selEnd);
    }

    private OnSelectionChangeListener mListener;
    private boolean mProgrammaticUpdate = false;

    public ToolFieldEditText(Context context) {
        super(context);
    }

    public ToolFieldEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ToolFieldEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSelectionChangeListener(OnSelectionChangeListener listener) {
        mListener = listener;
    }

    public void setProgrammaticUpdate(boolean programmatic) {
        mProgrammaticUpdate = programmatic;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        // Ignore -1 (invalid) and programmatic changes; only report user-driven selection.
        if (!mProgrammaticUpdate && mListener != null && selStart >= 0 && selEnd >= 0) {
            mListener.onSelectionChanged(this, selStart, selEnd);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        mProgrammaticUpdate = true;
        try {
            super.setText(text, type);
        } finally {
            mProgrammaticUpdate = false;
        }
    }

    @Override
    public void setSelection(int start, int stop) {
        mProgrammaticUpdate = true;
        try {
            super.setSelection(start, stop);
        } finally {
            mProgrammaticUpdate = false;
        }
    }
}
