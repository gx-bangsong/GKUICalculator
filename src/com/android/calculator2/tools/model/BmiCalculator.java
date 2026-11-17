/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Body Mass Index, independent of Android. Uses the Chinese adult classification.
 * <p>
 * bmi = weight(kg) / (height(m))^2. Categories: &lt;18.5 underweight, 18.5–23.9 normal,
 * 24–27.9 overweight, &gt;=28 obese.
 */
public final class BmiCalculator {

    public enum Category { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }

    /** Selected gender; drives the standard-weight reference shown with the BMI. */
    public enum Gender { MALE, FEMALE }

    public static final class Result {
        /** BMI rounded to one decimal. */
        public final BigDecimal bmi;
        public final Category category;

        public Result(BigDecimal bmi, Category category) {
            this.bmi = bmi;
            this.category = category;
        }
    }

    private BmiCalculator() {
    }

    /**
     * @param heightCm height in centimeters (e.g. 175)
     * @param weightKg weight in kilograms (e.g. 70)
     */
    public static Result compute(BigDecimal heightCm, BigDecimal weightKg, MathContext mc) {
        if (heightCm == null || weightKg == null
                || heightCm.signum() <= 0 || weightKg.signum() <= 0) {
            return null;
        }
        // height in meters
        BigDecimal heightM = heightCm.divide(new BigDecimal("100"), mc);
        BigDecimal squared = heightM.multiply(heightM, mc);
        if (squared.signum() == 0) {
            return null;
        }
        BigDecimal bmi = weightKg.divide(squared, mc).setScale(1, RoundingMode.HALF_UP);
        return new Result(bmi, categoryOf(bmi));
    }

    public static Category categoryOf(BigDecimal bmi) {
        double v = bmi.doubleValue();
        if (v < 18.5) {
            return Category.UNDERWEIGHT;
        } else if (v < 24) {
            return Category.NORMAL;
        } else if (v < 28) {
            return Category.OVERWEIGHT;
        } else {
            return Category.OBESE;
        }
    }

    /**
     * Standard weight in kilograms under the customary Chinese formulae:
     * male (height − 80) × 0.7, female (height − 70) × 0.6, rounded to 0.1 kg.
     */
    public static BigDecimal standardWeight(BigDecimal heightCm, Gender gender, MathContext mc) {
        if (heightCm == null || gender == null || heightCm.signum() <= 0) {
            return null;
        }
        final BigDecimal base = gender == Gender.MALE
                ? heightCm.subtract(new BigDecimal("80"), mc)
                : heightCm.subtract(new BigDecimal("70"), mc);
        final BigDecimal factor = gender == Gender.MALE
                ? new BigDecimal("0.7")
                : new BigDecimal("0.6");
        return base.multiply(factor, mc).setScale(1, RoundingMode.HALF_UP);
    }
}
