/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Selection;
import android.text.Spannable;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolInputFields;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.model.ChineseNumerals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Base for tools with several numeric inputs (mortgage, tax, BMI). The calculator's existing
 * numeric pad drives the currently active field; the user taps a field to make it active.
 * <p>
 * Field TextViews in the control layout use the ids {@code tool_field_0}, {@code tool_field_1}, ...
 * The active field is indicated by bold + primary-colored text. Subclasses supply the layout, the
 * labels/defaults, any extra controls (via {@link #onViewCreated}), and the
 * {@link #recomputeAndDisplay} hook.
 */
public abstract class FieldToolMode implements ToolMode {

    /** Compact result-line text size (sp) fallback if the dimen is missing. */
    private static final float TOOL_RESULT_TEXT_SP = 14f;
    /** Long 人民币大写 amounts need a lower formula-line floor to remain fully visible. */
    private static final float UPPERCASE_FORMULA_MAX_SP = 20f;
    private static final float UPPERCASE_FORMULA_MIN_SP = 10f;

    @Nullable
    protected ToolHost mHost;
    @Nullable
    protected ToolInputFields mFields;
    @Nullable
    private TextView[] mFieldViews;
    @Nullable
    private View mControlRoot;

    private int mActiveColor;
    private int mInactiveColor;

    /** Render monetary results as Chinese uppercase numerals (中文大写数字). */
    private boolean mUppercaseNumbers;

    protected abstract int fieldCount();

    @NonNull
    protected abstract String[] fieldLabels();

    @NonNull
    protected abstract String[] fieldDefaults();

    @LayoutRes
    protected abstract int controlLayoutRes();

    @Override
    public boolean wantsExpandedDisplay() {
        return true;
    }

    @Override
    public void setUppercaseNumbers(boolean enabled) {
        if (mUppercaseNumbers == enabled) {
            return;
        }
        mUppercaseNumbers = enabled;
        configureFormulaTextSize();
        recomputeAndDisplay();
    }

    /** Called after the control view is created, for extra controls (e.g. a method toggle). */
    protected void onViewCreated(@NonNull View root, @NonNull Context context) {
    }

    /** Recompute outputs from the current inputs and update the shared display. */
    protected abstract void recomputeAndDisplay();

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        mHost = host;
        final Context context = host.getContext();
        mActiveColor = resolveThemeColor(context, android.R.attr.colorPrimary, Color.BLACK);
        // Inactive fields need a concrete color; textColorPrimary is a ColorStateList whose
        // resource id would render invisibly, so use a flat color resource instead.
        mInactiveColor = ContextCompat.getColor(context, R.color.tool_field_text_color);
        if (mFields == null) {
            mFields = new ToolInputFields(fieldCount(), fieldDefaults());
        }
        if (carryValue != null) {
            mFields.set(0, carryValue);
        }
        // Multi-field results are long; use a compact result text size so they fit.
        // Prefer the resource so tablets/foldables can use a slightly larger (but still
        // compact) size than phones.
        final float px = context.getResources().getDimension(R.dimen.tool_result_textsize);
        final float sp = px / context.getResources().getDisplayMetrics().scaledDensity;
        host.setToolResultTextSizeSp(sp > 0f ? sp : TOOL_RESULT_TEXT_SP);
        configureFormulaTextSize();
        mountControls(context);
        recomputeAndDisplay();
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
        unmountControls(host);
        mHost = null;
    }

    @Override
    public void onDigit(int digit) {
        if (mFields != null) {
            mFields.onDigit(digit);
            afterInput();
        }
    }

    @Override
    public void onDecimalPoint() {
        if (mFields != null) {
            mFields.onDecimalPoint();
            afterInput();
        }
    }

    @Override
    public void onDelete() {
        if (mFields != null) {
            mFields.onDelete();
            afterInput();
        }
    }

    @Override
    public void onClear() {
        if (mFields != null) {
            mFields.onClear();
            afterInput();
        }
    }

    private void afterInput() {
        refreshFields();
        recomputeAndDisplay();
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
        mControlRoot = LayoutInflater.from(context).inflate(controlLayoutRes(), slot, false);
        mFieldViews = new TextView[fieldCount()];
        // Reference the field ids directly (declared via @+id in the control layouts). Avoid
        // getIdentifier(), which fails under the debug applicationIdSuffix (package-name mismatch).
        final int[] fieldIds = {R.id.tool_field_0, R.id.tool_field_1, R.id.tool_field_2,
                R.id.tool_field_3, R.id.tool_field_4};
        for (int i = 0; i < fieldCount(); i++) {
            final TextView view = mControlRoot.findViewById(fieldIds[i]);
            mFieldViews[i] = view;
            if (view != null) {
                final int index = i;
                view.setOnClickListener(v -> {
                    if (mFields != null) {
                        mFields.setActive(index);
                        // Place the text cursor on the activated input field so input position
                        // is visible during entry (cursorVisible + focusableInTouchMode).
                        view.requestFocus();
                        refreshFields();
                        recomputeAndDisplay();
                    }
                });
                // Cursor + touch selection are on (see ToolFieldStyle); enrich the selection
                // action mode with copy / select-all / 中文大写数字 conversion.
                view.setCustomSelectionActionModeCallback(new SelectionActions(view));
            }
        }
        onViewCreated(mControlRoot, context);
        refreshFields();

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
        mFieldViews = null;
    }

    protected void refreshFields() {
        if (mFields == null || mFieldViews == null) {
            return;
        }
        final String[] labels = fieldLabels();
        final int active = mFields.getActive();
        for (int i = 0; i < mFieldViews.length; i++) {
            final TextView view = mFieldViews[i];
            if (view == null) {
                continue;
            }
            final String label = i < labels.length ? labels[i] : "";
            final String value = mFields.get(i);
            final boolean isActive = i == active;
            view.setText(label + (value.isEmpty() ? "" : ": " + value));
            view.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            view.setTextColor(isActive ? mActiveColor : mInactiveColor);
        }
    }

    /** findViewById on the control root, for subclasses. */
    @Nullable
    protected <T extends View> T find(@IdRes int id) {
        return mControlRoot == null ? null : mControlRoot.findViewById(id);
    }

    /** Format a monetary amount with grouping; drops trailing ".00" for whole amounts. */
    @NonNull
    protected static String money(@Nullable BigDecimal value) {
        if (value == null) {
            return "—";
        }
        return new DecimalFormat("#,##0.##")
                .format(value.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Same as {@link #money} while 中文大写数字 is off, and the 人民币大写 form of the amount
     * while it is on. All calculated monetary outputs use this, including the primary value on
     * the formula line (月供、首月、年度税额).
     */
    @NonNull
    protected String moneyUpper(@Nullable BigDecimal value) {
        if (!mUppercaseNumbers) {
            return money(value);
        }
        return ChineseNumerals.toUppercaseAmount(value);
    }

    /** Keep a long 大写 primary amount on-screen, then restore the normal tool range when off. */
    private void configureFormulaTextSize() {
        if (mHost == null) {
            return;
        }
        if (mUppercaseNumbers) {
            mHost.setToolFormulaTextSizeRangeSp(
                    UPPERCASE_FORMULA_MAX_SP, UPPERCASE_FORMULA_MIN_SP);
            return;
        }
        final Context context = mHost.getContext();
        final float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        final float maxSp = context.getResources().getDimension(
                R.dimen.tool_formula_max_textsize) / scaledDensity;
        final float minSp = context.getResources().getDimension(
                R.dimen.formula_min_textsize) / scaledDensity;
        mHost.setToolFormulaTextSizeRangeSp(maxSp, minSp);
    }

    private static int resolveThemeColor(@NonNull Context context, int attr, int fallback) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return fallback;
    }

    /**
     * Selection action mode for tool input fields: keeps copy and select-all, and adds the
     * conversion of the selected digits into 中文大写数字 (Chinese financial numerals).
     */
    private final class SelectionActions implements ActionMode.Callback {
        private final TextView mView;

        SelectionActions(TextView view) {
            mView = view;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            menu.add(0, R.id.action_copy_selection, 0, android.R.string.copy);
            menu.add(0, R.id.action_select_all, 1, android.R.string.selectAll);
            menu.add(0, R.id.action_to_uppercase, 2, R.string.tool_field_to_uppercase);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            final CharSequence text = mView.getText();
            int start = mView.getSelectionStart();
            int end = mView.getSelectionEnd();
            if (start < 0 || end < 0 || text == null) {
                start = 0;
                end = text == null ? 0 : text.length();
            }
            if (start > end) {
                final int swap = start;
                start = end;
                end = swap;
            }
            final CharSequence selected = text.subSequence(start, end);
            final int id = item.getItemId();
            if (id == R.id.action_copy_selection) {
                final ClipboardManager clipboard = (ClipboardManager) mView.getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("calculator", selected));
                }
                mode.finish();
                return true;
            }
            if (id == R.id.action_select_all) {
                Selection.selectAll((Spannable) text);
                return true;
            }
            if (id == R.id.action_to_uppercase) {
                showUppercaseDialog(mView.getContext(), selected.toString());
                mode.finish();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }

    /** Shows the 中文大写数字 form of the selected digits, with a copy shortcut. */
    private static void showUppercaseDialog(@NonNull Context context, @NonNull String selected) {
        final String uppercase = ChineseNumerals.toUppercase(selected);
        final String message = uppercase == null || uppercase.isEmpty() ? "—" : uppercase;
        new AlertDialog.Builder(context)
                .setTitle(R.string.tool_field_uppercase_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.copy, (dialog, which) -> {
                    final ClipboardManager clipboard = (ClipboardManager) context
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("calculator", message));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
