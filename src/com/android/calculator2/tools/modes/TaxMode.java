/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.data.TaxTable;
import com.android.calculator2.tools.model.IncomeTaxCalculator;
import com.android.calculator2.tools.model.SocialInsurance;
import com.android.calculator2.tools.model.SpecialDeductions;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Individual income tax (China, cumulative withholding): monthly salary / social insurance /
 * special deductions. Uses the {@link TaxTable} from {@code assets/tools/tax_cn.json}.
 */
public class TaxMode extends FieldToolMode {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    @Nullable
    private TaxTable mTable;

    /** Year-end bonus mode (全年一次性奖金) instead of monthly-salary withholding. */
    private boolean mBonusMode;
    @Nullable
    private String[] mSalarySnapshot;
    @Nullable
    private String mBonusSnapshot;

    /** Structured 专项附加扣除 behind the special-deduction field. */
    private final SpecialDeductions mSpecial = new SpecialDeductions();
    /** Structured 五险一金 behind the insurance field: base plus six personal rates. */
    @Nullable
    private BigDecimal mInsuranceBase;
    private final BigDecimal[] mInsuranceRates = new BigDecimal[SocialInsurance.CATEGORIES];

    @Override protected void saveOptions(Bundle state) {
        state.putBoolean("bonus",mBonusMode);state.putStringArray("salary",mSalarySnapshot);
        state.putString("bonusValue",mBonusSnapshot);
        state.putString("insuranceBase",mInsuranceBase==null?null:mInsuranceBase.toPlainString());
        String[] rates=new String[mInsuranceRates.length];
        for(int i=0;i<rates.length;i++) rates[i]=mInsuranceRates[i]==null?null:mInsuranceRates[i].toPlainString();
        state.putStringArray("insuranceRates",rates);
        state.putInt("educationChildren",mSpecial.educationChildren);
        state.putString("educationShare",mSpecial.educationShare.name());
        state.putString("continuing",mSpecial.continuing.name());
        state.putString("medical",mSpecial.majorMedicalAnnualSelfPaid.toPlainString());
        state.putBoolean("mortgageInterest",mSpecial.mortgageInterest);
        state.putString("mortgageShare",mSpecial.mortgageShare.name());
        state.putString("rent",mSpecial.rent.name());state.putBoolean("elderlyOnly",mSpecial.elderlyOnlyChild);
        state.putString("elderlyShared",mSpecial.elderlySharedMonthly.toPlainString());
        state.putInt("infants",mSpecial.infantChildren);state.putString("infantShare",mSpecial.infantShare.name());
    }
    @Override protected void restoreOptions(Bundle state) {
        mBonusMode=state.getBoolean("bonus");mSalarySnapshot=state.getStringArray("salary");
        mBonusSnapshot=state.getString("bonusValue");mInsuranceBase=state.getString("insuranceBase")==null?null:parseDecimal(state.getString("insuranceBase"));
        String[] rates=state.getStringArray("insuranceRates");
        if(rates!=null) for(int i=0;i<Math.min(rates.length,mInsuranceRates.length);i++)
            mInsuranceRates[i]=rates[i]==null?null:parseDecimal(rates[i]);
        mSpecial.educationChildren=state.getInt("educationChildren");
        mSpecial.educationShare=SpecialDeductions.Share.valueOf(state.getString("educationShare"));
        mSpecial.continuing=SpecialDeductions.ContinuingEducation.valueOf(state.getString("continuing"));
        mSpecial.majorMedicalAnnualSelfPaid=new BigDecimal(state.getString("medical"));
        mSpecial.mortgageInterest=state.getBoolean("mortgageInterest");
        mSpecial.mortgageShare=SpecialDeductions.Share.valueOf(state.getString("mortgageShare"));
        mSpecial.rent=SpecialDeductions.RentTier.valueOf(state.getString("rent"));
        mSpecial.elderlyOnlyChild=state.getBoolean("elderlyOnly");
        mSpecial.elderlySharedMonthly=new BigDecimal(state.getString("elderlyShared"));
        mSpecial.infantChildren=state.getInt("infants");
        mSpecial.infantShare=SpecialDeductions.Share.valueOf(state.getString("infantShare"));
    }

    @Override
    public String getId() {
        return ToolId.TAX;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_tax;
    }

    @Override
    public int getIconRes() {
        return R.drawable.ic_tool_tax;
    }

    @Override
    public boolean supportsUppercaseNumbers() {
        return true;
    }

    @Override
    protected int fieldCount() {
        return 3;
    }

    @Override
    protected String[] fieldLabels() {
        Context ctx = ctx();
        if (mBonusMode) {
            return new String[] {ctx.getString(R.string.tool_tax_field_bonus), "", ""};
        }
        return new String[] {
                ctx.getString(R.string.tool_tax_field_salary),
                ctx.getString(R.string.tool_tax_field_insurance),
                ctx.getString(R.string.tool_tax_field_special),
        };
    }

    @Override
    protected String[] fieldDefaults() {
        return new String[] {"20000", "2000", "1000"};
    }

    @Override
    protected int controlLayoutRes() {
        return R.layout.tool_tax_control;
    }

    @Override
    protected void onViewCreated(@NonNull View root, @NonNull Context context) {
        final TextView modeToggle = root.findViewById(R.id.tax_mode_toggle);
        if (modeToggle != null) {
            modeToggle.setOnClickListener(v -> switchMode());
        }
        final TextView insurance = root.findViewById(R.id.tax_insurance_detail);
        if (insurance != null) {
            insurance.setText(context.getString(R.string.tool_tax_insurance_detail));
            insurance.setOnClickListener(v -> showInsuranceDialog(context));
        }
        final TextView special = root.findViewById(R.id.tax_special_detail);
        if (special != null) {
            special.setText(context.getString(R.string.tool_tax_special_detail));
            special.setOnClickListener(v -> showSpecialDialog(context));
        }
        updateModeUi();
    }

    /** Flips between monthly-salary withholding and the year-end bonus calculation. */
    private void switchMode() {
        if (mFields == null) {
            return;
        }
        if (!mBonusMode) {
            mSalarySnapshot = new String[] {mFields.get(0), mFields.get(1), mFields.get(2)};
            mFields.set(0, mBonusSnapshot != null ? mBonusSnapshot : "36000");
            mFields.set(1, "");
            mFields.set(2, "");
        } else {
            mBonusSnapshot = mFields.get(0);
            if (mSalarySnapshot != null) {
                mFields.set(0, mSalarySnapshot[0]);
                mFields.set(1, mSalarySnapshot[1]);
                mFields.set(2, mSalarySnapshot[2]);
            }
        }
        mBonusMode = !mBonusMode;
        mFields.setActive(0);
        updateModeUi();
        refreshFields();
        recomputeAndDisplay();
    }

    private void updateModeUi() {
        final Context context = mHost == null ? null : mHost.getContext();
        if (context == null) {
            return;
        }
        final TextView modeToggle = find(R.id.tax_mode_toggle);
        if (modeToggle != null) {
            modeToggle.setText(context.getString(mBonusMode
                    ? R.string.tool_tax_mode_bonus : R.string.tool_tax_mode_salary));
        }
        final int detailVisibility = mBonusMode ? View.GONE : View.VISIBLE;
        final TextView insurance = find(R.id.tax_insurance_detail);
        if (insurance != null) {
            insurance.setVisibility(detailVisibility);
        }
        final TextView special = find(R.id.tax_special_detail);
        if (special != null) {
            special.setVisibility(detailVisibility);
        }
        final View field1 = find(R.id.tool_field_1);
        if (field1 != null) {
            field1.setVisibility(mBonusMode ? View.GONE : View.VISIBLE);
        }
        final View field2 = find(R.id.tool_field_2);
        if (field2 != null) {
            field2.setVisibility(mBonusMode ? View.GONE : View.VISIBLE);
        }
    }

    // ---- detail dialogs ----

    /**
     * Shows an MD3 dialog without auto-opening the soft keyboard: numeric inputs are edited by
     * tapping them, so the keyboard appears on demand rather than racing the dialog window open.
     */
    private static void showDialog(@NonNull MaterialAlertDialogBuilder builder) {
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
    }

    /** Five insurances and one fund, entered proportionally as six categories. */
    private void showInsuranceDialog(@NonNull Context context) {
        final View view = android.view.LayoutInflater.from(context)
                .inflate(R.layout.dialog_social_insurance, null);
        final EditText base = view.findViewById(R.id.si_base_input);
        base.setText(mInsuranceBase == null ? "10000" : mInsuranceBase.toPlainString());
        final int[] rateIds = {R.id.si_rate_0, R.id.si_rate_1, R.id.si_rate_2,
                R.id.si_rate_3, R.id.si_rate_4, R.id.si_rate_5};
        for (int i = 0; i < rateIds.length; i++) {
            final EditText rate = view.findViewById(rateIds[i]);
            rate.setText(mInsuranceRates[i] != null
                    ? mInsuranceRates[i].toPlainString()
                    : SocialInsurance.DEFAULT_RATES_PERCENT[i]);
        }
        showDialog(new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tool_tax_insurance_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_apply, (dialog, which) -> {
                    final BigDecimal[] rates = new BigDecimal[SocialInsurance.CATEGORIES];
                    for (int i = 0; i < rateIds.length; i++) {
                        rates[i] = parseDecimal(
                                ((EditText) view.findViewById(rateIds[i])).getText().toString());
                    }
                    final BigDecimal baseValue = parseDecimal(base.getText().toString());
                    mInsuranceBase = baseValue;
                    System.arraycopy(rates, 0, mInsuranceRates, 0, rates.length);
                    if (mFields != null) {
                        mFields.set(1, SocialInsurance.monthly(
                                baseValue == null ? BigDecimal.ZERO : baseValue, rates, MC)
                                .toPlainString());
                        mFields.setActive(0);
                        refreshFields();
                        recomputeAndDisplay();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null));
    }

    /** The seven 专项附加扣除 categories, reduced to one monthly total. */
    private void showSpecialDialog(@NonNull Context context) {
        final View view = android.view.LayoutInflater.from(context)
                .inflate(R.layout.dialog_special_deductions, null);
        final String[] shares = {
                context.getString(R.string.share_full), context.getString(R.string.share_half)};
        final String[] continuing = {
                context.getString(R.string.cont_none),
                context.getString(R.string.cont_academic),
                context.getString(R.string.cont_certificate)};
        final String[] rents = {
                context.getString(R.string.rent_none),
                context.getString(R.string.rent_1500),
                context.getString(R.string.rent_1100),
                context.getString(R.string.rent_800)};
        final Spinner eduShare = view.findViewById(R.id.ded_edu_share);
        final Spinner mortgageShare = view.findViewById(R.id.ded_mortgage_share);
        final Spinner infantShare = view.findViewById(R.id.ded_infant_share);
        final Spinner continuingSpinner = view.findViewById(R.id.ded_continuing);
        final Spinner rentSpinner = view.findViewById(R.id.ded_rent);
        eduShare.setAdapter(spinnerAdapter(context, shares));
        mortgageShare.setAdapter(spinnerAdapter(context, shares));
        infantShare.setAdapter(spinnerAdapter(context, shares));
        continuingSpinner.setAdapter(spinnerAdapter(context, continuing));
        rentSpinner.setAdapter(spinnerAdapter(context, rents));

        final EditText eduChildren = view.findViewById(R.id.ded_edu_children);
        final EditText medical = view.findViewById(R.id.ded_medical);
        final CheckBox mortgage = view.findViewById(R.id.ded_mortgage);
        final CheckBox elderlyOnly = view.findViewById(R.id.ded_elderly_only);
        final EditText elderlyShared = view.findViewById(R.id.ded_elderly_shared);
        final EditText infantChildren = view.findViewById(R.id.ded_infant_children);
        eduChildren.setText(String.valueOf(mSpecial.educationChildren));
        eduShare.setSelection(mSpecial.educationShare == SpecialDeductions.Share.HALF ? 1 : 0);
        continuingSpinner.setSelection(mSpecial.continuing.ordinal());
        medical.setText(mSpecial.majorMedicalAnnualSelfPaid.toPlainString());
        mortgage.setChecked(mSpecial.mortgageInterest);
        mortgageShare.setSelection(mSpecial.mortgageShare == SpecialDeductions.Share.HALF ? 1 : 0);
        rentSpinner.setSelection(mSpecial.rent.ordinal());
        elderlyOnly.setChecked(mSpecial.elderlyOnlyChild);
        elderlyShared.setText(mSpecial.elderlySharedMonthly.toPlainString());
        infantChildren.setText(String.valueOf(mSpecial.infantChildren));
        infantShare.setSelection(mSpecial.infantShare == SpecialDeductions.Share.HALF ? 1 : 0);

        showDialog(new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tool_tax_special_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_apply, (dialog, which) -> {
                    mSpecial.educationChildren = parseCount(eduChildren.getText().toString());
                    mSpecial.educationShare = eduShare.getSelectedItemPosition() == 1
                            ? SpecialDeductions.Share.HALF : SpecialDeductions.Share.FULL;
                    mSpecial.continuing = SpecialDeductions.ContinuingEducation.values()[
                            continuingSpinner.getSelectedItemPosition()];
                    mSpecial.majorMedicalAnnualSelfPaid =
                            orZero(parseDecimal(medical.getText().toString()));
                    mSpecial.mortgageInterest = mortgage.isChecked();
                    mSpecial.mortgageShare = mortgageShare.getSelectedItemPosition() == 1
                            ? SpecialDeductions.Share.HALF : SpecialDeductions.Share.FULL;
                    mSpecial.rent = SpecialDeductions.RentTier.values()[
                            rentSpinner.getSelectedItemPosition()];
                    mSpecial.elderlyOnlyChild = elderlyOnly.isChecked();
                    mSpecial.elderlySharedMonthly =
                            orZero(parseDecimal(elderlyShared.getText().toString()));
                    mSpecial.infantChildren = parseCount(infantChildren.getText().toString());
                    mSpecial.infantShare = infantShare.getSelectedItemPosition() == 1
                            ? SpecialDeductions.Share.HALF : SpecialDeductions.Share.FULL;
                    if (mFields != null) {
                        mFields.set(2, mSpecial.monthlyTotal(MC).toPlainString());
                        mFields.setActive(0);
                        refreshFields();
                        recomputeAndDisplay();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null));
    }

    private static ArrayAdapter<String> spinnerAdapter(
            @NonNull Context context, @NonNull String[] entries) {
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    @Nullable
    private static BigDecimal parseDecimal(@NonNull String text) {
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal orZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int parseCount(@NonNull String text) {
        final BigDecimal value = parseDecimal(text);
        return value == null ? 0 : Math.max(0, value.intValue());
    }

    @Override
    protected void recomputeAndDisplay() {
        final ToolHost host = mHost;
        if (host == null || mFields == null) {
            return;
        }
        final BigDecimal salary = mFields.getNumber(0);
        final BigDecimal insurance = mFields.getNumber(1);
        final BigDecimal special = mFields.getNumber(2);

        String formula;
        String result;
        if (salary == null || salary.signum() <= 0) {
            formula = "—";
            result = "—";
        } else if (mBonusMode) {
            if (mTable == null) {
                mTable = TaxTable.load(host.getContext());
            }
            final IncomeTaxCalculator.BonusResult bonus =
                    IncomeTaxCalculator.computeYearEndBonus(salary, mTable, MC);
            final Context ctx = host.getContext();
            formula = ctx.getString(R.string.tool_tax_bonus_tax) + " " + moneyUpper(bonus.tax)
                    + " · " + ctx.getString(R.string.tool_tax_bonus_rate) + " "
                    + bonus.rate.multiply(new BigDecimal("100"), MC)
                            .stripTrailingZeros().toPlainString() + "%";
            result = ctx.getString(R.string.tool_tax_bonus_after) + " "
                    + moneyUpper(bonus.afterTax);
        } else {
            if (mTable == null) {
                mTable = TaxTable.load(host.getContext());
            }
            final IncomeTaxCalculator.Result r = IncomeTaxCalculator.computeAnnual(
                    salary,
                    insurance == null ? BigDecimal.ZERO : insurance,
                    special == null ? BigDecimal.ZERO : special,
                    mTable, MC);
            final Context ctx = host.getContext();
            formula = ctx.getString(R.string.tool_tax_annual_tax) + " "
                    + moneyUpper(r.annualTax);
            result = ctx.getString(R.string.tool_tax_annual_after_tax) + " "
                    + moneyUpper(r.annualAfterTax)
                    + " · " + ctx.getString(R.string.tool_tax_last_month) + " "
                    + moneyUpper(r.lastMonthWithholding);
        }
        host.setToolFormula(formula);
        host.setToolResult(result);
    }

    private Context ctx() {
        return mHost == null ? null : mHost.getContext();
    }
}
