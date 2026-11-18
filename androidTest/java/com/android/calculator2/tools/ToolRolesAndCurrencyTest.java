/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import android.content.ClipboardManager;
import android.os.SystemClock;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.calculator2.Calculator;
import com.android.calculator2.tools.data.*;
import com.android.calculator2.tools.modes.CurrencyConverterMode;
import com.android.calculator2.R;
import java.math.BigDecimal;
import java.util.*;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ToolRolesAndCurrencyTest {
    private ActivityScenario<Calculator> scenario;
    private Context context;
    private CachedRates rates(String usd) {
        Map<String,BigDecimal> values = new LinkedHashMap<>();
        values.put("EUR", BigDecimal.ONE); values.put("CNY", new BigDecimal("10"));
        values.put("USD", new BigDecimal(usd)); values.put("JPY", new BigDecimal("100"));
        return new CachedRates("EUR", System.currentTimeMillis(), "2026-01-01", values);
    }
    @Before public void launch() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("calc_tools", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("auto_update_rates", false).putBoolean("currency_secondary_output", true).commit();
        new RateCache(context).write(rates("2"));
        scenario = ActivityScenario.launch(Calculator.class);
        key(R.id.clr);
    }
    @After public void close() {
        if (scenario != null) scenario.close();
        new RateCache(context).clear();
    }
    private void key(int id) { onView(withId(id)).perform(click()); }
    private void tool(int name) { onView(withContentDescription(name)).perform(click()); }
    private void digits(String number) {
        int[] ids={R.id.digit_0,R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.digit_4,
            R.id.digit_5,R.id.digit_6,R.id.digit_7,R.id.digit_8,R.id.digit_9};
        for(char digit:number.toCharArray()) key(ids[digit-'0']);
    }
    private void text(int id, String expected) { onView(withId(id)).check(matches(withText(expected))); }
    private void currency() {
        key(R.id.mode_segment_convert); key(R.id.mode_segment_convert);
        key(R.id.clr); digits("175");
        onView(withId(R.id.currency_result_text)).perform(new ViewAction() {
            public Matcher<View> getConstraints() { return isDisplayed(); }
            public String getDescription() { return "wait for controlled cached rates"; }
            public void perform(UiController controller, View view) {
                long end=SystemClock.uptimeMillis()+10000;
                while (!"35.00".contentEquals(((TextView)view).getText()) && SystemClock.uptimeMillis()<end)
                    controller.loopMainThreadForAtLeast(50);
                assertEquals("35.00", ((TextView)view).getText().toString());
            }
        });
    }
    private void caret(int offset) {
        onView(withId(R.id.currency_input_text)).check((v,e)->{
            if(e!=null)throw e;
            assertEquals(offset,((TextView)v).getSelectionStart());
            assertEquals(offset,((TextView)v).getSelectionEnd());
        });
    }
    private void select(int start,int end) {
        onView(withId(R.id.currency_input_text)).perform(new ViewAction(){
            public Matcher<View> getConstraints(){return isDisplayed();}
            public String getDescription(){return "select currency digits";}
            public void perform(UiController c,View v){v.requestFocus();((EditText)v).setSelection(start,end);}
        });
    }
    private void tap(int offset) {
        onView(withId(R.id.currency_input_text)).perform(new ViewAction(){
            public Matcher<View> getConstraints(){return isDisplayed();}
            public String getDescription(){return "real touch at currency offset "+offset;}
            public void perform(UiController c,View v){
                TextView t=(TextView)v;int[] xy=new int[2];v.getLocationOnScreen(xy);
                float x=xy[0]+t.getCompoundPaddingLeft()+t.getLayout().getPrimaryHorizontal(offset)-t.getScrollX()+.5f;
                float y=xy[1]+v.getHeight()/2f;long time=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);
                MotionEvent up=MotionEvent.obtain(time,time+30,MotionEvent.ACTION_UP,x,y,0);
                try{c.injectMotionEvent(down);c.injectMotionEvent(up);c.loopMainThreadUntilIdle();}
                catch(InjectEventSecurityException e){throw new AssertionError(e);}
                finally{down.recycle();up.recycle();}
            }
        });
        caret(offset);
    }
    /** Verify behavior, not a class-name assertion: copy actual text; reject paste/cut/IME/keys. */
    private void readOnly(int id) {
        scenario.onActivity(a->{
            TextView output=a.findViewById(id);
            String before=output.getText().toString();
            assertFalse("nonempty result "+id,before.isEmpty());
            assertFalse("cursor "+id,output.isCursorVisible());
            assertNull("key listener "+id,output.getKeyListener());
            assertFalse(output.onCheckIsTextEditor());
            assertNull(output.onCreateInputConnection(new EditorInfo()));
            assertFalse(output.getText() instanceof Editable);
            Selection.setSelection((Spannable)output.getText(),0,before.length());
            assertTrue("copy "+id,output.onTextContextMenuItem(android.R.id.copy));
            ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals(before,clipboard.getPrimaryClip().getItemAt(0).coerceToText(a).toString());
            clipboard.setPrimaryClip(ClipData.newPlainText("attempted paste","999999"));
            assertFalse(output.onTextContextMenuItem(android.R.id.paste));
            assertFalse(output.onTextContextMenuItem(android.R.id.cut));
            output.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_9));
            output.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL));
            assertEquals(before,output.getText().toString());
        });
    }
    @Test public void sharedOutputsAreReadOnlyAcrossTools() {
        for(int mode:new int[]{R.string.tool_bmi,R.string.tool_date,R.string.tool_tax,R.string.tool_mortgage}){
            tool(mode);readOnly(R.id.formula);readOnly(R.id.result);
            if(mode!=R.string.tool_date)onView(withId(R.id.tool_field_0)).check((v,e)->{
                assertTrue(((TextView)v).isCursorVisible()); assertTrue(v instanceof EditText);
            });
        }
    }
    @Test public void relationshipAndCalculatorRestoreInputRoles() {
        tool(R.string.tool_bmi);readOnly(R.id.formula);
        tool(R.string.tool_relationship);key(R.id.digit_1);
        onView(withId(R.id.formula)).check((v,e)->assertTrue(((TextView)v).isCursorVisible()));
        readOnly(R.id.result);
        tool(R.string.tool_programmer);
        onView(withId(R.id.shared_display_rows)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        key(R.id.mode_segment_convert);key(R.id.mode_segment_convert);
        onView(withId(R.id.shared_display_rows)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        key(R.id.mode_segment_basic);key(R.id.clr);digits("12");
        onView(withId(R.id.formula)).check((v,e)->{
            assertTrue(v.isShown());assertTrue(((TextView)v).isCursorVisible());
        });
        tool(R.string.tool_relationship);
        onView(withId(R.id.formula)).check((v,e)->assertTrue(((TextView)v).isCursorVisible()));
    }
    @Test public void programmerPanelHasOnlyTwoValuesAndStatus() {
        tool(R.string.tool_programmer);key(R.id.clr);
        onView(withId(R.id.programmer_status)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        digits("2");key(R.id.op_add);readOnly(R.id.programmer_status);
        digits("3");key(R.id.eq);text(R.id.programmer_value_in,"5");
        text(R.id.programmer_value_out,"5");readOnly(R.id.programmer_value_out);readOnly(R.id.programmer_width_label);
        key(R.id.op_div);digits("0");key(R.id.eq);text(R.id.programmer_status,context.getString(R.string.programmer_divide_by_zero));readOnly(R.id.programmer_status);
        onView(withId(R.id.shared_display_rows)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        tool(R.string.tool_bmi);onView(withId(R.id.formula)).check(matches(isDisplayed()));
    }
    @Test public void programmerLongBinaryOutputIsCompleteAndCopyable() {
        tool(R.string.tool_programmer);key(R.id.clr);digits("9223372036854775807");
        text(R.id.programmer_value_out,"7FFFFFFFFFFFFFFF");
        key(R.id.programmer_chip_out);key(R.id.programmer_chip_out);
        String binary=new String(new char[63]).replace('\0','1');
        text(R.id.programmer_value_out,binary);
        onView(withId(R.id.programmer_output_scroll)).perform(swipeLeft()).check((v,e)->{
            if(e!=null)throw e;assertTrue("long binary can be browsed horizontally",v.getScrollX()>0);
        });
        readOnly(R.id.programmer_value_out);
    }
    @Test public void currencyTouchInsertionUpdatesBothControlledResults() {
        currency();tap(1);digits("9");text(R.id.currency_input_text,"1975");caret(2);
        text(R.id.currency_result_text,"395.00");
        digits("8");text(R.id.currency_input_text,"19875");caret(3);
        text(R.id.currency_result_text,"3,975.00");text(R.id.currency_result_2_text,"198,750.00");
        readOnly(R.id.currency_result_text);readOnly(R.id.currency_result_2_text);
        onView(withId(R.id.shared_display_rows)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }
    @Test public void currencyDeleteRangeDecimalClearAndSwap() {
        currency();tap(1);key(R.id.del);text(R.id.currency_input_text,"75");caret(0);
        select(0,1);digits("9");text(R.id.currency_input_text,"95");caret(1);
        key(R.id.dec_point);text(R.id.currency_input_text,"9.5");caret(2);
        key(R.id.dec_point);text(R.id.currency_input_text,"9.5");caret(2);
        key(R.id.currency_swap);caret(2);text(R.id.currency_result_text,"47.50");
        key(R.id.clr);digits("9");text(R.id.currency_input_text,"9");
    }
    @Test public void currencyRateCallbackPreservesSelection() {
        currency();tap(1);
        scenario.onActivity(a->{
            try{
                // Inject only a deterministic external rate-delivery event, never input/model text.
                java.lang.reflect.Field f=Calculator.class.getDeclaredField("mToolManager");f.setAccessible(true);
                Object mode=((ToolManager)f.get(a)).getActive();
                java.lang.reflect.Method callback=CurrencyConverterMode.class.getDeclaredMethod("onRatesLoaded",
                    CachedRates.class,ExchangeRateRepository.Source.class,CharSequence.class);
                callback.setAccessible(true);callback.invoke(mode,rates("4"),ExchangeRateRepository.Source.CACHE,"test rates");
            }catch(Exception e){throw new AssertionError(e);}
        });
        caret(1);text(R.id.currency_result_text,"70.00");digits("9");
        text(R.id.currency_input_text,"1975");caret(2);text(R.id.currency_result_text,"790.00");
    }
}
