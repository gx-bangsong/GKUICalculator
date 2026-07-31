/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Mortgage calculations, independent of Android.
 * <p>
 * Supports the two common Chinese repayment methods:
 * <ul>
 *   <li><b>Equal payment (等额本息)</b>: constant monthly payment
 *       {@code P * r * (1+r)^n / ((1+r)^n - 1)}, where r is the monthly rate and n the term in
 *       months. {@code (1+r)^n} is computed iteratively under a fixed {@link MathContext} to keep
 *       precision bounded.</li>
 *   <li><b>Equal principal (等额本金)</b>: principal portion is constant; interest decreases each
 *       month. Total interest {@code = P * r * (n+1) / 2}.</li>
 * </ul>
 * A zero rate is handled explicitly (no division by zero).
 */
public final class MortgageCalculator {

    /** Result of a mortgage computation. */
    public static final class Result {
        /** Number of months. */
        public final int months;
        /** Representative monthly payment: the constant payment for equal-payment, the FIRST month
         * payment for equal-principal. */
        public final BigDecimal monthlyPayment;
        /** Amount the monthly payment decreases each month (equal-principal); 0 for equal-payment. */
        public final BigDecimal monthlyDecrease;
        public final BigDecimal totalInterest;
        public final BigDecimal totalPayment;
        public final boolean equalPayment;

        public Result(int months, BigDecimal monthlyPayment, BigDecimal monthlyDecrease,
                BigDecimal totalInterest, BigDecimal totalPayment, boolean equalPayment) {
            this.months = months;
            this.monthlyPayment = monthlyPayment;
            this.monthlyDecrease = monthlyDecrease;
            this.totalInterest = totalInterest;
            this.totalPayment = totalPayment;
            this.equalPayment = equalPayment;
        }
    }

    private MortgageCalculator() {
    }

    /** Equal-payment (等额本息). annualRatePct is the nominal annual rate in percent (e.g. 4.9). */
    public static Result equalPayment(BigDecimal principal, BigDecimal annualRatePct, int years,
            MathContext mc) {
        final int n = Math.max(0, years) * 12;
        if (n == 0 || principal == null || principal.signum() <= 0) {
            return new Result(n, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    principal == null ? BigDecimal.ZERO : principal, true);
        }
        final BigDecimal r = monthlyRate(annualRatePct, mc);
        if (r.signum() == 0) {
            BigDecimal monthly = principal.divide(new BigDecimal(n), mc);
            return new Result(n, monthly, BigDecimal.ZERO, BigDecimal.ZERO, principal, true);
        }
        final BigDecimal factor = powIterated(BigDecimal.ONE.add(r, mc), n, mc);
        final BigDecimal denominator = factor.subtract(BigDecimal.ONE, mc);
        if (denominator.signum() == 0) {
            BigDecimal monthly = principal.divide(new BigDecimal(n), mc);
            return new Result(n, monthly, BigDecimal.ZERO, BigDecimal.ZERO, principal, true);
        }
        final BigDecimal monthly = principal.multiply(r, mc).multiply(factor, mc)
                .divide(denominator, mc);
        final BigDecimal totalPayment = monthly.multiply(new BigDecimal(n), mc);
        final BigDecimal totalInterest = totalPayment.subtract(principal, mc);
        return new Result(n, monthly, BigDecimal.ZERO, totalInterest, totalPayment, true);
    }

    /** Equal-principal (等额本金). */
    public static Result equalPrincipal(BigDecimal principal, BigDecimal annualRatePct, int years,
            MathContext mc) {
        final int n = Math.max(0, years) * 12;
        if (n == 0 || principal == null || principal.signum() <= 0) {
            return new Result(n, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    principal == null ? BigDecimal.ZERO : principal, false);
        }
        final BigDecimal r = monthlyRate(annualRatePct, mc);
        final BigDecimal nBd = new BigDecimal(n);
        final BigDecimal monthlyPrincipal = principal.divide(nBd, mc);
        final BigDecimal firstInterest = principal.multiply(r, mc);
        final BigDecimal firstPayment = monthlyPrincipal.add(firstInterest, mc);
        final BigDecimal decrease = monthlyPrincipal.multiply(r, mc);
        // total interest = P * r * (n + 1) / 2
        final BigDecimal totalInterest = principal.multiply(r, mc)
                .multiply(new BigDecimal(n + 1), mc)
                .divide(new BigDecimal(2), mc);
        final BigDecimal totalPayment = principal.add(totalInterest, mc);
        return new Result(n, firstPayment, decrease, totalInterest, totalPayment, false);
    }

    private static BigDecimal monthlyRate(BigDecimal annualRatePct, MathContext mc) {
        if (annualRatePct == null || annualRatePct.signum() == 0) {
            return BigDecimal.ZERO;
        }
        // r = annualRatePct / 100 / 12
        return annualRatePct.divide(new BigDecimal("100"), mc)
                .divide(new BigDecimal("12"), mc);
    }

    /** Compute base^n under a fixed MathContext (bounds scale growth vs BigDecimal.pow). */
    private static BigDecimal powIterated(BigDecimal base, int n, MathContext mc) {
        BigDecimal result = BigDecimal.ONE;
        for (int i = 0; i < n; i++) {
            result = result.multiply(base, mc);
        }
        return result;
    }
}
