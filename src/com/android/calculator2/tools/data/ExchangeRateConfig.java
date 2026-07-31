/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and exposes {@code assets/tools/exchange_rate_config.json}: the ECB endpoint, base
 * currency, cache TTL, the selectable currency list, and offline fallback rates. The endpoint and
 * parsing are externalized here so the data source can change without code edits.
 * <p>
 * On any failure the config degrades to safe built-in defaults, so the tool still functions.
 */
public final class ExchangeRateConfig {

    private static final String ASSET_PATH = "tools/exchange_rate_config.json";

    private static final String DEFAULT_API_URL =
            "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";
    private static final String DEFAULT_BASE = "EUR";
    private static final int DEFAULT_TTL_MINUTES = 60;

    @NonNull
    private final String mApiUrl;
    @NonNull
    private final String mBaseCurrency;
    private final int mCacheTtlMinutes;
    @NonNull
    private final List<CurrencyDef> mCurrencies;
    @NonNull
    private final Map<String, BigDecimal> mFallbackRates;

    public ExchangeRateConfig(@NonNull String apiUrl, @NonNull String baseCurrency,
            int cacheTtlMinutes, @NonNull List<CurrencyDef> currencies,
            @NonNull Map<String, BigDecimal> fallbackRates) {
        mApiUrl = apiUrl;
        mBaseCurrency = baseCurrency;
        mCacheTtlMinutes = cacheTtlMinutes;
        mCurrencies = currencies;
        mFallbackRates = fallbackRates;
    }

    @NonNull
    public String getApiUrl() {
        return mApiUrl;
    }

    @NonNull
    public String getBaseCurrency() {
        return mBaseCurrency;
    }

    public int getCacheTtlMinutes() {
        return mCacheTtlMinutes;
    }

    @NonNull
    public List<CurrencyDef> getCurrencies() {
        return Collections.unmodifiableList(mCurrencies);
    }

    @NonNull
    public Map<String, BigDecimal> getFallbackRates() {
        return Collections.unmodifiableMap(mFallbackRates);
    }

    /** Load from assets, or built-in defaults on any failure. */
    @NonNull
    public static ExchangeRateConfig load(@NonNull Context context) {
        try {
            String json = readAsset(context, ASSET_PATH);
            return parse(json);
        } catch (IOException | org.json.JSONException | NumberFormatException e) {
            return defaults();
        }
    }

    @NonNull
    static ExchangeRateConfig parse(@NonNull String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json);
        String apiUrl = root.optString("api_url", DEFAULT_API_URL);
        String base = root.optString("base_currency", DEFAULT_BASE);
        int ttl = root.optInt("cache_ttl_minutes", DEFAULT_TTL_MINUTES);

        List<CurrencyDef> currencies = new ArrayList<>();
        JSONArray curArray = root.optJSONArray("currencies");
        if (curArray != null) {
            for (int i = 0; i < curArray.length(); i++) {
                JSONObject c = curArray.getJSONObject(i);
                currencies.add(new CurrencyDef(
                        c.optString("id", ""),
                        c.optString("name", c.optString("id", "")),
                        c.optString("symbol", "")));
            }
        }

        Map<String, BigDecimal> fallback = new LinkedHashMap<>();
        JSONObject fb = root.optJSONObject("fallback_rates");
        if (fb != null) {
            JSONArray names = fb.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String code = names.getString(i);
                    fallback.put(code, new BigDecimal(fb.getString(code)));
                }
            }
        }
        if (!fallback.containsKey(base)) {
            fallback.put(base, BigDecimal.ONE);
        }
        return new ExchangeRateConfig(apiUrl, base, ttl, currencies, fallback);
    }

    @NonNull
    private static ExchangeRateConfig defaults() {
        List<CurrencyDef> currencies = new ArrayList<>();
        currencies.add(new CurrencyDef("EUR", "Euro", "€"));
        currencies.add(new CurrencyDef("USD", "US Dollar", "$"));
        currencies.add(new CurrencyDef("CNY", "Chinese Yuan", "¥"));
        currencies.add(new CurrencyDef("JPY", "Japanese Yen", "¥"));
        currencies.add(new CurrencyDef("GBP", "British Pound", "£"));
        Map<String, BigDecimal> fallback = new LinkedHashMap<>();
        fallback.put("EUR", new BigDecimal("1"));
        fallback.put("USD", new BigDecimal("1.08"));
        fallback.put("CNY", new BigDecimal("7.85"));
        fallback.put("JPY", new BigDecimal("157"));
        fallback.put("GBP", new BigDecimal("0.86"));
        return new ExchangeRateConfig(DEFAULT_API_URL, DEFAULT_BASE, DEFAULT_TTL_MINUTES,
                currencies, fallback);
    }

    @NonNull
    private static String readAsset(@NonNull Context context, @Nullable String path)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
