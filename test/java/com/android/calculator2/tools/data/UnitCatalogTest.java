/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.calculator2.tools.model.UnitConversion;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Guards the bundled converter catalog, including the expanded category set. */
public class UnitCatalogTest {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static UnitTable sTable;

    @BeforeClass
    public static void loadCatalog() throws Exception {
        final byte[] json = Files.readAllBytes(Paths.get("assets", "tools", "units.json"));
        sTable = UnitTable.parse(new String(json, StandardCharsets.UTF_8));
    }

    @Test
    public void containsAllConverterCategories() {
        final List<String> expected = Arrays.asList(
                "length", "area", "volume", "weight", "temperature",
                "time", "energy", "power", "data", "pressure", "angle", "speed");
        for (String id : expected) {
            assertNotNull("Missing unit category: " + id, category(id));
        }
        assertTrue(sTable.getCategories().size() >= expected.size());
    }

    @Test
    public void everyLinearCategoryHasUsablePositiveFactors() {
        for (UnitCategory category : sTable.getCategories()) {
            assertFalse("Category has no units: " + category.getId(),
                    category.getUnits().isEmpty());
            if (!category.isAffine()) {
                for (UnitDef unit : category.getUnits()) {
                    assertNotNull(category.getId() + "/" + unit.getId(), unit.getFactor());
                    assertTrue(category.getId() + "/" + unit.getId(),
                            unit.getFactor().signum() > 0);
                }
            }
        }
    }

    @Test
    public void representativeNewCategoryConversionsAreCorrect() {
        assertDecimal("3600", convert("time", "h", "s", "1"));
        assertDecimal("3600000", convert("energy", "kWh", "J", "1"));
        assertDecimal("1000", convert("power", "kW", "W", "1"));
        assertDecimal("8", convert("data", "B", "bit", "1"));
        assertDecimal("101325", convert("pressure", "atm", "Pa", "1"));
        assertDecimal("360", convert("angle", "turn", "deg", "1"));
        assertDecimal("36", convert("speed", "m_s", "km_h", "10"));
    }

    private static BigDecimal convert(String categoryId, String fromId, String toId,
            String value) {
        final UnitCategory category = category(categoryId);
        final UnitDef from = unit(category, fromId);
        final UnitDef to = unit(category, toId);
        return UnitConversion.convert(new BigDecimal(value), from.getFactor(), to.getFactor(), MC);
    }

    private static UnitCategory category(String id) {
        for (UnitCategory category : sTable.getCategories()) {
            if (id.equals(category.getId())) {
                return category;
            }
        }
        return null;
    }

    private static UnitDef unit(UnitCategory category, String id) {
        for (UnitDef unit : category.getUnits()) {
            if (id.equals(unit.getId())) {
                return unit;
            }
        }
        throw new AssertionError("Missing unit " + category.getId() + "/" + id);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
