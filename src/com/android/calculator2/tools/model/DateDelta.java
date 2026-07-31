/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * Date-interval helpers, independent of Android (pure {@code java.time}).
 */
public final class DateDelta {

    public static final class Result {
        /** Absolute number of days between the two dates (end - start). */
        public final long days;
        /** Calendar-based difference (years, months, days). */
        public final Period period;
        /** Whether the end date is before the start date (dates were swapped for display). */
        public final boolean swapped;

        public Result(long days, Period period, boolean swapped) {
            this.days = days;
            this.period = period;
            this.swapped = swapped;
        }
    }

    private DateDelta() {
    }

    /**
     * @return the interval between the two dates; days and period are always non-negative, with
     *         {@code swapped} indicating whether the inputs were reordered.
     */
    public static Result between(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        final LocalDate from;
        final LocalDate to;
        final boolean swapped;
        if (end.isBefore(start)) {
            from = end;
            to = start;
            swapped = true;
        } else {
            from = start;
            to = end;
            swapped = false;
        }
        long days = ChronoUnit.DAYS.between(from, to);
        Period period = Period.between(from, to);
        return new Result(days, period, swapped);
    }
}
