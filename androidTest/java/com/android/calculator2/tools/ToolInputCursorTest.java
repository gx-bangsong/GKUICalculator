/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import org.hamcrest.Matcher;

import android.content.Context;
import android.os.SystemClock;
import android.text.Layout;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.InjectEventSecurityException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.calculator2.Calculator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Regression tests for cursor-aware digit input in tool input fields (BMI).
 *
 * <p>These tests drive the calculator pad the way a user would: a field is made active with a tap,
 * the caret is placed either by a real touch on the value or by an explicit selection, and then a
 * pad key is pressed. The expected behavior is that the key edits at the caret rather than always
 * appending to the end.
 *
 * <p>Each test manages its own Activity lifecycle via {@link ActivityScenario}. The BMI tool opens
 * with default values ("175" for height, "70" for weight), so a test that needs a specific value
 * has to replace the default through the pad: select the value, then type the digits over it.
 * Tests must not treat a newly opened form as empty, and must not write the model directly.
 *
 * <p>Run with: {@code ./gradlew connectedDebugAndroidTest}
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToolInputCursorTest {

    private ActivityScenario<Calculator> mActivityScenario;
    private String mHeightLabel;
    private String mWeightLabel;

    @Before
    public void setUp() {
        final Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHeightLabel = context.getString(com.android.calculator2.R.string.tool_bmi_field_height);
        mWeightLabel = context.getString(com.android.calculator2.R.string.tool_bmi_field_weight);

        // Clear persistent tool preferences so digits from previous tests do not leak in.
        context.getSharedPreferences("calc_tools", Context.MODE_PRIVATE)
                .edit().clear().commit();

        // Launch the Calculator activity and wait for it to be ready.
        mActivityScenario = ActivityScenario.launch(Calculator.class);
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    /** Navigate to the BMI tool via its icon in the bottom navigation bar. */
    private void openBmiTool() {
        onView(withContentDescription(com.android.calculator2.R.string.tool_bmi))
                .perform(click());
        // After switching to the BMI tool, both input fields must be visible.
        onView(withId(com.android.calculator2.R.id.tool_field_0)).check(matches(isDisplayed()));
        onView(withId(com.android.calculator2.R.id.tool_field_1)).check(matches(isDisplayed()));
    }

    // ---- field text layout -------------------------------------------------

    /** The label the BMI tool shows in front of the given field's value. */
    private String labelFor(int fieldId) {
        return fieldId == com.android.calculator2.R.id.tool_field_0 ? mHeightLabel : mWeightLabel;
    }

    /**
     * Full text a field shows for a value: {@code "label: value"}, collapsing to the bare label
     * once the value is empty.
     */
    private String expectedText(int fieldId, String value) {
        final String label = labelFor(fieldId);
        return value.isEmpty() ? label : label + ": " + value;
    }

    /**
     * Offset of the value inside a field's text. The label is followed by ": ", but only while a
     * value is present; an empty field shows just the label, so the caret can only sit at its end.
     */
    private int valueStart(String text, int fieldId) {
        final int start = labelFor(fieldId).length() + 2;
        return text.length() >= start ? start : text.length();
    }

    /** Length of the value currently shown in the field. */
    private int valueLength(String text, int fieldId) {
        return text.length() - valueStart(text, fieldId);
    }

    private int valueLengthOf(int fieldId) {
        final int[] out = new int[1];
        onField(fieldId, editText -> out[0] = valueLength(editText.getText().toString(), fieldId));
        return out[0];
    }

    /** Action executed on the UI thread with one of the tool's input fields. */
    private interface FieldAction {
        void run(EditText editText);
    }

    private void onField(int fieldId, FieldAction action) {
        mActivityScenario.onActivity(activity -> {
            final View view = activity.findViewById(fieldId);
            if (!(view instanceof EditText)) {
                throw new AssertionError("View " + fieldId + " is not an EditText");
            }
            action.run((EditText) view);
        });
    }

    // ---- selection ---------------------------------------------------------

    /**
     * Place the caret (or a selection range) at the given value offsets without tapping the field.
     * Tapping would move the caret to wherever the touch landed and would destroy the position
     * this call is meant to establish.
     */
    private void selectValue(int fieldId, int startValuePos, int endValuePos) {
        onField(fieldId, editText -> {
            final String text = editText.getText().toString();
            final int valueStart = valueStart(text, fieldId);
            final int start = Math.min(valueStart + startValuePos, text.length());
            final int end = Math.min(valueStart + endValuePos, text.length());
            if (!editText.isFocused()) {
                editText.requestFocus();
            }
            editText.setSelection(start, end);
            if (editText.getSelectionStart() != start || editText.getSelectionEnd() != end) {
                throw new AssertionError("Field " + fieldId + ": could not select value range ["
                        + startValuePos + ", " + endValuePos + "); wanted start=" + start
                        + " end=" + end + ", got start=" + editText.getSelectionStart()
                        + " end=" + editText.getSelectionEnd() + " text=\"" + text + "\"");
            }
        });
    }

    /**
     * Tap the field so it becomes the pad's target. The caret ends up wherever the tap lands,
     * which is not specified - use {@link #selectValue} when a position is required.
     */
    private void activateField(int fieldId) {
        onView(withId(fieldId)).perform(click());
    }

    /** Make the field the pad's target and then place the caret inside its value. */
    private void activateAndSelectValue(int fieldId, int startValuePos, int endValuePos) {
        activateField(fieldId);
        selectValue(fieldId, startValuePos, endValuePos);
    }

    // ---- pad keys ----------------------------------------------------------

    /** Press a run of digit keys. This never touches the fields, so a selection survives. */
    private void pressDigits(String digits) {
        for (char c : digits.toCharArray()) {
            onView(withId(digitResId(c))).perform(click());
        }
    }

    private static int digitResId(char c) {
        switch (c) {
            case '0': return com.android.calculator2.R.id.digit_0;
            case '1': return com.android.calculator2.R.id.digit_1;
            case '2': return com.android.calculator2.R.id.digit_2;
            case '3': return com.android.calculator2.R.id.digit_3;
            case '4': return com.android.calculator2.R.id.digit_4;
            case '5': return com.android.calculator2.R.id.digit_5;
            case '6': return com.android.calculator2.R.id.digit_6;
            case '7': return com.android.calculator2.R.id.digit_7;
            case '8': return com.android.calculator2.R.id.digit_8;
            case '9': return com.android.calculator2.R.id.digit_9;
            default: throw new IllegalArgumentException("Unexpected digit: " + c);
        }
    }

    /** Press the delete key on the calculator pad. */
    private void pressDelete() {
        onView(withId(com.android.calculator2.R.id.del)).perform(click());
    }

    /** Press the AC (clear) key on the calculator pad. */
    private void pressClear() {
        onView(withId(com.android.calculator2.R.id.clr)).perform(click());
    }

    /**
     * Give the field a known value using the pad alone: select the whole value first, so the first
     * digit press replaces it instead of being appended to the default.
     */
    private void replaceValueWithDigits(int fieldId, String digits) {
        activateAndSelectValue(fieldId, 0, valueLengthOf(fieldId));
        pressDigits(digits);
    }

    // ---- real touches ------------------------------------------------------

    /**
     * Tap inside the value at the given value offset with a real touch, injected through Espresso's
     * {@link UiController}. The x coordinate is the caret position the framework would draw for
     * that offset, taken from the field's own {@link Layout} and converted to screen coordinates.
     */
    private void tapAtValuePos(int fieldId, int valuePos) {
        onView(withId(fieldId)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isDisplayed();
            }

            @Override
            public String getDescription() {
                return "tap in field at value position " + valuePos;
            }

            @Override
            public void perform(UiController uiController, View view) {
                final EditText editText = (EditText) view;
                final Layout layout = editText.getLayout();
                if (layout == null) {
                    throw new AssertionError("Field " + fieldId + " has no Layout; cannot tap");
                }
                final String text = editText.getText().toString();
                final int textIndex =
                        Math.min(valueStart(text, fieldId) + valuePos, layout.getText().length());

                // The caret for offset i is drawn at the leading edge of character i, so tapping
                // there lands exactly on that offset. Half a pixel of bias keeps a rounding error
                // from dropping the caret one character to the left.
                final float localX = editText.getCompoundPaddingLeft()
                        + layout.getPrimaryHorizontal(textIndex)
                        - editText.getScrollX()
                        + 0.5f;

                final int[] location = new int[2];
                view.getLocationOnScreen(location);
                final int screenX = location[0] + Math.round(localX);
                final int screenY = location[1] + view.getHeight() / 2;

                final long now = SystemClock.uptimeMillis();
                final MotionEvent down = MotionEvent.obtain(
                        now, now, MotionEvent.ACTION_DOWN, screenX, screenY, 0);
                final MotionEvent up = MotionEvent.obtain(
                        now, now, MotionEvent.ACTION_UP, screenX, screenY, 0);
                try {
                    uiController.injectMotionEvent(down);
                    uiController.injectMotionEvent(up);
                } catch (InjectEventSecurityException e) {
                    throw new AssertionError(
                            "Failed to inject motion event: " + e.getMessage(), e);
                } finally {
                    down.recycle();
                    up.recycle();
                }
            }
        });
    }

    // ---- assertions --------------------------------------------------------

    private void assertFieldText(int fieldId, String value) {
        onView(withId(fieldId)).check(matches(withText(expectedText(fieldId, value))));
    }

    /** Fail unless the caret sits at the given offset inside the value, and is not a range. */
    private void assertSelectionAtValuePos(int fieldId, int expectedValuePos) {
        onField(fieldId, editText -> {
            final String text = editText.getText().toString();
            final int expectedStart =
                    Math.min(valueStart(text, fieldId) + expectedValuePos, text.length());
            final int start = editText.getSelectionStart();
            final int end = editText.getSelectionEnd();
            if (start != expectedStart || end != expectedStart) {
                throw new AssertionError("Field " + fieldId + ": expected selection at value pos "
                        + expectedValuePos + " (abs " + expectedStart + "), but got start=" + start
                        + " end=" + end + " text=\"" + text + "\"");
            }
        });
    }

    /**
     * Fail unless the caret sits inside the value. Used after a plain centre tap, which lands
     * wherever it lands: the only property worth asserting is that it is not inside the label.
     */
    private void assertSelectionWithinValue(int fieldId) {
        onField(fieldId, editText -> {
            final String text = editText.getText().toString();
            final int valueStart = valueStart(text, fieldId);
            final int start = editText.getSelectionStart();
            final int end = editText.getSelectionEnd();
            if (start < valueStart || end < start || end > text.length()) {
                throw new AssertionError("Field " + fieldId + ": caret is outside the value ["
                        + valueStart + ", " + text.length() + "]; got start=" + start
                        + " end=" + end + " text=\"" + text + "\"");
            }
        });
    }

    // ---- tests -------------------------------------------------------------

    /**
     * Regression test: give field 0 the value "1234", let it lose focus, tap between "2" and "3"
     * with a real touch, press "9", and assert the result is "12934" (not "12349") with the caret
     * after the "9". The selection is asserted between the tap and the key press, so a wrong tap
     * coordinate is distinguishable from a flaw in the insertion logic.
     */
    @Test
    public void testMiddleInsertionAfterFocusLoss() {
        openBmiTool();

        // 1. Give field 0 (height) the value "1234", replacing the "175" default.
        replaceValueWithDigits(com.android.calculator2.R.id.tool_field_0, "1234");
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1234");
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 4);

        // 2. Tap field 1 (weight) to cause field 0 to lose focus.
        activateField(com.android.calculator2.R.id.tool_field_1);

        // 3. Tap between "2" and "3" within field 0's value (value offset 2).
        tapAtValuePos(com.android.calculator2.R.id.tool_field_0, 2);

        // 4. Verify the caret is at value position 2 before pressing "9".
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 2);

        // 5. Press "9".
        pressDigits("9");

        // 6. Verify result is "12934" (digit inserted in middle, not appended).
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "12934");

        // 7. Verify the caret is after "9" (value position 3).
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 3);
    }

    /**
     * Test that pressing delete with the caret at position 0 does nothing (does not delete the
     * last digit).
     */
    @Test
    public void testDeleteAtPosition0DoesNothing() {
        openBmiTool();

        // 1. Give field 0 the value "1234".
        replaceValueWithDigits(com.android.calculator2.R.id.tool_field_0, "1234");
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1234");

        // 2. Put the caret at value position 0 (before the "1").
        activateAndSelectValue(com.android.calculator2.R.id.tool_field_0, 0, 0);
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 0);

        // 3. Press delete - there is nothing before the caret, so nothing may change.
        pressDelete();

        // 4. Assert field 0 still shows "Height cm: 1234" (unchanged).
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1234");

        // 5. Verify the caret is still at value position 0.
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 0);
    }

    /**
     * Test consecutive cursor repositioning within the same field:
     * "1234", caret after "1", press "5" -> "15234". Then caret after the "5", press "6" ->
     * "156234".
     */
    @Test
    public void testConsecutiveCursorRepositioning() {
        openBmiTool();

        // 1. Give field 0 the value "1234".
        replaceValueWithDigits(com.android.calculator2.R.id.tool_field_0, "1234");
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1234");

        // 2. Put the caret between "1" and "2" (value position 1).
        activateAndSelectValue(com.android.calculator2.R.id.tool_field_0, 1, 1);
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 1);

        // 3. Press "5" -> "15234".
        pressDigits("5");
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "15234");

        // 4. Verify the caret is after "5" (value position 2).
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 2);

        // 5. Put the caret between "5" and "2" in "15234" (value position 2).
        selectValue(com.android.calculator2.R.id.tool_field_0, 2, 2);
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 2);

        // 6. Press "6" -> "156234".
        pressDigits("6");
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "156234");

        // 7. Verify the caret is after "6" (value position 3).
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 3);
    }

    /**
     * Test switching between fields: enter data in field 0, switch to field 1, enter data there,
     * switch back to field 0, and verify field 0's data is intact.
     */
    @Test
    public void testSwitchingBetweenFields() {
        openBmiTool();

        // 1. Give field 0 (height) the value "1234".
        replaceValueWithDigits(com.android.calculator2.R.id.tool_field_0, "1234");
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1234");
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 4);

        // 2. Switch to field 1 (weight) and give it "5678".
        replaceValueWithDigits(com.android.calculator2.R.id.tool_field_1, "5678");
        assertFieldText(com.android.calculator2.R.id.tool_field_1, "5678");
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_1, 4);

        // 3. Switch back to field 0 - its data should still be "1234". A plain centre tap does not
        // promise where the caret lands, only that the field is now the pad's target.
        activateField(com.android.calculator2.R.id.tool_field_0);
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1234");
        assertSelectionWithinValue(com.android.calculator2.R.id.tool_field_0);
        assertFieldText(com.android.calculator2.R.id.tool_field_1, "5678");
    }

    /**
     * Test entering data after clearing a field: from the default "175", clear it, then enter "99"
     * and verify. After clearing, the field shows just its label (no ": " suffix) and the caret is
     * at value position 0.
     */
    @Test
    public void testEnterAfterClear() {
        openBmiTool();

        // 1. The BMI tool opens with its default height, and field 0 is the pad's target.
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "175");
        activateField(com.android.calculator2.R.id.tool_field_0);

        // 2. Clear field 0 (tap AC).
        pressClear();

        // The field now shows just the label, and the caret is at value position 0.
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "");
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 0);

        // 3. Enter "99" into field 0.
        pressDigits("99");

        // 4. Assert field 0 shows "Height cm: 99" with the caret after "99" (value position 2).
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "99");
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 2);
    }

    /**
     * Minimal regression test: no touch coordinates, no field clearing.
     * Opens BMI tool, verifies default height is "175", sets the caret to the end of the value,
     * presses "9", and expects "1759" with the caret at value position 4.
     */
    @Test
    public void testCursorAtEndAppendsDigit() {
        openBmiTool();

        // 1. Assert default height value is "175".
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "175");

        // 2. Put the caret at the end of the value (value position 3) via the UI thread.
        selectValue(com.android.calculator2.R.id.tool_field_0, 3, 3);
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 3);

        // 3. Press "9".
        pressDigits("9");

        // 4. Verify the text becomes "1759" and the caret is at value position 4.
        assertFieldText(com.android.calculator2.R.id.tool_field_0, "1759");
        assertSelectionAtValuePos(com.android.calculator2.R.id.tool_field_0, 4);
    }
}
