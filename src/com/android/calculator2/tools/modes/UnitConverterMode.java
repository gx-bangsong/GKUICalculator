/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.data.UnitCategory;
import com.android.calculator2.tools.data.UnitDef;
import com.android.calculator2.tools.data.UnitRepository;
import com.android.calculator2.tools.model.TemperatureConversion;
import com.android.calculator2.tools.model.UnitConversion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline unit conversion (length, area, volume, weight, temperature, ...), data-driven from
 * {@code assets/tools/units.json}.
 * <p>
 * The big display is cleared: the numbers and tappable unit/category buttons are placed
 * in the host's {@link ToolHost#getToolControlSlot()} in a spacious, iOS-calculator style layout.
 */
public class UnitConverterMode implements ToolMode {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static final MathContext DISPLAY_MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final String DEFAULT_INPUT = "1";

    @Nullable
    private UnitRepository mRepository;
    @NonNull
    private List<UnitCategory> mCategories = Collections.emptyList();

    private int mCategoryIndex;
    private int mFromIndex;
    private int mToIndex;

    private final StringBuilder mInput = new StringBuilder();

    @Nullable
    private ToolHost mHost;
    @Nullable
    private View mControlRoot;
    @Nullable
    private TextView mCategoryView;
    @Nullable
    private TextView mFromView;
    @Nullable
    private TextView mToView;
    @Nullable
    private TextView mInputView;
    @Nullable
    private TextView mResultView;

    @NonNull
    @Override
    public String getId() {
        return ToolId.UNIT;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_unit;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        mHost = host;
        final Context context = host.getContext();
        if (mRepository == null) {
            mRepository = new UnitRepository(context);
        }
        mCategories = mRepository.getCategories();

        mInput.setLength(0);
        mInput.append(carryValue != null ? carryValue : DEFAULT_INPUT);
        mCategoryIndex = 0;
        mFromIndex = 0;
        mToIndex = Math.min(1, Math.max(0, currentUnitCount() - 1));

        mountControls(context);
        redisplay();
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
        mControlRoot = LayoutInflater.from(context).inflate(R.layout.tool_unit_control, slot, false);
        mCategoryView = mControlRoot.findViewById(R.id.unit_category);
        mFromView = mControlRoot.findViewById(R.id.unit_from);
        mToView = mControlRoot.findViewById(R.id.unit_to);
        mInputView = mControlRoot.findViewById(R.id.unit_input_text);
        mResultView = mControlRoot.findViewById(R.id.unit_result_text);

        final ImageButton swap = mControlRoot.findViewById(R.id.unit_swap);
        swap.setOnClickListener(v -> swapUnits());

        mCategoryView.setOnClickListener(v -> showCategoryPicker());
        mFromView.setOnClickListener(v -> showUnitPicker(true));
        mToView.setOnClickListener(v -> showUnitPicker(false));

        updateLabels();

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
        mCategoryView = null;
        mFromView = null;
        mToView = null;
        mInputView = null;
        mResultView = null;
    }

    private void swapUnits() {
        final int tmp = mFromIndex;
        mFromIndex = mToIndex;
        mToIndex = tmp;
        updateLabels();
        redisplay();
    }

    private void showCategoryPicker() {
        if (mHost == null || mCategories.isEmpty()) {
            return;
        }
        final Context ctx = mHost.getContext();
        final String[] labels = categoryNames().toArray(new String[0]);
        new android.app.AlertDialog.Builder(ctx)
                .setSingleChoiceItems(labels, mCategoryIndex, (d, which) -> {
                    mCategoryIndex = which;
                    mFromIndex = 0;
                    mToIndex = Math.min(1, Math.max(0, currentUnitCount() - 1));
                    updateLabels();
                    redisplay();
                    d.dismiss();
                })
                .show();
    }

    private void showUnitPicker(boolean isFrom) {
        if (mHost == null || mCategories.isEmpty()) {
            return;
        }
        final Context ctx = mHost.getContext();
        final String[] labels = unitNames(mCategoryIndex).toArray(new String[0]);
        final int current = isFrom ? clamp(mFromIndex) : clamp(mToIndex);
        new android.app.AlertDialog.Builder(ctx)
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    if (isFrom) {
                        mFromIndex = which;
                    } else {
                        mToIndex = which;
                    }
                    updateLabels();
                    redisplay();
                    d.dismiss();
                })
                .show();
    }

    private void updateLabels() {
        final UnitCategory category = currentCategory();
        if (category == null) {
            return;
        }
        if (mCategoryView != null) {
            mCategoryView.setText(category.getDisplayName());
        }
        if (mFromView != null && !category.getUnits().isEmpty()) {
            mFromView.setText(category.getUnits().get(clamp(mFromIndex)).getDisplayName());
        }
        if (mToView != null && !category.getUnits().isEmpty()) {
            mToView.setText(category.getUnits().get(clamp(mToIndex)).getDisplayName());
        }
    }

    // ---- computation & display ----

    private void redisplay() {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        if (mCategories.isEmpty()) {
            if (mInputView != null) {
                mInputView.setText("");
            }
            if (mResultView != null) {
                mResultView.setText(host.getContext().getString(R.string.tool_unit_no_data));
            }
            host.setToolFormula("");
            host.setToolResult("");
            return;
        }
        final UnitCategory category = currentCategory();
        final UnitDef from = category.getUnits().get(clamp(mFromIndex));
        final UnitDef to = category.getUnits().get(clamp(mToIndex));
        final BigDecimal value = parseInput();

        String resultText;
        if (value == null) {
            resultText = "—";
        } else {
            final BigDecimal out = convert(value, category, from, to);
            resultText = (out == null ? "—" : format(out));
        }

        if (mInputView != null) {
            mInputView.setText(displayInput());
        }
        if (mResultView != null) {
            mResultView.setText(resultText);
        }

        // Clear the big display lines so the UI layout looks neat, centered, and matches currency
        host.setToolFormula("");
        host.setToolResult("");
    }

    @Nullable
    private BigDecimal convert(@NonNull BigDecimal value, @NonNull UnitCategory category,
            @NonNull UnitDef from, @NonNull UnitDef to) {
        if (category.isAffine()) {
            final TemperatureConversion.Scale fromScale =
                    TemperatureConversion.scaleFromUnitId(from.getId());
            final TemperatureConversion.Scale toScale =
                    TemperatureConversion.scaleFromUnitId(to.getId());
            if (fromScale == null || toScale == null) {
                return null;
            }
            return TemperatureConversion.convert(value, fromScale, toScale, MC);
        }
        if (from.getFactor() == null || to.getFactor() == null) {
            return null;
        }
        return UnitConversion.convert(value, from.getFactor(), to.getFactor(), MC);
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
        BigDecimal rounded = value.round(DISPLAY_MC).stripTrailingZeros();
        if (rounded.scale() < 0) {
            rounded = rounded.setScale(0, RoundingMode.HALF_UP);
        }
        String text = rounded.toPlainString();
        // Avoid a "-0" result.
        if (text.equals("-0")) {
            text = "0";
        }
        return text;
    }

    // ---- helpers ----

    private int currentUnitCount() {
        final UnitCategory category = currentCategory();
        return category == null ? 0 : category.getUnits().size();
    }

    @Nullable
    private UnitCategory currentCategory() {
        if (mCategoryIndex < 0 || mCategoryIndex >= mCategories.size()) {
            return null;
        }
        return mCategories.get(mCategoryIndex);
    }

    private int clamp(int index) {
        final UnitCategory category = currentCategory();
        if (category == null || category.getUnits().isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(index, category.getUnits().size() - 1));
    }

    @NonNull
    private List<String> categoryNames() {
        List<String> names = new ArrayList<>();
        for (UnitCategory category : mCategories) {
            names.add(category.getDisplayName());
        }
        return names;
    }

    @NonNull
    private List<String> unitNames(int categoryIndex) {
        List<String> names = new ArrayList<>();
        if (categoryIndex >= 0 && categoryIndex < mCategories.size()) {
            for (UnitDef unit : mCategories.get(categoryIndex).getUnits()) {
                names.add(unit.getDisplayName());
            }
        }
        return names;
    }
}
