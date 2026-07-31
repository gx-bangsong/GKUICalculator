/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Pure linear unit conversion, independent of Android.
 * <p>
 * Each unit has a {@code factor} = how many base units it equals (e.g. for length with base m:
 * km = 1000, cm = 0.01, ft = 0.3048). Converting {@code value} from a source unit to a target
 * unit is then {@code value * fromFactor / toFactor}. Arithmetic uses {@link BigDecimal} so the
 * engine's arbitrary-precision guarantees are respected in spirit; this never touches
 * {@code Evaluator}/{@code CalculatorExpr}/{@code BoundedRational}.
 */
public final class UnitConversion {

    private UnitConversion() {
    }

    /**
     * @return the converted value, or {@code null} if the inputs are missing or the target
     *         factor is zero (division by zero).
     */
    public static BigDecimal convert(BigDecimal value, BigDecimal fromFactor,
            BigDecimal toFactor, MathContext mc) {
        if (value == null || fromFactor == null || toFactor == null) {
            return null;
        }
        if (toFactor.signum() == 0) {
            return null;
        }
        return value.multiply(fromFactor, mc).divide(toFactor, mc);
    }
}
