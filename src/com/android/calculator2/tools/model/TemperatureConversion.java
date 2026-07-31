/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Non-linear (affine) temperature conversion between Celsius, Fahrenheit and Kelvin, independent
 * of Android. Used for the temperature category; linear categories use {@link UnitConversion}.
 * <p>
 * Conversions route through Celsius as the base scale:
 * <ul>
 *   <li>F -&gt; C: (v - 32) * 5 / 9</li>
 *   <li>K -&gt; C: v - 273.15</li>
 *   <li>C -&gt; F: v * 9 / 5 + 32</li>
 *   <li>C -&gt; K: v + 273.15</li>
 * </ul>
 */
public final class TemperatureConversion {

    public enum Scale { CELSIUS, FAHRENHEIT, KELVIN }

    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal NINE = new BigDecimal("9");
    private static final BigDecimal THIRTY_TWO = new BigDecimal("32");
    private static final BigDecimal KELVIN_OFFSET = new BigDecimal("273.15");

    private TemperatureConversion() {
    }

    /** Map a unit id ("C"/"F"/"K") to a {@link Scale}, or {@code null} if unknown. */
    public static Scale scaleFromUnitId(String id) {
        if (id == null) {
            return null;
        }
        switch (id) {
            case "C":
                return Scale.CELSIUS;
            case "F":
                return Scale.FAHRENHEIT;
            case "K":
                return Scale.KELVIN;
            default:
                return null;
        }
    }

    /**
     * @return the converted value, or {@code null} if any input is missing.
     */
    public static BigDecimal convert(BigDecimal value, Scale from, Scale to, MathContext mc) {
        if (value == null || from == null || to == null) {
            return null;
        }
        BigDecimal celsius = toCelsius(value, from, mc);
        return fromCelsius(celsius, to, mc);
    }

    private static BigDecimal toCelsius(BigDecimal v, Scale from, MathContext mc) {
        switch (from) {
            case CELSIUS:
                return v;
            case FAHRENHEIT:
                return v.subtract(THIRTY_TWO, mc).multiply(FIVE, mc).divide(NINE, mc);
            case KELVIN:
                return v.subtract(KELVIN_OFFSET, mc);
            default:
                return v;
        }
    }

    private static BigDecimal fromCelsius(BigDecimal c, Scale to, MathContext mc) {
        switch (to) {
            case CELSIUS:
                return c;
            case FAHRENHEIT:
                return c.multiply(NINE, mc).divide(FIVE, mc).add(THIRTY_TWO, mc);
            case KELVIN:
                return c.add(KELVIN_OFFSET, mc);
            default:
                return c;
        }
    }
}
