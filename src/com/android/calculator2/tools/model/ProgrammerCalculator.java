/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import androidx.annotation.NonNull;

/** Pure signed 64-bit arithmetic and radix formatting for Programmer mode. */
public final class ProgrammerCalculator {

    public enum Operation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE, AND, OR, XOR, SHIFT_LEFT, SHIFT_RIGHT
    }

    private ProgrammerCalculator() {
    }

    /** Uses Java long overflow and two's-complement behavior, matching a 64-bit CPU register. */
    public static long apply(long left, long right, @NonNull Operation operation) {
        switch (operation) {
            case ADD:
                return left + right;
            case SUBTRACT:
                return left - right;
            case MULTIPLY:
                return left * right;
            case DIVIDE:
                if (right == 0) {
                    throw new ArithmeticException("division by zero");
                }
                return left / right;
            case AND:
                return left & right;
            case OR:
                return left | right;
            case XOR:
                return left ^ right;
            case SHIFT_LEFT:
                return left << (right & 63);
            case SHIFT_RIGHT:
                return left >> (right & 63);
            default:
                throw new AssertionError("Unknown operation " + operation);
        }
    }

    public static long not(long value) {
        return ~value;
    }

    /**
     * Decimal is displayed as signed. Other bases display the full unsigned two's-complement bit
     * pattern for negative values, as conventional programmer calculators do.
     */
    @NonNull
    public static String format(long value, int radix) {
        checkRadix(radix);
        if (radix == 10) {
            return Long.toString(value);
        }
        return Long.toUnsignedString(value, radix).toUpperCase(java.util.Locale.ROOT);
    }

    /** Parses one signed-decimal or unsigned two's-complement non-decimal 64-bit value. */
    public static long parse(@NonNull String text, int radix) {
        checkRadix(radix);
        if (radix == 10 || text.startsWith("-")) {
            return Long.parseLong(text, radix);
        }
        return Long.parseUnsignedLong(text, radix);
    }

    public static boolean isDigitValid(int digit, int radix) {
        checkRadix(radix);
        return digit >= 0 && digit < radix;
    }

    private static void checkRadix(int radix) {
        if (radix != 2 && radix != 8 && radix != 10 && radix != 16) {
            throw new IllegalArgumentException("Unsupported radix " + radix);
        }
    }
}
