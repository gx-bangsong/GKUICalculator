/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * A single unit within a category.
 * <p>
 * {@code factor} is the number of base units this unit equals (null for affine categories such
 * as temperature, where conversion is handled by {@code TemperatureConversion}). The display
 * name is the raw text from configuration (possibly a {@code @string/...} reference before the
 * repository resolves it to a localized string).
 */
public final class UnitDef {

    @NonNull
    private final String mId;
    @NonNull
    private final String mDisplayName;
    @Nullable
    private final BigDecimal mFactor;

    public UnitDef(@NonNull String id, @NonNull String displayName, @Nullable BigDecimal factor) {
        mId = id;
        mDisplayName = displayName;
        mFactor = factor;
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
    public BigDecimal getFactor() {
        return mFactor;
    }
}
