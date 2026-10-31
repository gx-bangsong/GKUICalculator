/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigDecimal;

public class ChineseNumeralsTest {

    @Test
    public void rendersPlainUppercaseNumerals() {
        assertEquals("零", ChineseNumerals.toUppercase("0"));
        assertEquals("壹", ChineseNumerals.toUppercase("1"));
        assertEquals("壹拾", ChineseNumerals.toUppercase("10"));
        assertEquals("壹拾壹", ChineseNumerals.toUppercase("11"));
        assertEquals("壹佰零壹", ChineseNumerals.toUppercase("101"));
        assertEquals("壹仟零壹", ChineseNumerals.toUppercase("1001"));
        assertEquals("壹仟零壹拾", ChineseNumerals.toUppercase("1010"));
        assertEquals("壹万零壹", ChineseNumerals.toUppercase("10001"));
        assertEquals("壹万壹仟", ChineseNumerals.toUppercase("11000"));
        assertEquals("壹拾万", ChineseNumerals.toUppercase("100000"));
        assertEquals("壹佰万", ChineseNumerals.toUppercase("1000000"));
        assertEquals("壹佰万零壹", ChineseNumerals.toUppercase("1000001"));
        assertEquals("壹亿", ChineseNumerals.toUppercase("100000000"));
        assertEquals("壹亿零壹万", ChineseNumerals.toUppercase("100010000"));
        assertEquals("壹亿零伍仟", ChineseNumerals.toUppercase("100005000"));
        assertEquals("壹亿贰仟叁佰肆拾伍万陆仟柒佰捌拾玖",
                ChineseNumerals.toUppercase("123456789"));
        assertEquals("壹万亿", ChineseNumerals.toUppercase("1000000000000"));
    }

    @Test
    public void rendersFractionsAndSigns() {
        assertEquals("零点伍", ChineseNumerals.toUppercase("0.5"));
        assertEquals("壹拾贰点叁肆", ChineseNumerals.toUppercase("12.34"));
        assertEquals("壹仟贰佰叁拾肆点伍陆", ChineseNumerals.toUppercase("1,234.56"));
        assertEquals("负伍点贰", ChineseNumerals.toUppercase("-5.2"));
        // Grouping separators are ignored; so is a trailing decimal point.
        assertEquals("壹仟贰佰叁拾肆", ChineseNumerals.toUppercase("1,234."));
    }

    @Test
    public void leavesNonNumbersAlone() {
        assertEquals("—", ChineseNumerals.toUppercase("—"));
        assertEquals("", ChineseNumerals.toUppercase(""));
        assertEquals("", ChineseNumerals.toUppercase("   "));
        assertEquals("Loading", ChineseNumerals.toUppercase("Loading"));
    }

    @Test
    public void rendersAmountsInTheRegulatedMoneyForm() {
        assertEquals("零元整", ChineseNumerals.toUppercaseAmount(new BigDecimal("0")));
        assertEquals("壹元整", ChineseNumerals.toUppercaseAmount(new BigDecimal("1")));
        assertEquals("壹拾元整", ChineseNumerals.toUppercaseAmount(new BigDecimal("10")));
        // 分 is zero, so the amount ends in 整.
        assertEquals("壹元伍角整", ChineseNumerals.toUppercaseAmount(new BigDecimal("1.5")));
        assertEquals("壹元零伍分", ChineseNumerals.toUppercaseAmount(new BigDecimal("1.05")));
        assertEquals("壹元伍角伍分", ChineseNumerals.toUppercaseAmount(new BigDecimal("1.55")));
        assertEquals("伍仟叁佰零肆元叁角壹分",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("5304.31")));
        assertEquals("玖拾壹万零陆佰壹拾陆元肆角贰分",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("910616.42")));
        assertEquals("壹佰玖拾壹万零陆佰壹拾陆元肆角贰分",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("1910616.42")));
        assertEquals("壹佰万元整",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("1000000")));
        assertEquals("壹亿元整",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("100000000")));
        assertEquals("壹亿贰仟叁佰肆拾伍万陆仟柒佰捌拾玖元玖角玖分",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("123456789.99")));
    }

    @Test
    public void rendersSubUnitAmountsWithoutYuan() {
        assertEquals("伍分", ChineseNumerals.toUppercaseAmount(new BigDecimal("0.05")));
        assertEquals("肆角伍分", ChineseNumerals.toUppercaseAmount(new BigDecimal("0.45")));
        assertEquals("肆角整", ChineseNumerals.toUppercaseAmount(new BigDecimal("0.40")));
        assertEquals("负壹拾贰元叁角整",
                ChineseNumerals.toUppercaseAmount(new BigDecimal("-12.30")));
    }

    @Test
    public void roundsHalfUpToFen() {
        assertEquals("捌元捌角整", ChineseNumerals.toUppercaseAmount(new BigDecimal("8.8")));
        assertEquals("壹元整", ChineseNumerals.toUppercaseAmount(new BigDecimal("0.995")));
        assertEquals("壹元零壹分", ChineseNumerals.toUppercaseAmount(new BigDecimal("1.005")));
    }

    @Test
    public void handlesMissingValues() {
        assertEquals("—", ChineseNumerals.toUppercaseAmount(null));
        assertEquals("", ChineseNumerals.toUppercase(null));
    }
}
