/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link TaxTable#parse} (pure JSON parsing).
 */
public class TaxTableTest {

    private static final String SAMPLE_JSON =
            "{\n"
            + "  \"id\": \"cn_iit_cumulative\",\n"
            + "  \"monthly_threshold\": \"5000\",\n"
            + "  \"brackets\": [\n"
            + "    { \"up_to\": \"36000\", \"rate\": \"0.03\", \"quick_deduction\": \"0\" },\n"
            + "    { \"up_to\": \"144000\", \"rate\": \"0.10\", \"quick_deduction\": \"2520\" },\n"
            + "    { \"up_to\": \"999999999\", \"rate\": \"0.45\", \"quick_deduction\": \"181920\" }\n"
            + "  ]\n"
            + "}";

    @Test
    public void parsesThresholdAndBrackets() throws Exception {
        TaxTable table = TaxTable.parse(SAMPLE_JSON);
        assertEquals(0, new BigDecimal("5000").compareTo(table.getMonthlyThreshold()));
        List<TaxBracket> brackets = table.getBrackets();
        assertEquals(3, brackets.size());
        assertEquals(0, new BigDecimal("36000").compareTo(brackets.get(0).getUpTo()));
        assertEquals(0, new BigDecimal("0.03").compareTo(brackets.get(0).getRate()));
        assertEquals(0, new BigDecimal("0.10").compareTo(brackets.get(1).getRate()));
        assertEquals(0, new BigDecimal("2520").compareTo(brackets.get(1).getQuickDeduction()));
    }

    @Test
    public void defaultsHasSevenBrackets() {
        TaxTable table = TaxTable.defaults();
        assertEquals(7, table.getBrackets().size());
        assertEquals(0, new BigDecimal("5000").compareTo(table.getMonthlyThreshold()));
    }
}
