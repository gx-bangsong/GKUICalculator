/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.data.CachedRates;
import com.android.calculator2.tools.data.CurrencyDef;
import com.android.calculator2.tools.data.ExchangeRateRepository;
import com.android.calculator2.tools.model.CurrencyConversion;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Currency conversion using ECB reference rates (EUR base) with on-disk caching and offline
 * fallback. Mirrors the data source of {@code com.yangdai.calc}, extended with caching/offline.
 * <p>
 * The display is reused: the formula line shows {@code "amount <from>"}, the result line shows
 * {@code "result <to>"}. The mode-specific controls (from / to selectors + swap) and the
 * update-time label are mounted into the host's {@link ToolHost#getToolControlSlot()}.
 */
public class CurrencyConverterMode implements ToolMode {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static final MathContext DISPLAY_MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final String DEFAULT_INPUT = "1";

    @Nullable
    private ExchangeRateRepository mRepository;
    @NonNull
    private List<CurrencyDef> mCurrencies = Collections.emptyList();
    @Nullable
    private CachedRates mRates;

    private int mFromIndex;
    private int mToIndex;

    private final StringBuilder mInput = new StringBuilder();

    @Nullable
    private ToolHost mHost;
    @Nullable
    private View mControlRoot;
    @Nullable
    private MaterialAutoCompleteTextView mFromView;
    @Nullable
    private MaterialAutoCompleteTextView mToView;
    @Nullable
    private TextView mInputView;
    @Nullable
    private TextView mResultView;
    @Nullable
    private TextView mUpdateLabel;

    @NonNull
    @Override
    public String getId() {
        return ToolId.CURRENCY;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_currency;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public boolean wantsExpandedDisplay() {
        return true;
    }

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        mHost = host;
        final Context context = host.getContext();
        if (mRepository == null) {
            mRepository = new ExchangeRateRepository(context);
        }
        mCurrencies = mRepository.getConfig().getCurrencies();
        mRates = null;

        mInput.setLength(0);
        mInput.append(carryValue != null ? carryValue : DEFAULT_INPUT);

        // Sensible defaults: CNY -> USD when both exist, otherwise the first two.
        int cny = indexOfCode("CNY");
        int usd = indexOfCode("USD");
        mFromIndex = cny >= 0 ? cny : 0;
        mToIndex = usd >= 0 ? usd : Math.min(1, Math.max(0, mCurrencies.size() - 1));
        if (mFromIndex == mToIndex && mCurrencies.size() > 1) {
            mToIndex = (mFromIndex + 1) % mCurrencies.size();
        }

        mountControls(context);
        redisplay();

        if (mRepository != null) {
            mRepository.loadRates(this::onRatesLoaded);
        }
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
        unmountControls(host);
        mHost = null;
    }

    @Override
    public void onDigit(int digit) {
        mInput.append(digit);
        redisplay();
    }

    @Override
    public void onDecimalPoint() {
        if (mInput.indexOf(".") < 0) {
            if (mInput.length() == 0) {
                mInput.append("0");
            }
            mInput.append(".");
        }
        redisplay();
    }

    @Override
    public void onDelete() {
        if (mInput.length() > 0) {
            mInput.deleteCharAt(mInput.length() - 1);
        }
        redisplay();
    }

    @Override
    public void onClear() {
        mInput.setLength(0);
        redisplay();
    }

    // ---- async rates ----

    private void onRatesLoaded(@Nullable CachedRates rates, @NonNull ExchangeRateRepository.Source source,
            @NonNull CharSequence label) {
        if (mHost == null) {
            return; // deactivated
        }
        mRates = rates;
        if (mUpdateLabel != null) {
            mUpdateLabel.setText(label);
        }
        redisplay();
    }

    // ---- UI ----

    private void mountControls(@NonNull Context context) {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        final ViewGroup slot = host.getToolControlSlot();
        if (slot == null) {
            return;
        }
        mControlRoot = LayoutInflater.from(context)
                .inflate(R.layout.tool_currency_control, slot, false);
        mFromView = mControlRoot.findViewById(R.id.currency_from);
        mToView = mControlRoot.findViewById(R.id.currency_to);
        mInputView = mControlRoot.findViewById(R.id.currency_input_text);
        mResultView = mControlRoot.findViewById(R.id.currency_result_text);
        mUpdateLabel = mControlRoot.findViewById(R.id.currency_update_label);
        final ImageButton swap = mControlRoot.findViewById(R.id.currency_swap);
        swap.setOnClickListener(v -> swapCurrencies());
        configureCurrencyDropdown(mFromView, true, context);
        configureCurrencyDropdown(mToView, false, context);
        updateCurrencyLabels();

        if (mUpdateLabel != null) {
            mUpdateLabel.setText(R.string.currency_loading);
        }

        slot.removeAllViews();
        slot.addView(mControlRoot);
        slot.setVisibility(View.VISIBLE);
    }

    private void unmountControls(@NonNull ToolHost host) {
        final ViewGroup slot = host.getToolControlSlot();
        if (slot != null) {
            slot.removeAllViews();
            slot.setVisibility(View.GONE);
        }
        mControlRoot = null;
        mFromView = null;
        mToView = null;
        mInputView = null;
        mResultView = null;
        mUpdateLabel = null;
    }

    private void swapCurrencies() {
        final int tmp = mFromIndex;
        mFromIndex = mToIndex;
        mToIndex = tmp;
        updateCurrencyLabels();
        redisplay();
    }

    private void configureCurrencyDropdown(@Nullable MaterialAutoCompleteTextView dropdown,
            boolean isFrom, @NonNull Context context) {
        if (dropdown == null) {
            return;
        }
        dropdown.setAdapter(new ArrayAdapter<>(
                context, R.layout.tool_dropdown_item, currencyMenuLabels()));
        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= mCurrencies.size()) {
                return;
            }
            if (isFrom) {
                mFromIndex = position;
            } else {
                mToIndex = position;
            }
            updateCurrencyLabels();
            redisplay();
        });
        dropdown.setOnClickListener(v ->
                ((MaterialAutoCompleteTextView) v).showDropDown());
    }

    private void updateCurrencyLabels() {
        if (mFromView != null && !mCurrencies.isEmpty()) {
            mFromView.setText(mCurrencies.get(clamp(mFromIndex)).shortLabel(), false);
        }
        if (mToView != null && !mCurrencies.isEmpty()) {
            mToView.setText(mCurrencies.get(clamp(mToIndex)).shortLabel(), false);
        }
    }

    // ---- computation & display ----

    private void redisplay() {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        if (mCurrencies.isEmpty()) {
            host.setToolFormula("");
            host.setToolResult(host.getContext().getString(R.string.tool_currency_no_data));
            return;
        }
        final CurrencyDef from = mCurrencies.get(clamp(mFromIndex));
        final CurrencyDef to = mCurrencies.get(clamp(mToIndex));
        final BigDecimal value = parseInput();

        final String resultText;
        if (value == null || mRates == null) {
            resultText = "—";
        } else {
            final BigDecimal fromRate = mRates.rateFor(from.getId());
            final BigDecimal toRate = mRates.rateFor(to.getId());
            final BigDecimal out = CurrencyConversion.convert(value, fromRate, toRate, MC);
            resultText = (out == null ? "—" : format(out));
        }
        // Update the inline number+unit display in the control slot (not the big display lines).
        if (mInputView != null) {
            mInputView.setText(displayInput());
        }
        if (mResultView != null) {
            mResultView.setText(resultText);
        }
        // Keep the big display lines empty — the conversion with tappable units is in the slot.
        host.setToolFormula("");
        host.setToolResult("");
    }

    @Nullable
    private BigDecimal parseInput() {
        if (mInput.length() == 0) {
            return null;
        }
        String text = mInput.toString();
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
            if (text.isEmpty()) {
                return null;
            }
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private String displayInput() {
        return mInput.length() == 0 ? "0" : mInput.toString();
    }

    @NonNull
    private static String format(@NonNull BigDecimal value) {
        return new java.text.DecimalFormat("#,##0.00##")
                .format(value.setScale(4, RoundingMode.HALF_UP));
    }

    // ---- helpers ----

    private int indexOfCode(@NonNull String code) {
        for (int i = 0; i < mCurrencies.size(); i++) {
            if (code.equals(mCurrencies.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private int clamp(int index) {
        if (mCurrencies.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(index, mCurrencies.size() - 1));
    }

    @NonNull
    private List<String> currencyMenuLabels() {
        final List<String> labels = new ArrayList<>();
        for (CurrencyDef currency : mCurrencies) {
            labels.add(currency.shortLabel() + " · " + currency.getName());
        }
        return labels;
    }
}
