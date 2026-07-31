/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure parser for the units configuration (no Android dependencies).
 * <p>
 * Reads the JSON document produced from {@code assets/tools/units.json} into {@link UnitCategory}
 * / {@link UnitDef} objects. Display names are kept verbatim (they may be {@code @string/...}
 * references); {@link UnitRepository} resolves them to localized strings on the Android side.
 * Factors are read as strings and parsed via {@link BigDecimal#BigDecimal(String)} to preserve
 * exactness (e.g. {@code "0.45359237"}).
 */
public final class UnitTable {

    @NonNull
    private final List<UnitCategory> mCategories;

    public UnitTable(@NonNull List<UnitCategory> categories) {
        mCategories = categories;
    }

    @NonNull
    public List<UnitCategory> getCategories() {
        return Collections.unmodifiableList(mCategories);
    }

    @NonNull
    public static UnitTable parse(@NonNull String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray categoryArray = root.getJSONArray("categories");
        List<UnitCategory> categories = new ArrayList<>();
        for (int i = 0; i < categoryArray.length(); i++) {
            JSONObject c = categoryArray.getJSONObject(i);
            String id = c.getString("id");
            String name = c.optString("name", id);
            String base = c.optString("base", (String) null);
            boolean affine = c.optBoolean("affine", false);
            JSONArray unitArray = c.getJSONArray("units");
            List<UnitDef> units = new ArrayList<>();
            for (int j = 0; j < unitArray.length(); j++) {
                JSONObject u = unitArray.getJSONObject(j);
                String unitId = u.getString("id");
                String unitName = u.optString("name", unitId);
                BigDecimal factor = null;
                if (u.has("factor")) {
                    factor = new BigDecimal(u.getString("factor"));
                }
                units.add(new UnitDef(unitId, unitName, factor));
            }
            categories.add(new UnitCategory(id, name, base, affine, units));
        }
        return new UnitTable(categories);
    }
}
