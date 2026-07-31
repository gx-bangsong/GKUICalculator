/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented UI tests for the life-calculation tool bar.
 * <p>
 * Run on a connected device/emulator with:
 * <pre>./gradlew connectedDebugAndroidTest</pre>
 * These guard the runtime behaviors that compiled-but-broken changes kept regressing
 * (e.g. the overlay being a MotionLayout child that got forced visible at launch).
 */
@RunWith(AndroidJUnit4.class)
public class CalculatorToolUiTest {

    @Rule
    public ActivityScenarioRule<Calculator> mActivityRule =
            new ActivityScenarioRule<>(Calculator.class);

    /** The "more" overlay must be collapsed (not displayed) when the app launches. */
    @Test
    public void overlayStartsCollapsed() {
        onView(withId(R.id.tool_panel_overlay))
                .check(matches(org.hamcrest.Matchers.not(isDisplayed())));
    }

    /** Tapping the "more" button must expand the overlay. */
    @Test
    public void moreButtonExpandsOverlay() {
        onView(withId(R.id.tool_more_button)).perform(click());
        onView(withId(R.id.tool_panel_overlay)).check(matches(isDisplayed()));
    }

    /** Tapping "more" again must collapse the overlay. */
    @Test
    public void moreButtonCollapsesOverlay() {
        onView(withId(R.id.tool_more_button)).perform(click());   // expand
        onView(withId(R.id.tool_more_button)).perform(click());   // collapse
        onView(withId(R.id.tool_panel_overlay))
                .check(matches(org.hamcrest.Matchers.not(isDisplayed())));
    }
}
