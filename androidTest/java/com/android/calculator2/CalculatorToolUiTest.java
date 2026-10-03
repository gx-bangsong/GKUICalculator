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
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
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

    /** A pending instant result from the calculator must not overwrite a newly opened tool. */
    @Test
    public void pendingCalculatorResultDoesNotReplaceMortgageResult() {
        onView(withId(R.id.clr)).perform(click());
        onView(withId(R.id.digit_9)).perform(click());
        onView(withId(R.id.op_mul)).perform(click());
        onView(withId(R.id.digit_9)).perform(click());
        // Do not press equals: this leaves the calculator's instant evaluation active, matching
        // the regression where its result was redrawn after the mortgage display was mounted.
        onView(withId(R.id.tool_more_button)).perform(click());
        onView(allOf(withId(R.id.tool_panel_item_text),
                withText(R.string.tool_mortgage), isDisplayed())).perform(click());

        final Context context = ApplicationProvider.getApplicationContext();
        onView(withId(R.id.result)).check(matches(withText(containsString(
                context.getString(R.string.tool_mortgage_interest)))));
    }
}
