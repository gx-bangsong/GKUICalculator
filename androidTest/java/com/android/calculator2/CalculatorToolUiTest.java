/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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

    private ActivityScenario<Calculator> mActivityScenario;

    /**
     * Recover System UI before launching the calculator: closing a persistently stalled System UI
     * process can destroy an already-launched activity, so start ActivityScenario afterward.
     */
    @Before
    public void recoverSystemUiThenLaunchCalculator() {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            device.wakeUp();
            device.executeShellCommand("wm dismiss-keyguard");
            recoverSystemUiPrompt(device, 5000);
        } catch (Exception exception) {
            Log.w("CalculatorToolUiTest", "Unable to recover emulator window focus", exception);
        }
        mActivityScenario = ActivityScenario.launch(Calculator.class);
    }

    @After
    public void closeCalculatorActivity() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    /** A first-run install opens on the standard keypad, with 123 selected in the mode control. */
    @Test
    public void firstRunUsesStandardKeypad() {
        resetTestPreferences();
        onView(withId(R.id.advanced_pad))
                .check(matches(org.hamcrest.Matchers.not(isDisplayed())));
        onView(withId(R.id.mode_segment_basic)).check(matches(isSelected()));
        onView(withId(R.id.mode_segment_scientific)).check(matches(not(isSelected())));
    }

    /** The fx segment is the only way to reach the scientific pad from the calculator. */
    @Test
    public void scientificSegmentTogglesPad() {
        resetTestPreferences();
        onView(withId(R.id.mode_segment_scientific)).perform(click());
        onView(withId(R.id.advanced_pad)).check(matches(isDisplayed()));
        onView(withId(R.id.mode_segment_scientific)).check(matches(isSelected()));
        onView(withId(R.id.mode_segment_basic)).check(matches(not(isSelected())));

        onView(withId(R.id.mode_segment_basic)).perform(click());
        onView(withId(R.id.advanced_pad))
                .check(matches(org.hamcrest.Matchers.not(isDisplayed())));
        onView(withId(R.id.mode_segment_basic)).check(matches(isSelected()));
    }

    /**
     * Google Calculator keeps the display and the 123 / fx / ⇄ strip perfectly still when the
     * scientific pad opens: the pad block has a fixed height and only its rows are re-divided.
     * Ours used to grow a separate 0.17 H band and push the display up, so lock the geometry.
     */
    @Test
    public void scientificPadDoesNotMoveDisplay() {
        resetTestPreferences();
        final int[] before = new int[4];
        final int[] after = new int[4];
        mActivityScenario.onActivity(activity -> {
            final View display = activity.findViewById(R.id.display);
            final View bar = activity.findViewById(R.id.tool_bar);
            before[0] = display.getHeight();
            before[1] = display.getTop();
            before[2] = bar.getTop();
            before[3] = activity.findViewById(R.id.input_pad).getHeight();
        });
        onView(withId(R.id.mode_segment_scientific)).perform(click());
        onView(withId(R.id.advanced_pad)).check(matches(isDisplayed()));
        mActivityScenario.onActivity(activity -> {
            final View display = activity.findViewById(R.id.display);
            final View bar = activity.findViewById(R.id.tool_bar);
            after[0] = display.getHeight();
            after[1] = display.getTop();
            after[2] = bar.getTop();
            after[3] = activity.findViewById(R.id.input_pad).getHeight();
        });
        assertEquals("display height changed when fx opened", before[0], after[0]);
        assertEquals("display top changed when fx opened", before[1], after[1]);
        assertEquals("mode strip moved when fx opened", before[2], after[2]);
        assertTrue("digit rows should get shorter, not move", after[3] < before[3]);
    }

    /** The converter segment opens a converter without leaving the mode control. */
    @Test
    public void convertSegmentOpensConverter() {
        resetTestPreferences();
        onView(withId(R.id.mode_segment_convert)).perform(click());
        onView(withId(R.id.mode_segment_convert)).check(matches(isSelected()));
        onView(withId(R.id.unit_category_pills)).check(matches(isDisplayed()));

        // ⇄ again flips to the other converter, and 123 returns to the calculator.
        onView(withId(R.id.mode_segment_convert)).perform(click());
        onView(withId(R.id.currency_input_text)).check(matches(isDisplayed()));
        onView(withId(R.id.mode_segment_basic)).perform(click());
        onView(withId(R.id.mode_segment_basic)).check(matches(isSelected()));
        onView(withId(R.id.input_pad)).check(matches(isDisplayed()));
    }

    /**
     * Every tool is reachable from the mode control itself: the programmer calculator and the
     * life-calculation tools (BMI, income tax, mortgage, date, relatives) are icon-only segments
     * built by ToolBarView.render(), so they are identified by content description.
     */
    @Test
    public void everyToolHasAModeSegment() {
        resetTestPreferences();
        onView(withContentDescription(R.string.tool_programmer)).perform(click());
        onView(withContentDescription(R.string.tool_programmer)).check(matches(isSelected()));
        onView(withId(R.id.mode_segment_basic)).check(matches(not(isSelected())));

        onView(withContentDescription(R.string.tool_bmi)).perform(click());
        onView(withContentDescription(R.string.tool_bmi)).check(matches(isSelected()));
        onView(withContentDescription(R.string.tool_programmer)).check(matches(not(isSelected())));

        onView(withId(R.id.mode_segment_basic)).perform(click());
        onView(withId(R.id.mode_segment_basic)).check(matches(isSelected()));
    }

    /**
     * The pill is the only tool switcher, so on a fresh install every tool must already be a
     * segment inside it and fully on screen: none hidden behind a menu or scrolled out of view.
     */
    @Test
    public void pillShowsEveryToolWithoutScrolling() {
        resetTestPreferences();
        final int[] tools = {R.string.tool_programmer, R.string.tool_bmi, R.string.tool_tax,
                R.string.tool_mortgage, R.string.tool_date, R.string.tool_relationship};
        for (int tool : tools) {
            onView(withContentDescription(tool)).check(matches(isCompletelyDisplayed()));
        }
        mActivityScenario.onActivity(activity -> {
            final View pill = activity.findViewById(R.id.mode_segments_scroll);
            final View row = activity.findViewById(R.id.mode_segments);
            assertEquals("segments must fill the pill exactly", pill.getWidth(), row.getWidth());
            assertEquals(9, ((android.view.ViewGroup) row).getChildCount());
        });
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
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        boolean keypadShown = false;
        String diagnostics = "";
        for (int attempt = 1; attempt <= 3 && !keypadShown; attempt++) {
            if (attempt > 1) {
                // A stalled System UI or a missed first draw can leave the window unattached on
                // the hosted emulator; give it a recovery pass before judging the layout again.
                Log.w("CalculatorToolUiTest", "Keypad not shown on attempt " + (attempt - 1)
                        + "; recovering emulator before retry");
                try {
                    device.wakeUp();
                    device.executeShellCommand("wm dismiss-keyguard");
                    recoverSystemUiPrompt(device, 1000);
                    mActivityScenario.recreate();
                } catch (Exception exception) {
                    Log.w("CalculatorToolUiTest", "Emulator recovery pass failed", exception);
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            }
            final boolean[] shown = new boolean[1];
            final String[] detail = new String[1];
            mActivityScenario.onActivity(activity -> {
                final View inputPad = activity.findViewById(R.id.input_pad);
                shown[0] = inputPad != null && inputPad.isShown();
                detail[0] = describeViewChain(activity, inputPad);
            });
            keypadShown = shown[0];
            diagnostics = detail[0];
        }
        assertTrue("Calculator keypad is not visible. " + diagnostics, keypadShown);
        saveScreenshot("calculator-default.png");
    }

    /** Human-readable state of a view and its ancestors, so a failure names the culprit. */
    private static String describeViewChain(android.app.Activity activity, View view) {
        final StringBuilder sb = new StringBuilder();
        sb.append("activity(finishing=").append(activity.isFinishing())
                .append(", destroyed=").append(activity.isDestroyed())
                .append(", focused=").append(activity.hasWindowFocus()).append(')');
        if (view == null) {
            return sb.append(" input_pad=null").toString();
        }
        View current = view;
        while (current != null) {
            final String name = current.getId() == View.NO_ID
                    ? current.getClass().getSimpleName()
                    : current.getResources().getResourceEntryName(current.getId());
            sb.append(" <- ").append(name)
                    .append("(vis=").append(visibilityName(current))
                    .append(", shown=").append(current.isShown())
                    .append(", ").append(current.getWidth()).append('x')
                    .append(current.getHeight()).append(')');
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return sb.toString();
    }

    private static String visibilityName(View view) {
        switch (view.getVisibility()) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return Integer.toString(view.getVisibility());
        }
    }

    @Test
    public void captureUnitConverterScreenshot() throws IOException {
        resetTestPreferences();
        openUnitConverter();
        onView(withId(R.id.unit_category_pills)).check(matches(isDisplayed()));
        saveScreenshot("calculator-units.png");
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
        onView(withContentDescription(R.string.tool_mortgage)).perform(click());

        final Context context = ApplicationProvider.getApplicationContext();
        onView(withId(R.id.result)).check(matches(withText(containsString(
                context.getString(R.string.tool_mortgage_interest)))));
    }

    /** 中文大写 applies to the primary mortgage result (月供/首月), not only its totals. */
    @Test
    public void uppercaseNumbersIncludePrimaryMortgagePayment() {
        onView(withContentDescription(R.string.tool_mortgage)).perform(click());
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
        onView(withId(R.id.mode_segment_convert)).perform(click());
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
        // The convert segment opens units first; a second tap switches to currency.
        onView(withId(R.id.mode_segment_convert)).perform(click());
        onView(withId(R.id.mode_segment_convert)).perform(click());
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
        mActivityScenario.recreate();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void openUnitConverter() {
        onView(withId(R.id.mode_segment_convert)).perform(click());
    }

    private void saveScreenshot(String name) throws IOException {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String packageName = context.getPackageName();
        final String path = "/sdcard/" + name;
        boolean capturedUnobstructedFrame = false;

        // System UI can become unresponsive again while the emulator is settling. If a recovery
        // dialog appears across a capture, dismiss it and overwrite the file with a clean frame.
        for (int attempt = 1; attempt <= 3; attempt++) {
            recoverSystemUiPrompt(device, attempt == 1 ? 1000 : 250);
            ensureCalculatorForeground(device, packageName);
            if (hasSystemUiRecoveryPrompt(device, 250)
                    || !packageName.equals(device.getCurrentPackageName())) {
                Log.w("CalculatorToolUiTest", "Emulator is not ready for screenshot attempt " + attempt);
                continue;
            }

            device.executeShellCommand("screencap -p " + path);
            if (!hasSystemUiRecoveryPrompt(device, 1000)
                    && packageName.equals(device.getCurrentPackageName())) {
                capturedUnobstructedFrame = true;
                break;
            }
            Log.w("CalculatorToolUiTest", "System UI recovery interrupted screenshot attempt " + attempt);
        }

        assertTrue("Could not capture a calculator screenshot without a System UI recovery dialog",
                capturedUnobstructedFrame);
        assertEquals("Calculator is not the foreground app at screenshot time",
                packageName, device.getCurrentPackageName());
        Log.i("CalculatorToolUiTest", "Saved unobstructed test-generated screenshot: " + path);
    }

    private static void recoverSystemUiPrompt(UiDevice device, long initialWaitMillis) {
        if (!hasSystemUiRecoveryPrompt(device, initialWaitMillis)) {
            Log.i("CalculatorToolUiTest", "No System UI ANR prompt is visible");
            return;
        }

        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                final UiObject waitButton = device.findObject(new UiSelector().text("Wait"));
                if (waitButton.waitForExists(250)) {
                    Log.i("CalculatorToolUiTest", "Dismissing the System UI ANR prompt with Wait");
                    waitButton.click();
                    device.waitForIdle(5000);
                }
                if (!hasSystemUiRecoveryPrompt(device, 500)) {
                    return;
                }

                final UiObject closeAppButton = device.findObject(new UiSelector().text("Close app"));
                if (closeAppButton.waitForExists(250)) {
                    Log.w("CalculatorToolUiTest", "System UI is still stalled; closing it to recover focus");
                    closeAppButton.click();
                    device.waitForIdle(10000);
                }
            }
            Log.w("CalculatorToolUiTest", "System UI recovery prompt remained after dismissal attempts");
        } catch (Exception exception) {
            Log.w("CalculatorToolUiTest", "Unable to recover emulator window focus", exception);
        }
    }

    private static boolean hasSystemUiRecoveryPrompt(UiDevice device, long timeoutMillis) {
        final UiObject waitButton = device.findObject(new UiSelector().text("Wait"));
        final UiObject closeAppButton = device.findObject(new UiSelector().text("Close app"));
        final UiObject systemUiAnrTitle = device.findObject(
                new UiSelector().textContains("isn't responding"));
        return waitButton.waitForExists(timeoutMillis)
                || closeAppButton.waitForExists(100)
                || systemUiAnrTitle.waitForExists(100);
    }

    private static void ensureCalculatorForeground(UiDevice device, String packageName)
            throws IOException {
        if (!packageName.equals(device.getCurrentPackageName())) {
            device.executeShellCommand("am start -n " + packageName + "/.Calculator");
            device.waitForIdle(5000);
        }
    }
}
