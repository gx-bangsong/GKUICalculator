/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.KeyMaps;
import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.model.ProgrammerCalculator;
import com.android.calculator2.tools.model.ProgrammerCalculator.Operation;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.math.BigDecimal;

/** Signed 64-bit programmer calculator with four bases and arithmetic/bitwise operations. */
public final class ProgrammerMode implements ToolMode {

    private static final float PROGRAMMER_FORMULA_MAX_SP = 13f;
    private static final float PROGRAMMER_FORMULA_MIN_SP = 11f;
    private static final float PROGRAMMER_FORMULA_WEIGHT = 0.32f;
    private static final float PROGRAMMER_RESULT_MAX_SP = 30f;
    private static final float PROGRAMMER_RESULT_SINGLE_LINE_MIN_SP = 13f;
    private static final float PROGRAMMER_RESULT_MULTI_LINE_MIN_SP = 10f;
    private static final int PROGRAMMER_RESULT_MAX_LINES = 2;

    private int mRadix = 10;
    private final StringBuilder mEntry = new StringBuilder("0");
    private long mAccumulator;
    @Nullable
    private Operation mPendingOperation;
    private boolean mStartNewEntry = true;
    @NonNull
    private String mExpression = "";
    @Nullable
    private String mError;

    @Nullable
    private ToolHost mHost;
    @Nullable
    private View mControlRoot;
    @Nullable
    private TextView mResultView;
    private final Runnable mFitResultRunnable = this::fitResultText;

    @NonNull
    @Override
    public String getId() {
        return ToolId.PROGRAMMER;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_programmer;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        mHost = host;
        mRadix = 10;
        reset();
        final Long carried = parseCarryValue(carryValue);
        if (carried != null) {
            setEntry(carried);
        }
        mountControls(host.getContext());
        host.setProgrammerPadMode(true, mRadix);
        host.setToolFormulaTextSizeRangeSp(PROGRAMMER_FORMULA_MAX_SP, PROGRAMMER_FORMULA_MIN_SP);
        host.setToolResultTextSizeSp(PROGRAMMER_RESULT_MAX_SP);
        configureDisplayLayout(true);
        redisplay();
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
        configureDisplayLayout(false);
        host.setProgrammerPadMode(false, 10);
        final ViewGroup slot = host.getToolControlSlot();
        if (slot != null) {
            slot.removeAllViews();
            slot.setVisibility(View.GONE);
        }
        mControlRoot = null;
        mHost = null;
    }

    @Override
    public boolean onPadKey(int viewId) {
        final int digit = KeyMaps.digVal(viewId);
        if (digit != KeyMaps.NOT_DIGIT) {
            appendDigit(digit);
            return true;
        }
        if (viewId == R.id.clr) {
            onClear();
            return true;
        } else if (viewId == R.id.del) {
            onDelete();
            return true;
        } else if (viewId == R.id.eq) {
            onEquals();
            return true;
        } else if (viewId == R.id.dec_point) {
            return true; // Programmer mode is integer-only.
        } else if (viewId == R.id.op_add) {
            selectOperation(Operation.ADD);
            return true;
        } else if (viewId == R.id.op_sub) {
            selectOperation(Operation.SUBTRACT);
            return true;
        } else if (viewId == R.id.op_mul) {
            selectOperation(Operation.MULTIPLY);
            return true;
        } else if (viewId == R.id.op_div) {
            selectOperation(Operation.DIVIDE);
            return true;
        } else if (viewId == R.id.op_sqrt) {
            appendDigit(10); // A
            return true;
        } else if (viewId == R.id.const_pi) {
            appendDigit(11); // B
            return true;
        } else if (viewId == R.id.op_pow) {
            appendDigit(12); // C
            return true;
        } else if (viewId == R.id.op_fact) {
            appendDigit(13); // D
            return true;
        } else if (viewId == R.id.toggle_mode) {
            appendDigit(14); // E
            return true;
        } else if (viewId == R.id.fun_sin) {
            appendDigit(15); // F
            return true;
        } else if (viewId == R.id.fun_cos) {
            selectOperation(Operation.AND);
            return true;
        } else if (viewId == R.id.fun_tan) {
            selectOperation(Operation.OR);
            return true;
        } else if (viewId == R.id.toggle_inv) {
            selectOperation(Operation.XOR);
            return true;
        } else if (viewId == R.id.const_e) {
            applyNot();
            return true;
        } else if (viewId == R.id.fun_ln) {
            selectOperation(Operation.SHIFT_LEFT);
            return true;
        } else if (viewId == R.id.fun_log) {
            selectOperation(Operation.SHIFT_RIGHT);
            return true;
        }
        return false;
    }

    @Override
    public void onDigit(int digit) {
        appendDigit(digit);
    }

    @Override
    public void onDecimalPoint() {
        // Signed 64-bit programmer values are integers.
    }

    @Override
    public void onDelete() {
        mError = null;
        mStartNewEntry = false;
        if (mEntry.length() > 1) {
            mEntry.deleteCharAt(mEntry.length() - 1);
            if (mEntry.length() == 1 && mEntry.charAt(0) == '-') {
                mEntry.setLength(0);
                mEntry.append('0');
            }
        } else {
            mEntry.setLength(0);
            mEntry.append('0');
        }
        redisplay();
    }

    @Override
    public void onClear() {
        reset();
        redisplay();
    }

    private void mountControls(@NonNull Context context) {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        final ViewGroup slot = host.getToolControlSlot();
        if (slot == null) {
            return;
        }
        mControlRoot = LayoutInflater.from(context)
                .inflate(R.layout.tool_programmer_control, slot, false);
        final MaterialButtonToggleGroup group =
                mControlRoot.findViewById(R.id.programmer_base_group);
        group.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.programmer_base_hex) {
                changeRadix(16);
            } else if (checkedId == R.id.programmer_base_dec) {
                changeRadix(10);
            } else if (checkedId == R.id.programmer_base_oct) {
                changeRadix(8);
            } else if (checkedId == R.id.programmer_base_bin) {
                changeRadix(2);
            }
        });
        slot.removeAllViews();
        slot.addView(mControlRoot);
        slot.setVisibility(View.VISIBLE);
    }

    private void changeRadix(int radix) {
        if (radix == mRadix) {
            return;
        }
        final long current = currentValue();
        mRadix = radix;
        setEntry(current);
        if (mPendingOperation != null) {
            mExpression = ProgrammerCalculator.format(mAccumulator, mRadix)
                    + " " + symbol(mPendingOperation);
        }
        if (mHost != null) {
            mHost.setProgrammerPadMode(true, mRadix);
        }
        redisplay();
    }

    private void appendDigit(int digit) {
        if (!ProgrammerCalculator.isDigitValid(digit, mRadix)) {
            return;
        }
        mError = null;
        if (mStartNewEntry) {
            mEntry.setLength(0);
            mStartNewEntry = false;
        }
        final char character = Character.toUpperCase(Character.forDigit(digit, mRadix));
        final String old = mEntry.toString();
        if (mEntry.length() == 1 && mEntry.charAt(0) == '0') {
            mEntry.setLength(0);
        }
        mEntry.append(character);
        try {
            currentValue(); // Reject input beyond one signed/unsigned 64-bit register.
        } catch (NumberFormatException e) {
            mEntry.setLength(0);
            mEntry.append(old);
        }
        redisplay();
    }

    private void selectOperation(@NonNull Operation operation) {
        mError = null;
        if (mPendingOperation != null && !mStartNewEntry && !applyPending()) {
            return;
        } else if (mPendingOperation == null) {
            mAccumulator = currentValue();
        }
        mPendingOperation = operation;
        mExpression = ProgrammerCalculator.format(mAccumulator, mRadix)
                + " " + symbol(operation);
        mStartNewEntry = true;
        redisplay();
    }

    private void onEquals() {
        if (mPendingOperation == null) {
            return;
        }
        final long right = currentValue();
        final String expression = ProgrammerCalculator.format(mAccumulator, mRadix)
                + " " + symbol(mPendingOperation) + " "
                + ProgrammerCalculator.format(right, mRadix) + " =";
        if (applyPending(right)) {
            mExpression = expression;
            mPendingOperation = null;
            mStartNewEntry = true;
            redisplay();
        }
    }

    private void applyNot() {
        final long value = currentValue();
        final long result = ProgrammerCalculator.not(value);
        mExpression = "NOT " + ProgrammerCalculator.format(value, mRadix);
        setEntry(result);
        mAccumulator = result;
        mPendingOperation = null;
        mStartNewEntry = true;
        mError = null;
        redisplay();
    }

    private boolean applyPending() {
        return applyPending(currentValue());
    }

    private boolean applyPending(long right) {
        if (mPendingOperation == null) {
            return true;
        }
        try {
            mAccumulator = ProgrammerCalculator.apply(
                    mAccumulator, right, mPendingOperation);
            setEntry(mAccumulator);
            mError = null;
            return true;
        } catch (ArithmeticException e) {
            if (mHost != null) {
                mError = mHost.getContext().getString(R.string.programmer_divide_by_zero);
            }
            mPendingOperation = null;
            mStartNewEntry = true;
            redisplay();
            return false;
        }
    }

    private long currentValue() {
        return ProgrammerCalculator.parse(mEntry.toString(), mRadix);
    }

    private void setEntry(long value) {
        mEntry.setLength(0);
        mEntry.append(ProgrammerCalculator.format(value, mRadix));
    }

    private void reset() {
        mEntry.setLength(0);
        mEntry.append('0');
        mAccumulator = 0;
        mPendingOperation = null;
        mStartNewEntry = true;
        mExpression = "";
        mError = null;
    }

    private void redisplay() {
        if (mHost == null) {
            return;
        }
        mHost.setToolFormula(mExpression);
        mHost.setToolResult(mError == null ? mEntry : mError);
        fitResultText();
    }

    private void configureDisplayLayout(boolean active) {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        final ViewGroup slot = host.getToolControlSlot();
        final ViewParent parent = slot != null ? slot.getParent() : null;
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        final ViewGroup display = (ViewGroup) parent;
        final View formulaScroll = display.findViewById(R.id.formula_scroll_view);
        final View formula = display.findViewById(R.id.formula);
        final TextView result = display.findViewById(R.id.result);
        if (active) {
            mResultView = result;
            if (formula != null) {
                formula.setMinimumHeight(0);
            }
            if (formulaScroll != null
                    && formulaScroll.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                final LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) formulaScroll.getLayoutParams();
                lp.height = 0;
                lp.weight = PROGRAMMER_FORMULA_WEIGHT;
                formulaScroll.setLayoutParams(lp);
            }
            if (result != null) {
                result.scrollTo(0, 0);
            }
        } else {
            if (mResultView != null) {
                mResultView.removeCallbacks(mFitResultRunnable);
                mResultView.scrollTo(0, 0);
                mResultView = null;
            }
            if (formulaScroll != null
                    && formulaScroll.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                final LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) formulaScroll.getLayoutParams();
                lp.height = 0;
                lp.weight = 1f;
                formulaScroll.setLayoutParams(lp);
            }
        }
    }

    private void fitResultText() {
        final TextView result = mResultView;
        final ToolHost host = mHost;
        if (result == null || host == null) {
            return;
        }
        final CharSequence text = result.getText();
        final String value = text == null ? "" : text.toString();
        final DisplayMetrics dm = host.getContext().getResources().getDisplayMetrics();
        final int measuredWidth = result.getWidth();
        final int availWidth = (measuredWidth > 0 ? measuredWidth : dm.widthPixels)
                - result.getPaddingLeft() - result.getPaddingRight();
        if (availWidth <= 0 || value.isEmpty()) {
            result.setTextSize(TypedValue.COMPLEX_UNIT_SP, PROGRAMMER_RESULT_MAX_SP);
            result.scrollTo(0, 0);
            return;
        }
        final TextPaint paint = new TextPaint(result.getPaint());
        float chosenSp = -1f;
        for (float sp = PROGRAMMER_RESULT_MAX_SP;
                sp >= PROGRAMMER_RESULT_SINGLE_LINE_MIN_SP; sp -= 1f) {
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm));
            if (paint.measureText(value) <= availWidth) {
                chosenSp = sp;
                break;
            }
        }
        if (chosenSp > 0f) {
            result.setSingleLine(true);
            result.setMaxLines(1);
            result.setHorizontallyScrolling(false);
            result.setTextSize(TypedValue.COMPLEX_UNIT_SP, chosenSp);
        } else {
            result.setSingleLine(false);
            result.setHorizontallyScrolling(false);
            result.setMaxLines(PROGRAMMER_RESULT_MAX_LINES);
            float multiSp = PROGRAMMER_RESULT_MULTI_LINE_MIN_SP;
            for (float sp = PROGRAMMER_RESULT_SINGLE_LINE_MIN_SP;
                    sp >= PROGRAMMER_RESULT_MULTI_LINE_MIN_SP; sp -= 1f) {
                paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm));
                final StaticLayout layout = StaticLayout.Builder
                        .obtain(value, 0, value.length(), paint, availWidth)
                        .setIncludePad(false)
                        .build();
                if (layout.getLineCount() <= PROGRAMMER_RESULT_MAX_LINES) {
                    multiSp = sp;
                    break;
                }
            }
            result.setTextSize(TypedValue.COMPLEX_UNIT_SP, multiSp);
        }
        result.scrollTo(0, 0);
        if (measuredWidth <= 0) {
            result.removeCallbacks(mFitResultRunnable);
            result.post(mFitResultRunnable);
        }
    }

    @Nullable
    private static Long parseCarryValue(@Nullable String carryValue) {
        if (carryValue == null) {
            return null;
        }
        try {
            return new BigDecimal(carryValue).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static String symbol(@NonNull Operation operation) {
        switch (operation) {
            case ADD:
                return "+";
            case SUBTRACT:
                return "−";
            case MULTIPLY:
                return "×";
            case DIVIDE:
                return "÷";
            case AND:
                return "AND";
            case OR:
                return "OR";
            case XOR:
                return "XOR";
            case SHIFT_LEFT:
                return "<<";
            case SHIFT_RIGHT:
                return ">>";
            default:
                throw new AssertionError("Unknown operation " + operation);
        }
    }
}
