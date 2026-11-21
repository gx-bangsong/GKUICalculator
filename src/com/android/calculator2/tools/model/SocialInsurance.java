/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The employee side of the "five insurances and one fund" (五险一金), entered proportionally as
 * six separate categories: pension, medical, unemployment, work injury, maternity and the
 * housing fund. Pure data; independent of Android.
 */
public final class SocialInsurance {

    public static final int CATEGORIES = 6;

    public static final int PENSION = 0;
    public static final int MEDICAL = 1;
    public static final int UNEMPLOYMENT = 2;
    public static final int INJURY = 3;
    public static final int MATERNITY = 4;
    public static final int HOUSING_FUND = 5;

    /** Typical employee-side rates in percent: 8 / 2 / 0.5 / 0 / 0 / 12. */
    public static final String[] DEFAULT_RATES_PERCENT = {"8", "2", "0.5", "0", "0", "12"};

    private SocialInsurance() {
    }

    /**
     * Monthly personal contribution = base × Σ(rates) / 100, rounded to 2 decimals.
     *
     * @param base       the contribution base (缴费基数), usually last year's monthly average pay
     * @param ratesPct   six personal rates in percent; null entries count as zero
     */
    public static BigDecimal monthly(BigDecimal base, BigDecimal[] ratesPct, MathContext mc) {
        if (base == null || base.signum() <= 0 || ratesPct == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < CATEGORIES; i++) {
            BigDecimal rate = i < ratesPct.length ? ratesPct[i] : null;
            if (rate != null && rate.signum() > 0) {
                sum = sum.add(rate, mc);
            }
        }
        return base.multiply(sum, mc)
                .divide(new BigDecimal(100), mc)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
