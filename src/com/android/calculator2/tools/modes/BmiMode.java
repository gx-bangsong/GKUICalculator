/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.model.BmiCalculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Body Mass Index: height (cm) / weight (kg). Inputs use the shared numeric pad via
 * {@link FieldToolMode}. Uses the Chinese adult classification.
 */
public class BmiMode extends FieldToolMode {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    @Override
    public String getId() {
        return ToolId.BMI;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_bmi;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    protected int fieldCount() {
        return 2;
    }

    @Override
    protected String[] fieldLabels() {
        Context ctx = ctx();
        return new String[] {
                ctx.getString(R.string.tool_bmi_field_height),
                ctx.getString(R.string.tool_bmi_field_weight),
        };
    }

    @Override
    protected String[] fieldDefaults() {
        return new String[] {"175", "70"};
    }

    @Override
    protected int controlLayoutRes() {
        return R.layout.tool_bmi_control;
    }

    @Override
    protected void recomputeAndDisplay() {
        final ToolHost host = mHost;
        if (host == null || mFields == null) {
            return;
        }
        final BigDecimal height = mFields.getNumber(0);
        final BigDecimal weight = mFields.getNumber(1);

        String formula;
        String result;
        if (height == null || weight == null || height.signum() <= 0 || weight.signum() <= 0) {
            formula = "—";
            result = "—";
        } else {
            final BmiCalculator.Result r = BmiCalculator.compute(height, weight, MC);
            if (r == null) {
                formula = "—";
                result = "—";
            } else {
                final Context ctx = host.getContext();
                formula = ctx.getString(R.string.tool_bmi_value) + " " + r.bmi.toPlainString();
                result = categoryLabel(ctx, r.category);
            }
        }
        host.setToolFormula(formula);
        host.setToolResult(result);
    }

    private static String categoryLabel(Context ctx, BmiCalculator.Category category) {
        final int res;
        switch (category) {
            case UNDERWEIGHT:
                res = R.string.tool_bmi_underweight;
                break;
            case NORMAL:
                res = R.string.tool_bmi_normal;
                break;
            case OVERWEIGHT:
                res = R.string.tool_bmi_overweight;
                break;
            case OBESE:
            default:
                res = R.string.tool_bmi_obese;
                break;
        }
        return ctx.getString(res);
    }

    private Context ctx() {
        return mHost == null ? null : mHost.getContext();
    }
}
