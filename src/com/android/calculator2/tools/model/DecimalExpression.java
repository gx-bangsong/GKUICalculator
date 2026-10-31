/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Small decimal expression editor/evaluator shared by the unit and currency converters.
 * Supports the four arithmetic operations with normal multiplication/division precedence.
 */
public final class DecimalExpression {

    public enum Operation {
        ADD('+'), SUBTRACT('\u2212'), MULTIPLY('\u00d7'), DIVIDE('\u00f7');

        final char symbol;

        Operation(char symbol) {
            this.symbol = symbol;
        }
    }

    private static final int MAX_INPUT_LENGTH = 100;
    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);

    private final StringBuilder mExpression = new StringBuilder();
    private boolean mReplaceOnDigit;

    public DecimalExpression(@Nullable String initialValue) {
        set(initialValue);
    }

    /** Replace the expression with a plain decimal value, or zero when it is invalid/missing. */
    public void set(@Nullable String value) {
        mExpression.setLength(0);
        if (value != null) {
            try {
                mExpression.append(format(new BigDecimal(value.replace(",", ""))));
            } catch (NumberFormatException ignored) {
                // Leave it empty; the default below is easier to edit than an error token.
            }
        }
        if (mExpression.length() == 0) {
            mExpression.append('0');
        }
        mReplaceOnDigit = false;
    }

    public void onDigit(int digit) {
        if (digit < 0 || digit > 9 || mExpression.length() >= MAX_INPUT_LENGTH) {
            return;
        }
        beginEntryIfNeeded();
        final int operandStart = currentOperandStart();
        if (mExpression.length() == operandStart + 1
                && mExpression.charAt(operandStart) == '0') {
            mExpression.setCharAt(operandStart, (char) ('0' + digit));
        } else {
            mExpression.append((char) ('0' + digit));
        }
    }

    public void onDecimalPoint() {
        if (mExpression.length() >= MAX_INPUT_LENGTH) {
            return;
        }
        beginEntryIfNeeded();
        final int operandStart = currentOperandStart();
        for (int i = operandStart; i < mExpression.length(); i++) {
            if (mExpression.charAt(i) == '.') {
                return;
            }
        }
        if (operandStart == mExpression.length()) {
            mExpression.append('0');
        }
        mExpression.append('.');
    }

    public void onOperator(@NonNull Operation operation) {
        mReplaceOnDigit = false;
        if (mExpression.length() == 0) {
            if (operation == Operation.SUBTRACT) {
                mExpression.append(operation.symbol);
            }
            return;
        }
        final int last = mExpression.length() - 1;
        final char lastChar = mExpression.charAt(last);
        if (lastChar == '.') {
            mExpression.append('0');
        } else if (isOperator(lastChar)) {
            // A second operator replaces the first, making accidental taps easy to correct.
            // Preserve a sole leading minus because it is the sign of the first operand.
            if (mExpression.length() == 1 && isMinus(lastChar)) {
                return;
            }
            mExpression.setCharAt(last, operation.symbol);
            return;
        }
        if (mExpression.length() < MAX_INPUT_LENGTH) {
            mExpression.append(operation.symbol);
        }
    }

    public void onDelete() {
        mReplaceOnDigit = false;
        if (mExpression.length() > 0) {
            mExpression.deleteCharAt(mExpression.length() - 1);
        }
    }

    public void onClear() {
        mExpression.setLength(0);
        mReplaceOnDigit = false;
    }

    /** Evaluate and replace the expression with its result. Returns false for incomplete/errors. */
    public boolean onEquals() {
        final BigDecimal value = evaluate(false);
        if (value == null) {
            return false;
        }
        mExpression.setLength(0);
        mExpression.append(format(value));
        mReplaceOnDigit = true;
        return true;
    }

    /** Current value used by the converter; a trailing operator is ignored while editing. */
    @Nullable
    public BigDecimal getValue() {
        return evaluate(true);
    }

    /** Expression shown in the converter input row. */
    @NonNull
    public String getDisplayText() {
        return mExpression.length() == 0 ? "0" : mExpression.toString();
    }

    /** Evaluated value as ungrouped decimal text, suitable for Chinese numeral conversion. */
    @Nullable
    public String getValueText() {
        final BigDecimal value = getValue();
        return value == null ? null : format(value);
    }

    private void beginEntryIfNeeded() {
        if (mReplaceOnDigit) {
            mExpression.setLength(0);
            mReplaceOnDigit = false;
        }
    }

    private int currentOperandStart() {
        for (int i = mExpression.length() - 1; i >= 0; i--) {
            if (isOperator(mExpression.charAt(i))) {
                return i + 1;
            }
        }
        return 0;
    }

    @Nullable
    private BigDecimal evaluate(boolean allowTrailingOperator) {
        String text = mExpression.toString();
        if (allowTrailingOperator) {
            while (!text.isEmpty() && isOperator(text.charAt(text.length() - 1))) {
                // A single leading minus is a sign, not a complete expression.
                if (text.length() == 1 && isMinus(text.charAt(0))) {
                    return null;
                }
                text = text.substring(0, text.length() - 1);
            }
        }
        if (text.isEmpty() || text.endsWith(".")) {
            if (text.endsWith(".") && text.length() > 1) {
                text += "0";
            } else {
                return null;
            }
        }
        try {
            final Parser parser = new Parser(text);
            final BigDecimal value = parser.parseExpression();
            return parser.atEnd() ? value : null;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static String format(@NonNull BigDecimal value) {
        BigDecimal rounded = value.round(MC).stripTrailingZeros();
        if (rounded.scale() < 0) {
            rounded = rounded.setScale(0, RoundingMode.HALF_UP);
        }
        final String text = rounded.toPlainString();
        return "-0".equals(text) ? "0" : text;
    }

    private static boolean isOperator(char character) {
        return character == '+' || isMinus(character)
                || character == '\u00d7' || character == '*'
                || character == '\u00f7' || character == '/';
    }

    private static boolean isMinus(char character) {
        return character == '-' || character == '\u2212';
    }

    /** Recursive-descent parser: expression = term ((+|−) term)*, term = number ((*|÷) number)*. */
    private static final class Parser {
        private final String mText;
        private int mPosition;

        Parser(@NonNull String text) {
            mText = text;
        }

        BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (!atEnd()) {
                final char operation = peek();
                if (operation != '+' && !isMinus(operation)) {
                    break;
                }
                mPosition++;
                final BigDecimal right = parseTerm();
                value = operation == '+' ? value.add(right, MC) : value.subtract(right, MC);
            }
            return value;
        }

        BigDecimal parseTerm() {
            BigDecimal value = parseNumber();
            while (!atEnd()) {
                final char operation = peek();
                if (operation != '\u00d7' && operation != '*'
                        && operation != '\u00f7' && operation != '/') {
                    break;
                }
                mPosition++;
                final BigDecimal right = parseNumber();
                if (operation == '\u00d7' || operation == '*') {
                    value = value.multiply(right, MC);
                } else {
                    value = value.divide(right, MC);
                }
            }
            return value;
        }

        BigDecimal parseNumber() {
            final int start = mPosition;
            if (!atEnd() && isMinus(peek())) {
                mPosition++;
            }
            boolean hasDigit = false;
            boolean hasPoint = false;
            while (!atEnd()) {
                final char character = peek();
                if (character >= '0' && character <= '9') {
                    hasDigit = true;
                    mPosition++;
                } else if (character == '.' && !hasPoint) {
                    hasPoint = true;
                    mPosition++;
                } else {
                    break;
                }
            }
            if (!hasDigit) {
                throw new NumberFormatException("Missing operand");
            }
            return new BigDecimal(mText.substring(start, mPosition).replace('\u2212', '-'));
        }

        char peek() {
            return mText.charAt(mPosition);
        }

        boolean atEnd() {
            return mPosition >= mText.length();
        }
    }
}
