/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;

import java.math.BigDecimal;

/**
 * One bracket of the Chinese individual-income-tax cumulative-withholding table (annual,
 * cumulative thresholds). {@code upTo} is the upper bound of cumulative taxable income for this
 * bracket (the last bracket uses a very large value); {@code rate} and {@code quickDeduction} are
 * the marginal rate and速算扣除数.
 */
public final class TaxBracket {

    @NonNull
    private final BigDecimal mUpTo;
    @NonNull
    private final BigDecimal mRate;
    @NonNull
    private final BigDecimal mQuickDeduction;

    public TaxBracket(@NonNull BigDecimal upTo, @NonNull BigDecimal rate,
            @NonNull BigDecimal quickDeduction) {
        mUpTo = upTo;
        mRate = rate;
        mQuickDeduction = quickDeduction;
    }

    @NonNull
    public BigDecimal getUpTo() {
        return mUpTo;
    }

    @NonNull
    public BigDecimal getRate() {
        return mRate;
    }

    @NonNull
    public BigDecimal getQuickDeduction() {
        return mQuickDeduction;
    }
}
