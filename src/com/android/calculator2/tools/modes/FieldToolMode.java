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
import android.util.Log;
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
import com.android.calculator2.tools.ToolFieldEditText;
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
 * <p>
 * Cursor position is tracked per field via {@link ToolFieldEditText.onSelectionChanged}, which
 * reports the actual selection as observed by the Android framework. This avoids the race
 * condition where OnClickListener/OnTouchListener reads a stale selection before EditText has
 * updated it.
 */
public abstract class FieldToolMode implements ToolMode {

    private static final String TAG = "FieldToolMode";
    private static final boolean DEBUG_LOG = true;

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
    private ToolFieldEditText[] mFieldViews;
    @Nullable
    private View mControlRoot;

    private int mActiveColor;
    private int mInactiveColor;

    /** Render monetary results as Chinese uppercase numerals (中文大写数字). */
    private boolean mUppercaseNumbers;

    /** Per-field cursor position within the value text (excluding label prefix). -1 = end. */
    int[] mCursorPos;
    /** Per-field selection start within the value text (excluding label prefix). -1 = unknown. */
    private int[] mSelectionStart;
    /** Per-field selection end within the value text (excluding label prefix). -1 = unknown. */
    private int[] mSelectionEnd;

    /** Set to true while the application is programmatically calling setText()/setSelection()
         on the EditText, so that selection listeners do not overwrite cached state. */
    private boolean mProgrammaticUpdate = false;

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
        mInactiveColor = ContextCompat.getColor(context, R.color.tool_field_text_color);
        if (mFields == null) {
            mFields = new ToolInputFields(fieldCount(), fieldDefaults());
        }
        if (carryValue != null) {
            mFields.set(0, carryValue);
        }
        final int count = fieldCount();
        mCursorPos = new int[count];
        mSelectionStart = new int[count];
        mSelectionEnd = new int[count];
        for (int i = 0; i < count; i++) {
            mCursorPos[i] = -1;
            mSelectionStart[i] = -1;
            mSelectionEnd[i] = -1;
        }
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
        mCursorPos = null;
        mSelectionStart = null;
        mSelectionEnd = null;
    }

    @Override
    public void onDigit(int digit) {
        if (mFields == null) {
            return;
        }
        final int active = mFields.getActive();
        final ToolFieldEditText view = mFieldViews == null ? null : mFieldViews[active];
        if (view != null) {
            applyDigitFromView(view, active, digit);
        } else {
            mFields.onDigit(digit, -1);
            afterInput();
        }
    }

    @Override
    public void onDecimalPoint() {
        if (mFields == null) {
            return;
        }
        final int active = mFields.getActive();
        final ToolFieldEditText view = mFieldViews == null ? null : mFieldViews[active];
        if (view != null) {
            applyDecimalFromView(view, active);
        } else {
            mFields.onDecimalPoint(-1);
            afterInput();
        }
    }

    @Override
    public void onDelete() {
        if (mFields == null) {
            return;
        }
        final int active = mFields.getActive();
        final ToolFieldEditText view = mFieldViews == null ? null : mFieldViews[active];
        if (view != null) {
            applyDeleteFromView(view, active);
        } else {
            final String value = mFields.get(active);
            if (value.length() > 0) {
                mFields.onDelete(-1);
            }
            afterInput();
        }
    }

    @Override
    public void onClear() {
        if (mFields != null) {
            mFields.onClear();
            final int active = mFields.getActive();
            if (mCursorPos != null) mCursorPos[active] = -1;
            if (mSelectionStart != null) mSelectionStart[active] = 0;
            if (mSelectionEnd != null) mSelectionEnd[active] = 0;
            afterInput();
        }
    }

    /** Apply a digit key press based on the actual selection in the EditText. */
    private void applyDigitFromView(ToolFieldEditText view, int active, int digit) {
        final String label = fieldLabels()[active];
        final int valueStart = label.length() + 2; // ": "
        final String value = mFields.get(active);
        final int valLen = value.length();
        final int selStart = view.getSelectionStart();
        final int selEnd = view.getSelectionEnd();

        if (DEBUG_LOG) {
            Log.d(TAG, String.format("onDigit: active=%d digit=%d selStart=%d selEnd=%d valueStart=%d value='%s'",
                    active, digit, selStart, selEnd, valueStart, value));
        }

        // Clamp selection to the value range (exclude label prefix).
        int selStartVal = clampToValue(selStart, valueStart, valLen);
        int selEndVal = clampToValue(selEnd, valueStart, valLen);
        if (selStartVal > selEndVal) {
            int tmp = selStartVal;
            selStartVal = selEndVal;
            selEndVal = tmp;
        }

        int newCursorPosVal;
        if (selStartVal == selEndVal) {
            // Collapsed cursor: insert at current position.
            int insertPos = (selStartVal >= 0 && selStartVal <= valLen) ? selStartVal : -1;
            mFields.onDigit(digit, insertPos);
            newCursorPosVal = (insertPos >= 0) ? insertPos + 1 : -1;
        } else {
            // Range selected: replace it with the digit.
            mFields.onDigitReplace(digit, selStartVal, selEndVal);
            newCursorPosVal = selStartVal + 1;
        }

        cacheCursorPos(active, newCursorPosVal, mFields.get(active).length());
        afterInput();
    }

    /** Apply a decimal point based on the actual selection in the EditText. */
    private void applyDecimalFromView(ToolFieldEditText view, int active) {
        final String label = fieldLabels()[active];
        final int valueStart = label.length() + 2; // ": "
        final String valueBefore = mFields.get(active);
        final int valLen = valueBefore.length();
        final int selStart = view.getSelectionStart();
        final int selEnd = view.getSelectionEnd();

        if (DEBUG_LOG) {
            Log.d(TAG, String.format("onDecimalPoint: active=%d selStart=%d selEnd=%d valueStart=%d value='%s'",
                    active, selStart, selEnd, valueStart, valueBefore));
        }

        // Clamp selection to the value range (exclude label prefix).
        int selStartVal = clampToValue(selStart, valueStart, valLen);
        int selEndVal = clampToValue(selEnd, valueStart, valLen);
        if (selStartVal > selEndVal) {
            int tmp = selStartVal;
            selStartVal = selEndVal;
            selEndVal = tmp;
        }

        int newCursorPosVal;
        if (selStartVal == selEndVal) {
            // Collapsed cursor: insert decimal at current position.
            int insertPos = (selStartVal >= 0 && selStartVal <= valLen) ? selStartVal : -1;
            int beforeLen = valueBefore.length();
            mFields.onDecimalPoint(insertPos);
            int afterLen = mFields.get(active).length();
            if (afterLen > beforeLen) {
                // Decimal was actually inserted. If "0." was prepended to empty field, advance by 2.
                newCursorPosVal = (insertPos == 0 && beforeLen == 0) ? 2 : insertPos + 1;
            } else {
                // Decimal was ignored (already present): cursor stays.
                newCursorPosVal = selStartVal;
            }
        } else {
            // Range selected: replace it with a decimal point.
            final StringBuilder f = new StringBuilder(valueBefore);
            f.replace(selStartVal, selEndVal, ".");
            mFields.set(active, f.toString());
            newCursorPosVal = selStartVal + 1;
        }

        cacheCursorPos(active, newCursorPosVal, mFields.get(active).length());
        afterInput();
    }

    /** Apply a delete key press based on the actual selection in the EditText. */
    private void applyDeleteFromView(ToolFieldEditText view, int active) {
        final String label = fieldLabels()[active];
        final int valueStart = label.length() + 2; // ": "
        final String value = mFields.get(active);
        final int valLen = value.length();
        final int selStart = view.getSelectionStart();
        final int selEnd = view.getSelectionEnd();

        if (DEBUG_LOG) {
            Log.d(TAG, String.format("onDelete: active=%d selStart=%d selEnd=%d valueStart=%d value='%s'",
                    active, selStart, selEnd, valueStart, value));
        }

        if (valLen == 0) {
            afterInput();
            return;
        }

        // Clamp selection to the value range (exclude label prefix).
        int selStartVal = clampToValue(selStart, valueStart, valLen);
        int selEndVal = clampToValue(selEnd, valueStart, valLen);
        if (selStartVal > selEndVal) {
            int tmp = selStartVal;
            selStartVal = selEndVal;
            selEndVal = tmp;
        }

        int newCursorPosVal;
        if (selStartVal == selEndVal) {
            // Collapsed cursor: delete character before cursor if position > 0.
            // Position 0: nothing to delete.
            if (selStartVal > 0 && selStartVal <= valLen) {
                mFields.onDelete(selStartVal);
                newCursorPosVal = selStartVal - 1;
            } else {
                // Position 0: nothing to delete.
                newCursorPosVal = selStartVal;
            }
        } else {
            // Range selected: delete the selected range.
            final StringBuilder f = new StringBuilder(value);
            f.delete(selStartVal, selEndVal);
            mFields.set(active, f.toString());
            newCursorPosVal = selStartVal;
        }

        cacheCursorPos(active, newCursorPosVal, mFields.get(active).length());
        afterInput();
    }

    /** Clamp a selection position to the valid value range [0, valLen]. */
    private static int clampToValue(int selPos, int valueStart, int valLen) {
        int pos = selPos - valueStart;
        if (pos < 0) return 0;
        if (pos > valLen) return valLen;
        return pos;
    }

    /** Cache the cursor position for the active field, clamped to the value length. */
    private void cacheCursorPos(int active, int pos, int valLen) {
        if (pos < 0) pos = 0;
        if (pos > valLen) pos = valLen;
        if (mCursorPos != null) mCursorPos[active] = pos;
        if (mSelectionStart != null) mSelectionStart[active] = pos;
        if (mSelectionEnd != null) mSelectionEnd[active] = pos;
        if (DEBUG_LOG) {
            Log.d(TAG, String.format("cacheCursorPos: active=%d pos=%d valLen=%d", active, pos, valLen));
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
        final int count = fieldCount();
        mFieldViews = new ToolFieldEditText[count];
        final int[] fieldIds = {R.id.tool_field_0, R.id.tool_field_1, R.id.tool_field_2,
                R.id.tool_field_3, R.id.tool_field_4};
        for (int i = 0; i < count; i++) {
            final ToolFieldEditText view = mControlRoot.findViewById(fieldIds[i]);
            mFieldViews[i] = view;
            if (view != null) {
                view.setShowSoftInputOnFocus(false);
                final int index = i;

                // Report selection changes from user touch to FieldToolMode.
                // This is the authoritative source of cursor position: EditText calls
                // onSelectionChanged after the framework has updated the selection.
                view.setSelectionChangeListener((v, selStart, selEnd) -> {
                    if (mProgrammaticUpdate || mFields == null) {
                        return;
                    }
                    final String label = fieldLabels()[index];
                    final int valueStart = label.length() + 2;
                    int startVal = clampToValue(selStart, valueStart, mFields.get(index).length());
                    int endVal = clampToValue(selEnd, valueStart, mFields.get(index).length());
                    if (DEBUG_LOG) {
                        Log.d(TAG, String.format("onSelectionChanged: field=%d selStart=%d selEnd=%d valueStart=%d -> startVal=%d endVal=%d",
                                index, selStart, selEnd, valueStart, startVal, endVal));
                    }
                    if (mSelectionStart != null) mSelectionStart[index] = startVal;
                    if (mSelectionEnd != null) mSelectionEnd[index] = endVal;
                    if (mCursorPos != null) mCursorPos[index] = startVal;
                });

                // Focus only toggles the active field and visual style; it does not overwrite
                // the text or cursor position during a touch interaction.
                view.setOnFocusChangeListener((v, hasFocus) -> {
                    if (mProgrammaticUpdate || mFields == null) {
                        return;
                    }
                    if (hasFocus) {
                        mFields.setActive(index);
                        if (DEBUG_LOG) {
                            Log.d(TAG, String.format("onFocusChange: field=%d gained focus", index));
                        }
                    }
                });

                view.setOnClickListener(v -> {
                    if (mFields != null) {
                        mFields.setActive(index);
                        view.requestFocus();
                    }
                });

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
        mProgrammaticUpdate = true;
        try {
            for (int i = 0; i < mFieldViews.length; i++) {
                final ToolFieldEditText view = mFieldViews[i];
                if (view == null) {
                    continue;
                }
                final String label = i < labels.length ? labels[i] : "";
                final String value = mFields.get(i);
                final boolean isActive = i == active;
                view.setText(label + (value.isEmpty() ? "" : ": " + value));
                view.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
                view.setTextColor(isActive ? mActiveColor : mInactiveColor);
                if (isActive) {
                    // Restore cursor position within the value text, accounting for the label prefix.
                    final int valueStart = label.length() + 2;
                    int pos;
                    if (mCursorPos != null && mCursorPos[i] >= 0) {
                        pos = mCursorPos[i];
                    } else {
                        pos = value.length();
                    }
                    if (pos > value.length()) pos = value.length();
                    view.setSelection(valueStart + pos, valueStart + pos);
                }
            }
        } finally {
            mProgrammaticUpdate = false;
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

    /** Keep a long 大写 primary amount on-screen, then restore the normal range when off. */
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
        private final ToolFieldEditText mView;

        SelectionActions(ToolFieldEditText view) {
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
                int swap = start;
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
