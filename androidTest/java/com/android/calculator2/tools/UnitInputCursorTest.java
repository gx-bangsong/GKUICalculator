/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Selection;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.calculator2.Calculator;
import com.android.calculator2.R;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UnitInputCursorTest {
    private ActivityScenario<Calculator> scenario;

    @Before public void launch() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("calc_tools", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("unit_secondary_output", true).commit();
        scenario = ActivityScenario.launch(Calculator.class);
        onView(withId(R.id.mode_segment_convert)).perform(click());
        onView(withId(R.id.clr)).perform(click());
        digits("175");
    }
    @After public void close() { if (scenario != null) scenario.close(); }

    private void digits(String text) {
        int[] ids = {R.id.digit_0, R.id.digit_1, R.id.digit_2, R.id.digit_3,
                R.id.digit_4, R.id.digit_5, R.id.digit_6, R.id.digit_7,
                R.id.digit_8, R.id.digit_9};
        for (char c : text.toCharArray()) onView(withId(ids[c - '0'])).perform(click());
    }

    private void caret(int offset) {
        onView(withId(R.id.unit_input_text)).check((view, error) -> {
            if (error != null) throw error;
            TextView input = (TextView) view;
            assertEquals("selection start", offset, input.getSelectionStart());
            assertEquals("selection end", offset, input.getSelectionEnd());
        });
    }

    private void tapAt(int offset) {
        onView(withId(R.id.unit_input_text)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "real tap at unit expression offset " + offset; }
            public void perform(UiController controller, View view) {
                EditText input = (EditText) view;
                int[] location = new int[2];
                input.getLocationOnScreen(location);
                float x = location[0] + input.getCompoundPaddingLeft()
                        + input.getLayout().getPrimaryHorizontal(offset) - input.getScrollX() + .5f;
                float y = location[1] + input.getHeight() / 2f;
                long time = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP, x, y, 0);
                try {
                    controller.injectMotionEvent(down);
                    controller.injectMotionEvent(up);
                    controller.loopMainThreadUntilIdle();
                } catch (InjectEventSecurityException e) {
                    throw new AssertionError("Cannot inject unit caret tap", e);
                } finally { down.recycle(); up.recycle(); }
            }
        });
        caret(offset);
    }

    @Test public void unitArithmeticSymbolsRespectCaret() {
        int[] keys={R.id.op_add,R.id.op_sub,R.id.op_mul,R.id.op_div};
        String[] symbols={"+","−","×","÷"};
        String[] values={"15","9","36","4"};
        for(int i=0;i<keys.length;i++) {
            onView(withId(R.id.clr)).perform(click());digits("123");tapAt(2);
            onView(withId(keys[i])).perform(click());
            onView(withId(R.id.unit_input_text)).check(matches(withText("12"+symbols[i]+"3")));caret(3);
            onView(withId(R.id.unit_result_text)).check(matches(withText(values[i]+"000")));
            onView(withId(R.id.eq)).perform(click());
            onView(withId(R.id.unit_input_text)).check(matches(withText(values[i])));
        }
        onView(withId(R.id.clr)).perform(click());digits("123");tapAt(2);
        onView(withId(R.id.op_add)).perform(click());caret(3);digits("9");caret(4);
        onView(withId(R.id.unit_input_text)).check(matches(withText("12+93")));
        onView(withId(R.id.unit_result_text)).check(matches(withText("105000")));
        onView(withId(R.id.eq)).perform(click());
        onView(withId(R.id.unit_input_text)).check(matches(withText("105")));
    }

    @Test public void unitMiddleInsertionUpdatesConversion() {
        onView(withId(R.id.unit_from)).check(matches(withText("km")));
        onView(withId(R.id.unit_to)).check(matches(withText("m")));
        tapAt(1);
        digits("9");
        onView(withId(R.id.unit_input_text)).check(matches(withText("1975")));
        caret(2);
        onView(withId(R.id.unit_result_text)).check(matches(withText("1975000")));
        // No second tap/selection: the next key must follow the newly inserted digit.
        digits("8");
        onView(withId(R.id.unit_input_text)).check(matches(withText("19875")));
        caret(3);
        onView(withId(R.id.unit_result_text)).check(matches(withText("19875000")));
        onView(withId(R.id.unit_result_2_text)).check(matches(withText("1987500000")));
        // Equals evaluates the real expression, independently of its displayed string.
        onView(withId(R.id.eq)).perform(click());
        onView(withId(R.id.unit_input_text)).check(matches(withText("19875")));
    }

    @Test public void unitMiddleDeleteAndRangeReplacement() {
        tapAt(1);
        onView(withId(R.id.del)).perform(click());
        onView(withId(R.id.unit_input_text)).check(matches(withText("75")));
        caret(0);
        onView(withId(R.id.unit_input_text)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "select unit value range"; }
            public void perform(UiController c, View v) { ((EditText) v).setSelection(0, 2); }
        });
        digits("9");
        onView(withId(R.id.unit_input_text)).check(matches(withText("9")));
        caret(1);
        onView(withId(R.id.unit_result_text)).check(matches(withText("9000")));
    }

    @Test public void unitSharedDisplayRowsAreHidden() {
        // Include every candidate in a single diagnostic: do not identify a caret by screen
        // position alone. Bounds are screen coordinates for comparison with a device capture.
        StringBuilder snapshot = new StringBuilder();
        scenario.onActivity(activity -> {
            for (int id : new int[]{R.id.unit_result_text, R.id.unit_result_2_text,
                    R.id.formula, R.id.result}) {
                TextView view = activity.findViewById(id);
                int[] xy = new int[2];
                view.getLocationOnScreen(xy);
                snapshot.append(activity.getResources().getResourceEntryName(id))
                        .append(" shown=").append(view.isShown())
                        .append(" focus=").append(view.hasFocus())
                        .append(" cursorVisible=").append(view.isCursorVisible())
                        .append(" text=[").append(view.getText()).append("] bounds=")
                        .append(xy[0]).append(',').append(xy[1]).append(',')
                        .append(view.getWidth()).append(',').append(view.getHeight()).append("; ");
            }
        });
        onView(withId(R.id.formula)).check((v, e) ->
                assertFalse(snapshot.toString(), v.isShown()));
        onView(withId(R.id.result)).check((v, e) ->
                assertFalse(snapshot.toString(), v.isShown()));
    }

    @Test public void unitSharedRowsRestoreAcrossModes() {
        onView(withId(R.id.formula)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.result)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.formula_scroll_view))
                .check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.mode_segment_basic)).perform(click());
        onView(withId(R.id.formula_scroll_view)).check(matches(isDisplayed()));
        onView(withId(R.id.formula)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.clr)).perform(click());
        digits("2");
        onView(withId(R.id.op_add)).perform(click());
        digits("3");
        onView(withId(R.id.formula)).check(matches(withText("2+3")));
        // Another tool still needs the shared output rows (unlike units).
        onView(withContentDescription(R.string.tool_bmi)).perform(click());
        onView(withId(R.id.formula)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.result)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.mode_segment_convert)).perform(click());
        onView(withId(R.id.formula)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.result)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.clr)).perform(click());
        digits("175");
        tapAt(1);
        digits("98");
        onView(withId(R.id.unit_input_text)).check(matches(withText("19875")));
        caret(3);
        onView(withId(R.id.unit_result_text)).check(matches(withText("19875000")));
    }

    @Test public void unitResultsAreReadOnlyAndSelectable() {
        for (int id : new int[]{R.id.unit_result_text, R.id.unit_result_2_text}) {
            onView(withId(id)).check(matches(isDisplayed())).perform(longClick())
                    .check((view, error) -> {
                if (error != null) throw error;
                TextView output = (TextView) view;
                assertFalse("result must not be an input", output instanceof EditText);
                assertFalse(output.onCheckIsTextEditor());
                assertFalse(output.isCursorVisible());
                assertTrue("copy selection remains available", output.isTextSelectable());
                assertNull("no editable key listener", output.getKeyListener());
                assertFalse(output.getText() instanceof Editable);
                assertNull("IME cannot commit, delete or paste", output.onCreateInputConnection(new EditorInfo()));
                assertTrue("real long press selects output text",
                        output.getSelectionEnd() > output.getSelectionStart());
                String before = output.getText().toString();
                output.requestFocus();
                Selection.setSelection((android.text.Spannable) output.getText(), 0, before.length());
                assertTrue("copy allowed", output.onTextContextMenuItem(android.R.id.copy));
                output.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_9));
                output.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                assertEquals(before, output.getText().toString());
            });
        }
        onView(withId(R.id.unit_input_text)).check(matches(withText("175")));
    }
}
