/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.os.Bundle;
import com.android.calculator2.tools.ToolState;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.button.MaterialButton;

import java.math.BigDecimal;

/** Signed 64-bit programmer calculator with four bases and arithmetic/bitwise operations. */
public final class ProgrammerMode implements ToolMode {

    private int mRadix = 10;
    /** Second numeral system shown beneath the entry, converter-style. */
    private int mSecondRadix = 16;
    /** Conversion options from the overflow menu: register width and signedness. */
    private int mWidthBits = 64;
    private boolean mUnsigned;
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
        return R.drawable.ic_tool_programmer;
    }

    @Override public Bundle saveState() {
        Bundle state=new Bundle();
        state.putInt("radix",mRadix);state.putInt("second",mSecondRadix);state.putInt("width",mWidthBits);
        state.putBoolean("unsigned",mUnsigned);state.putString("entry",mEntry.toString());
        state.putLong("accumulator",mAccumulator);state.putBoolean("newEntry",mStartNewEntry);
        state.putString("pending",mPendingOperation==null?null:mPendingOperation.name());
        state.putString("expression",mExpression);state.putString("error",mError);
        if(mControlRoot!=null) state.putBundle("selection",ToolState.selection(mControlRoot.findViewById(R.id.programmer_value_in)));
        return state;
    }
    @Override public void onRestore(ToolHost host,Bundle state) { activate(host,null,state); }
    @Override public void onActivate(ToolHost host,String carryValue) { activate(host,carryValue,null); }
    private void activate(ToolHost host,String carryValue,Bundle state) {
        mHost = host;
        if(state!=null) {
            mRadix=state.getInt("radix");mSecondRadix=state.getInt("second");mWidthBits=state.getInt("width");
            mUnsigned=state.getBoolean("unsigned");mEntry.replace(0,mEntry.length(),state.getString("entry"));
            mAccumulator=state.getLong("accumulator");mStartNewEntry=state.getBoolean("newEntry");
            String operation=state.getString("pending");mPendingOperation=operation==null?null:Operation.valueOf(operation);
            mExpression=state.getString("expression", "");mError=state.getString("error");
        } else {
        mRadix = 10;
        reset();
        final Long carried = parseCarryValue(carryValue);
        if (carried != null) {
            setEntry(carried);
        }
        }
        mountControls(host.getContext());
        host.setProgrammerPadMode(true, mRadix);
        host.setToolResultTextSizeSp(36f);
        host.refreshOptionsMenu();
        redisplay();
        if(state!=null) ToolState.restoreSelection(mControlRoot.findViewById(R.id.programmer_value_in),state.getBundle("selection"));
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
        host.setProgrammerPadMode(false, 10);
        final ViewGroup slot = host.getToolControlSlot();
        if (slot != null) {
            slot.removeAllViews();
            slot.setVisibility(View.GONE);
        }
        mControlRoot = null;
        mHost = null;
        host.refreshOptionsMenu();
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
        // inputType="text" gives the value fields an Editor so cursorVisible and the long-press
        // selection menu work. Keep the soft keyboard off: the calculator pad is the keyboard.
        final TextView valueIn = mControlRoot.findViewById(R.id.programmer_value_in);
        if (valueIn != null) valueIn.setShowSoftInputOnFocus(false);
        final TextView valueOut = mControlRoot.findViewById(R.id.programmer_value_out);
        if (valueOut != null) valueOut.setShowSoftInputOnFocus(false);
        final MaterialButton chipIn = mControlRoot.findViewById(R.id.programmer_chip_in);
        if (chipIn != null) {
            chipIn.setOnClickListener(v -> changeRadix(nextRadix(mRadix, mSecondRadix)));
        }
        final MaterialButton chipOut = mControlRoot.findViewById(R.id.programmer_chip_out);
        if (chipOut != null) {
            chipOut.setOnClickListener(v -> {
                mSecondRadix = nextRadix(mSecondRadix, mRadix);
                redisplay();
            });
        }
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
            mExpression = fmt(mAccumulator, mRadix) + " " + symbol(mPendingOperation);
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
        mExpression = fmt(mAccumulator, mRadix) + " " + symbol(operation);
        mStartNewEntry = true;
        redisplay();
    }

    private void onEquals() {
        if (mPendingOperation == null) {
            return;
        }
        final long right = currentValue();
        final String expression = fmt(mAccumulator, mRadix)
                + " " + symbol(mPendingOperation) + " "
                + fmt(right, mRadix) + " =";
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
        mExpression = "NOT " + fmt(value, mRadix);
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
        mHost.setToolFormula("");
        mHost.setToolResult("");
        if (mControlRoot == null) {
            return;
        }
        final TextView status = mControlRoot.findViewById(R.id.programmer_status);
        String statusText = mError != null ? mError : mExpression;
        status.setText(statusText);
        status.setVisibility(statusText.isEmpty() ? View.GONE : View.VISIBLE);
        final long value = mError == null ? currentValue() : 0L;
        final TextView valueIn = mControlRoot.findViewById(R.id.programmer_value_in);
        if (valueIn != null) {
            valueIn.setText(fmt(value, mRadix));
        }
        final TextView valueOut = mControlRoot.findViewById(R.id.programmer_value_out);
        if (valueOut != null) {
            valueOut.setText(fmt(value, mSecondRadix));
        }
        final MaterialButton chipIn = mControlRoot.findViewById(R.id.programmer_chip_in);
        if (chipIn != null) {
            chipIn.setText(radixNameRes(mRadix));
        }
        final MaterialButton chipOut = mControlRoot.findViewById(R.id.programmer_chip_out);
        if (chipOut != null) {
            chipOut.setText(radixNameRes(mSecondRadix));
        }
        final TextView widthLabel = mControlRoot.findViewById(R.id.programmer_width_label);
        if (widthLabel != null) {
            final Context context = mHost.getContext();
            widthLabel.setText(context.getString(mUnsigned
                    ? R.string.programmer_unsigned : R.string.programmer_signed)
                    + " " + mWidthBits + "-bit");
        }
    }

    /** The next radix in the HEX/DEC/OCT/BIN cycle that is not {@code excluded}. */
    private static int nextRadix(int current, int excluded) {
        final int[] order = {16, 10, 8, 2};
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                for (int step = 1; step <= order.length; step++) {
                    final int candidate = order[(i + step) % order.length];
                    if (candidate != excluded) {
                        return candidate;
                    }
                }
            }
        }
        return current;
    }

    private static int radixNameRes(int radix) {
        switch (radix) {
            case 16:
                return R.string.programmer_hex;
            case 8:
                return R.string.programmer_oct;
            case 2:
                return R.string.programmer_bin;
            default:
                return R.string.programmer_dec;
        }
    }

    /** Formats a register value under the overflow menu's width / signedness options. */
    private String fmt(long value, int radix) {
        long masked = value;
        if (mWidthBits < 64) {
            masked &= (1L << mWidthBits) - 1;
            if (!mUnsigned && (masked & (1L << (mWidthBits - 1))) != 0) {
                masked |= -1L << mWidthBits;   // sign-extend back to 64 bits
            }
        }
        final String digits = mUnsigned
                ? Long.toUnsignedString(masked, radix)
                : Long.toString(masked, radix);
        return digits.toUpperCase(java.util.Locale.ROOT);
    }

    /** Overflow-menu conversion options. */
    public void setWidthBits(int widthBits) {
        if (widthBits == 8 || widthBits == 16 || widthBits == 32 || widthBits == 64) {
            mWidthBits = widthBits;
            redisplay();
        }
    }

    public int getWidthBits() {
        return mWidthBits;
    }

    public void setUnsigned(boolean unsigned) {
        mUnsigned = unsigned;
        redisplay();
    }

    public boolean isUnsigned() {
        return mUnsigned;
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
