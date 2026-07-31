/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.model.MortgageCalculator;

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

    @NonNull
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
        return 0;
    }

    @Override
    protected int fieldCount() {
        return 3;
    }

    @Override
    protected String[] fieldLabels() {
        Context ctx = ctx();
        return new String[] {
                ctx.getString(R.string.tool_mortgage_field_principal),
                ctx.getString(R.string.tool_mortgage_field_rate),
                ctx.getString(R.string.tool_mortgage_field_years),
        };
    }

    @Override
    protected String[] fieldDefaults() {
        return new String[] {"1000000", "4.9", "30"};
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
        updateMethodLabel();
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
        final int years = yearsBd == null ? 0 : yearsBd.intValue();

        String formula;
        String result;
        if (principal == null || principal.signum() <= 0 || years <= 0) {
            formula = "—";
            result = "—";
        } else {
            final BigDecimal safeRate = rate == null ? BigDecimal.ZERO : rate;
            final MortgageCalculator.Result r = mEqualPayment
                    ? MortgageCalculator.equalPayment(principal, safeRate, years, MC)
                    : MortgageCalculator.equalPrincipal(principal, safeRate, years, MC);
            final Context ctx = host.getContext();
            if (mEqualPayment) {
                formula = ctx.getString(R.string.tool_mortgage_monthly) + " " + money(r.monthlyPayment);
                result = ctx.getString(R.string.tool_mortgage_interest) + " " + money(r.totalInterest)
                        + "  ·  " + ctx.getString(R.string.tool_mortgage_total) + " " + money(r.totalPayment);
            } else {
                formula = ctx.getString(R.string.tool_mortgage_first_month) + " " + money(r.monthlyPayment);
                result = ctx.getString(R.string.tool_mortgage_decrease) + " " + money(r.monthlyDecrease)
                        + "  ·  " + ctx.getString(R.string.tool_mortgage_interest) + " " + money(r.totalInterest);
            }
        }
        host.setToolFormula(formula);
        host.setToolResult(result);
    }

    private Context ctx() {
        return mHost == null ? null : mHost.getContext();
    }
}
