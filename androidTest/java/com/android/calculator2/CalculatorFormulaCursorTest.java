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
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
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
    @Test public void middleArithmeticSymbolsRespectCaret() {
        int[] keys={R.id.op_add,R.id.op_sub,R.id.op_mul,R.id.op_div};
        String[] symbols={"+","−","×","÷"};
        String[] values={"15","9","36","4"};
        for(int i=0;i<keys.length;i++) {
            key(R.id.clr);key(R.id.digit_1);key(R.id.digit_2);key(R.id.digit_3);
            tap(2);key(keys[i]);formula("12"+symbols[i]+"3");caret(3);result(values[i]);
        }
        key(R.id.clr);key(R.id.digit_1);key(R.id.digit_2);key(R.id.digit_3);
        tap(2);key(R.id.op_add);formula("12+3");caret(3);
        key(R.id.digit_9);formula("12+93");caret(4);result("105");
    }
    @Test public void semanticSymbolBoundariesAndRejection() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[] inserted={R.id.lparen,R.id.fun_sin,R.id.fun_cos,R.id.fun_tan,
            R.id.fun_arcsin,R.id.fun_arccos,R.id.fun_arctan,R.id.fun_ln,R.id.fun_log,
            R.id.fun_exp,R.id.op_sqrt,R.id.const_pi,R.id.const_e};
        for(int id:inserted) {
            CalculatorExpr expression=new CalculatorExpr();
            for(int k:new int[]{R.id.digit_1,R.id.op_add,R.id.digit_3,R.id.digit_0}) expression.add(k);
            int at=expression.editAtDisplay(context,2,2,id,false);
            String token=KeyMaps.toString(context,id);
            assertEquals("1+"+token+"30",expression.toSpannableStringBuilder(context).toString());
            assertEquals(2+token.length(),at);
        }
        for(int id:new int[]{R.id.op_sqr,R.id.op_fact,R.id.op_pct,R.id.op_pow}) {
            CalculatorExpr expression=new CalculatorExpr();
            for(int k:new int[]{R.id.digit_3,R.id.op_add,R.id.digit_4}) expression.add(k);
            assertTrue(expression.editAtDisplay(context,1,1,id,false)>1);
            // Use token rendering (square is a semantic suffix, not its button label x²).
            CalculatorExpr prefix=new CalculatorExpr();prefix.add(R.id.digit_3);prefix.add(id);
            assertEquals(prefix.toSpannableStringBuilder(context).toString()+"+4",
                    expression.toSpannableStringBuilder(context).toString());
        }
        CalculatorExpr expression=new CalculatorExpr();expression.add(R.id.digit_3);
        assertEquals(-1,expression.editAtDisplay(context,0,0,R.id.rparen,false));
        assertEquals("3",expression.toSpannableStringBuilder(context).toString());
        assertEquals(-1,expression.editAtDisplay(context,0,0,R.id.op_fact,false));
        assertEquals("3",expression.toSpannableStringBuilder(context).toString());
    }

    @Test public void selectedOperatorReplacementEvaluates() {
        input();
        scenario.onActivity(a -> Selection.setSelection((Spannable)((TextView)a.findViewById(R.id.formula)).getText(),2,3));
        key(R.id.op_mul);formula("12×3");caret(3);result("36");
    }

    @Test public void scientificFunctionTouchInsertionEvaluates() {
        key(R.id.mode_segment_scientific);
        int[] functions={R.id.fun_sin,R.id.fun_cos,R.id.fun_log,R.id.op_sqrt};
        int[][] operands={{R.id.digit_0},{R.id.digit_0},{R.id.digit_1,R.id.digit_0},{R.id.digit_9}};
        String[] arguments={"0","0","10","9"};
        String[] values={"1","2","2","4"};
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(int i=0;i<functions.length;i++) {
            key(R.id.clr);key(R.id.digit_1);key(R.id.op_add);
            for(int digit:operands[i]) key(digit);
            tap(2);key(functions[i]);
            String token=KeyMaps.toString(context,functions[i]);
            formula("1+"+token+arguments[i]);caret(2+token.length());result(values[i]);
        }
    }

    /** Real UI pad presses, including the inverse layer which exposes square. */
    @Test public void scientificSuffixTouchInsertionEvaluates() {
        key(R.id.mode_segment_scientific);
        int[] suffixes={R.id.op_sqr,R.id.op_fact,R.id.op_pct,R.id.op_pow};
        String[] text={"3²+4","3!+4","3%+4","3^+4"};
        String[] values={"13","10","4.03","13"};
        for(int i=0;i<suffixes.length;i++) {
            key(R.id.clr);key(R.id.digit_3);key(R.id.op_add);key(R.id.digit_4);
            if(i==0) key(R.id.toggle_inv);
            tap(1);key(suffixes[i]);formula(text[i]);caret(2);
            if(i==3) { key(R.id.digit_2);formula("3^2+4");caret(3); }
            result(values[i]);
            if(i==0) key(R.id.toggle_inv);
        }
    }

    /** Model-only: exact semantic insertion followed by the production evaluator. */
    @Test public void scientificModelFunctionsAndConstantsEvaluate() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        int[] functions={R.id.fun_sin,R.id.fun_cos,R.id.fun_tan,R.id.fun_arcsin,
            R.id.fun_arccos,R.id.fun_arctan,R.id.fun_ln,R.id.fun_log,R.id.fun_exp,
            R.id.op_sqrt,R.id.const_pi,R.id.const_e};
        int[][] arguments={{0},{0},{0},{0},{1},{0},{1},{1,0},{0},{9},{1},{1}};
        double[] expected={1,2,1,1,1,1,1,2,2,4,1+Math.PI,1+Math.E};
        for(int i=0;i<functions.length;i++) {
            CalculatorExpr expression=new CalculatorExpr();
            expression.add(R.id.digit_1);expression.add(R.id.op_add);
            for(int digit:arguments[i]) expression.add(modelDigit(digit));
            String before=expression.toSpannableStringBuilder(context).toString();
            String token=KeyMaps.toString(context,functions[i]);
            assertEquals(2+token.length(),expression.editAtDisplay(context,2,2,functions[i],false));
            assertEquals(before.substring(0,2)+token+before.substring(2),
                    expression.toSpannableStringBuilder(context).toString());
            assertEquals(expected[i],Double.parseDouble(expression.eval(true,null).toStringTruncated(8)),0.000001);
        }
    }

    private int modelDigit(int digit) {
        return new int[]{R.id.digit_0,R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.digit_4,
            R.id.digit_5,R.id.digit_6,R.id.digit_7,R.id.digit_8,R.id.digit_9}[digit];
    }

    /** Model-only: explicit and smart parentheses use the prefix, not the expression end. */
    @Test public void modelParenthesesAndLocalOperatorContext() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(int open:new int[]{R.id.lparen,R.id.paren}) {
            CalculatorExpr expression=new CalculatorExpr();
            for(int k:new int[]{R.id.digit_1,R.id.op_add,R.id.digit_2,R.id.digit_3,R.id.op_add,R.id.digit_4}) expression.add(k);
            assertEquals(3,expression.editAtDisplay(context,2,2,open,false));
            assertEquals("1+(23+4",expression.toSpannableStringBuilder(context).toString());
            assertEquals(6,expression.editAtDisplay(context,5,5,open==R.id.paren?R.id.paren:R.id.rparen,false));
            assertEquals("1+(23)+4",expression.toSpannableStringBuilder(context).toString());
            assertEquals("28",expression.eval(true,null).toNiceString());
        }
        for(int operator:new int[]{R.id.op_mul,R.id.op_sub}) {
            CalculatorExpr expression=new CalculatorExpr();
            for(int k:new int[]{R.id.digit_1,R.id.digit_2,R.id.op_add,R.id.digit_3}) expression.add(k);
            assertEquals(3,expression.editAtDisplay(context,3,3,operator,false));
            assertEquals(operator==R.id.op_mul?"12×3":"12−3",expression.toSpannableStringBuilder(context).toString());
            assertEquals(operator==R.id.op_mul?"36":"9",expression.eval(true,null).toNiceString());
        }
    }

    @Test public void smartParenthesesTouchInsertionEvaluates() {
        key(R.id.digit_1);key(R.id.op_add);key(R.id.digit_2);key(R.id.digit_3);
        key(R.id.op_add);key(R.id.digit_4);
        tap(2);key(R.id.paren);formula("1+(23+4");caret(3);
        tap(5);key(R.id.paren);formula("1+(23)+4");caret(6);result("28");
    }

    /** Real touch, then rejected suffix input must preserve both content and selection. */
    @Test public void invalidSymbolTouchPreservesSelection() {
        key(R.id.mode_segment_scientific);
        key(R.id.digit_3);key(R.id.op_add);key(R.id.digit_4);tap(0);
        key(R.id.op_fact);formula("3+4");caret(0);
        key(R.id.op_mul);formula("3+4");caret(0);
        result("7");
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
        // Floating ActionMode toolbars are separate windows; use accessibility UI lookup
        // instead of assuming their menu text belongs to Espresso's activity root.
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String copyLabel = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(android.R.string.copy);
        UiObject2 copy = device.wait(Until.findObject(By.text(copyLabel)), 3000);
        if (copy == null) copy = device.findObject(By.desc(copyLabel));
        if (copy == null) {
            UiObject2 overflow = device.findObject(By.res("android", "floating_toolbar_overflow_button"));
            if (overflow != null) {
                overflow.click();
                copy = device.wait(Until.findObject(By.text(copyLabel)), 3000);
            }
        }
        assertNotNull("long-press result must expose Copy", copy);
        copy.click();
        device.waitForIdle();
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
