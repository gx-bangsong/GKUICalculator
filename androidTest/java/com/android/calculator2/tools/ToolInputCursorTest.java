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
import static org.junit.Assert.assertEquals;
import org.hamcrest.Matcher;

import android.content.Context;
import android.os.SystemClock;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import org.junit.After;
import org.junit.Before;
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

    private Context mContext;
    private UiDevice mDevice;
    private String mHeightLabel;
    private String mWeightLabel;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mHeightLabel = mContext.getString(com.android.calculator2.R.string.tool_bmi_field_height);
        mWeightLabel = mContext.getString(com.android.calculator2.R.string.tool_bmi_field_weight);
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevice.waitForIdle();
    }

    /** Navigate to the BMI tool via its icon in the bottom navigation bar. */
    private void openBmiTool() {
        try {
            mDevice.findObject(new androidx.test.uiautomator.UiSelector()
                    .descriptionContains("BMI")).click();
        } catch (UiObjectNotFoundException e) {
            throw new AssertionError("BMI tool icon not found", e);
        }
        mDevice.waitForIdle();
    }

    /** Prefix for field 0 (height): "label: ". */
    private String field0Prefix() {
        return mHeightLabel + ": ";
    }

    /** Prefix for field 1 (weight): "label: ". */
    private String field1Prefix() {
        return mWeightLabel + ": ";
    }

    /** Enter a string of digits into the given field using the calculator pad. */
    private void typeDigitsIntoField(int fieldId, String digits) {
        onView(withId(fieldId)).perform(click());
        for (char c : digits.toCharArray()) {
            int resId;
            switch (c) {
                case '0': resId = com.android.calculator2.R.id.digit_0; break;
                case '1': resId = com.android.calculator2.R.id.digit_1; break;
                case '2': resId = com.android.calculator2.R.id.digit_2; break;
                case '3': resId = com.android.calculator2.R.id.digit_3; break;
                case '4': resId = com.android.calculator2.R.id.digit_4; break;
                case '5': resId = com.android.calculator2.R.id.digit_5; break;
                case '6': resId = com.android.calculator2.R.id.digit_6; break;
                case '7': resId = com.android.calculator2.R.id.digit_7; break;
                case '8': resId = com.android.calculator2.R.id.digit_8; break;
                case '9': resId = com.android.calculator2.R.id.digit_9; break;
                default: throw new IllegalArgumentException("Unexpected digit: " + c);
            }
            onView(withId(resId)).perform(click());
        }
        mDevice.waitForIdle();
    }

    /**
     * Tap within an EditText at the boundary between two value characters. The tap coordinate is
     * calculated from the EditText's {@link Layout} so it lands on the actual glyph boundary,
     * not an arbitrary screen ratio.
     *
     * @param fieldId   the EditText resource id
     * @param valueIndex the index within the value text where the tap lands
     *                    (0 = before first char, 1 = between char 0 and 1, etc.)
     */
    private void tapAtValueBoundary(int fieldId, int valueIndex) {
        final String prefix = field0Prefix();
        final int textIndex = prefix.length() + valueIndex;

        onView(withId(fieldId)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return "Tap at value boundary index " + valueIndex;
            }

            @Override
            public void perform(UiController uiController, View view) {
                EditText editText = (EditText) view;
                Layout layout = editText.getLayout();
                if (layout == null) {
                    return;
                }

                // Clamp to valid character boundary range [1, length-1]
                int clampedIndex = Math.max(1,
                        Math.min(textIndex, layout.getText().length() - 1));

                // Get x positions of the characters on either side of the boundary
                float x1 = layout.getPrimaryHorizontal(clampedIndex - 1);
                float x2 = layout.getPrimaryHorizontal(clampedIndex);
                float x = (x1 + x2) / 2f;

                int[] location = new int[2];
                view.getLocationOnScreen(location);
                int screenX = location[0] + (int) x;
                int screenY = location[1] + view.getHeight() / 2;

                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(
                        now, now, MotionEvent.ACTION_DOWN, screenX, screenY, 0);
                MotionEvent up = MotionEvent.obtain(
                        now, now, MotionEvent.ACTION_UP, screenX, screenY, 0);
                InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
                InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
                down.recycle();
                up.recycle();
            }
        });
        mDevice.waitForIdle();
    }

    /** Press the delete key on the calculator pad. */
    private void pressDelete() {
        onView(withId(com.android.calculator2.R.id.del)).perform(click());
        mDevice.waitForIdle();
    }

    /** Press the AC (clear) key on the calculator pad. */
    private void pressClear() {
        onView(withId(com.android.calculator2.R.id.clr)).perform(click());
        mDevice.waitForIdle();
    }

    /**
     * Regression test: enter "1234" into field 0, lose focus, tap between "2" and "3", press "9",
     * and assert the result is "12934" (not "12349") with cursor after "9".
     */
    @Test
    public void testMiddleInsertionAfterFocusLoss() {
        openBmiTool();

        // 1. Enter "1234" into field 0 (height)
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_0, "1234");
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));

        // 2. Tap field 1 (weight) to cause field 0 to lose focus
        onView(withId(com.android.calculator2.R.id.tool_field_1)).perform(click());

        // 3. Tap between "2" and "3" within field 0's value
        // Value "1234": boundary index 2 = between char 1 ('2') and char 2 ('3')
        tapAtValueBoundary(com.android.calculator2.R.id.tool_field_0, 2);

        // 4. Press "9"
        onView(withId(com.android.calculator2.R.id.digit_9)).perform(click());

        // 5. Verify result is "12934" (digit inserted in middle, not appended)
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "12934")));
    }

    /**
     * Test that pressing delete with cursor at position 0 does nothing (does not delete the last
     * digit).
     */
    @Test
    public void testDeleteAtPosition0DoesNothing() {
        openBmiTool();

        // 1. Enter "1234" into field 0
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_0, "1234");
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));

        // 2. Tap field 1 to lose focus from field 0
        onView(withId(com.android.calculator2.R.id.tool_field_1)).perform(click());

        // 3. Tap at value boundary index 0 (before "1")
        tapAtValueBoundary(com.android.calculator2.R.id.tool_field_0, 0);

        // 4. Press delete - should do nothing since cursor is at position 0
        pressDelete();

        // 5. Assert field 0 still shows "Height cm: 1234" (unchanged)
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));
    }

    /**
     * Test consecutive cursor repositioning within the same field:
     * Enter "1234", tap between "1" and "2", press "5" → "15234".
     * Tap between "5" and "2", press "6" → "156234".
     */
    @Test
    public void testConsecutiveCursorRepositioning() {
        openBmiTool();

        // 1. Enter "1234" into field 0
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_0, "1234");
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));

        // 2. Tap field 1 to lose focus
        onView(withId(com.android.calculator2.R.id.tool_field_1)).perform(click());

        // 3. Tap between "1" and "2" (value boundary index 1)
        tapAtValueBoundary(com.android.calculator2.R.id.tool_field_0, 1);

        // 4. Press "5" → "15234"
        onView(withId(com.android.calculator2.R.id.digit_5)).perform(click());
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "15234")));

        // 5. Tap field 1 again to reset focus state
        onView(withId(com.android.calculator2.R.id.tool_field_1)).perform(click());

        // 6. Tap between "5" and "2" in "15234" (value boundary index 2)
        tapAtValueBoundary(com.android.calculator2.R.id.tool_field_0, 2);

        // 7. Press "6" → "156234"
        onView(withId(com.android.calculator2.R.id.digit_6)).perform(click());
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "156234")));
    }

    /**
     * Test switching between fields: enter data in field 0, switch to field 1, enter data there,
     * switch back to field 0, and verify field 0's data is intact.
     */
    @Test
    public void testSwitchingBetweenFields() {
        openBmiTool();

        // 1. Enter "1234" into field 0 (height)
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_0, "1234");
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));

        // 2. Switch to field 1 (weight) and enter "5678"
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_1, "5678");
        onView(withId(com.android.calculator2.R.id.tool_field_1))
                .check(matches(withText(field1Prefix() + "5678")));

        // 3. Switch back to field 0 - its data should still be "1234"
        onView(withId(com.android.calculator2.R.id.tool_field_0)).perform(click());
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));
    }

    /**
     * Test entering data after clearing a field: enter "1234", clear, then enter "99" and verify.
     */
    @Test
    public void testEnterAfterClear() {
        openBmiTool();

        // 1. Enter "1234" into field 0
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_0, "1234");
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "1234")));

        // 2. Clear field 0 (tap AC)
        pressClear();

        // Field 0 should now show just the label (empty value)
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(mHeightLabel)));

        // 3. Enter "99" into field 0
        typeDigitsIntoField(com.android.calculator2.R.id.tool_field_0, "99");

        // 4. Assert field 0 shows "Height cm: 99"
        onView(withId(com.android.calculator2.R.id.tool_field_0))
                .check(matches(withText(field0Prefix() + "99")));
    }
}
