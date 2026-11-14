/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.app.Application;

import com.android.calculator2.tools.modes.RelationshipMode;
import com.google.android.material.color.DynamicColors;

public class CalculatorApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        // The kinship tables expand once per process on interpreted ART the first time (a few
        // seconds on a device, longer on a shared CI emulator). Starting the prepare here,
        // before any activity, means the mode pill's one-tap kinship segment never meets a
        // cold table cache in normal use.
        RelationshipMode.preload(this);
    }
}
