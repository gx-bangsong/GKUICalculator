/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import androidx.annotation.NonNull;

import com.android.calculator2.tools.data.TaxBracket;
import com.android.calculator2.tools.data.TaxTable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Chinese individual income tax under the cumulative withholding method (累计预扣预缴), independent
 * of Android.
 * <p>
 * For a constant monthly salary, the cumulative taxable income grows linearly across the year and
 * may cross brackets. For each month m (1..12): cumulative taxable income = max(0, monthlyNet * m),
 * where monthlyNet = salary − social insurance − special deductions − threshold; the cumulative tax
 * is {@code cumTaxable * rate − quickDeduction} for the matching annual bracket; the month's
 * withholding is the difference from the previous month's cumulative tax.
 */
public final class IncomeTaxCalculator {

    public static final class Result {
        /** Cumulative annual taxable income (after deductions). */
        public final BigDecimal annualTaxableIncome;
        /** Total tax withheld over the year. */
        public final BigDecimal annualTax;
        /** Tax withheld in the 12th month. */
        public final BigDecimal lastMonthWithholding;
        /** Annual take-home pay = 12 * (salary − insurance) − annualTax. */
        public final BigDecimal annualAfterTax;
        /** Marginal rate applicable at year end (the bracket of month 12). */
        public final BigDecimal applicableRate;

        public Result(BigDecimal annualTaxableIncome, BigDecimal annualTax,
                BigDecimal lastMonthWithholding, BigDecimal annualAfterTax,
                BigDecimal applicableRate) {
            this.annualTaxableIncome = annualTaxableIncome;
            this.annualTax = annualTax;
            this.lastMonthWithholding = lastMonthWithholding;
            this.annualAfterTax = annualAfterTax;
            this.applicableRate = applicableRate;
        }
    }

    private IncomeTaxCalculator() {
    }

    /**
     * @param table threshold + annual cumulative brackets
     */
    public static Result computeAnnual(@NonNull BigDecimal monthlySalary,
            @NonNull BigDecimal monthlyInsurance, @NonNull BigDecimal monthlySpecialDeduction,
            @NonNull TaxTable table, MathContext mc) {
        final List<TaxBracket> brackets = table.getBrackets();
        final BigDecimal threshold = table.getMonthlyThreshold();
        final BigDecimal monthlyNet = monthlySalary
                .subtract(monthlyInsurance, mc)
                .subtract(monthlySpecialDeduction, mc)
                .subtract(threshold, mc);

        if (monthlyNet.signum() <= 0 || brackets.isEmpty()) {
            BigDecimal takeHome = monthlySalary.subtract(monthlyInsurance, mc)
                    .multiply(new BigDecimal(12), mc);
            return new Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    takeHome, BigDecimal.ZERO);
        }

        BigDecimal prevCumTax = BigDecimal.ZERO;
        BigDecimal cumTax12 = BigDecimal.ZERO;
        TaxBracket lastBracket = brackets.get(brackets.size() - 1);
        for (int m = 1; m <= 12; m++) {
            BigDecimal cumTaxable = monthlyNet.multiply(new BigDecimal(m), mc);
            TaxBracket bracket = bracketFor(brackets, cumTaxable);
            BigDecimal cumTax = cumTaxable.multiply(bracket.getRate(), mc)
                    .subtract(bracket.getQuickDeduction(), mc);
            if (cumTax.signum() < 0) {
                cumTax = BigDecimal.ZERO;
            }
            if (m == 12) {
                cumTax12 = cumTax;
                lastBracket = bracket;
            }
            prevCumTax = cumTax;
        }
        // prevCumTax is now cumTax(12); recompute cumTax(11) for the 12th-month withholding.
        BigDecimal cumTax11 = BigDecimal.ZERO;
        BigDecimal cumTaxable11 = monthlyNet.multiply(new BigDecimal(11), mc);
        if (cumTaxable11.signum() > 0) {
            TaxBracket b11 = bracketFor(brackets, cumTaxable11);
            cumTax11 = cumTaxable11.multiply(b11.getRate(), mc)
                    .subtract(b11.getQuickDeduction(), mc);
            if (cumTax11.signum() < 0) {
                cumTax11 = BigDecimal.ZERO;
            }
        }
        BigDecimal lastMonth = cumTax12.subtract(cumTax11, mc);
        if (lastMonth.signum() < 0) {
            lastMonth = BigDecimal.ZERO;
        }

        BigDecimal annualAfterTax = monthlySalary.subtract(monthlyInsurance, mc)
                .multiply(new BigDecimal(12), mc)
                .subtract(cumTax12, mc);
        BigDecimal annualTaxableIncome = monthlyNet.multiply(new BigDecimal(12), mc);
        return new Result(annualTaxableIncome, cumTax12, lastMonth, annualAfterTax,
                lastBracket.getRate());
    }

    private static TaxBracket bracketFor(@NonNull List<TaxBracket> brackets,
            @NonNull BigDecimal cumulativeTaxable) {
        for (TaxBracket bracket : brackets) {
            if (cumulativeTaxable.compareTo(bracket.getUpTo()) <= 0) {
                return bracket;
            }
        }
        return brackets.get(brackets.size() - 1);
    }
}
