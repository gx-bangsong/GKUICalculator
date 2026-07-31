/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.android.calculator2.R;

/**
 * Coordinates currency-rate loading with a cache-first, network-refreshing, offline-degrading
 * strategy (data source: ECB daily XML, same as {@code com.yangdai.calc}).
 * <p>
 * Resolution order:
 * <ol>
 *   <li>If auto-update is on and the cache is stale (or absent) and we are online: fetch, parse,
 *       cache, return NETWORK.</li>
 *   <li>Otherwise: return the cached rates (CACHE), labelled with their reference date.</li>
 *   <li>If nothing is cached: return the bundled fallback rates (FALLBACK), labelled "offline".</li>
 * </ol>
 * All I/O happens on a background executor; results are delivered on the main thread.
 */
public final class ExchangeRateRepository {

    /** Where the delivered rates came from. */
    public enum Source { NETWORK, CACHE, FALLBACK }

    /** Delivered on the main thread. */
    public interface Callback {
        void onRates(@Nullable CachedRates rates, @NonNull Source source, @NonNull CharSequence label);
    }

    private static final String PREFS_NAME = "calc_tools";
    private static final String PREF_AUTO_UPDATE = "auto_update_rates";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final Context mAppContext;
    private final ExchangeRateConfig mConfig;
    private final RateCache mCache;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public ExchangeRateRepository(@NonNull Context context) {
        mAppContext = context.getApplicationContext();
        mConfig = ExchangeRateConfig.load(mAppContext);
        mCache = new RateCache(mAppContext);
    }

    @NonNull
    public ExchangeRateConfig getConfig() {
        return mConfig;
    }

    /** Asynchronously load rates; {@link Callback} is invoked on the main thread. */
    public void loadRates(@NonNull final Callback callback) {
        mExecutor.execute(() -> {
            final Result result = doLoad();
            mMain.post(() -> callback.onRates(result.rates, result.source, result.label));
        });
    }

    private Result doLoad() {
        final CachedRates cached = mCache.read();
        final long now = System.currentTimeMillis();
        final long ttlMs = mConfig.getCacheTtlMinutes() * 60_000L;
        final boolean fresh = cached != null && (now - cached.getFetchedAt()) < ttlMs;

        if (isAutoUpdateEnabled(mAppContext)) {
            if (!fresh && isOnline()) {
                try {
                    final String xml = fetch(mConfig.getApiUrl());
                    final EcbResult parsed = EcbRateParser.parse(xml);
                    final Map<String, BigDecimal> rates = parsed.getRates();
                    final CachedRates freshRates = new CachedRates(mConfig.getBaseCurrency(), now,
                            parsed.getPublishDate(), rates);
                    mCache.write(freshRates);
                    return new Result(freshRates, Source.NETWORK,
                            updatedLabel(parsed.getPublishDate(), now));
                } catch (Exception ignored) {
                    // Fall through to cache / fallback below.
                }
            } else if (fresh && cached != null) {
                return new Result(cached, Source.CACHE,
                        updatedLabel(cached.getPublishDate(), cached.getFetchedAt()));
            }
        }

        if (cached != null) {
            return new Result(cached, Source.CACHE,
                    updatedLabel(cached.getPublishDate(), cached.getFetchedAt()));
        }

        final CachedRates fallback = new CachedRates(mConfig.getBaseCurrency(), 0L, null,
                new LinkedHashMap<>(mConfig.getFallbackRates()));
        return new Result(fallback, Source.FALLBACK,
                mAppContext.getString(R.string.currency_offline));
    }

    @NonNull
    private String fetch(@NonNull String urlStr) throws IOException {
        final HttpURLConnection connection =
                (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoInput(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            final StringBuilder sb = new StringBuilder();
            try (InputStream is = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } finally {
            connection.disconnect();
        }
    }

    private boolean isOnline() {
        try {
            final ConnectivityManager cm =
                    (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true; // assume online; the fetch will fail gracefully if not
            }
            final Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            final NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (SecurityException e) {
            // ACCESS_NETWORK_STATE not granted: assume online and let the fetch fail gracefully
            // (caught by doLoad's try/catch) so a missing permission never crashes the app.
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @NonNull
    private CharSequence updatedLabel(@Nullable String publishDate, long fallbackTime) {
        String date = publishDate;
        if (date == null && fallbackTime > 0L) {
            DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            date = fmt.format(new Date(fallbackTime));
        }
        if (date == null) {
            date = "—";
        }
        return mAppContext.getString(R.string.currency_updated, date);
    }

    // ---- Auto-update setting (default SharedPreferences) ----

    public static boolean isAutoUpdateEnabled(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_UPDATE, true);
    }

    public static void setAutoUpdateEnabled(@NonNull Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_AUTO_UPDATE, enabled)
                .apply();
    }

    private static final class Result {
        @Nullable
        final CachedRates rates;
        @NonNull
        final Source source;
        @NonNull
        final CharSequence label;

        Result(@Nullable CachedRates rates, @NonNull Source source, @NonNull CharSequence label) {
            this.rates = rates;
            this.source = source;
            this.label = label;
        }
    }
}
