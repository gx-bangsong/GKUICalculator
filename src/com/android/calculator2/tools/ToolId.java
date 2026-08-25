/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

/**
 * Stable identifiers for built-in tool modes.
 * <p>
 * These ids are persisted (frequency ordering, last-active tool) and referenced from
 * configuration, so they must never change once shipped.
 */
public final class ToolId {

    /** The default calculator mode. Transparently delegates to the existing engine/UI. */
    public static final String CALCULATOR = "calculator";

    /** Offline unit conversion (length, weight, temperature, ...). */
    public static final String UNIT = "unit";

    /** Online currency conversion (ECB rates + offline fallback). */
    public static final String CURRENCY = "currency";

    /** Mortgage (equal-payment / equal-principal). */
    public static final String MORTGAGE = "mortgage";

    /** Individual income tax (cumulative withholding). */
    public static final String TAX = "tax";

    /** Body mass index. */
    public static final String BMI = "bmi";

    /** Date interval. */
    public static final String DATE = "date";

    /** Signed 64-bit programmer calculator. */
    public static final String PROGRAMMER = "programmer";

    private ToolId() {
    }
}
