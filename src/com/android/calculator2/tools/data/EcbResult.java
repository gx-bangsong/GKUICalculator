/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of parsing an ECB daily reference-rates document: the reference (publication) date and
 * the currency -&gt; rate map (with EUR implicitly = 1).
 */
public final class EcbResult {

    @Nullable
    private final String mPublishDate;
    @NonNull
    private final Map<String, BigDecimal> mRates;

    public EcbResult(@Nullable String publishDate, @NonNull Map<String, BigDecimal> rates) {
        mPublishDate = publishDate;
        mRates = rates;
    }

    @Nullable
    public String getPublishDate() {
        return mPublishDate;
    }

    @NonNull
    public Map<String, BigDecimal> getRates() {
        return Collections.unmodifiableMap(mRates);
    }
}
