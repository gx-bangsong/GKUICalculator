/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;

/**
 * The default calculator mode.
 * <p>
 * It does <em>not</em> drive the display through {@link ToolMode}: when it is active,
 * {@code Calculator} routes pad presses to the existing engine and the evaluator owns the
 * display. All methods here are therefore no-ops; the host reclaims/restores the display via
 * {@link ToolHost#restoreCalculatorDisplay()}.
 */
public class CalculatorMode implements ToolMode {

    @NonNull
    @Override
    public String getId() {
        return ToolId.CALCULATOR;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_calculator;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        // No-op: the calculator engine/UI continues to own the display natively.
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
    }

    @Override
    public void onDigit(int digit) {
        // Never invoked: pad presses are handled by Calculator while this mode is active.
    }

    @Override
    public void onDecimalPoint() {
    }

    @Override
    public void onDelete() {
    }

    @Override
    public void onClear() {
    }
}
