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
import androidx.appcompat.app.AlertDialog;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.data.UnitCategory;
import com.android.calculator2.tools.data.UnitDef;
import com.android.calculator2.tools.data.UnitRepository;
import com.android.calculator2.tools.model.TemperatureConversion;
import com.android.calculator2.tools.model.UnitConversion;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline, data-driven unit conversion. Category and unit selection use anchored exposed menus.
 * Linear categories also expose an "Add custom unit" action that opens a Material 3 form and
 * persists the resulting exact conversion factor through {@link UnitRepository}.
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
    private MaterialAutoCompleteTextView mCategoryView;
    @Nullable
    private MaterialAutoCompleteTextView mFromView;
    @Nullable
    private MaterialAutoCompleteTextView mToView;
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
        mControlRoot = LayoutInflater.from(context)
                .inflate(R.layout.tool_unit_control, slot, false);
        mCategoryView = mControlRoot.findViewById(R.id.unit_category);
        mFromView = mControlRoot.findViewById(R.id.unit_from);
        mToView = mControlRoot.findViewById(R.id.unit_to);
        mInputView = mControlRoot.findViewById(R.id.unit_input_text);
        mResultView = mControlRoot.findViewById(R.id.unit_result_text);

        final ImageButton swap = mControlRoot.findViewById(R.id.unit_swap);
        swap.setOnClickListener(v -> swapUnits());

        configureCategoryDropdown(context);
        configureUnitDropdowns(context);
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

    private void configureCategoryDropdown(@NonNull Context context) {
        if (mCategoryView == null) {
            return;
        }
        mCategoryView.setAdapter(dropdownAdapter(context, categoryNames()));
        mCategoryView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= mCategories.size()) {
                return;
            }
            mCategoryIndex = position;
            mFromIndex = 0;
            mToIndex = Math.min(1, Math.max(0, currentUnitCount() - 1));
            configureUnitDropdowns(context);
            updateLabels();
            redisplay();
        });
        showMenuOnClick(mCategoryView);
    }

    private void configureUnitDropdowns(@NonNull Context context) {
        final UnitCategory category = currentCategory();
        if (category == null) {
            return;
        }
        final List<String> labels = unitMenuNames(category);
        if (mFromView != null) {
            mFromView.setAdapter(dropdownAdapter(context, labels));
            mFromView.setOnItemClickListener((parent, view, position, id) ->
                    onUnitMenuItemSelected(true, position));
            showMenuOnClick(mFromView);
        }
        if (mToView != null) {
            mToView.setAdapter(dropdownAdapter(context, labels));
            mToView.setOnItemClickListener((parent, view, position, id) ->
                    onUnitMenuItemSelected(false, position));
            showMenuOnClick(mToView);
        }
    }

    private void onUnitMenuItemSelected(boolean isFrom, int position) {
        final UnitCategory category = currentCategory();
        if (category == null) {
            return;
        }
        final int unitCount = category.getUnits().size();
        if (!category.isAffine() && position == unitCount) {
            // Restore the current value while the form is open instead of displaying the action
            // label as though it were a selected unit.
            updateLabels();
            showCustomUnitDialog(isFrom);
            return;
        }
        if (position < 0 || position >= unitCount) {
            updateLabels();
            return;
        }
        if (isFrom) {
            mFromIndex = position;
        } else {
            mToIndex = position;
        }
        updateLabels();
        redisplay();
    }

    private void showCustomUnitDialog(boolean selectAsFrom) {
        final ToolHost host = mHost;
        final UnitRepository repository = mRepository;
        final UnitCategory category = currentCategory();
        if (host == null || repository == null || category == null || category.isAffine()
                || category.getUnits().isEmpty()) {
            return;
        }
        final Context context = host.getContext();
        final View content = LayoutInflater.from(context)
                .inflate(R.layout.dialog_custom_unit, null);
        final TextInputLayout nameLayout = content.findViewById(R.id.custom_unit_name_layout);
        final TextInputLayout valueLayout = content.findViewById(R.id.custom_unit_value_layout);
        final TextInputEditText nameInput = content.findViewById(R.id.custom_unit_name);
        final TextInputEditText valueInput = content.findViewById(R.id.custom_unit_value);
        final MaterialAutoCompleteTextView baseDropdown =
                content.findViewById(R.id.custom_unit_base);

        // Snapshot the units shown in this modal form so selection remains stable.
        final List<UnitDef> baseUnits = new ArrayList<>(category.getUnits());
        final List<String> baseNames = new ArrayList<>();
        for (UnitDef unit : baseUnits) {
            baseNames.add(unit.getDisplayName());
        }
        baseDropdown.setAdapter(dropdownAdapter(context, baseNames));
        final int[] selectedBase = {baseUnitIndex(category)};
        baseDropdown.setText(baseUnits.get(selectedBase[0]).getDisplayName(), false);
        baseDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < baseUnits.size()) {
                selectedBase[0] = position;
            }
        });
        showMenuOnClick(baseDropdown);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.custom_unit_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.custom_unit_add, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    nameLayout.setError(null);
                    valueLayout.setError(null);

                    final String name = nameInput.getText() == null
                            ? "" : nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        nameLayout.setError(context.getString(R.string.custom_unit_name_required));
                        return;
                    }
                    if (hasUnitName(category, name)) {
                        nameLayout.setError(
                                context.getString(R.string.custom_unit_name_duplicate));
                        return;
                    }

                    final String valueText = valueInput.getText() == null
                            ? "" : valueInput.getText().toString().trim();
                    final BigDecimal conversionValue;
                    try {
                        conversionValue = new BigDecimal(valueText);
                    } catch (NumberFormatException e) {
                        valueLayout.setError(
                                context.getString(R.string.custom_unit_value_invalid));
                        return;
                    }
                    if (conversionValue.signum() <= 0) {
                        valueLayout.setError(
                                context.getString(R.string.custom_unit_value_invalid));
                        return;
                    }

                    final UnitDef selected = baseUnits.get(selectedBase[0]);
                    if (selected.getFactor() == null) {
                        valueLayout.setError(
                                context.getString(R.string.custom_unit_value_invalid));
                        return;
                    }
                    // Example: 1 new unit = 2 ft, and 1 ft = 0.3048 m, therefore the stored
                    // category-base factor for the new unit is 0.6096 m.
                    final BigDecimal factor = conversionValue.multiply(selected.getFactor());
                    final UnitDef added = repository.addCustomUnit(
                            category.getId(), name, factor);
                    if (added == null) {
                        valueLayout.setError(
                                context.getString(R.string.custom_unit_save_failed));
                        return;
                    }

                    mCategories = repository.getCategories();
                    final int addedIndex = indexOfUnitId(currentCategory(), added.getId());
                    if (selectAsFrom) {
                        mFromIndex = addedIndex;
                    } else {
                        mToIndex = addedIndex;
                    }
                    configureUnitDropdowns(context);
                    updateLabels();
                    redisplay();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void swapUnits() {
        final int tmp = mFromIndex;
        mFromIndex = mToIndex;
        mToIndex = tmp;
        updateLabels();
        redisplay();
    }

    private void updateLabels() {
        final UnitCategory category = currentCategory();
        if (category == null) {
            return;
        }
        if (mCategoryView != null) {
            mCategoryView.setText(category.getDisplayName(), false);
        }
        if (mFromView != null && !category.getUnits().isEmpty()) {
            mFromView.setText(category.getUnits().get(clamp(mFromIndex)).getDisplayName(), false);
        }
        if (mToView != null && !category.getUnits().isEmpty()) {
            mToView.setText(category.getUnits().get(clamp(mToIndex)).getDisplayName(), false);
        }
    }

    private static void showMenuOnClick(@NonNull MaterialAutoCompleteTextView view) {
        view.setOnClickListener(v -> ((MaterialAutoCompleteTextView) v).showDropDown());
    }

    @NonNull
    private static ArrayAdapter<String> dropdownAdapter(@NonNull Context context,
            @NonNull List<String> labels) {
        return new ArrayAdapter<>(context, R.layout.tool_dropdown_item, labels);
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

        final String resultText;
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
        final String text = rounded.toPlainString();
        return text.equals("-0") ? "0" : text;
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

    private int baseUnitIndex(@NonNull UnitCategory category) {
        final String baseId = category.getBaseUnitId();
        if (baseId != null) {
            for (int i = 0; i < category.getUnits().size(); i++) {
                if (baseId.equals(category.getUnits().get(i).getId())) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int indexOfUnitId(@Nullable UnitCategory category, @NonNull String id) {
        if (category != null) {
            for (int i = 0; i < category.getUnits().size(); i++) {
                if (id.equals(category.getUnits().get(i).getId())) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static boolean hasUnitName(@NonNull UnitCategory category, @NonNull String name) {
        for (UnitDef unit : category.getUnits()) {
            if (name.equalsIgnoreCase(unit.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<String> categoryNames() {
        final List<String> names = new ArrayList<>();
        for (UnitCategory category : mCategories) {
            names.add(category.getDisplayName());
        }
        return names;
    }

    @NonNull
    private List<String> unitMenuNames(@NonNull UnitCategory category) {
        final List<String> names = new ArrayList<>();
        for (UnitDef unit : category.getUnits()) {
            names.add(unit.getDisplayName());
        }
        if (!category.isAffine() && mHost != null) {
            names.add(mHost.getContext().getString(R.string.custom_unit_add_item));
        }
        return names;
    }
}
