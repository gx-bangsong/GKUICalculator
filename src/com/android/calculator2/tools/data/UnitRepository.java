/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Loads {@code assets/tools/units.json} into resolved {@link UnitCategory} objects (Android side).
 * It also owns the user's custom linear units. Custom units are stored as exact decimal factors
 * in private preferences and merged into the bundled catalog whenever it is loaded.
 */
public final class UnitRepository {

    private static final String ASSET_PATH = "tools/units.json";
    private static final String STRING_PREFIX = "@string/";
    private static final String PREFS_NAME = "custom_units";
    private static final String PREFS_CATALOG = "catalog";

    private final Context mAppContext;
    private List<UnitCategory> mCategories;

    public UnitRepository(@NonNull Context context) {
        mAppContext = context.getApplicationContext();
    }

    /** Returns the bundled catalog with persisted custom units appended to their categories. */
    @NonNull
    public List<UnitCategory> getCategories() {
        if (mCategories == null) {
            mCategories = load();
        }
        return Collections.unmodifiableList(mCategories);
    }

    /**
     * Adds and persists a custom linear unit.
     *
     * @param factor number of this category's base units represented by one new unit
     * @return the new unit, or {@code null} when the category/factor is invalid
     */
    @Nullable
    public UnitDef addCustomUnit(@NonNull String categoryId, @NonNull String displayName,
            @NonNull BigDecimal factor) {
        if (displayName.trim().isEmpty() || factor.signum() <= 0) {
            return null;
        }
        // Ensure the catalog is loaded before looking up and replacing a category.
        getCategories();
        final int categoryIndex = indexOfCategory(categoryId);
        if (categoryIndex < 0) {
            return null;
        }
        final UnitCategory category = mCategories.get(categoryIndex);
        if (category.isAffine()) {
            // A single multiplier cannot describe an affine scale such as temperature.
            return null;
        }
        final String name = displayName.trim();
        for (UnitDef existing : category.getUnits()) {
            if (name.equalsIgnoreCase(existing.getDisplayName())) {
                return null;
            }
        }

        final UnitDef unit = new UnitDef("custom_" + UUID.randomUUID(), name, factor);
        final List<UnitDef> units = new ArrayList<>(category.getUnits());
        units.add(unit);
        mCategories.set(categoryIndex, copyCategory(category, units));
        persistCustomUnit(categoryId, unit);
        return unit;
    }

    @NonNull
    private List<UnitCategory> load() {
        try {
            final String json = readAsset(ASSET_PATH);
            final UnitTable table = UnitTable.parse(json);
            final List<UnitCategory> categories = resolveNames(table.getCategories());
            mergeCustomUnits(categories);
            return categories;
        } catch (IOException | JSONException | NumberFormatException e) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private List<UnitCategory> resolveNames(@NonNull List<UnitCategory> raw) {
        final List<UnitCategory> out = new ArrayList<>();
        for (UnitCategory category : raw) {
            final String categoryName = resolve(category.getDisplayName());
            final List<UnitDef> units = new ArrayList<>();
            for (UnitDef unit : category.getUnits()) {
                units.add(new UnitDef(unit.getId(), resolve(unit.getDisplayName()),
                        unit.getFactor()));
            }
            out.add(new UnitCategory(category.getId(), categoryName, category.getBaseUnitId(),
                    category.isAffine(), units));
        }
        return out;
    }

    /** Merges well-formed saved entries; a damaged preference never hides the bundled catalog. */
    private void mergeCustomUnits(@NonNull List<UnitCategory> categories) {
        final JSONObject root = readCustomCatalog();
        for (int i = 0; i < categories.size(); i++) {
            final UnitCategory category = categories.get(i);
            if (category.isAffine()) {
                continue;
            }
            final JSONArray saved = root.optJSONArray(category.getId());
            if (saved == null) {
                continue;
            }
            final List<UnitDef> units = new ArrayList<>(category.getUnits());
            for (int j = 0; j < saved.length(); j++) {
                final JSONObject item = saved.optJSONObject(j);
                if (item == null) {
                    continue;
                }
                final String id = item.optString("id", "");
                final String name = item.optString("name", "").trim();
                final String factorText = item.optString("factor", "");
                try {
                    final BigDecimal factor = new BigDecimal(factorText);
                    if (!id.isEmpty() && !name.isEmpty() && factor.signum() > 0
                            && !containsId(units, id) && !containsName(units, name)) {
                        units.add(new UnitDef(id, name, factor));
                    }
                } catch (NumberFormatException ignored) {
                    // Skip only the malformed custom entry.
                }
            }
            categories.set(i, copyCategory(category, units));
        }
    }

    private void persistCustomUnit(@NonNull String categoryId, @NonNull UnitDef unit) {
        final JSONObject root = readCustomCatalog();
        JSONArray entries = root.optJSONArray(categoryId);
        if (entries == null) {
            entries = new JSONArray();
            try {
                root.put(categoryId, entries);
            } catch (JSONException ignored) {
                return;
            }
        }
        final JSONObject item = new JSONObject();
        try {
            item.put("id", unit.getId());
            item.put("name", unit.getDisplayName());
            item.put("factor", unit.getFactor().toPlainString());
            entries.put(item);
            preferences().edit().putString(PREFS_CATALOG, root.toString()).apply();
        } catch (JSONException ignored) {
            // JSONObject only receives primitive strings here; retain the in-memory unit if the
            // platform JSON implementation nevertheless rejects an entry.
        }
    }

    @NonNull
    private JSONObject readCustomCatalog() {
        final String json = preferences().getString(PREFS_CATALOG, "{}");
        try {
            return new JSONObject(json == null ? "{}" : json);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    @NonNull
    private SharedPreferences preferences() {
        return mAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private int indexOfCategory(@NonNull String categoryId) {
        for (int i = 0; i < mCategories.size(); i++) {
            if (categoryId.equals(mCategories.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsId(@NonNull List<UnitDef> units, @NonNull String id) {
        for (UnitDef unit : units) {
            if (id.equals(unit.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsName(@NonNull List<UnitDef> units, @NonNull String name) {
        for (UnitDef unit : units) {
            if (name.equalsIgnoreCase(unit.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static UnitCategory copyCategory(@NonNull UnitCategory category,
            @NonNull List<UnitDef> units) {
        return new UnitCategory(category.getId(), category.getDisplayName(),
                category.getBaseUnitId(), category.isAffine(), units);
    }

    @NonNull
    private String resolve(@NonNull String name) {
        if (name.startsWith(STRING_PREFIX)) {
            final String key = name.substring(STRING_PREFIX.length());
            final int resId = mAppContext.getResources()
                    .getIdentifier(key, "string", mAppContext.getPackageName());
            if (resId != 0) {
                return mAppContext.getString(resId);
            }
        }
        return name;
    }

    @NonNull
    private String readAsset(@NonNull String path) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (InputStream is = mAppContext.getAssets().open(path);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
