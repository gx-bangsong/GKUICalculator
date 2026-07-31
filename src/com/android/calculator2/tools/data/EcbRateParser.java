/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Pure parser for the European Central Bank daily reference-rates XML, independent of Android.
 * <p>
 * Uses the JDK/Android DOM parser ({@code javax.xml.parsers}) instead of {@code android.util.Xml}
 * specifically so it is unit-testable on the JVM. Matches the data source of
 * {@code com.yangdai.calc}: it reads {@code <Cube currency="..." rate="..."/>} entries (relative to
 * a EUR base) and adds EUR = 1. The reference date is taken from the wrapping
 * {@code <Cube time="yyyy-MM-dd"/>} element.
 */
public final class EcbRateParser {

    private static final String TAG_CUBE = "Cube";
    private static final String ATTR_CURRENCY = "currency";
    private static final String ATTR_RATE = "rate";
    private static final String ATTR_TIME = "time";

    private EcbRateParser() {
    }

    /**
     * @param xml the raw ECB XML document
     * @return parsed rates + reference date
     * @throws org.xml.sax.SAXException if the document is malformed
     * @throws IOException              on I/O error reading the source
     */
    @NonNull
    public static EcbResult parse(@NonNull String xml)
            throws org.xml.sax.SAXException, IOException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // Harden against XXE (ECB documents carry no DOCTYPE, but be safe).
        disableDocType(factory);

        final DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IOException("XML parser unavailable", e);
        }
        final Document doc = builder.parse(new InputSource(new StringReader(xml)));

        final Map<String, BigDecimal> rates = new LinkedHashMap<>();
        String publishDate = null;
        final NodeList cubes = doc.getElementsByTagName(TAG_CUBE);
        for (int i = 0; i < cubes.getLength(); i++) {
            final Element element = (Element) cubes.item(i);
            final String currency = element.getAttribute(ATTR_CURRENCY);
            final String rate = element.getAttribute(ATTR_RATE);
            if (!currency.isEmpty() && !rate.isEmpty()) {
                rates.put(currency, new BigDecimal(rate));
            } else {
                final String time = element.getAttribute(ATTR_TIME);
                if (!time.isEmpty() && publishDate == null) {
                    publishDate = time;
                }
            }
        }
        rates.put("EUR", BigDecimal.ONE);
        return new EcbResult(publishDate, rates);
    }

    private static void disableDocType(@NonNull DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (ParserConfigurationException ignored) {
        }
        try {
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException ignored) {
        }
        factory.setExpandEntityReferences(false);
    }
}
