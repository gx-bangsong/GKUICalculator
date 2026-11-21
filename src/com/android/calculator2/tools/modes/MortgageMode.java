/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.model.MortgageCalculator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Mortgage tool: principal / annual rate (%) / term (years), with a toggle between equal-payment
 * (等额本息) and equal-principal (等额本金). Inputs use the shared numeric pad via {@link FieldToolMode}.
 */
public class MortgageMode extends FieldToolMode {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private boolean mEqualPayment = true;

    /** Loan category: commercial, provident fund, or a combined loan. */
    private int mLoanType = LOAN_COMMERCIAL;
    /** Commercial rate basis: benchmark rate or LPR + spread. */
    private boolean mLprBasis;

    private static final int LOAN_COMMERCIAL = 0;
    private static final int LOAN_FUND = 1;
    private static final int LOAN_COMBINED = 2;

    /** Current provident-fund rate for first homes, percent. */
    private static final String FUND_RATE_PERCENT = "3.1";

    @NonNull
    @Override protected void saveOptions(Bundle state) { state.putBoolean("equal",mEqualPayment);state.putInt("loan",mLoanType);state.putBoolean("lpr",mLprBasis); }
    @Override protected void restoreOptions(Bundle state) { mEqualPayment=state.getBoolean("equal");mLoanType=state.getInt("loan");mLprBasis=state.getBoolean("lpr"); }

    @Override
    public String getId() {
        return ToolId.MORTGAGE;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_mortgage;
    }

    @Override
    public int getIconRes() {
        return R.drawable.ic_tool_mortgage;
    }

    @Override
    public boolean supportsUppercaseNumbers() {
        return true;
    }

    @Override
    protected int fieldCount() {
        return 5;
    }

    @Override
    protected String[] fieldLabels() {
        Context ctx = ctx();
        final String rateLabel;
        if (mLoanType == LOAN_FUND) {
            rateLabel = ctx.getString(R.string.tool_mortgage_field_fund_rate);
        } else if (mLprBasis) {
            rateLabel = ctx.getString(R.string.tool_mortgage_field_lpr);
        } else {
            rateLabel = ctx.getString(R.string.tool_mortgage_field_rate);
        }
        return new String[] {
                ctx.getString(R.string.tool_mortgage_field_principal),
                rateLabel,
                ctx.getString(R.string.tool_mortgage_field_years),
                ctx.getString(R.string.tool_mortgage_field_spread),
                ctx.getString(R.string.tool_mortgage_field_fund_amount),
        };
    }

    @Override
    protected String[] fieldDefaults() {
        return new String[] {"1000000", mLprBasis ? "3.6" : "4.9", "30", "30", "400000"};
    }

    @Override
    protected int controlLayoutRes() {
        return R.layout.tool_mortgage_control;
    }

    @Override
    protected void onViewCreated(@NonNull View root, @NonNull Context context) {
        final TextView method = root.findViewById(R.id.mortgage_method);
        if (method != null) {
            method.setOnClickListener(v -> {
                mEqualPayment = !mEqualPayment;
                updateMethodLabel();
                recomputeAndDisplay();
            });
        }
        final MaterialButtonToggleGroup types = root.findViewById(R.id.mortgage_type_group);
        if (types != null) {
            // Reflect the restored loan model before attaching the user-change listener.
            types.check(mLoanType==LOAN_COMBINED?R.id.mortgage_type_combined:
                    mLoanType==LOAN_FUND?R.id.mortgage_type_fund:R.id.mortgage_type_commercial);
            // LoanTypeToggleGroup measures complete controls and reflows before layout.
            types.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                final int type = checkedId == R.id.mortgage_type_fund ? LOAN_FUND
                        : checkedId == R.id.mortgage_type_combined ? LOAN_COMBINED
                        : LOAN_COMMERCIAL;
                if (type != mLoanType) {
                    mLoanType = type;
                    onLoanShapeChanged();
                }
            });
        }
        final MaterialButton basisToggle = root.findViewById(R.id.mortgage_basis_toggle);
        if (basisToggle != null) {
            basisToggle.setText(mHost.getContext().getString(
                    mLprBasis ? R.string.tool_mortgage_lpr : R.string.tool_mortgage_benchmark));
            basisToggle.setOnClickListener(v -> {
                mLprBasis = !mLprBasis;
                basisToggle.setText(mHost.getContext().getString(
                        mLprBasis ? R.string.tool_mortgage_lpr : R.string.tool_mortgage_benchmark));
                onLoanShapeChanged();
            });
        }
        updateMethodLabel();
        updateLoanShapeUi();
    }

    /** Rate/field shape changed: refresh labels, visibility and the rate defaults. */
    private void onLoanShapeChanged() {
        if (mFields != null) {
            mFields.set(1, mLprBasis && mLoanType != LOAN_FUND ? "3.6"
                    : mLoanType == LOAN_FUND ? FUND_RATE_PERCENT : "4.9");
            mFields.setActive(0);
            mCursorPos[0] = mFields.get(0).length();
        }
        updateLoanShapeUi();
        refreshFields();
        recomputeAndDisplay();
    }

    private void updateLoanShapeUi() {
        final View basisToggle = find(R.id.mortgage_basis_toggle);
        if (basisToggle != null) {
            basisToggle.setVisibility(mLoanType == LOAN_FUND ? View.GONE : View.VISIBLE);
        }
        final View spread = find(R.id.tool_field_3);
        if (spread != null) {
            spread.setVisibility(mLprBasis && mLoanType != LOAN_FUND
                    ? View.VISIBLE : View.GONE);
        }
        final View fundAmount = find(R.id.tool_field_4);
        if (fundAmount != null) {
            fundAmount.setVisibility(mLoanType == LOAN_COMBINED ? View.VISIBLE : View.GONE);
        }
    }

    private void updateMethodLabel() {
        final TextView method = find(R.id.mortgage_method);
        if (method != null && mHost != null) {
            method.setText(mHost.getContext().getString(R.string.tool_mortgage_method)
                    + ": "
                    + mHost.getContext().getString(mEqualPayment
                            ? R.string.tool_mortgage_equal
                            : R.string.tool_mortgage_principal));
        }
    }

    @Override
    protected void recomputeAndDisplay() {
        final ToolHost host = mHost;
        if (host == null || mFields == null) {
            return;
        }
        final BigDecimal principal = mFields.getNumber(0);
        final BigDecimal rate = mFields.getNumber(1);
        final BigDecimal yearsBd = mFields.getNumber(2);
        final BigDecimal spread = mFields.getNumber(3);
        final BigDecimal fundAmount = mFields.getNumber(4);
        final int years = yearsBd == null ? 0 : yearsBd.intValue();

        String formula;
        String result;
        if (principal == null || principal.signum() <= 0 || years <= 0) {
            formula = "—";
            result = "—";
        } else {
            final BigDecimal safeRate = rate == null ? BigDecimal.ZERO : rate;
            final BigDecimal commercialRate = mLprBasis && mLoanType != LOAN_FUND
                    ? MortgageCalculator.lprRate(safeRate, spread, MC)
                    : safeRate;
            final MortgageCalculator.Result r;
            if (mLoanType == LOAN_COMBINED) {
                final BigDecimal fund = fundAmount == null ? BigDecimal.ZERO : fundAmount;
                final BigDecimal commercial = principal.subtract(fund, MC);
                r = MortgageCalculator.combined(commercial, commercialRate, fund,
                        new BigDecimal(FUND_RATE_PERCENT), years, mEqualPayment, MC);
            } else if (mLoanType == LOAN_FUND) {
                r = mEqualPayment
                        ? MortgageCalculator.equalPayment(principal, safeRate, years, MC)
                        : MortgageCalculator.equalPrincipal(principal, safeRate, years, MC);
            } else {
                r = mEqualPayment
                        ? MortgageCalculator.equalPayment(principal, commercialRate, years, MC)
                        : MortgageCalculator.equalPrincipal(principal, commercialRate, years, MC);
            }
            final Context ctx = host.getContext();
            if (mEqualPayment) {
                formula = ctx.getString(R.string.tool_mortgage_monthly) + " "
                        + moneyUpper(r.monthlyPayment);
                result = ctx.getString(R.string.tool_mortgage_interest) + " "
                        + moneyUpper(r.totalInterest)
                        + " · " + ctx.getString(R.string.tool_mortgage_total) + " "
                        + moneyUpper(r.totalPayment);
            } else {
                formula = ctx.getString(R.string.tool_mortgage_first_month) + " "
                        + moneyUpper(r.monthlyPayment);
                result = ctx.getString(R.string.tool_mortgage_decrease) + " "
                        + moneyUpper(r.monthlyDecrease)
                        + " · " + ctx.getString(R.string.tool_mortgage_interest) + " "
                        + moneyUpper(r.totalInterest);
            }
        }
        host.setToolFormula(formula);
        host.setToolResult(result);
    }

    private Context ctx() {
        return mHost == null ? null : mHost.getContext();
    }
}
