/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link DateDelta} (pure {@code java.time}).
 */
public class DateDeltaTest {

    @Test
    public void daysWithinSameMonth() {
        DateDelta.Result r = DateDelta.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 11));
        assertEquals(10L, r.days);
        assertEquals(0, r.period.getYears());
        assertEquals(0, r.period.getMonths());
        assertEquals(10, r.period.getDays());
        assertFalse(r.swapped);
    }

    @Test
    public void fullYearIs365Days() {
        // 2024 is a leap year, but Jan 1 -> Dec 31 is 365 elapsed days.
        DateDelta.Result r = DateDelta.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertEquals(365L, r.days);
        assertEquals(11, r.period.getMonths());
        assertEquals(30, r.period.getDays());
    }

    @Test
    public void swappedWhenEndBeforeStart() {
        DateDelta.Result r = DateDelta.between(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1));
        assertTrue(r.swapped);
        assertEquals(31L, r.days); // Jan 1 -> Feb 1
        assertEquals(1, r.period.getMonths());
    }

    @Test
    public void sameDayIsZero() {
        DateDelta.Result r = DateDelta.between(LocalDate.of(2024, 6, 15), LocalDate.of(2024, 6, 15));
        assertEquals(0L, r.days);
        assertFalse(r.swapped);
    }
}
