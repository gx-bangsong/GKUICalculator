/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Manages a small fixed set of numeric input fields with one "active" field that receives
 * numeric-pad input. Lets multi-input tools (mortgage, tax, BMI) reuse the calculator's existing
 * keypad: the user taps a field to focus it, then types. Pure data; no Android UI.
 */
public final class ToolInputFields {

    private final StringBuilder[] mFields;
    private int mActive;

    public ToolInputFields(int count, @Nullable String[] defaults) {
        mFields = new StringBuilder[count];
        for (int i = 0; i < count; i++) {
            String d = (defaults != null && i < defaults.length && defaults[i] != null)
                    ? defaults[i] : "";
            mFields[i] = new StringBuilder(d);
        }
        mActive = 0;
    }

    public int count() {
        return mFields.length;
    }

    public int getActive() {
        return mActive;
    }

    public void setActive(int index) {
        if (index >= 0 && index < mFields.length) {
            mActive = index;
        }
    }

    public void set(int index, @Nullable String value) {
        if (index < 0 || index >= mFields.length) {
            return;
        }
        mFields[index].setLength(0);
        if (value != null) {
            mFields[index].append(value);
        }
    }

    @NonNull
    public String get(int index) {
        return mFields[index].toString();
    }

    public boolean isEmpty(int index) {
        return mFields[index].length() == 0;
    }

    @Nullable
    public BigDecimal getNumber(int index) {
        String text = get(index);
        if (text.isEmpty()) {
            return null;
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
            if (text.isEmpty()) {
                return null;
            }
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean onDigit(int digit) {
        mFields[mActive].append(digit);
        return true;
    }

    public boolean onDecimalPoint() {
        StringBuilder f = mFields[mActive];
        if (f.indexOf(".") < 0) {
            if (f.length() == 0) {
                f.append("0");
            }
            f.append(".");
        }
        return true;
    }

    public boolean onDelete() {
        StringBuilder f = mFields[mActive];
        if (f.length() > 0) {
            f.deleteCharAt(f.length() - 1);
        }
        return true;
    }

    public boolean onClear() {
        mFields[mActive].setLength(0);
        return true;
    }

    public void clearAll() {
        for (StringBuilder f : mFields) {
            f.setLength(0);
        }
        mActive = 0;
    }
}
