/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * A single, pluggable "life calculation" tool (unit conversion, currency, mortgage, ...).
 * <p>
 * Tools are registered with {@code ToolManager}; new tools can be added without touching the
 * main UI. The calculator's own mode is just a {@code ToolMode} that transparently passes
 * through to the existing engine and is never driven through this interface.
 */
public interface ToolMode {

    /** Stable id (see {@link ToolId}); used for persistence and look-up. */
    @NonNull
    String getId();

    /** Display name resource. */
    @StringRes
    int getNameRes();

    /**
     * Leading icon resource, or {@code 0} for a text-only entry. Icons are added per tool as
     * they are implemented; {@code 0} is handled gracefully by the toolbar / panel.
     */
    @DrawableRes
    int getIconRes();

    /** Whether this tool needs network access (e.g. currency). Drives permission / settings UI. */
    default boolean isOnline() {
        return false;
    }

    /**
     * Whether the host should enlarge the display region while this tool is active (for tools with
     * many inputs that do not fit the default display height). Default false.
     */
    default boolean wantsExpandedDisplay() {
        return false;
    }

    /**
     * Called when the user selects this tool. The display has already been prepared by the host
     * (see {@link ToolHost#prepareForToolDisplay}). {@code carryValue} is the number that was on
     * the calculator display before switching (may be {@code null}).
     */
    void onActivate(@NonNull ToolHost host, @Nullable String carryValue);

    /** Called when leaving this tool. Release resources, cancel requests, etc. */
    void onDeactivate(@NonNull ToolHost host);

    // ---- Numeric-pad input (only invoked while this tool is the active, non-calculator mode) ----

    void onDigit(int digit);

    void onDecimalPoint();

    void onDelete();

    void onClear();
}
