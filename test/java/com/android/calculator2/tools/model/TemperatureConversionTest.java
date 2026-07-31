/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import com.android.calculator2.tools.model.TemperatureConversion.Scale;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for {@link TemperatureConversion} (affine C / F / K conversions).
 */
public class TemperatureConversionTest {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static BigDecimal convert(String value, Scale from, Scale to) {
        return TemperatureConversion.convert(bd(value), from, to, MC);
    }

    private static void assertScale(String expected, String value, Scale from, Scale to) {
        assertEquals(0, bd(expected).compareTo(convert(value, from, to)));
    }

    @Test
    public void scaleFromUnitId() {
        assertEquals(Scale.CELSIUS, TemperatureConversion.scaleFromUnitId("C"));
        assertEquals(Scale.FAHRENHEIT, TemperatureConversion.scaleFromUnitId("F"));
        assertEquals(Scale.KELVIN, TemperatureConversion.scaleFromUnitId("K"));
        assertNull(TemperatureConversion.scaleFromUnitId("X"));
        assertNull(TemperatureConversion.scaleFromUnitId(null));
    }

    @Test
    public void celsiusToFahrenheit() {
        assertScale("32", "0", Scale.CELSIUS, Scale.FAHRENHEIT);
        assertScale("212", "100", Scale.CELSIUS, Scale.FAHRENHEIT);
        assertScale("-40", "-40", Scale.CELSIUS, Scale.FAHRENHEIT); // the famous equality point
        assertScale("98.6", "37", Scale.CELSIUS, Scale.FAHRENHEIT); // body temperature
    }

    @Test
    public void fahrenheitToCelsius() {
        assertScale("0", "32", Scale.FAHRENHEIT, Scale.CELSIUS);
        assertScale("100", "212", Scale.FAHRENHEIT, Scale.CELSIUS);
        assertScale("-40", "-40", Scale.FAHRENHEIT, Scale.CELSIUS);
    }

    @Test
    public void celsiusToKelvin() {
        assertScale("273.15", "0", Scale.CELSIUS, Scale.KELVIN);
        assertScale("373.15", "100", Scale.CELSIUS, Scale.KELVIN);
    }

    @Test
    public void kelvinToCelsius() {
        assertScale("0", "273.15", Scale.KELVIN, Scale.CELSIUS);
        assertScale("26.85", "300", Scale.KELVIN, Scale.CELSIUS);
    }

    @Test
    public void kelvinToFahrenheit() {
        // 273.15 K = 0 C = 32 F
        assertScale("32", "273.15", Scale.KELVIN, Scale.FAHRENHEIT);
    }

    @Test
    public void celsiusRoundTripThroughFahrenheit() {
        BigDecimal f = convert("23", Scale.CELSIUS, Scale.FAHRENHEIT);
        BigDecimal c = TemperatureConversion.convert(f, Scale.FAHRENHEIT, Scale.CELSIUS, MC);
        assertEquals(0, bd("23").compareTo(c));
    }

    @Test
    public void fahrenheitRoundTripThroughKelvin() {
        BigDecimal k = convert("98.6", Scale.FAHRENHEIT, Scale.KELVIN);
        BigDecimal f = TemperatureConversion.convert(k, Scale.KELVIN, Scale.FAHRENHEIT, MC);
        assertEquals(0, bd("98.6").compareTo(f));
    }

    @Test
    public void nullInputsReturnNull() {
        assertNull(TemperatureConversion.convert(null, Scale.CELSIUS, Scale.FAHRENHEIT, MC));
        assertNull(TemperatureConversion.convert(bd("1"), null, Scale.KELVIN, MC));
        assertNull(TemperatureConversion.convert(bd("1"), Scale.KELVIN, null, MC));
    }
}
