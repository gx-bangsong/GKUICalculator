/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.os.Bundle;
import com.android.calculator2.tools.ToolState;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.EditText;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.data.CachedRates;
import com.android.calculator2.tools.data.CurrencyDef;
import com.android.calculator2.tools.data.ExchangeRateRepository;
import com.android.calculator2.tools.model.ChineseNumerals;
import com.android.calculator2.tools.model.CurrencyConversion;
import com.android.calculator2.tools.model.DecimalExpression;
import com.android.calculator2.tools.model.DecimalExpression.Operation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Currency conversion using ECB reference rates (EUR base) with on-disk caching and offline
 * fallback. Mirrors the data source of {@code com.yangdai.calc}, extended with caching/offline.
 * <p>
 * Input and output values, selectors, and the optional second output are mounted into the host's
 * {@link ToolHost#getToolControlSlot()}. Chinese-uppercase mode replaces the amounts in that same
 * value column instead of drawing a separate result beneath the rows.
 */
public class CurrencyConverterMode implements ToolMode {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static final MathContext DISPLAY_MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final String DEFAULT_INPUT = "1";
    private static final String PREFS_NAME = "calc_tools";
    private static final String PREF_SECONDARY = "currency_secondary_output";
    private static final float NUMBER_TEXT_SIZE_SP = 28f;
    private static final float UPPERCASE_TEXT_SIZE_SP = 16f;

    private static final int SELECTOR_FROM = 0;
    private static final int SELECTOR_TO = 1;
    private static final int SELECTOR_TO_SECONDARY = 2;

    @Nullable
    private ExchangeRateRepository mRepository;
    @NonNull
    private List<CurrencyDef> mCurrencies = Collections.emptyList();
    @Nullable
    private CachedRates mRates;

    private int mFromIndex;
    private int mToIndex;
    private int mToSecondaryIndex;
    private boolean mSecondaryEnabled;
    private boolean mUppercaseNumbers;
    private int mPendingCaret = -1;
    private boolean mEditingInput;

    private final DecimalExpression mInput = new DecimalExpression(DEFAULT_INPUT);

    @Nullable
    private ToolHost mHost;
    @Nullable
    private View mControlRoot;
    @Nullable
    private TextView mFromView;
    @Nullable
    private TextView mToView;
    @Nullable
    private TextView mInputView;
    @Nullable
    private TextView mResultView;
    @Nullable
    private TextView mUpdateLabel;
    @Nullable
    private View mSecondaryRow;
    @Nullable
    private TextView mToSecondaryView;
    @Nullable
    private TextView mSecondaryResultView;
    @Nullable
    private PopupMenu mOpenDropdown;

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
        return R.drawable.ic_tool_currency;
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
    public boolean supportsSecondaryOutput() {
        return true;
    }

    @Override
    public boolean isSecondaryOutputEnabled() {
        return mSecondaryEnabled;
    }

    @Override
    public void setSecondaryOutputEnabled(boolean enabled) {
        mSecondaryEnabled = enabled;
        if (mHost != null) {
            mHost.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_SECONDARY, enabled).apply();
        }
        updateSecondaryVisibility();
        redisplay();
    }

    @Override
    public boolean supportsUppercaseNumbers() {
        return true;
    }

    @Override
    public void setUppercaseNumbers(boolean enabled) {
        if (mUppercaseNumbers == enabled) {
            return;
        }
        mUppercaseNumbers = enabled;
        redisplay();
    }

    @Override public Bundle saveState() {
        Bundle state=new Bundle();state.putStringArray("input",mInput.saveEditingState());
        state.putInt("from",mFromIndex);state.putInt("to",mToIndex);state.putInt("secondary",mToSecondaryIndex);
        state.putBoolean("secondaryEnabled",mSecondaryEnabled);
        state.putBundle("selection",ToolState.selection(mInputView));
        state.putBoolean("uppercase",mUppercaseNumbers);state.putBoolean("editing",mEditingInput);
        if(mRates!=null) {
            Bundle rates=new Bundle();
            for(java.util.Map.Entry<String,BigDecimal> rate:mRates.getRates().entrySet())
                rates.putString(rate.getKey(),rate.getValue().toPlainString());
            state.putBundle("rates",rates);state.putString("base",mRates.getBaseCurrency());
            state.putString("published",mRates.getPublishDate());state.putLong("fetched",mRates.getFetchedAt());
        }
        return state;
    }
    @Override public void onRestore(ToolHost host,Bundle state) { activate(host,null,state); }
    @Override public void onActivate(ToolHost host,String carryValue) { activate(host,carryValue,null); }
    private void activate(ToolHost host,String carryValue,Bundle state) {
        mHost = host;
        final Context context = host.getContext();
        if (mRepository == null) {
            mRepository = new ExchangeRateRepository(context);
        }
        mCurrencies = mRepository.getConfig().getCurrencies();
        if(state==null) {
        mRates = null;

        mEditingInput = false;
        mPendingCaret = -1;
        mInput.set(carryValue != null ? carryValue : DEFAULT_INPUT);

        // Sensible defaults: CNY -> USD when both exist, otherwise the first two.
        int cny = indexOfCode("CNY");
        int usd = indexOfCode("USD");
        int jpy = indexOfCode("JPY");
        mFromIndex = cny >= 0 ? cny : 0;
        mToIndex = usd >= 0 ? usd : Math.min(1, Math.max(0, mCurrencies.size() - 1));
        mToSecondaryIndex = jpy >= 0 ? jpy
                : Math.min(2, Math.max(0, mCurrencies.size() - 1));
        if (mFromIndex == mToIndex && mCurrencies.size() > 1) {
            mToIndex = (mFromIndex + 1) % mCurrencies.size();
        }
        mSecondaryEnabled = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_SECONDARY, false);

        } else {
            mPendingCaret=-1;mEditingInput=state.getBoolean("editing");mUppercaseNumbers=state.getBoolean("uppercase");
            mInput.restoreEditingState(state.getStringArray("input"));
            mFromIndex=state.getInt("from");mToIndex=state.getInt("to");
            mToSecondaryIndex=state.getInt("secondary");mSecondaryEnabled=state.getBoolean("secondaryEnabled");
            Bundle rates=state.getBundle("rates");
            if(rates!=null) {
                java.util.Map<String,BigDecimal> values=new java.util.LinkedHashMap<>();
                for(String code:rates.keySet()) values.put(code,new BigDecimal(rates.getString(code)));
                mRates=new CachedRates(state.getString("base"),state.getLong("fetched"),state.getString("published"),values);
            }
        }

        mountControls(context);
        redisplay();
        if(state!=null) ToolState.restoreSelection(mInputView,state.getBundle("selection"));

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
    public boolean onPadKey(int viewId) {
        Operation operation;
        if (viewId == R.id.op_add) operation = Operation.ADD;
        else if (viewId == R.id.op_sub) operation = Operation.SUBTRACT;
        else if (viewId == R.id.op_mul) operation = Operation.MULTIPLY;
        else if (viewId == R.id.op_div) operation = Operation.DIVIDE;
        else if (viewId == R.id.eq) {
            mInput.onEquals();
            mPendingCaret = mInput.getDisplayText().length();
            redisplay();
            return true;
        } else return false;
        int edited = mInput.insertOperator(operation, selectionStart(), selectionEnd());
        if (edited < 0) return true;
        mPendingCaret = edited;
        redisplay();
        return true;
    }

    @Override
    public void onDigit(int digit) {
        mPendingCaret = mInput.insertDigit(digit, selectionStart(), selectionEnd());
        redisplay();
    }

    @Override
    public void onDecimalPoint() {
        mPendingCaret = mInput.insertDecimalPoint(selectionStart(), selectionEnd());
        redisplay();
    }

    @Override
    public void onDelete() {
        mPendingCaret = mInput.deleteAt(selectionStart(), selectionEnd());
        redisplay();
    }

    @Override
    public void onClear() {
        mInput.onClear();
        mPendingCaret = 0;
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
        // A rate delivery changes outputs, not the expression or its native editor state.
        // Re-applying setSelection here can request a caret rectangle and pull a short
        // scrolled window back toward the input, even when its text/selection is unchanged.
        redisplay(false);
    }

    private int selectionStart() {
        if (mInputView == null || (mUppercaseNumbers && !mEditingInput)
                || mInputView.getSelectionStart() < 0) return mInput.getDisplayText().length();
        return Math.min(mInputView.getSelectionStart(), mInputView.getSelectionEnd());
    }

    private int selectionEnd() {
        if (mInputView == null || (mUppercaseNumbers && !mEditingInput)
                || mInputView.getSelectionEnd() < 0) return mInput.getDisplayText().length();
        return Math.max(mInputView.getSelectionStart(), mInputView.getSelectionEnd());
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
        mSecondaryRow = mControlRoot.findViewById(R.id.currency_secondary_row);
        mToSecondaryView = mControlRoot.findViewById(R.id.currency_to_2);
        mSecondaryResultView = mControlRoot.findViewById(R.id.currency_result_2_text);
        if (mInputView != null) {
            mInputView.setShowSoftInputOnFocus(false);
            mInputView.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !mEditingInput) {
                    // Financial Chinese is presentation, not expression offsets. Return to the
                    // canonical numeric expression before the framework hit-tests the touch.
                    mEditingInput = true;
                    redisplay();
                }
                return false;
            });
        }
        final ImageButton swap = mControlRoot.findViewById(R.id.currency_swap);
        swap.setOnClickListener(v -> swapCurrencies());
        configureCurrencyDropdown(mFromView, SELECTOR_FROM);
        configureCurrencyDropdown(mToView, SELECTOR_TO);
        configureCurrencyDropdown(mToSecondaryView, SELECTOR_TO_SECONDARY);
        updateCurrencyLabels();
        updateSecondaryVisibility();

        if (mUpdateLabel != null) {
            mUpdateLabel.setText(R.string.currency_loading);
        }

        slot.removeAllViews();
        slot.addView(mControlRoot);
        slot.setVisibility(View.VISIBLE);
    }

    private void unmountControls(@NonNull ToolHost host) {
        if (mOpenDropdown != null) {
            mOpenDropdown.dismiss();
            mOpenDropdown = null;
        }
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
        mSecondaryRow = null;
        mToSecondaryView = null;
        mSecondaryResultView = null;
    }

    private void swapCurrencies() {
        final int tmp = mFromIndex;
        mFromIndex = mToIndex;
        mToIndex = tmp;
        updateCurrencyLabels();
        redisplay();
    }

    private void configureCurrencyDropdown(@Nullable TextView dropdown, int selector) {
        if (dropdown != null) {
            dropdown.setOnClickListener(v -> showCurrencyDropdown(dropdown, selector));
        }
    }

    private void showCurrencyDropdown(@NonNull TextView anchor, int selector) {
        if (mCurrencies.isEmpty() || mOpenDropdown != null) {
            return;
        }
        final PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        mOpenDropdown = popup;
        popup.setOnDismissListener(dismissed -> {
            if (mOpenDropdown == dismissed) {
                mOpenDropdown = null;
            }
        });
        final List<String> labels = currencyMenuLabels();
        for (int i = 0; i < labels.size(); i++) {
            // The selected currency is already shown by the anchor; no checkbox is needed.
            popup.getMenu().add(Menu.NONE, i + 1, i, labels.get(i));
        }
        popup.setOnMenuItemClickListener(item -> {
            final int position = item.getItemId() - 1;
            if (position < 0 || position >= mCurrencies.size()) {
                return false;
            }
            if (selector == SELECTOR_FROM) {
                mFromIndex = position;
            } else if (selector == SELECTOR_TO) {
                mToIndex = position;
            } else {
                mToSecondaryIndex = position;
            }
            updateCurrencyLabels();
            redisplay();
            return true;
        });
        popup.show();
    }

    private void updateCurrencyLabels() {
        if (mFromView != null && !mCurrencies.isEmpty()) {
            mFromView.setText(mCurrencies.get(clamp(mFromIndex)).shortLabel());
        }
        if (mToView != null && !mCurrencies.isEmpty()) {
            mToView.setText(mCurrencies.get(clamp(mToIndex)).shortLabel());
        }
        if (mToSecondaryView != null && !mCurrencies.isEmpty()) {
            mToSecondaryView.setText(
                    mCurrencies.get(clamp(mToSecondaryIndex)).shortLabel());
        }
    }

    private void updateSecondaryVisibility() {
        if (mSecondaryRow != null) {
            mSecondaryRow.setVisibility(mSecondaryEnabled ? View.VISIBLE : View.GONE);
        }
    }

    // ---- computation & display ----

    private void redisplay() {
        redisplay(true);
    }

    private void redisplay(boolean updateInput) {
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
        final CurrencyDef toSecondary = mCurrencies.get(clamp(mToSecondaryIndex));
        final BigDecimal value = mInput.getValue();

        final String resultText;
        final String secondaryResultText;
        if (value == null || mRates == null) {
            resultText = "—";
            secondaryResultText = "—";
        } else {
            final BigDecimal fromRate = mRates.rateFor(from.getId());
            final BigDecimal toRate = mRates.rateFor(to.getId());
            final BigDecimal secondaryRate = mRates.rateFor(toSecondary.getId());
            final BigDecimal out = CurrencyConversion.convert(value, fromRate, toRate, MC);
            final BigDecimal secondaryOut = CurrencyConversion.convert(
                    value, fromRate, secondaryRate, MC);
            resultText = (out == null ? "—" : format(out));
            secondaryResultText = secondaryOut == null ? "—" : format(secondaryOut);
        }
        // 大写 replaces the three amounts in the existing left-hand value column (the area next
        // to the currency selectors). It never uses the shared display below the rows, so showing
        // the optional second output cannot collide with a separate 大写 area.
        final String inputValueText = mInput.getValueText();
        final int start = mPendingCaret >= 0 ? mPendingCaret : selectionStart();
        final int end = mPendingCaret >= 0 ? mPendingCaret : selectionEnd();
        if (updateInput && mInputView != null) {
            final String shown = mUppercaseNumbers && !mEditingInput
                    ? (inputValueText == null ? "—" : ChineseNumerals.toUppercase(inputValueText))
                    : mInput.getDisplayText();
            if (!shown.contentEquals(mInputView.getText())) mInputView.setText(shown);
            mInputView.setTextSize(mUppercaseNumbers && !mEditingInput
                    ? UPPERCASE_TEXT_SIZE_SP : NUMBER_TEXT_SIZE_SP);
            ((EditText) mInputView).setSelection(Math.min(start, shown.length()),
                    Math.min(end, shown.length()));
        }
        if (updateInput) mPendingCaret = -1;
        setAmountText(mResultView, resultText, ChineseNumerals.toUppercase(resultText));
        setAmountText(mSecondaryResultView, secondaryResultText,
                ChineseNumerals.toUppercase(secondaryResultText));

        host.setToolFormula("");
        host.setToolResult("");
    }

    private void setAmountText(@Nullable TextView view, @NonNull String number,
            @NonNull String uppercase) {
        if (view == null) {
            return;
        }
        final String shown = mUppercaseNumbers ? uppercase : number;
        if (!shown.contentEquals(view.getText())) view.setText(shown);
        view.setTextSize(mUppercaseNumbers ? UPPERCASE_TEXT_SIZE_SP : NUMBER_TEXT_SIZE_SP);
        // Long 大写 readings wrap inside their own row; ordinary numbers stay on one line.
        view.setSingleLine(!mUppercaseNumbers);
        view.setHorizontallyScrolling(!mUppercaseNumbers);
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
            labels.add(currency.shortLabel() + " · " + localizedName(currency));
        }
        return labels;
    }

    /**
     * Returns the localized currency name when the resources provide an entry whose key is
     * "currency_name_" followed by the lower-case ISO code, and otherwise the name that is
     * shipped with the configuration.
     */
    @NonNull
    private String localizedName(@NonNull CurrencyDef currency) {
        final Context context = mHost == null ? null : mHost.getContext();
        if (context == null) {
            return currency.getName();
        }
        final String key = "currency_name_" + currency.getId().toLowerCase(Locale.US);
        final int resId = context.getResources()
                .getIdentifier(key, "string", context.getPackageName());
        return resId == 0 ? currency.getName() : context.getString(resId);
    }
}
