/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import com.android.calculator2.tools.modes.RelationshipMode;

public class CalculatorApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        RelationshipMode.prewarm(this);
    }
}
