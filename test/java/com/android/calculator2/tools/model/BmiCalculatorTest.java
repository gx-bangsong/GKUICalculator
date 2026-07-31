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
 * Unit tests for {@link BmiCalculator} (Chinese adult classification).
 */
public class BmiCalculatorTest {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    public void normalRange() {
        // 70 / (1.75^2) = 22.857 -> 22.9, normal
        BmiCalculator.Result r = BmiCalculator.compute(bd("175"), bd("70"), MC);
        assertEquals(0, bd("22.9").compareTo(r.bmi));
        assertEquals(BmiCalculator.Category.NORMAL, r.category);
    }

    @Test
    public void obese() {
        // 85 / (1.70^2) = 29.41 -> 29.4, obese (>=28)
        BmiCalculator.Result r = BmiCalculator.compute(bd("170"), bd("85"), MC);
        assertEquals(0, bd("29.4").compareTo(r.bmi));
        assertEquals(BmiCalculator.Category.OBESE, r.category);
    }

    @Test
    public void overweight() {
        // 85 / (1.75^2) = 27.76 -> 27.8, overweight (24..27.9)
        BmiCalculator.Result r = BmiCalculator.compute(bd("175"), bd("85"), MC);
        assertEquals(0, bd("27.8").compareTo(r.bmi));
        assertEquals(BmiCalculator.Category.OVERWEIGHT, r.category);
    }

    @Test
    public void underweight() {
        // 55 / (1.80^2) = 16.975 -> 17.0, underweight (<18.5)
        BmiCalculator.Result r = BmiCalculator.compute(bd("180"), bd("55"), MC);
        assertEquals(0, bd("17.0").compareTo(r.bmi));
        assertEquals(BmiCalculator.Category.UNDERWEIGHT, r.category);
    }

    @Test
    public void invalidInputsReturnNull() {
        assertNull(BmiCalculator.compute(null, bd("70"), MC));
        assertNull(BmiCalculator.compute(bd("175"), BigDecimal.ZERO, MC));
        assertNull(BmiCalculator.compute(BigDecimal.ZERO, bd("70"), MC));
    }

    @Test
    public void categoryBoundaries() {
        assertEquals(BmiCalculator.Category.UNDERWEIGHT, BmiCalculator.categoryOf(bd("18.4")));
        assertEquals(BmiCalculator.Category.NORMAL, BmiCalculator.categoryOf(bd("18.5")));
        assertEquals(BmiCalculator.Category.NORMAL, BmiCalculator.categoryOf(bd("23.9")));
        assertEquals(BmiCalculator.Category.OVERWEIGHT, BmiCalculator.categoryOf(bd("24")));
        assertEquals(BmiCalculator.Category.OVERWEIGHT, BmiCalculator.categoryOf(bd("27.9")));
        assertEquals(BmiCalculator.Category.OBESE, BmiCalculator.categoryOf(bd("28")));
    }
}
