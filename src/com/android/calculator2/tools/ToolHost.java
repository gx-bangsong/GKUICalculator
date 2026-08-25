/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Access that {@link ToolMode} implementations have to the shared display area.
 * <p>
 * Tools reuse the calculator's existing display (formula + result lines) and numeric pad
 * instead of introducing a second input surface. They never touch the arbitrary-precision
 * engine ({@code Evaluator}/{@code CalculatorExpr}/{@code BoundedRational}); the host owns
 * the display and hands it off to / reclaims it from the active tool.
 */
public interface ToolHost {

    @NonNull
    Context getContext();

    /**
     * Show the given text on the formula (upper) line of the display. Called on the UI thread.
     */
    void setToolFormula(@NonNull CharSequence text);

    /**
     * Show the given text on the result (lower) line of the display. Called on the UI thread.
     */
    void setToolResult(@NonNull CharSequence text);

    /**
     * Set the result line's text size while a tool is active (tools showing several numbers need
     * a smaller size to fit). Restored to the default when returning to the calculator.
     */
    void setToolResultTextSizeSp(float sp);

    /**
     * Best-effort numeric value currently shown on the display (result preferred, falling back
     * to the formula). Used to carry a value into a newly activated tool.
     *
     * @return a parseable numeric string, or {@code null} if no number is available.
     */
    @Nullable
    String getCurrentDisplayNumber();

    /**
     * Hand the display over to a tool: reset any transforms/text set by the calculator so the
     * tool owns the surface cleanly. Called by {@code ToolManager} right before a non-calculator
     * tool's {@link ToolMode#onActivate}.
     */
    void prepareForToolDisplay();

    /**
     * Reclaim the display for the calculator: restore the evaluator-driven formula/result.
     * Called by {@code ToolManager} when (re)entering {@link ToolId#CALCULATOR}.
     */
    void restoreCalculatorDisplay();

    /**
     * Run the given action on the UI thread.
     */
    void runOnUiThread(@NonNull Runnable action);

    /**
     * Container inside the display area into which the active tool may mount its mode-specific
     * controls (e.g. unit / currency selectors). The host owns the container; the tool adds and
     * removes its view on activate / deactivate. May be {@code null} if the host has no slot.
     */
    @Nullable
    ViewGroup getToolControlSlot();

    /**
     * Enlarge (or restore) the display region to give input-heavy tools more vertical room.
     * Called by {@code ToolManager} based on {@link ToolMode#wantsExpandedDisplay()}.
     */
    void setExpandedDisplay(boolean expanded);

    /** Relabel/restore the scientific pad for Programmer mode and enable valid radix digits. */
    void setProgrammerPadMode(boolean enabled, int radix);

    /** Re-evaluate mode-specific items in the activity overflow menu. */
    void refreshOptionsMenu();
}
