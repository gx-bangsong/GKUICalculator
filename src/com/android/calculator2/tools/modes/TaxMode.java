/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.data.TaxTable;
import com.android.calculator2.tools.model.IncomeTaxCalculator;

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
            formula = ctx.getString(R.string.tool_tax_annual_tax) + " " + money(r.annualTax);
            result = ctx.getString(R.string.tool_tax_annual_after_tax) + " " + money(r.annualAfterTax)
                    + "  ·  " + ctx.getString(R.string.tool_tax_last_month) + " " + money(r.lastMonthWithholding);
        }
        host.setToolFormula(formula);
        host.setToolResult(result);
    }

    private Context ctx() {
        return mHost == null ? null : mHost.getContext();
    }
}
