/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProgrammerCalculatorTest {

    @Test
    public void formatsAcrossSupportedBases() {
        assertEquals("255", ProgrammerCalculator.format(255, 10));
        assertEquals("FF", ProgrammerCalculator.format(255, 16));
        assertEquals("377", ProgrammerCalculator.format(255, 8));
        assertEquals("11111111", ProgrammerCalculator.format(255, 2));
    }

    @Test
    public void negativeNonDecimalUsesTwosComplement() {
        assertEquals("FFFFFFFFFFFFFFFF", ProgrammerCalculator.format(-1, 16));
        assertEquals(-1, ProgrammerCalculator.parse("FFFFFFFFFFFFFFFF", 16));
    }

    @Test
    public void arithmeticUsesSigned64BitBehavior() {
        assertEquals(8, ProgrammerCalculator.apply(5, 3,
                ProgrammerCalculator.Operation.ADD));
        assertEquals(-2, ProgrammerCalculator.apply(5, 7,
                ProgrammerCalculator.Operation.SUBTRACT));
        assertEquals(Long.MIN_VALUE, ProgrammerCalculator.apply(Long.MAX_VALUE, 1,
                ProgrammerCalculator.Operation.ADD));
    }

    @Test
    public void bitwiseOperationsAndShifts() {
        assertEquals(0x20, ProgrammerCalculator.apply(0x30, 0x2A,
                ProgrammerCalculator.Operation.AND));
        assertEquals(0x3A, ProgrammerCalculator.apply(0x30, 0x2A,
                ProgrammerCalculator.Operation.OR));
        assertEquals(0x1A, ProgrammerCalculator.apply(0x30, 0x2A,
                ProgrammerCalculator.Operation.XOR));
        assertEquals(16, ProgrammerCalculator.apply(1, 4,
                ProgrammerCalculator.Operation.SHIFT_LEFT));
        assertEquals(-2, ProgrammerCalculator.apply(-4, 1,
                ProgrammerCalculator.Operation.SHIFT_RIGHT));
        assertEquals(-1, ProgrammerCalculator.not(0));
    }

    @Test(expected = ArithmeticException.class)
    public void divisionByZeroThrows() {
        ProgrammerCalculator.apply(1, 0, ProgrammerCalculator.Operation.DIVIDE);
    }

    @Test
    public void validatesDigitsForCurrentBase() {
        assertTrue(ProgrammerCalculator.isDigitValid(1, 2));
        assertFalse(ProgrammerCalculator.isDigitValid(2, 2));
        assertTrue(ProgrammerCalculator.isDigitValid(15, 16));
        assertFalse(ProgrammerCalculator.isDigitValid(16, 16));
    }
}
