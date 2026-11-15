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

/**
 * Unit tests for {@link SpecialDeductions}: the seven 专项附加扣除 categories and their monthly
 * totals, including the mortgage/rent exclusion and the sharing caps.
 */
public class SpecialDeductionsTest {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertMonthly(String expected, SpecialDeductions d) {
        assertEquals(0, bd(expected).compareTo(d.monthlyTotal(MC)));
    }

    @Test
    public void typicalOnlyChildFamily() {
        SpecialDeductions d = new SpecialDeductions();
        d.educationChildren = 1;                 // 1000 at 100%
        d.rent = SpecialDeductions.RentTier.TIER_1500;
        d.elderlyOnlyChild = true;               // 2000
        assertMonthly("4500", d);
    }

    @Test
    public void mortgageInterestExcludesRent() {
        SpecialDeductions d = new SpecialDeductions();
        d.mortgageInterest = true;               // 1000 at 100%
        d.rent = SpecialDeductions.RentTier.TIER_1500;  // ignored
        assertMonthly("1000", d);
    }

    @Test
    public void majorMedicalIsCappedAndSpread() {
        SpecialDeductions d = new SpecialDeductions();
        d.majorMedicalAnnualSelfPaid = bd("95000");   // over-floor part 80000 (the cap)
        assertMonthly("6666.67", d);                    // 80000 / 12
    }

    @Test
    public void sharedElderlySupportIsCappedAt1000() {
        SpecialDeductions d = new SpecialDeductions();
        d.elderlySharedMonthly = bd("1500");
        assertMonthly("1000", d);
    }

    @Test
    public void halfSharesHalvePerChildAmounts() {
        SpecialDeductions d = new SpecialDeductions();
        d.educationChildren = 2;
        d.educationShare = SpecialDeductions.Share.HALF;   // 2 * 1000 * 0.5
        d.infantChildren = 1;
        d.infantShare = SpecialDeductions.Share.HALF;      // 500
        assertMonthly("1500", d);
    }

    @Test
    public void certificateYearIsSpreadOverTwelveMonths() {
        SpecialDeductions d = new SpecialDeductions();
        d.continuing = SpecialDeductions.ContinuingEducation.CERTIFICATE_YEAR;
        assertMonthly("300", d);
    }
}
