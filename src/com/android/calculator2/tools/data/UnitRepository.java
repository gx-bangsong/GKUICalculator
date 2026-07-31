/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads {@code assets/tools/units.json} into resolved {@link UnitCategory} objects (Android side).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Read the asset and parse it via the pure {@link UnitTable}.</li>
 *   <li>Resolve any {@code @string/...} display names to localized strings.</li>
 *   <li>Cache the result for the lifetime of the instance.</li>
 * </ul>
 * On any failure it returns an empty list rather than throwing, so the tool degrades gracefully.
 */
public final class UnitRepository {

    private static final String ASSET_PATH = "tools/units.json";
    private static final String STRING_PREFIX = "@string/";

    private final Context mAppContext;
    private List<UnitCategory> mCategories;

    public UnitRepository(@NonNull Context context) {
        mAppContext = context.getApplicationContext();
    }

    @NonNull
    public List<UnitCategory> getCategories() {
        if (mCategories == null) {
            mCategories = load();
        }
        return mCategories;
    }

    @NonNull
    private List<UnitCategory> load() {
        try {
            String json = readAsset(ASSET_PATH);
            UnitTable table = UnitTable.parse(json);
            return resolveNames(table.getCategories());
        } catch (IOException | org.json.JSONException | NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    private List<UnitCategory> resolveNames(@NonNull List<UnitCategory> raw) {
        List<UnitCategory> out = new ArrayList<>();
        for (UnitCategory category : raw) {
            String categoryName = resolve(category.getDisplayName());
            List<UnitDef> units = new ArrayList<>();
            for (UnitDef unit : category.getUnits()) {
                units.add(new UnitDef(unit.getId(), resolve(unit.getDisplayName()),
                        unit.getFactor()));
            }
            out.add(new UnitCategory(category.getId(), categoryName, category.getBaseUnitId(),
                    category.isAffine(), units));
        }
        return out;
    }

    @NonNull
    private String resolve(@NonNull String name) {
        if (name.startsWith(STRING_PREFIX)) {
            String key = name.substring(STRING_PREFIX.length());
            int resId = mAppContext.getResources()
                    .getIdentifier(key, "string", mAppContext.getPackageName());
            if (resId != 0) {
                return mAppContext.getString(resId);
            }
        }
        return name;
    }

    @NonNull
    private String readAsset(@NonNull String path) throws IOException {
        StringBuilder sb = new StringBuilder();
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
