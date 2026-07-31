/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import com.android.calculator2.tools.data.TaxTable;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link IncomeTaxCalculator} (cumulative withholding, China).
 */
public class IncomeTaxCalculatorTest {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final TaxTable TABLE = TaxTable.defaults();

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }

    @Test
    public void typicalSalary() {
        // salary 20000, insurance 2000, special 1000, threshold 5000 -> monthlyNet 12000
        IncomeTaxCalculator.Result r = IncomeTaxCalculator.computeAnnual(
                bd("20000"), bd("2000"), bd("1000"), TABLE, MC);
        assertDecimal("144000", r.annualTaxableIncome); // 12000 * 12
        assertDecimal("11880", r.annualTax);            // 144000*0.10 - 2520
        assertDecimal("1200", r.lastMonthWithholding);  // month 12: 11880 - (132000*0.10-2520)
        assertDecimal("204120", r.annualAfterTax);      // (20000-2000)*12 - 11880
        assertDecimal("0.10", r.applicableRate);
    }

    @Test
    public void belowThresholdPaysNoTax() {
        IncomeTaxCalculator.Result r = IncomeTaxCalculator.computeAnnual(
                bd("4000"), BigDecimal.ZERO, BigDecimal.ZERO, TABLE, MC);
        assertDecimal("0", r.annualTax);
        assertDecimal("0", r.annualTaxableIncome);
        assertDecimal("48000", r.annualAfterTax); // 4000 * 12
    }

    @Test
    public void highSalaryCrossesBrackets() {
        // salary 50000, insurance 5000, special 2000 -> monthlyNet 38000, annual 456000 (30%)
        IncomeTaxCalculator.Result r = IncomeTaxCalculator.computeAnnual(
                bd("50000"), bd("5000"), bd("2000"), TABLE, MC);
        assertDecimal("456000", r.annualTaxableIncome);
        assertDecimal("83880", r.annualTax); // 456000*0.30 - 52920
        assertDecimal("456120", r.annualAfterTax);
        assertDecimal("0.30", r.applicableRate);
    }

    @Test
    public void monthTaxNeverNegative() {
        // Edge: large special deduction brings monthlyNet just above 0.
        IncomeTaxCalculator.Result r = IncomeTaxCalculator.computeAnnual(
                bd("6000"), BigDecimal.ZERO, BigDecimal.ZERO, TABLE, MC);
        // monthlyNet = 1000, annualTaxable = 12000 -> 3%, annual tax = 12000*0.03 = 360
        assertDecimal("360", r.annualTax);
        assertTrue(r.lastMonthWithholding.signum() >= 0);
    }

    private static void assertTrue(boolean condition) {
        org.junit.Assert.assertTrue(condition);
    }
}
