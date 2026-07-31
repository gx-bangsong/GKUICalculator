/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link CurrencyConversion} (EUR-based cross-rate conversion).
 */
public class CurrencyConversionTest {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }

    @Test
    public void euroToUsd() {
        // 100 EUR (rate 1) -> USD (rate 1.0823) = 108.23
        assertDecimal("108.23", CurrencyConversion.convert(bd("100"), bd("1"), bd("1.0823"), MC));
    }

    @Test
    public void usdToEuro() {
        // 108.23 USD (rate 1.0823) -> EUR (rate 1) = 100
        assertDecimal("100",
                CurrencyConversion.convert(bd("108.23"), bd("1.0823"), bd("1"), MC));
    }

    @Test
    public void euroToCny() {
        assertDecimal("785.23", CurrencyConversion.convert(bd("100"), bd("1"), bd("7.8523"), MC));
    }

    @Test
    public void sameCurrencyIsIdentity() {
        BigDecimal r = bd("1.0823");
        assertDecimal("50", CurrencyConversion.convert(bd("50"), r, r, MC));
    }

    @Test
    public void roundTrip() {
        BigDecimal eur = bd("100");
        BigDecimal usd = CurrencyConversion.convert(eur, bd("1"), bd("1.0823"), MC);
        BigDecimal back = CurrencyConversion.convert(usd, bd("1.0823"), bd("1"), MC);
        assertEquals(0, eur.compareTo(back));
    }

    @Test
    public void nullInputsReturnNull() {
        assertNull(CurrencyConversion.convert(null, bd("1"), bd("1"), MC));
        assertNull(CurrencyConversion.convert(bd("1"), null, bd("1"), MC));
        assertNull(CurrencyConversion.convert(bd("1"), bd("1"), null, MC));
    }

    @Test
    public void zeroFromRateReturnsNull() {
        assertNull(CurrencyConversion.convert(bd("1"), BigDecimal.ZERO, bd("1"), MC));
    }
}
