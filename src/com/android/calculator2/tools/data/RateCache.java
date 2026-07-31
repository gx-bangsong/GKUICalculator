/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * On-disk cache of {@link CachedRates} (a small JSON file in the app's files dir). Enables offline
 * use with an accurate "updated" timestamp. All access is meant to happen off the UI thread.
 */
public final class RateCache {

    private static final String CACHE_FILE = "exchange_rates_cache.json";
    private static final String KEY_BASE = "base";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final String KEY_PUBLISH_DATE = "publish_date";
    private static final String KEY_RATES = "rates";

    private final File mFile;

    public RateCache(@NonNull Context context) {
        mFile = new File(context.getFilesDir(), CACHE_FILE);
    }

    @Nullable
    public CachedRates read() {
        if (!mFile.exists()) {
            return null;
        }
        try {
            String json = readAll();
            JSONObject root = new JSONObject(json);
            String base = root.optString(KEY_BASE, "EUR");
            long fetchedAt = root.optLong(KEY_FETCHED_AT, 0L);
            String publishDate = root.has(KEY_PUBLISH_DATE)
                    ? root.getString(KEY_PUBLISH_DATE) : null;
            JSONObject ratesObj = root.getJSONObject(KEY_RATES);
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            Iterator<String> keys = ratesObj.keys();
            while (keys.hasNext()) {
                String code = keys.next();
                rates.put(code, new BigDecimal(ratesObj.getString(code)));
            }
            return new CachedRates(base, fetchedAt, publishDate, rates);
        } catch (Exception e) {
            return null;
        }
    }

    public void write(@NonNull CachedRates rates) {
        try {
            JSONObject root = new JSONObject();
            root.put(KEY_BASE, rates.getBaseCurrency());
            root.put(KEY_FETCHED_AT, rates.getFetchedAt());
            if (rates.getPublishDate() != null) {
                root.put(KEY_PUBLISH_DATE, rates.getPublishDate());
            }
            JSONObject ratesObj = new JSONObject();
            for (Map.Entry<String, BigDecimal> entry : rates.getRates().entrySet()) {
                ratesObj.put(entry.getKey(), entry.getValue().toPlainString());
            }
            root.put(KEY_RATES, ratesObj);
            writeAll(root.toString());
        } catch (Exception e) {
            // Best-effort cache; ignore write failures.
        }
    }

    public void clear() {
        //noinspection ResultOfMethodCallIgnored
        mFile.delete();
    }

    @NonNull
    private String readAll() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(mFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void writeAll(@NonNull String json) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(mFile), StandardCharsets.UTF_8))) {
            writer.write(json);
        }
    }
}
