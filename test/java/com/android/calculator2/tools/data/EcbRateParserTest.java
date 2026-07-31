/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link EcbRateParser}. Uses the JDK DOM parser so it runs on the JVM.
 */
public class EcbRateParserTest {

    private static final String SAMPLE_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<gesmes:Envelope xmlns:gesmes=\"http://www.gesmes.org/xml/2002-08-01\">\n"
            + "  <gesmes:subject>Reference rates</gesmes:subject>\n"
            + "  <Cube>\n"
            + "    <Cube time=\"2024-01-15\">\n"
            + "      <Cube currency=\"USD\" rate=\"1.0823\"/>\n"
            + "      <Cube currency=\"JPY\" rate=\"157.45\"/>\n"
            + "      <Cube currency=\"GBP\" rate=\"0.8612\"/>\n"
            + "      <Cube currency=\"CNY\" rate=\"7.8523\"/>\n"
            + "    </Cube>\n"
            + "  </Cube>\n"
            + "</gesmes:Envelope>\n";

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static void assertRate(Map<String, BigDecimal> rates, String code, String expected) {
        BigDecimal actual = rates.get(code);
        assertNotNull("Missing rate for " + code, actual);
        assertEquals(0, bd(expected).compareTo(actual));
    }

    @Test
    public void parsesReferenceDateAndRates() throws Exception {
        EcbResult result = EcbRateParser.parse(SAMPLE_XML);
        assertEquals("2024-01-15", result.getPublishDate());
        Map<String, BigDecimal> rates = result.getRates();
        assertRate(rates, "USD", "1.0823");
        assertRate(rates, "JPY", "157.45");
        assertRate(rates, "GBP", "0.8612");
        assertRate(rates, "CNY", "7.8523");
    }

    @Test
    public void baseEurIsAlwaysOne() throws Exception {
        EcbResult result = EcbRateParser.parse(SAMPLE_XML);
        assertEquals(0, BigDecimal.ONE.compareTo(result.getRates().get("EUR")));
    }

    @Test
    public void ignoresNonRateCubes() throws Exception {
        EcbResult result = EcbRateParser.parse(SAMPLE_XML);
        // Only the 4 quote currencies + EUR should be present.
        assertEquals(5, result.getRates().size());
    }

    @Test
    public void publishDateIsNullWhenAbsent() throws Exception {
        String xml = "<Cube><Cube currency=\"USD\" rate=\"1.0\"/></Cube>";
        EcbResult result = EcbRateParser.parse(xml);
        assertNull(result.getPublishDate());
        assertEquals(0, bd("1.0").compareTo(result.getRates().get("USD")));
    }

    @Test
    public void malformedXmlThrows() {
        try {
            EcbRateParser.parse("<<not xml>>");
            fail("Expected parsing to fail");
        } catch (Exception expected) {
            // SAXException or IOException.
        }
    }
}
