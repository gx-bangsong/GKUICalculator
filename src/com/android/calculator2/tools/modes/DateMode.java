/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.model.DateDelta;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Date-interval tool. Uses date pickers (not the numeric pad); shows the day count and the
 * calendar-based difference between two dates.
 */
public class DateMode implements ToolMode {

    @Nullable
    private ToolHost mHost;
    @Nullable
    private View mControlRoot;
    @Nullable
    private LocalDate mStart;
    @Nullable
    private LocalDate mEnd;

    private final DateTimeFormatter mFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault());

    @NonNull
    @Override
    public String getId() {
        return ToolId.DATE;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_date;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        mHost = host;
        if (mStart == null) {
            mStart = LocalDate.now();
            mEnd = mStart.plusDays(30);
        }
        mountControls(host.getContext());
        recomputeAndDisplay();
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
        final ViewGroup slot = host.getToolControlSlot();
        if (slot != null) {
            slot.removeAllViews();
            slot.setVisibility(View.GONE);
        }
        mControlRoot = null;
        mHost = null;
    }

    @Override
    public void onDigit(int digit) {
        // Date tool uses pickers, not the numeric pad.
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

    private void mountControls(@NonNull Context context) {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        final ViewGroup slot = host.getToolControlSlot();
        if (slot == null) {
            return;
        }
        mControlRoot = LayoutInflater.from(context).inflate(R.layout.tool_date_control, slot, false);
        final TextView startBtn = mControlRoot.findViewById(R.id.date_start);
        final TextView endBtn = mControlRoot.findViewById(R.id.date_end);
        if (startBtn != null) {
            startBtn.setOnClickListener(v -> showPicker(true));
        }
        if (endBtn != null) {
            endBtn.setOnClickListener(v -> showPicker(false));
        }
        refreshDateLabels();
        slot.removeAllViews();
        slot.addView(mControlRoot);
        slot.setVisibility(View.VISIBLE);
    }

    private void showPicker(final boolean start) {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        final LocalDate current = start ? mStart : mEnd;
        final LocalDate base = current != null ? current : LocalDate.now();
        new DatePickerDialog(host.getContext(),
                (view, year, month, day) -> {
                    final LocalDate picked = LocalDate.of(year, month + 1, day);
                    if (start) {
                        mStart = picked;
                    } else {
                        mEnd = picked;
                    }
                    refreshDateLabels();
                    recomputeAndDisplay();
                },
                base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()).show();
    }

    private void refreshDateLabels() {
        if (mControlRoot == null) {
            return;
        }
        final TextView startBtn = mControlRoot.findViewById(R.id.date_start);
        final TextView endBtn = mControlRoot.findViewById(R.id.date_end);
        if (startBtn != null && mStart != null) {
            startBtn.setText(mStart.format(mFormatter));
        }
        if (endBtn != null && mEnd != null) {
            endBtn.setText(mEnd.format(mFormatter));
        }
    }

    private void recomputeAndDisplay() {
        final ToolHost host = mHost;
        if (host == null || mStart == null || mEnd == null) {
            return;
        }
        final DateDelta.Result r = DateDelta.between(mStart, mEnd);
        if (r == null) {
            host.setToolFormula("—");
            host.setToolResult("—");
            return;
        }
        final Context ctx = host.getContext();
        // Formula line: total days.
        host.setToolFormula(ctx.getString(R.string.tool_date_days, r.days));
        // Result line: weeks + remaining days + total hours (no redundant period breakdown).
        final long days = r.days;
        final long weeks = days / 7;
        final long remDays = days % 7;
        final long hours = days * 24;
        final StringBuilder sb = new StringBuilder();
        if (weeks > 0) {
            sb.append(weeks).append(ctx.getString(R.string.tool_date_weeks)).append(" ");
        }
        sb.append(remDays).append(ctx.getString(R.string.tool_date_days_short));
        if (hours > 0) {
            sb.append(" · ").append(hours).append(ctx.getString(R.string.tool_date_hours));
        }
        if (r.swapped) {
            sb.append("  (").append(ctx.getString(R.string.tool_date_swapped)).append(")");
        }
        host.setToolResult(sb.toString().trim());
    }

    @NonNull
    private static String periodText(@NonNull Context ctx, @NonNull java.time.Period period,
            boolean swapped) {
        final StringBuilder sb = new StringBuilder();
        if (period.getYears() > 0) {
            sb.append(period.getYears())
                    .append(ctx.getString(R.string.tool_date_years)).append(' ');
        }
        if (period.getMonths() > 0) {
            sb.append(period.getMonths())
                    .append(ctx.getString(R.string.tool_date_months)).append(' ');
        }
        if (period.getDays() > 0 || sb.length() == 0) {
            sb.append(period.getDays())
                    .append(ctx.getString(R.string.tool_date_days_short));
        }
        if (swapped) {
            sb.append("  (").append(ctx.getString(R.string.tool_date_swapped)).append(')');
        }
        return sb.toString().trim();
    }
}
