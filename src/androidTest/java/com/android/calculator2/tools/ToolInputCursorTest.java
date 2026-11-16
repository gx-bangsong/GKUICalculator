/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static androidx.test.uiautomator.UiDevice.getInstance;
import static androidx.test.uiautomator.UiSelector;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import com.android.calculator2.Calculator;
import com.android.calculator2.R;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Regression tests for cursor-aware digit input in tool input fields (mortgage, tax, BMI).
 *
 * <p>These tests simulate real user workflows: entering digits into a tool's input field, losing
 * focus, tapping at a specific position within the field's value, and pressing a calculator pad
 * key. The expected behavior is that the digit is inserted at the tapped position rather than
 * always appended to the end.
 *
 * <p>Run with: {@code ./gradlew connectedDebugAndroidTest}
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToolInputCursorTest {

    private static final String PACKAGE_NAME = "com.android.calculator2.dev";
    private Context mContext;

    @Rule
    public ActivityTestRule<Calculator> mActivityRule =
            new ActivityTestRule<>(Calculator.class);

    private UiDevice mDevice;

    @Before
    public void setUp() {
        mDevice = getInstance(getInstrumentation());
        mContext = getInstrumentation().getTargetContext();
        mDevice.waitForIdle();
    }

    /** Navigate to the BMI tool by tapping its icon in the bottom navigation bar. */
    private void openBmiTool() {
        mDevice.findObject(new UiSelector().descriptionContains("BMI")).click();
        mDevice.waitForIdle();
    }

    /** Get the localized label string for a field. */
    private String fieldLabel(int labelResId) {
        return mContext.getString(labelResId);
    }

    /** Enter a string of digits into the given field using the calculator pad. */
    private void typeDigitsIntoField(int fieldId, String digits) {
        onView(withId(fieldId)).perform(click());
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            int digitResId;
            switch (c) {
                case '0': digitResId = R.id.digit_0; break;
                case '1': digitResId = R.id.digit_1; break;
                case '2': digitResId = R.id.digit_2; break;
                case '3': digitResId = R.id.digit_3; break;
                case '4': digitResId = R.id.digit_4; break;
                case '5': digitResId = R.id.digit_5; break;
                case '6': digitResId = R.id.digit_6; break;
                case '7': digitResId = R.id.digit_7; break;
                case '8': digitResId = R.id.digit_8; break;
                case '9': digitResId = R.id.digit_9; break;
                default: throw new IllegalArgumentException("Unexpected digit: " + c);
            }
            onView(withId(digitResId)).perform(click());
        }
        mDevice.waitForIdle();
    }

    /** Tap at a relative x-position within the bounds of field 0's EditText. */
    private void tapField0AtRelativeX(float relativeX) {
        UiObject field0 =
                mDevice.findObject(new UiSelector()
                        .resourceId(PACKAGE_NAME + ":id/tool_field_0"));
        Rect bounds = field0.getBounds();
        int tapX = bounds.left + (int) (bounds.width() * relativeX);
        int tapY = bounds.centerY();
        mDevice.click(tapX, tapY);
        mDevice.waitForIdle();
    }

    /** Press a digit key on the calculator pad by resource ID. */
    private void pressDigit(int digitResId) {
        onView(withId(digitResId)).perform(click());
        mDevice.waitForIdle();
    }

    /** Full expected text for field 0: "label: value". */
    private String expectedField0(int labelResId, String value) {
        return fieldLabel(labelResId) + ": " + value;
    }

    /**
     * Regression test: enter "1234" into field 0, lose focus, tap between "2" and "3", press "9",
     * and assert the result is "12934" (not "12349").
     */
    @Test
    public void testMiddleInsertionAfterFocusLoss() {
        openBmiTool();

        // 1. Enter "1234" into the height field (field 0)
        typeDigitsIntoField(R.id.tool_field_0, "1234");

        // Verify field 0 shows "Height: 1234"
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "1234"))));

        // 2. Tap field 1 (weight) to cause field 0 to lose focus
        onView(withId(R.id.tool_field_1)).perform(click());
        mDevice.waitForIdle();

        // 3. Tap between "2" and "3" within field 0.
        // The text is "Height: 1234". The "Height: " prefix is roughly 35-40% of width.
        // Tapping at ~45% lands between "2" and "3".
        tapField0AtRelativeX(0.45f);

        // 4. Press "9" on the calculator pad
        pressDigit(R.id.digit_9);

        // 5. Assert field 0 now shows "Height: 12934" (inserted in middle, not appended)
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "12934"))));
    }

    /**
     * Test that pressing delete with cursor at position 0 does nothing (does not delete the last
     * digit).
     */
    @Test
    public void testDeleteAtPosition0DoesNothing() {
        openBmiTool();

        // 1. Enter "1234" into field 0
        typeDigitsIntoField(R.id.tool_field_0, "1234");

        // 2. Tap field 1 to lose focus from field 0
        onView(withId(R.id.tool_field_1)).perform(click());
        mDevice.waitForIdle();

        // 3. Tap right after the "Height: " prefix (position 0 of the value "1234")
        tapField0AtRelativeX(0.41f);

        // 4. Press delete - should do nothing since cursor is at position 0
        onView(withId(R.id.del)).perform(click());
        mDevice.waitForIdle();

        // 5. Assert field 0 still shows "Height: 1234" (unchanged)
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "1234"))));
    }

    /**
     * Test consecutive cursor repositioning within the same field: enter "1234", tap between
     * "1" and "2", press "5", then tap between "5" and "2", press "6". Result should be "156234".
     */
    @Test
    public void testConsecutiveCursorRepositioning() {
        openBmiTool();

        // 1. Enter "1234" into field 0
        typeDigitsIntoField(R.id.tool_field_0, "1234");

        // 2. Tap field 1 to lose focus
        onView(withId(R.id.tool_field_1)).perform(click());
        mDevice.waitForIdle();

        // 3. Tap between "1" and "2" (~42% of field width)
        tapField0AtRelativeX(0.42f);

        // 4. Press "5" → should become "15234"
        pressDigit(R.id.digit_5);
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "15234"))));

        // 5. Tap field 1 again to reset focus state
        onView(withId(R.id.tool_field_1)).perform(click());
        mDevice.waitForIdle();

        // 6. Tap between "5" and "2" in "15234" (~48% of field width)
        tapField0AtRelativeX(0.48f);

        // 7. Press "6" → should become "156234"
        pressDigit(R.id.digit_6);
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "156234"))));
    }

    /**
     * Test switching between fields: enter data in field 0, switch to field 1, enter data there,
     * switch back to field 0, and verify field 0's data is intact.
     */
    @Test
    public void testSwitchingBetweenFields() {
        openBmiTool();

        // 1. Enter "1234" into field 0 (height)
        typeDigitsIntoField(R.id.tool_field_0, "1234");
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "1234"))));

        // 2. Switch to field 1 (weight) and enter "5678"
        typeDigitsIntoField(R.id.tool_field_1, "5678");
        onView(withId(R.id.tool_field_1))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_weight, "5678"))));

        // 3. Switch back to field 0 - its data should still be "1234"
        onView(withId(R.id.tool_field_0)).perform(click());
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "1234"))));
    }

    /**
     * Test entering data after clearing a field: enter "1234", clear, then enter "99" and verify.
     */
    @Test
    public void testEnterAfterClear() {
        openBmiTool();

        // 1. Enter "1234" into field 0
        typeDigitsIntoField(R.id.tool_field_0, "1234");
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "1234"))));

        // 2. Clear field 0 (tap AC)
        onView(withId(R.id.clr)).perform(click());
        mDevice.waitForIdle();

        // Field 0 should now show just the label (empty value)
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(fieldLabel(R.string.tool_bmi_field_height))));

        // 3. Enter "99" into field 0
        typeDigitsIntoField(R.id.tool_field_0, "99");

        // 4. Assert field 0 shows "Height: 99"
        onView(withId(R.id.tool_field_0))
                .check(matches(withText(expectedField0(R.string.tool_bmi_field_height, "99"))));
    }
}
