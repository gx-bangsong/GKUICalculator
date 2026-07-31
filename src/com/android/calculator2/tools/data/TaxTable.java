/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Chinese individual-income-tax table: the monthly tax-free threshold and the annual
 * cumulative-withholding brackets. {@link #parse(String)} is pure (unit-testable); {@link #load}
 * reads {@code assets/tools/tax_cn.json} and caches the result.
 */
public final class TaxTable {

    private static final String ASSET_PATH = "tools/tax_cn.json";

    @NonNull
    private final BigDecimal mMonthlyThreshold;
    @NonNull
    private final List<TaxBracket> mBrackets;

    public TaxTable(@NonNull BigDecimal monthlyThreshold, @NonNull List<TaxBracket> brackets) {
        mMonthlyThreshold = monthlyThreshold;
        mBrackets = brackets;
    }

    @NonNull
    public BigDecimal getMonthlyThreshold() {
        return mMonthlyThreshold;
    }

    @NonNull
    public List<TaxBracket> getBrackets() {
        return Collections.unmodifiableList(mBrackets);
    }

    @NonNull
    public static TaxTable parse(@NonNull String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        BigDecimal threshold = new BigDecimal(root.optString("monthly_threshold", "5000"));
        JSONArray arr = root.getJSONArray("brackets");
        List<TaxBracket> brackets = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.getJSONObject(i);
            brackets.add(new TaxBracket(
                    new BigDecimal(b.getString("up_to")),
                    new BigDecimal(b.getString("rate")),
                    new BigDecimal(b.optString("quick_deduction", "0"))));
        }
        return new TaxTable(threshold, brackets);
    }

    @NonNull
    public static TaxTable load(@NonNull Context context) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(ASSET_PATH),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return parse(sb.toString());
        } catch (IOException | JSONException | NumberFormatException e) {
            return TaxTable.defaults();
        }
    }

    /** Built-in fallback table (2024 China IIT cumulative brackets), used if the asset is missing. */
    @NonNull
    public static TaxTable defaults() {
        List<TaxBracket> brackets = new ArrayList<>();
        brackets.add(new TaxBracket(new BigDecimal("36000"),     new BigDecimal("0.03"), new BigDecimal("0")));
        brackets.add(new TaxBracket(new BigDecimal("144000"),    new BigDecimal("0.10"), new BigDecimal("2520")));
        brackets.add(new TaxBracket(new BigDecimal("300000"),    new BigDecimal("0.20"), new BigDecimal("16920")));
        brackets.add(new TaxBracket(new BigDecimal("420000"),    new BigDecimal("0.25"), new BigDecimal("31920")));
        brackets.add(new TaxBracket(new BigDecimal("660000"),    new BigDecimal("0.30"), new BigDecimal("52920")));
        brackets.add(new TaxBracket(new BigDecimal("960000"),    new BigDecimal("0.35"), new BigDecimal("85920")));
        brackets.add(new TaxBracket(new BigDecimal("999999999"), new BigDecimal("0.45"), new BigDecimal("181920")));
        return new TaxTable(new BigDecimal("5000"), brackets);
    }
}
