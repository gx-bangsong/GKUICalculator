/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Renders numbers as Chinese uppercase numerals (中文大写数字), the form used on cheques,
 * contracts and receipts so that an amount cannot be altered by adding a stroke.
 * <p>
 * Two flavours are provided:
 * <ul>
 *   <li>{@link #toUppercase(String)} — plain numerals, e.g. {@code 1,234.56} becomes
 *       壹仟贰佰叁拾肆点伍陆. Used where the unit is not the yuan (currency conversion).</li>
 *   <li>{@link #toUppercaseAmount(BigDecimal)} — the regulated 人民币大写 money form with
 *       元 / 角 / 分 and a trailing 整, e.g. {@code 5304.31} becomes 伍仟叁佰零肆元叁角壹分.
 *       Used by the yuan-denominated tools (mortgage, income tax).</li>
 * </ul>
 * Values are grouped in fours (万 / 亿 / 万亿 / 亿亿), and runs of zero digits collapse into a
 * single 零, following the usual convention.
 */
public final class ChineseNumerals {

    private static final char[] DIGITS = {'零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'};

    /** Unit of a digit inside a group of four: ones / tens / hundreds / thousands. */
    private static final String[] UNIT = {"", "拾", "佰", "仟"};

    /** Unit of each group of four digits, lowest group first. */
    private static final String[] GROUP_UNIT = {"", "万", "亿", "万亿", "亿亿"};

    private static final BigInteger HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger TEN_THOUSAND = BigInteger.valueOf(10000);

    private ChineseNumerals() {
    }

    /**
     * Convert a plain number to Chinese uppercase numerals, keeping the decimal point as 点
     * (each fractional digit is read separately, as is conventional).
     * <p>
     * Grouping separators and spaces are ignored; a leading {@code -} (or the calculator's
     * minus sign) becomes 负. Anything that is not a number is returned unchanged so that
     * placeholders such as "—" survive.
     */
    @NonNull
    public static String toUppercase(@Nullable String text) {
        if (text == null) {
            return "";
        }
        final String raw = text.trim();
        if (raw.isEmpty()) {
            return "";
        }
        final StringBuilder digits = new StringBuilder(raw.length());
        boolean negative = false;
        boolean seenDigit = false;
        boolean seenDot = false;
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
                seenDigit = true;
            } else if (c == '.' && !seenDot) {
                digits.append(c);
                seenDot = true;
            } else if ((c == '-' || c == '\u2212') && !seenDigit && !seenDot) {
                negative = true;
            } else if (c == '+' && !seenDigit && !seenDot) {
                // Ignored.
            } else if (c == ',' || Character.isWhitespace(c)) {
                // Grouping separators and (thin) spaces: ignored.
            } else {
                return raw;  // Not a number: leave it alone.
            }
        }
        if (digits.length() == 0 || !seenDigit) {
            return raw;
        }

        final String value = digits.toString();
        final int dot = value.indexOf('.');
        final String intPart = dot < 0 ? value : value.substring(0, dot);
        final String fracPart = dot < 0 ? "" : value.substring(dot + 1);

        final StringBuilder out = new StringBuilder();
        if (negative && (!isZero(intPart) || !isZero(fracPart))) {
            out.append('负');
        }
        if (intPart.length() == 0) {
            out.append('零');   // e.g. ".5"
        } else {
            appendInteger(out, intPart);
        }
        if (fracPart.length() > 0) {
            out.append('点');
            for (int i = 0; i < fracPart.length(); i++) {
                final char c = fracPart.charAt(i);
                if (c >= '0' && c <= '9') {
                    out.append(DIGITS[c - '0']);
                }
            }
        }
        return out.toString();
    }

    /**
     * Convert an amount of yuan to the regulated 人民币大写 form: 元 / 角 / 分, ending in 整
     * whenever the 分 digit is zero. {@code null} yields "—".
     */
    @NonNull
    public static String toUppercaseAmount(@Nullable BigDecimal value) {
        if (value == null) {
            return "—";
        }
        final BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        final boolean negative = rounded.signum() < 0;
        final BigInteger cents = rounded.abs().unscaledValue();
        final BigInteger[] wholeAndRest = cents.divideAndRemainder(HUNDRED);
        final BigInteger yuan = wholeAndRest[0];
        final int rest = wholeAndRest[1].intValue();
        final int jiao = rest / 10;
        final int fen = rest % 10;

        final StringBuilder out = new StringBuilder();
        if (negative && (yuan.signum() != 0 || jiao != 0 || fen != 0)) {
            out.append('负');
        }
        if (yuan.signum() == 0) {
            if (jiao == 0 && fen == 0) {
                return out.append("零元整").toString();
            }
            if (jiao > 0) {
                out.append(DIGITS[jiao]).append('角');
            }
            if (fen > 0) {
                out.append(DIGITS[fen]).append('分');
            } else {
                out.append('整');
            }
            return out.toString();
        }
        appendInteger(out, yuan.toString());
        out.append('元');
        if (jiao > 0) {
            out.append(DIGITS[jiao]).append('角');
            if (fen > 0) {
                out.append(DIGITS[fen]).append('分');
            } else {
                out.append('整');
            }
        } else if (fen > 0) {
            out.append('零').append(DIGITS[fen]).append('分');
        } else {
            out.append('整');
        }
        return out.toString();
    }

    /** Append the uppercase form of a non-negative integer literal (digits only). */
    private static void appendInteger(@NonNull StringBuilder out, @NonNull String digits) {
        final String upper = groupUppercase(digits);
        if (upper != null) {
            out.append(upper);
            return;
        }
        // Astronomically large (beyond 亿亿): fall back to reading the digits one by one,
        // which is still unambiguous and, above all, complete.
        for (int i = 0; i < digits.length(); i++) {
            final char c = digits.charAt(i);
            out.append(c >= '0' && c <= '9' ? DIGITS[c - '0'] : c);
        }
    }

    /**
     * Uppercase form of a non-negative integer literal using 拾/佰/仟 and 万/亿 grouping,
     * or {@code null} if the value is too large to name (more than {@code 亿亿}).
     */
    @Nullable
    private static String groupUppercase(@NonNull String digits) {
        final BigInteger value = new BigInteger(digits);
        if (value.signum() == 0) {
            return "零";
        }
        int groupCount = 0;
        BigInteger rest = value;
        while (rest.signum() > 0) {
            rest = rest.divide(TEN_THOUSAND);
            groupCount++;
        }
        if (groupCount > GROUP_UNIT.length) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        boolean pendingZero = false;
        for (int group = groupCount - 1; group >= 0; group--) {
            final int chunk = chunkAt(value, group);
            if (chunk == 0) {
                if (out.length() > 0) {
                    pendingZero = true;
                }
                continue;
            }
            if (out.length() > 0 && (pendingZero || chunk < 1000)) {
                out.append('零');
            }
            pendingZero = false;
            appendChunk(out, chunk);
            out.append(GROUP_UNIT[group]);
        }
        return out.toString();
    }

    /** Digits of the {@code group}-th group of four, counted from the lowest. */
    private static int chunkAt(@NonNull BigInteger value, int group) {
        BigInteger chunk = value;
        for (int i = 0; i < group; i++) {
            chunk = chunk.divide(TEN_THOUSAND);
        }
        return chunk.mod(TEN_THOUSAND).intValue();
    }

    /** Uppercase form of a number in [1, 9999] with 拾/佰/仟 units and 零 filling. */
    private static void appendChunk(@NonNull StringBuilder out, int chunk) {
        // A 零 is only inserted between two digits of this chunk; leading zeros are handled by
        // the caller (they are what makes a lower group need a 零 in front of it).
        boolean started = false;
        boolean pendingZero = false;
        for (int pos = 3; pos >= 0; pos--) {
            final int divisor = (int) Math.pow(10, pos);
            final int digit = (chunk / divisor) % 10;
            if (digit == 0) {
                if (started) {
                    pendingZero = true;
                }
            } else {
                if (pendingZero) {
                    out.append('零');
                    pendingZero = false;
                }
                out.append(DIGITS[digit]).append(UNIT[pos]);
                started = true;
            }
        }
    }

    private static boolean isZero(@NonNull String digits) {
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
