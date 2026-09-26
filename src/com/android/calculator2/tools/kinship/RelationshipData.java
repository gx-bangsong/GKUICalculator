/*
 * SPDX-FileCopyrightText: 2016 Haole Zheng
 * SPDX-License-Identifier: MIT
 *
 * Data for the Chinese kinship engine, derived from the relation tables of
 * https://github.com/mumuy/relationship (MIT). The tables are kept in their original shape —
 * prefixes, branches, the main table, collective terms, ranked names, the chain-rewriting rules
 * and the regional overrides — so the engine can expand them exactly the way the reference does,
 * instead of shipping 76 thousand expanded entries.
 */

package com.android.calculator2.tools.kinship;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Parsed form of {@code assets/tools/relationship.json}. */
final class RelationshipData {

    static final String ASSET_PATH = "tools/relationship.json";

    /** A chain-rewriting rule: {@code exp} is replaced by {@code str}, globally when {@code g}. */
    static final class Rule {
        @NonNull
        final String exp;
        @NonNull
        final String str;
        final boolean global;

        Rule(@NonNull String exp, @NonNull String str, boolean global) {
            this.exp = exp;
            this.str = str;
            this.global = global;
        }
    }

    @NonNull
    final Map<String, Map<String, List<String>>> prefix = new HashMap<>();
    @NonNull
    final Map<String, List<String>> branch = new HashMap<>();
    @NonNull
    final Map<String, List<String>> main = new HashMap<>();
    @NonNull
    final Map<String, List<String>> multiple = new HashMap<>();
    @NonNull
    final Map<String, List<String>> sort = new HashMap<>();
    @NonNull
    final Map<String, Map<String, List<String>>> locales = new HashMap<>();
    @NonNull
    final List<Rule> rules = new ArrayList<>();

    @NonNull
    static RelationshipData load(@NonNull Context context) throws IOException {
        try (InputStream is = context.getAssets().open(ASSET_PATH)) {
            return parse(read(is));
        }
    }

    @NonNull
    static RelationshipData parse(@NonNull String json) throws IOException {
        try {
            final JSONObject root = new JSONObject(json);
            final RelationshipData data = new RelationshipData();
            readNested(root.optJSONObject("prefix"), data.prefix);
            readFlat(root.optJSONObject("branch"), data.branch);
            readFlat(root.optJSONObject("main"), data.main);
            readFlat(root.optJSONObject("multiple"), data.multiple);
            readFlat(root.optJSONObject("sort"), data.sort);
            final JSONObject localeObject = root.optJSONObject("locales");
            if (localeObject != null) {
                readNested(localeObject, data.locales);
            }
            final JSONArray filters = root.optJSONArray("filter");
            if (filters != null) {
                for (int i = 0; i < filters.length(); i++) {
                    final JSONObject rule = filters.optJSONObject(i);
                    if (rule == null) {
                        continue;
                    }
                    data.rules.add(new Rule(rule.optString("exp", ""), rule.optString("str", ""),
                            rule.optString("flags", "").contains("g")));
                }
            }
            return data;
        } catch (org.json.JSONException e) {
            throw new IOException("Unreadable relationship data", e);
        }
    }

    @NonNull
    private static String read(@NonNull InputStream is) throws IOException {
        final StringBuilder sb = new StringBuilder();
        final char[] buffer = new char[8192];
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            int count;
            while ((count = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, count);
            }
        }
        return sb.toString();
    }

    private static void readFlat(@NonNull JSONObject source, @NonNull Map<String, List<String>> out)
            throws org.json.JSONException {
        final Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            out.put(key, strings(source.optJSONArray(key)));
        }
    }

    private static void readNested(@NonNull JSONObject source,
            @NonNull Map<String, Map<String, List<String>>> out) throws org.json.JSONException {
        final Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final Map<String, List<String>> inner = new HashMap<>();
            readFlat(source.optJSONObject(key), inner);
            out.put(key, inner);
        }
    }

    @NonNull
    private static List<String> strings(@NonNull JSONArray source) throws org.json.JSONException {
        final List<String> out = new ArrayList<>(source.length());
        for (int i = 0; i < source.length(); i++) {
            out.add(source.optString(i, ""));
        }
        return out;
    }
}
