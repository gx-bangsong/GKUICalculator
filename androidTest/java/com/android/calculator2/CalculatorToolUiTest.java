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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

    /**
     * Keep headless CI runs interactive. Some fresh AVD boots surface Android's
     * System UI "isn't responding" dialog after the Gradle install phase; dismissing its Wait
     * action (or closing the still-stalled System UI process) restores focus before Espresso.
     */
    @Before
    public void clearSystemUiRecoveryPrompt() {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            device.wakeUp();
            device.executeShellCommand("wm dismiss-keyguard");
            final UiObject waitButton = device.findObject(new UiSelector().text("Wait"));
            if (waitButton.waitForExists(5000)) {
                Log.i("CalculatorToolUiTest", "Dismissing the System UI ANR prompt with Wait");
                waitButton.click();
                device.waitForIdle(5000);
                final UiObject stillWaiting = device.findObject(new UiSelector().text("Wait"));
                if (stillWaiting.waitForExists(2000)) {
                    final UiObject closeApp = device.findObject(new UiSelector().text("Close app"));
                    if (closeApp.waitForExists(1000)) {
                        Log.w("CalculatorToolUiTest", "System UI is still stalled; closing it to recover focus");
                        closeApp.click();
                        device.waitForIdle(10000);
                    }
                }
            } else {
                Log.i("CalculatorToolUiTest", "No System UI ANR prompt appeared before the test");
            }
        } catch (Exception exception) {
            Log.w("CalculatorToolUiTest", "Unable to recover emulator window focus", exception);
        }
    }

    /** The "more" overlay must be collapsed (not displayed) when the app launches. */
    @Test
    public void overlayStartsCollapsed() {
        onView(withId(R.id.tool_panel_overlay))
                .check(matches(org.hamcrest.Matchers.not(isDisplayed())));
    }

    /** A first-run install opens on the standard keypad with the science pad one tap away. */
    @Test
    public void firstRunUsesStandardKeypad() {
        resetTestPreferences();
        onView(withId(R.id.advanced_pad))
                .check(matches(org.hamcrest.Matchers.not(isDisplayed())));
        onView(withId(R.id.scientific_toggle))
                .check(matches(withText(R.string.tool_scientific_show)));
    }

    /** Unit categories stay as inline pills, rather than opening a full-screen unit chooser. */
    @Test
    public void unitCategoriesRemainInlinePills() {
        openUnitConverter();
        onView(withId(R.id.unit_category_pills)).check(matches(isDisplayed()));
        onView(withText(R.string.tool_units_area)).perform(click());
        onView(withId(R.id.unit_from)).check(matches(isDisplayed()));
        onView(withId(R.id.unit_to)).check(matches(isDisplayed()));
    }

    /** Capture the two key UI states so the emulator job can publish screenshots for review. */
    @Test
    public void captureCalculatorScreenshot() throws IOException {
        resetTestPreferences();
        onView(withId(R.id.input_pad)).check(matches(isDisplayed()));
        saveScreenshot("calculator-default.png");
    }

    @Test
    public void captureUnitConverterScreenshot() throws IOException {
        resetTestPreferences();
        openUnitConverter();
        onView(withId(R.id.unit_category_pills)).check(matches(isDisplayed()));
        saveScreenshot("calculator-units.png");
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

    /** Currency replaces the existing value column with 大写 and accepts arithmetic input. */
    @Test
    public void currencyConverterUsesValueColumnForUppercaseAndAcceptsArithmetic() {
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
        // The same circled amount view now shows 大写; no duplicate reading is drawn below.
        onView(withId(R.id.currency_input_text)).check(matches(withText("肆")));
        onView(withId(R.id.result)).check(matches(withText("")));
    }

    private void resetTestPreferences() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("calc_tools", Context.MODE_PRIVATE)
                .edit().clear().commit();
        mActivityRule.getScenario().recreate();
        clearSystemUiRecoveryPrompt();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void openUnitConverter() {
        onView(withId(R.id.tool_more_button)).perform(click());
        onView(allOf(withId(R.id.tool_panel_item_text),
                withText(R.string.tool_unit), isDisplayed())).perform(click());
    }

    private static void saveScreenshot(String name) throws IOException {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        final UiObject waitButton = device.findObject(new UiSelector().text("Wait"));
        final UiObject closeAppButton = device.findObject(new UiSelector().text("Close app"));
        final UiObject systemUiAnrTitle = device.findObject(
                new UiSelector().textContains("isn't responding"));
        assertFalse("System UI recovery dialog is visible; refusing to save an obscured screenshot",
                waitButton.waitForExists(500) || closeAppButton.waitForExists(500)
                        || systemUiAnrTitle.waitForExists(500));

        final Bitmap screenshot = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().takeScreenshot();
        assertNotNull("UI Automation did not return a screenshot", screenshot);

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final File parent = context.getExternalFilesDir("ui-screenshots");
        if (parent == null) {
            screenshot.recycle();
            throw new IOException("External app files directory is unavailable");
        }
        if (!parent.exists() && !parent.mkdirs()) {
            screenshot.recycle();
            throw new IOException("Could not create screenshot directory: " + parent);
        }
        final File file = new File(parent, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Could not encode screenshot: " + file);
            }
            Log.i("CalculatorToolUiTest", "Saved in-app screenshot: " + file.getAbsolutePath());
        } finally {
            screenshot.recycle();
        }
    }
}
