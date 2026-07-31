/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;

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
 * The display is reused: the formula line shows {@code "value <from>"}, the result line shows
 * {@code "result <to>"}. The mode-specific controls (category / from / to selectors + swap) are
 * mounted into the host's {@link ToolHost#getToolControlSlot()}. Linear categories use
 * {@link UnitConversion}; temperature uses {@link TemperatureConversion}.
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
    private Spinner mCategorySpinner;
    @Nullable
    private Spinner mFromSpinner;
    @Nullable
    private Spinner mToSpinner;
    private boolean mUpdating;

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
        mCategorySpinner = mControlRoot.findViewById(R.id.unit_category);
        mFromSpinner = mControlRoot.findViewById(R.id.unit_from);
        mToSpinner = mControlRoot.findViewById(R.id.unit_to);
        final ImageButton swap = mControlRoot.findViewById(R.id.unit_swap);
        swap.setOnClickListener(v -> swapUnits());

        mUpdating = true;
        final ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, categoryNames());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategorySpinner.setAdapter(categoryAdapter);
        mCategorySpinner.setSelection(mCategoryIndex);
        refreshUnitSpinners(context);
        mCategorySpinner.setOnItemSelectedListener(new CategoryListener());
        mFromSpinner.setOnItemSelectedListener(new UnitListener(true));
        mToSpinner.setOnItemSelectedListener(new UnitListener(false));
        mUpdating = false;

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
        mCategorySpinner = null;
        mFromSpinner = null;
        mToSpinner = null;
    }

    private void refreshUnitSpinners(@NonNull Context context) {
        if (mFromSpinner == null || mToSpinner == null) {
            return;
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, unitNames(mCategoryIndex));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mFromSpinner.setAdapter(adapter);
        mToSpinner.setAdapter(adapter);
        mFromSpinner.setSelection(clamp(mFromIndex));
        mToSpinner.setSelection(clamp(mToIndex));
    }

    private void swapUnits() {
        final int tmp = mFromIndex;
        mFromIndex = mToIndex;
        mToIndex = tmp;
        mUpdating = true;
        if (mFromSpinner != null) {
            mFromSpinner.setSelection(clamp(mFromIndex));
        }
        if (mToSpinner != null) {
            mToSpinner.setSelection(clamp(mToIndex));
        }
        mUpdating = false;
        redisplay();
    }

    private final class CategoryListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (mUpdating) {
                return;
            }
            mCategoryIndex = position;
            mFromIndex = 0;
            mToIndex = Math.min(1, Math.max(0, currentUnitCount() - 1));
            mUpdating = true;
            refreshUnitSpinners(parent.getContext());
            mUpdating = false;
            redisplay();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private final class UnitListener implements AdapterView.OnItemSelectedListener {
        private final boolean mFrom;

        UnitListener(boolean from) {
            mFrom = from;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (mUpdating) {
                return;
            }
            if (mFrom) {
                mFromIndex = position;
            } else {
                mToIndex = position;
            }
            redisplay();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    // ---- computation & display ----

    private void redisplay() {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        if (mCategories.isEmpty()) {
            host.setToolFormula("");
            host.setToolResult(host.getContext().getString(R.string.tool_unit_no_data));
            return;
        }
        final UnitCategory category = currentCategory();
        final UnitDef from = category.getUnits().get(clamp(mFromIndex));
        final UnitDef to = category.getUnits().get(clamp(mToIndex));
        final BigDecimal value = parseInput();

            host.setToolFormula(displayInput());

            String result;
            if (value == null) {
                result = "—";
            } else {
                final BigDecimal out = convert(value, category, from, to);
                result = (out == null ? "—" : format(out));
            }
            host.setToolResult(result);
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
