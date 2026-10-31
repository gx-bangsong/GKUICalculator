/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import static com.android.calculator2.tools.model.DecimalExpression.Operation.ADD;
import static com.android.calculator2.tools.model.DecimalExpression.Operation.DIVIDE;
import static com.android.calculator2.tools.model.DecimalExpression.Operation.MULTIPLY;
import static com.android.calculator2.tools.model.DecimalExpression.Operation.SUBTRACT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;

public class DecimalExpressionTest {

    @Test
    public void evaluatesFourOperationsWithNormalPrecedence() {
        DecimalExpression input = new DecimalExpression("2");
        input.onOperator(ADD);
        input.onDigit(3);
        input.onOperator(MULTIPLY);
        input.onDigit(4);
        input.onOperator(SUBTRACT);
        input.onDigit(5);
        input.onOperator(DIVIDE);
        input.onDigit(2);

        assertEquals(new BigDecimal("11.5"), input.getValue());
        assertEquals("2+3×4−5÷2", input.getDisplayText());
    }

    @Test
    public void equalsCommitsAndNextDigitStartsANewExpression() {
        DecimalExpression input = new DecimalExpression("10");
        input.onOperator(DIVIDE);
        input.onDigit(4);

        assertTrue(input.onEquals());
        assertEquals("2.5", input.getDisplayText());
        input.onDigit(7);
        assertEquals("7", input.getDisplayText());
    }

    @Test
    public void allowsOneDecimalPointPerOperand() {
        DecimalExpression input = new DecimalExpression(null);
        input.onDigit(1);
        input.onDecimalPoint();
        input.onDigit(5);
        input.onDecimalPoint();
        input.onOperator(ADD);
        input.onDecimalPoint();
        input.onDigit(5);

        assertEquals("1.5+0.5", input.getDisplayText());
        assertEquals(new BigDecimal("2.0"), input.getValue());
    }

    @Test
    public void trailingOperatorKeepsTheLastUsableConversionValue() {
        DecimalExpression input = new DecimalExpression("12");
        input.onOperator(ADD);

        assertEquals("12+", input.getDisplayText());
        assertEquals(new BigDecimal("12"), input.getValue());
        assertFalse(input.onEquals());
    }

    @Test
    public void secondOperatorCorrectsTheFirstAndDeleteEditsExpression() {
        DecimalExpression input = new DecimalExpression("8");
        input.onOperator(MULTIPLY);
        input.onOperator(SUBTRACT);
        input.onDigit(3);
        assertEquals("8−3", input.getDisplayText());

        input.onDelete();
        assertEquals(new BigDecimal("8"), input.getValue());
    }

    @Test
    public void divisionByZeroIsNotAConversionValue() {
        DecimalExpression input = new DecimalExpression("1");
        input.onOperator(DIVIDE);
        input.onDigit(0);

        assertNull(input.getValue());
        assertFalse(input.onEquals());
    }

    @Test
    public void supportsNegativeInitialValuesAndFormattedCarryValues() {
        DecimalExpression negative = new DecimalExpression("-12.50");
        assertEquals("-12.5", negative.getDisplayText());
        assertEquals("-12.5", negative.getValueText());

        DecimalExpression grouped = new DecimalExpression("1,234.00");
        assertEquals("1234", grouped.getDisplayText());
    }
}
