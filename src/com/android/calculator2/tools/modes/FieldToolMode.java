/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolInputFields;
import com.android.calculator2.tools.ToolMode;

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
        final int[] fieldIds = {R.id.tool_field_0, R.id.tool_field_1, R.id.tool_field_2};
        for (int i = 0; i < fieldCount(); i++) {
            final TextView view = mControlRoot.findViewById(fieldIds[i]);
            mFieldViews[i] = view;
            if (view != null) {
                final int index = i;
                view.setOnClickListener(v -> {
                    if (mFields != null) {
                        mFields.setActive(index);
                        refreshFields();
                        recomputeAndDisplay();
                    }
                });
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

    private static int resolveThemeColor(@NonNull Context context, int attr, int fallback) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return fallback;
    }
}
