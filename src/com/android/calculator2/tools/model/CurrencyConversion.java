/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Pure currency conversion, independent of Android.
 * <p>
 * ECB publishes rates with EUR as the base (EUR = 1). Converting {@code amount} from one currency
 * to another is therefore a cross-rate through EUR:
 * <pre>result = amount / fromRate * toRate</pre>
 * This never touches the calculator engine.
 */
public final class CurrencyConversion {

    private CurrencyConversion() {
    }

    /**
     * @return the converted amount, or {@code null} if any input is missing or fromRate is zero.
     */
    public static BigDecimal convert(BigDecimal amount, BigDecimal fromRate, BigDecimal toRate,
            MathContext mc) {
        if (amount == null || fromRate == null || toRate == null) {
            return null;
        }
        if (fromRate.signum() == 0) {
            return null;
        }
        // amount / fromRate * toRate, multiplying first to keep one rounding step.
        return amount.multiply(toRate, mc).divide(fromRate, mc);
    }
}
