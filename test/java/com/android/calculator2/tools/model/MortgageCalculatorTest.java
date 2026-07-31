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
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MortgageCalculator}.
 */
public class MortgageCalculatorTest {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }

    @Test
    public void equalPrincipalExactValues() {
        // P=120000, annual 12% -> monthly r=0.01, 1 year (n=12)
        MortgageCalculator.Result r = MortgageCalculator.equalPrincipal(
                bd("120000"), bd("12"), 1, MC);
        assertEquals(12, r.months);
        assertDecimal("11200", r.monthlyPayment);   // 10000 + 1200
        assertDecimal("100", r.monthlyDecrease);    // 10000 * 0.01
        assertDecimal("7800", r.totalInterest);     // 1200 * 13 / 2
        assertDecimal("127800", r.totalPayment);    // 120000 + 7800
    }

    @Test
    public void equalPrincipalZeroRate() {
        MortgageCalculator.Result r = MortgageCalculator.equalPrincipal(
                bd("120000"), BigDecimal.ZERO, 1, MC);
        assertDecimal("10000", r.monthlyPayment);
        assertDecimal("0", r.monthlyDecrease);
        assertDecimal("0", r.totalInterest);
        assertDecimal("120000", r.totalPayment);
    }

    @Test
    public void equalPaymentZeroRate() {
        MortgageCalculator.Result r = MortgageCalculator.equalPayment(
                bd("120000"), BigDecimal.ZERO, 1, MC);
        assertEquals(12, r.months);
        assertDecimal("10000", r.monthlyPayment); // 120000 / 12
        assertDecimal("0", r.totalInterest);
        assertDecimal("120000", r.totalPayment);
    }

    @Test
    public void equalPaymentInvariants() {
        MortgageCalculator.Result r = MortgageCalculator.equalPayment(
                bd("100000"), bd("12"), 1, MC);
        assertTrue("monthly must be positive", r.monthlyPayment.signum() > 0);
        assertTrue("interest must be positive", r.totalInterest.signum() > 0);
        // total = monthly * months, interest = total - principal
        assertEquals(0, r.totalPayment.compareTo(
                r.monthlyPayment.multiply(new BigDecimal(12), MC)));
        assertEquals(0, r.totalInterest.compareTo(
                r.totalPayment.subtract(bd("100000"), MC)));
    }

    @Test
    public void equalPaymentTypicalLoanBallpark() {
        // 1,000,000 at 4.9% for 30 years is about 5307/month.
        MortgageCalculator.Result r = MortgageCalculator.equalPayment(
                bd("1000000"), bd("4.9"), 30, MC);
        assertTrue("monthly too small: " + r.monthlyPayment,
                r.monthlyPayment.compareTo(bd("5300")) >= 0);
        assertTrue("monthly too large: " + r.monthlyPayment,
                r.monthlyPayment.compareTo(bd("5310")) <= 0);
        assertEquals(360, r.months);
    }

    @Test
    public void zeroTermIsSafe() {
        MortgageCalculator.Result r = MortgageCalculator.equalPayment(
                bd("100000"), bd("5"), 0, MC);
        assertEquals(0, r.months);
        assertDecimal("0", r.monthlyPayment);
    }
}
