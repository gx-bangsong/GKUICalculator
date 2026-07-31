/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A group of related units (length, weight, temperature, ...).
 * <p>
 * Linear categories multiply by {@link UnitDef#getFactor()}; affine categories (temperature) use
 * dedicated conversion logic and ignore factors.
 */
public final class UnitCategory {

    @NonNull
    private final String mId;
    @NonNull
    private final String mDisplayName;
    @Nullable
    private final String mBaseUnitId;
    private final boolean mAffine;
    @NonNull
    private final List<UnitDef> mUnits;

    public UnitCategory(@NonNull String id, @NonNull String displayName,
            @Nullable String baseUnitId, boolean affine, @NonNull List<UnitDef> units) {
        mId = id;
        mDisplayName = displayName;
        mBaseUnitId = baseUnitId;
        mAffine = affine;
        mUnits = units;
    }

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    @Nullable
    public String getBaseUnitId() {
        return mBaseUnitId;
    }

    public boolean isAffine() {
        return mAffine;
    }

    @NonNull
    public List<UnitDef> getUnits() {
        return Collections.unmodifiableList(mUnits);
    }
}
