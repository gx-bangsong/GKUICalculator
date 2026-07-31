/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A currency descriptor from configuration: ISO code, display name, and optional symbol.
 */
public final class CurrencyDef {

    @NonNull
    private final String mId;
    @NonNull
    private final String mName;
    @NonNull
    private final String mSymbol;

    public CurrencyDef(@NonNull String id, @NonNull String name, @NonNull String symbol) {
        mId = id;
        mName = name;
        mSymbol = symbol;
    }

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public String getSymbol() {
        return mSymbol;
    }

    /** Short label used in selectors, e.g. "¥ CNY". */
    @NonNull
    public String shortLabel() {
        return mSymbol.isEmpty() ? mId : mSymbol + " " + mId;
    }
}
