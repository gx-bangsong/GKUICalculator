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
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link UnitConversion} (linear conversions via base factors).
 */
public class UnitConversionTest {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual));
    }

    @Test
    public void sameFactorIsIdentity() {
        BigDecimal f = bd("1");
        assertDecimal("100", UnitConversion.convert(bd("100"), f, f, MC));
    }

    @Test
    public void centimetersToMeters() {
        // 100 cm (factor 0.01) -> m (factor 1) = 1 m
        assertDecimal("1", UnitConversion.convert(bd("100"), bd("0.01"), bd("1"), MC));
    }

    @Test
    public void metersToCentimeters() {
        assertDecimal("100", UnitConversion.convert(bd("1"), bd("1"), bd("0.01"), MC));
    }

    @Test
    public void kilometersToMeters() {
        assertDecimal("1000", UnitConversion.convert(bd("1"), bd("1000"), bd("1"), MC));
    }

    @Test
    public void milesToMeters() {
        assertDecimal("1609.344", UnitConversion.convert(bd("1"), bd("1609.344"), bd("1"), MC));
    }

    @Test
    public void feetToMeters() {
        assertDecimal("0.3048", UnitConversion.convert(bd("1"), bd("0.3048"), bd("1"), MC));
    }

    @Test
    public void feetRoundTrip() {
        // 5 ft -> m -> ft should be exactly 5 (0.3048 * 5 = 1.524, 1.524 / 0.3048 = 5)
        BigDecimal meters = UnitConversion.convert(bd("5"), bd("0.3048"), bd("1"), MC);
        BigDecimal feet = UnitConversion.convert(meters, bd("1"), bd("0.3048"), MC);
        assertDecimal("5", feet);
    }

    @Test
    public void kilogramsToGrams() {
        assertDecimal("1000", UnitConversion.convert(bd("1"), bd("1"), bd("0.001"), MC));
    }

    @Test
    public void poundsToKilograms() {
        assertDecimal("0.45359237",
                UnitConversion.convert(bd("1"), bd("0.45359237"), bd("1"), MC));
    }

    @Test
    public void nullInputsReturnNull() {
        assertNull(UnitConversion.convert(null, bd("1"), bd("1"), MC));
        assertNull(UnitConversion.convert(bd("1"), null, bd("1"), MC));
        assertNull(UnitConversion.convert(bd("1"), bd("1"), null, MC));
    }

    @Test
    public void zeroTargetFactorReturnsNull() {
        assertNull(UnitConversion.convert(bd("1"), bd("1"), BigDecimal.ZERO, MC));
    }

    @Test
    public void handlesLargeValues() {
        // 1 km -> mm : 1 * 1000 / 0.001 = 1,000,000
        assertDecimal("1000000", UnitConversion.convert(bd("1"), bd("1000"), bd("0.001"), MC));
    }
}
