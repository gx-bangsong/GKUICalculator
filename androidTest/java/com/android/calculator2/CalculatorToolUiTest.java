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

    /** 中文大写 applies to the primary mortgage result (月供/首月), not only its totals. */
    @Test
    public void uppercaseNumbersIncludePrimaryMortgagePayment() {
        onView(withId(R.id.tool_more_button)).perform(click());
        onView(allOf(withId(R.id.tool_panel_item_text),
                withText(R.string.tool_mortgage), isDisplayed())).perform(click());
        // The off state must remain obvious and tappable; after tapping, the glyph itself previews
        // the Arabic-to-financial conversion.
        onView(withId(R.id.uppercase_toggle))
                .check(matches(withText(R.string.tool_uppercase_toggle)))
                .perform(click())
                .check(matches(withText(R.string.tool_uppercase_toggle_on)));

        // 人民币大写 always gives the calculated monthly amount a 元/角/分 suffix. The old
        // behavior left this formula line as digits while only the secondary line used 大写.
        onView(withId(R.id.formula)).check(matches(withText(containsString("元"))));
    }

    /** Unit conversion accepts +, −, ×, ÷ and equals from the calculator pad. */
    @Test
    public void unitConverterAcceptsArithmetic() {
        onView(withId(R.id.tool_more_button)).perform(click());
        onView(allOf(withId(R.id.tool_panel_item_text),
                withText(R.string.tool_unit), isDisplayed())).perform(click());
        onView(withId(R.id.clr)).perform(click());
        onView(withId(R.id.digit_2)).perform(click());
        onView(withId(R.id.op_add)).perform(click());
        onView(withId(R.id.digit_3)).perform(click());
        onView(withId(R.id.op_mul)).perform(click());
        onView(withId(R.id.digit_4)).perform(click());
        onView(withId(R.id.eq)).perform(click());

        onView(withId(R.id.unit_input_text)).check(matches(withText("14")));
    }

    /** Currency keeps 大写 readings in their rows and accepts arithmetic input. */
    @Test
    public void currencyConverterKeepsUppercaseInlineAndAcceptsArithmetic() {
        onView(withId(R.id.tool_more_button)).perform(click());
        onView(allOf(withId(R.id.tool_panel_item_text),
                withText(R.string.tool_currency), isDisplayed())).perform(click());
        onView(withId(R.id.clr)).perform(click());
        onView(withId(R.id.digit_8)).perform(click());
        onView(withId(R.id.op_div)).perform(click());
        onView(withId(R.id.digit_2)).perform(click());
        onView(withId(R.id.eq)).perform(click());
        onView(withId(R.id.currency_input_text)).check(matches(withText("4")));

        onView(withId(R.id.uppercase_toggle)).perform(click());
        onView(withId(R.id.currency_input_uppercase)).check(matches(isDisplayed()));
        onView(withId(R.id.currency_result_uppercase)).check(matches(isDisplayed()));
        // The shared result area stays empty, so an optional second row cannot overlap it.
        onView(withId(R.id.result)).check(matches(withText("")));
    }
}
