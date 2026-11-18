/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.text.Selection;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CalculatorFormulaCursorTest {
    private ActivityScenario<Calculator> scenario;
    @Before public void launch() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("calc_tools", Context.MODE_PRIVATE).edit().clear().commit();
        scenario = ActivityScenario.launch(Calculator.class);
        onView(withId(R.id.mode_segment_basic)).perform(click());
        onView(withId(R.id.clr)).perform(click());
    }
    @After public void close() { if (scenario != null) scenario.close(); }
    private void key(int id) { onView(withId(id)).perform(click()); }
    private void input() {
        key(R.id.digit_1); key(R.id.digit_2); key(R.id.op_add); key(R.id.digit_3);
        formula("12+3");
    }
    private void formula(String text) { onView(withId(R.id.formula)).check(matches(withText(text))); }
    private void caret(int at) {
        onView(withId(R.id.formula)).check((v, e) -> {
            if (e != null) throw e;
            assertEquals(at, ((TextView) v).getSelectionStart());
            assertEquals(at, ((TextView) v).getSelectionEnd());
        });
    }
    private void tap(int at) {
        onView(withId(R.id.formula)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "real formula caret tap at " + at; }
            public void perform(UiController controller, View v) {
                TextView text = (TextView) v;
                int[] xy = new int[2]; text.getLocationOnScreen(xy);
                float x = xy[0] + text.getCompoundPaddingLeft()
                        + text.getLayout().getPrimaryHorizontal(at) - text.getScrollX() + .5f;
                float y = xy[1] + text.getHeight() / 2f;
                long time = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP, x, y, 0);
                try {
                    controller.injectMotionEvent(down); controller.injectMotionEvent(up);
                    controller.loopMainThreadUntilIdle();
                } catch (InjectEventSecurityException e) { throw new AssertionError(e); }
                finally { down.recycle(); up.recycle(); }
            }
        });
        caret(at);
    }
    private void result(String expected) {
        key(R.id.eq);
        onView(withId(R.id.result)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "wait for evaluated result " + expected; }
            public void perform(UiController controller, View v) {
                CalculatorResult result = (CalculatorResult) v;
                long until = SystemClock.uptimeMillis() + 10000;
                do {
                    if (expected.equals(result.getText().toString())) return;
                    controller.loopMainThreadForAtLeast(50);
                } while (SystemClock.uptimeMillis() < until);
                assertEquals(expected, result.getText().toString());
            }
        });
    }
    @Test public void realTapMiddleInsertionEvaluatesModel() {
        input(); tap(1); key(R.id.digit_9); formula("192+3"); caret(2); result("195");
    }
    @Test public void consecutiveInsertionKeepsCaretAcrossGrouping() {
        input(); tap(1); key(R.id.digit_9); caret(2);
        key(R.id.digit_8); formula("1,982+3"); caret(4); result("1,985");
    }
    @Test public void middleBackspaceEditsModel() {
        input(); tap(1); key(R.id.del); formula("2+3"); caret(0); result("5");
    }
    @Test public void selectedRangeIsReplacedInModel() {
        input();
        onView(withId(R.id.formula)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "select first operand without changing text"; }
            public void perform(UiController controller, View v) {
                v.requestFocus(); Selection.setSelection((Spannable) ((TextView) v).getText(), 0, 2);
            }
        });
        key(R.id.digit_9); formula("9+3"); caret(1); result("12");
    }
    @Test public void ordinaryResultIsReadOnlyAndCopyable() {
        input(); result("15");
        onView(withId(R.id.result)).check((v, e) -> {
            if (e != null) throw e;
            TextView output = (TextView) v;
            assertFalse(output instanceof EditText);
            assertFalse(output.isCursorVisible());
            assertFalse(output.onCheckIsTextEditor());
            assertNull(output.getKeyListener());
            assertNull(output.onCreateInputConnection(new EditorInfo()));
        }).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "invoke result long-press copy action"; }
            public void perform(UiController controller, View view) {
                // Exercise CalculatorResult's actual long-click callback. Its RESULT animation
                // moves/scales the hit rectangle, so coordinate injection is covered separately
                // by formula taps and the unit result long-press test, not guessed here.
                assertTrue("result supports long-press actions", view.performLongClick());
                controller.loopMainThreadUntilIdle();
            }
        });
        onView(withText(android.R.string.copy)).perform(click());
        scenario.onActivity(activity -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard.getPrimaryClip());
            assertEquals("15", clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString());
            assertEquals("15", ((TextView) activity.findViewById(R.id.result)).getText().toString());
        });
    }
    @Test public void formulaEditingSurvivesUnitRoundTrip() {
        input();
        key(R.id.mode_segment_convert); key(R.id.mode_segment_basic);
        formula("12+3"); tap(1); key(R.id.digit_9); formula("192+3"); result("195");
    }
    @Test public void mappingKeepsFunctionTokensAtomic() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CalculatorExpr expression = new CalculatorExpr();
        expression.add(R.id.fun_sin); expression.add(R.id.digit_2);
        int boundary = expression.toSpannableStringBuilder(context).length() - 1;
        int caret = expression.editAtDisplay(context, boundary, boundary, R.id.digit_9, false);
        assertEquals(boundary + 1, caret);
        assertTrue(expression.hasTrigFuncs());
        assertTrue(expression.toSpannableStringBuilder(context).toString().endsWith("92"));
    }
}
