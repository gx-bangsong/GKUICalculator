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
 * A snapshot of exchange rates held in memory / cache.
 * <p>
 * Rates are expressed relative to {@code baseCurrency} (= 1). {@code publishDate} is the source's
 * reference date (e.g. the ECB working-day date); {@code fetchedAt} is when we stored it.
 */
public final class CachedRates {

    @NonNull
    private final String mBaseCurrency;
    private final long mFetchedAt;
    @Nullable
    private final String mPublishDate;
    @NonNull
    private final Map<String, BigDecimal> mRates;

    public CachedRates(@NonNull String baseCurrency, long fetchedAt, @Nullable String publishDate,
            @NonNull Map<String, BigDecimal> rates) {
        mBaseCurrency = baseCurrency;
        mFetchedAt = fetchedAt;
        mPublishDate = publishDate;
        mRates = rates;
    }

    @NonNull
    public String getBaseCurrency() {
        return mBaseCurrency;
    }

    public long getFetchedAt() {
        return mFetchedAt;
    }

    @Nullable
    public String getPublishDate() {
        return mPublishDate;
    }

    @NonNull
    public Map<String, BigDecimal> getRates() {
        return Collections.unmodifiableMap(mRates);
    }

    @Nullable
    public BigDecimal rateFor(@NonNull String currency) {
        return mRates.get(currency);
    }
}
