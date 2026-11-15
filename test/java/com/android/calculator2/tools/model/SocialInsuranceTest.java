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

/** Unit tests for {@link SocialInsurance}: six proportional personal categories. */
public class SocialInsuranceTest {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private static BigDecimal[] rates(String... values) {
        BigDecimal[] out = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = new BigDecimal(values[i]);
        }
        return out;
    }

    @Test
    public void defaultRatesOnTenThousandBase() {
        // 8 + 2 + 0.5 + 0 + 0 + 12 = 22.5% of 10000 = 2250
        assertEquals(0, new BigDecimal("2250").compareTo(SocialInsurance.monthly(
                new BigDecimal("10000"),
                rates("8", "2", "0.5", "0", "0", "12"), MC)));
    }

    @Test
    public void zeroBaseContributesNothing() {
        assertEquals(0, BigDecimal.ZERO.compareTo(SocialInsurance.monthly(
                BigDecimal.ZERO, rates("8", "2", "0.5", "0", "0", "12"), MC)));
    }

    @Test
    public void missingRatesCountAsZero() {
        // only pension 8% of 5000 = 400
        assertEquals(0, new BigDecimal("400").compareTo(SocialInsurance.monthly(
                new BigDecimal("5000"), rates("8"), MC)));
    }
}
