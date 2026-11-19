/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.DatePicker;
import android.text.Selection;
import android.text.Spannable;
import java.util.Map;
import java.util.TreeMap;
import java.lang.reflect.Field;
import com.android.calculator2.tools.ToolManager;
import com.android.calculator2.tools.ToolInputFields;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.junit.*;
import org.junit.runner.RunWith;

/** Actual display rotation, not ActivityScenario.recreate or a simulated small-window test. */
@RunWith(AndroidJUnit4.class)
public class WindowConfigurationTest {
    private ActivityScenario<Calculator> scenario;
    private UiDevice device;
    @Before public void launch() throws Exception {
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.setOrientationNatural();
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("calc_tools",Context.MODE_PRIVATE).edit().clear()
                .putBoolean("auto_update_rates",false).commit();
        Map<String,java.math.BigDecimal> rates=new java.util.LinkedHashMap<>();
        rates.put("EUR",java.math.BigDecimal.ONE);rates.put("CNY",new java.math.BigDecimal("10"));
        rates.put("USD",new java.math.BigDecimal("2"));rates.put("JPY",new java.math.BigDecimal("100"));
        new com.android.calculator2.tools.data.RateCache(context).write(
            new com.android.calculator2.tools.data.CachedRates("EUR",System.currentTimeMillis(),"2026-01-01",rates));
        scenario=ActivityScenario.launch(Calculator.class);
        awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
    }
    @After public void close() throws Exception {
        if(scenario!=null) scenario.close();
        if(device!=null) { device.setOrientationNatural();device.unfreezeRotation(); }
    }
    private void awaitOrientation(int orientation) {
        long until=SystemClock.uptimeMillis()+10000;
        boolean[] ready={false};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> ready[0]=a.getResources().getConfiguration().orientation==orientation
                    && a.getWindow().getDecorView().hasWindowFocus());
            if(ready[0]) return;
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<until);
        fail("Actual display rotation/window focus did not settle");
    }
    private void evidence(String name) {
        scenario.onActivity(a -> {
            Configuration c=a.getResources().getConfiguration();
            View decor=a.getWindow().getDecorView();
            float density=a.getResources().getDisplayMetrics().density;
            Log.i("WindowEvidence",name+" device="+Build.MANUFACTURER+" "+Build.MODEL
                    +" Android="+Build.VERSION.RELEASE+" API="+Build.VERSION.SDK_INT
                    +" configDp="+c.screenWidthDp+"x"+c.screenHeightDp
                    +" decorDp="+(decor.getWidth()/density)+"x"+(decor.getHeight()/density)
                    +" multiWindow="+a.isInMultiWindowMode());
        });
        int[] capturedBounds=new int[4];
        scenario.onActivity(a -> {
            Rect r=a.getWindowManager().getCurrentWindowMetrics().getBounds();
            capturedBounds[0]=r.left;capturedBounds[1]=r.top;capturedBounds[2]=r.right;capturedBounds[3]=r.bottom;
        });
        // Shell screencap can write /sdcard under scoped storage; the instrumentation UID cannot.
        try {
            String path="/sdcard/window-"+name+".png";
            device.executeShellCommand("screencap -p "+path);
            device.executeShellCommand("echo '["+capturedBounds[0]+","+capturedBounds[1]+","+capturedBounds[2]+","+capturedBounds[3]+"]' > /sdcard/window-"+name+".json");
            String size=device.executeShellCommand("stat -c %s "+path).trim();
            if(Long.parseLong(size)<=0) throw new java.io.IOException("empty screenshot");
            Log.i("WindowEvidence","CAPTURE_OK "+name+" bytes="+size);
        } catch(Exception e) {
            // Evidence status is reported separately; it must never short-circuit geometry.
            Log.w("WindowEvidence","CAPTURE_FAIL "+name+": "+e);
        }
    }
    private void key(int id) { onView(withId(id)).perform(click()); }
    private void digits(String text) {
        int[] ids={R.id.digit_0,R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.digit_4,
            R.id.digit_5,R.id.digit_6,R.id.digit_7,R.id.digit_8,R.id.digit_9};
        for(char digit:text.toCharArray()) key(ids[digit-'0']);
    }
    private Object active(Calculator a) {
        try { Field f=Calculator.class.getDeclaredField("mToolManager");f.setAccessible(true);
            return ((ToolManager)f.get(a)).getActive();
        } catch(Exception e) { throw new AssertionError(e); }
    }
    private Object member(Object object,String name) {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {
            try { Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object); }
            catch(NoSuchFieldException e) { /* try superclass */ }
            catch(Exception e) { throw new AssertionError(e); }
        }
        return null;
    }
    private Map<String,String> modelSnapshot() {
        Map<String,String> snapshot=new TreeMap<>();
        scenario.onActivity(a -> {
            Object mode=active(a);snapshot.put("mode",mode.getClass().getName());
            for(String name:new String[]{"mGender","mEqualPayment","mLoanType","mLprBasis",
                    "mBonusMode","mStart","mEnd","mChain","mReverse","mDialect",
                    "mRadix","mSecondRadix","mWidthBits","mUnsigned","mEntry",
                    "mAccumulator","mPendingOperation","mStartNewEntry","mExpression","mError",
                    "mCategoryIndex","mFromIndex","mToIndex","mToSecondaryIndex","mSecondaryEnabled"}) {
                Object value=member(mode,name);snapshot.put(name,String.valueOf(value));
            }
            Object fields=member(mode,"mFields");
            if(fields instanceof ToolInputFields) {
                ToolInputFields values=(ToolInputFields)fields;
                snapshot.put("activeField",String.valueOf(values.getActive()));
                for(int i=0;i<values.count();i++) snapshot.put("field"+i,values.get(i));
            }
            Object input=member(mode,"mInput");
            if(input instanceof com.android.calculator2.tools.model.DecimalExpression)
                snapshot.put("expression",((com.android.calculator2.tools.model.DecimalExpression)input).getDisplayText());
        });
        return snapshot;
    }
    private Map<Integer,String> displaySnapshot() {
        Map<Integer,String> values=new TreeMap<>();
        scenario.onActivity(a -> {
            for(int id:new int[]{R.id.formula,R.id.result,R.id.programmer_value_in,R.id.programmer_value_out,
                    R.id.programmer_status,R.id.programmer_width_label,R.id.unit_input_text,R.id.unit_result_text,
                    R.id.currency_input_text,R.id.currency_result_text,R.id.date_start,R.id.date_end}) {
                TextView v=a.findViewById(id);
                if(v!=null&&v.getVisibility()==View.VISIBLE) values.put(id,v.getText().toString());
            }
        });return values;
    }
    private void selectTail(int id,int count) {
        scenario.onActivity(a -> {
            TextView v=a.findViewById(id);v.requestFocus();
            Selection.setSelection((Spannable)v.getText(),v.length()-count,v.length());
        });
    }
    private int[] selection(int id) {
        int[] result={-1,-1};
        scenario.onActivity(a -> {TextView v=a.findViewById(id);
            result[0]=v.getSelectionStart();result[1]=v.getSelectionEnd();});return result;
    }
    private void menu(int title) {
        androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
        onView(withText(title)).perform(click());
    }
    private void date(int id,int year,int month,int day) {
        key(id);
        onView(isAssignableFrom(DatePicker.class)).perform(new androidx.test.espresso.ViewAction() {
            public org.hamcrest.Matcher<View> getConstraints(){return isAssignableFrom(DatePicker.class);}
            public String getDescription(){return "select nondefault date in actual native picker";}
            public void perform(androidx.test.espresso.UiController ui,View view){
                ((DatePicker)view).updateDate(year,month-1,day);ui.loopMainThreadUntilIdle();}
        });
        onView(withId(android.R.id.button1)).perform(click());
    }
    private int prepareTool(int name) {
        if(name==R.string.tool_bmi) {
            key(R.id.bmi_gender_female);key(R.id.tool_field_1);key(R.id.clr);digits("83");
            selectTail(R.id.tool_field_1,1);return R.id.tool_field_1;
        } else if(name==R.string.tool_mortgage) {
            fingerTap(R.id.mortgage_type_combined);fingerTap(R.id.mortgage_basis_toggle);fingerTap(R.id.mortgage_method);
            fingerTap(R.id.tool_field_0);key(R.id.clr);digits("1234567");
            fingerTap(R.id.tool_field_2);key(R.id.clr);digits("17");selectTail(R.id.tool_field_2,1);return R.id.tool_field_2;
        } else if(name==R.string.tool_tax) {
            key(R.id.tax_mode_toggle);key(R.id.tool_field_0);key(R.id.clr);digits("45000");
            selectTail(R.id.tool_field_0,2);return R.id.tool_field_0;
        } else if(name==R.string.tool_programmer) {
            menu(R.string.programmer_width_16);menu(R.string.programmer_unsigned_menu);
            key(R.id.clr);digits("27");key(R.id.programmer_chip_in);key(R.id.programmer_chip_out);
            key(R.id.op_add);digits("3");selectTail(R.id.programmer_value_in,1);return R.id.programmer_value_in;
        } else if(name==R.string.tool_date) {
            date(R.id.date_start,2025,2,3);date(R.id.date_end,2026,4,5);return 0;
        } else if(name==R.string.tool_relationship) {
            menu(R.string.menu_relationship_north);key(R.id.digit_7);key(R.id.digit_8);
            // Wait for the legitimate async resolver, not a different answer after rotation.
            long until=SystemClock.uptimeMillis()+15000;
            boolean[] ready={false};
            do {
                scenario.onActivity(a -> ready[0]=!((TextView)a.findViewById(R.id.result)).getText()
                    .toString().equals(a.getString(R.string.relationship_preparing)));
                if(ready[0]) break;SystemClock.sleep(100);
            } while(SystemClock.uptimeMillis()<until);
            assertTrue("relationship answer prepared",ready[0]);
            selectTail(R.id.formula,1);return R.id.formula;
        }return 0;
    }
    private void rotateTool(int name) throws Exception {
        onView(withContentDescription(name)).perform(click());
        onView(withContentDescription(name)).check(matches(isSelected()));
        int input=prepareTool(name);
        Map<String,String> model=modelSnapshot();Map<Integer,String> display=displaySnapshot();
        int[] selected=input==0?null:selection(input);
        evidence(name+"-portrait");
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        evidence(name+"-landscape");
        onView(withContentDescription(name)).check(matches(isSelected()));
        onView(withId(R.id.mode_segment_scientific)).check(matches(org.hamcrest.Matchers.not(isSelected())));
        assertEquals("real tool model after landscape",model,modelSnapshot());
        assertEquals("tool results after landscape",display,displaySnapshot());
        if(input!=0) assertArrayEquals("legal input selection after landscape",selected,selection(input));
        device.setOrientationNatural();awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
        evidence(name+"-returned");
        onView(withContentDescription(name)).check(matches(isSelected()));
        assertEquals("real tool model after portrait return",model,modelSnapshot());
        assertEquals("tool results after portrait return",display,displaySnapshot());
        if(input!=0) assertArrayEquals(selected,selection(input));
        if(name==R.string.tool_date) {
            // Re-open a real picker AFTER restoration; retaining labels alone is insufficient.
            date(R.id.date_start,2024,6,7);
            assertNotEquals("picker still edits restored date model",model.get("mStart"),modelSnapshot().get("mStart"));
        }
    }
    @Test public void bmiSurvivesActualRotation() throws Exception { rotateTool(R.string.tool_bmi); }
    @Test public void dateSurvivesActualRotation() throws Exception { rotateTool(R.string.tool_date); }
    @Test public void relationshipSurvivesActualRotation() throws Exception { rotateTool(R.string.tool_relationship); }
    @Test public void programmerSurvivesActualRotation() throws Exception { rotateTool(R.string.tool_programmer); }
    @Test public void taxSurvivesActualRotation() throws Exception { rotateTool(R.string.tool_tax); }
    @Test public void mortgageSurvivesActualRotation() throws Exception { rotateTool(R.string.tool_mortgage); }
    // Converters share one segment; its selection must remain after rotation.
    private void rotateConverter(boolean currency) throws Exception {
        onView(withId(R.id.mode_segment_convert)).perform(click());
        if(currency) onView(withId(R.id.mode_segment_convert)).perform(click());
        int input=currency?R.id.currency_input_text:R.id.unit_input_text;
        onView(withId(input)).check(matches(isDisplayed()));
        key(R.id.clr);digits("123");key(R.id.op_add);digits("45");
        key(currency?R.id.currency_swap:R.id.unit_swap);
        scenario.onActivity(a -> {
            TextView v=a.findViewById(input);v.requestFocus();
            Selection.setSelection((Spannable)v.getText(),1,2);
        });
        Map<String,String> model=modelSnapshot();Map<Integer,String> display=displaySnapshot();
        int[] selected=selection(input);
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        evidence(currency?"currency-landscape":"unit-landscape");
        onView(withId(input)).check(matches(isDisplayed()));
        onView(withId(R.id.mode_segment_convert)).check(matches(isSelected()));
        assertEquals(model,modelSnapshot());assertEquals(display,displaySnapshot());
        assertArrayEquals(selected,selection(input));
        device.setOrientationNatural();awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
        assertEquals(model,modelSnapshot());assertEquals(display,displaySnapshot());
        assertArrayEquals(selected,selection(input));
        // Do not click/refocus/reposition the input after rotation. Use the real keypad.
        digits("9");
        assertEquals("key edits restored middle range in real expression","193+45",modelSnapshot().get("expression"));
        onView(withId(input)).check(matches(withText("193+45")));
        int output=currency?R.id.currency_result_text:R.id.unit_result_text;
        assertNotEquals("conversion result updates from changed model",display.get(output),displaySnapshot().get(output));
        scenario.onActivity(a -> assertTrue("restored input has usable measured height",a.findViewById(input).getHeight()>0));
    }
    @Test public void unitSurvivesActualRotation() throws Exception { rotateConverter(false); }
    @Test public void currencySurvivesActualRotation() throws Exception { rotateConverter(true); }
    @Test public void restoredFieldContinuesEditingWithoutRetargeting() throws Exception {
        onView(withContentDescription(R.string.tool_bmi)).perform(click());
        key(R.id.tool_field_1);key(R.id.clr);digits("183");
        scenario.onActivity(a -> {
            TextView v=a.findViewById(R.id.tool_field_1);
            Selection.setSelection((Spannable)v.getText(),v.length()-2,v.length()-1);
        });
        int[] before=selection(R.id.tool_field_1);
        Map<String,String> model=modelSnapshot();
        String result=displaySnapshot().get(R.id.formula);
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        assertArrayEquals(before,selection(R.id.tool_field_1));
        digits("9");
        Map<String,String> after=modelSnapshot();
        assertEquals("193",after.get("field1"));
        assertEquals("active field retained",model.get("activeField"),after.get("activeField"));
        assertEquals("other field unchanged",model.get("field0"),after.get("field0"));
        assertNotEquals("numeric BMI recomputed",result,displaySnapshot().get(R.id.formula));
        scenario.onActivity(a -> {
            java.math.BigDecimal height=new java.math.BigDecimal(after.get("field0"));
            com.android.calculator2.tools.model.BmiCalculator.Result computed=
                com.android.calculator2.tools.model.BmiCalculator.compute(height,new java.math.BigDecimal("193"),
                    new java.math.MathContext(10,java.math.RoundingMode.HALF_UP));
            assertTrue(((TextView)a.findViewById(R.id.formula)).getText().toString().contains(computed.bmi.toPlainString()));
        });
    }
    @Test public void toolGesturesDoNotOpenHistory() throws Exception {
        key(R.id.mode_segment_convert);
        key(R.id.clr);digits("12345");
        int[] rect=new int[4];
        scenario.onActivity(a -> {
            Rect r=new Rect();assertTrue(a.findViewById(R.id.unit_input_text).getGlobalVisibleRect(r));
            rect[0]=r.left;rect[1]=r.top;rect[2]=r.right;rect[3]=r.bottom;
        });
        // A real long press selects input text without exposing history.
        assertTrue(device.swipe(rect[2]-20,(rect[1]+rect[3])/2,rect[2]-20,(rect[1]+rect[3])/2,90));
        awaitMotionState(R.id.start_state);
        scenario.onActivity(a -> {
            TextView v=a.findViewById(R.id.unit_input_text);
            assertTrue("long press produces text selection",v.getSelectionStart()!=v.getSelectionEnd());
        });
        device.pressBack();
        onView(withId(R.id.unit_result_text)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),
            androidx.test.espresso.action.ViewActions.longClick());
        awaitMotionState(R.id.start_state);
        scenario.onActivity(a -> {
            TextView v=a.findViewById(R.id.unit_result_text);
            assertTrue("output remains selectable for copy",v.getSelectionStart()!=v.getSelectionEnd());
        });
        // Floating toolbar lives in a separate window; locate the real system action there.
        String copyLabel=InstrumentationRegistry.getInstrumentation().getTargetContext().getString(android.R.string.copy);
        androidx.test.uiautomator.UiObject2 copy=device.wait(
            androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(copyLabel)),5000);
        assertNotNull("system floating Copy action",copy);copy.click();
        awaitMotionState(R.id.start_state);
        scenario.onActivity(a -> {
            android.content.ClipboardManager cb=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            assertTrue("real copy populated clipboard",cb.hasPrimaryClip());
        });
        // Let Android's clipboard overlay disappear before touching tool controls.
        SystemClock.sleep(7000);
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        // Vertical scroll starts inside tool content, not from the handle.
        int[] initialScroll={0};
        scenario.onActivity(a -> initialScroll[0]=converterScroll(a).getScrollY());
        scenario.onActivity(a -> {
            Rect r=new Rect();assertTrue(a.findViewById(R.id.tool_control_slot).getGlobalVisibleRect(r));
            rect[0]=r.centerX();rect[1]=r.top+r.height()/4;rect[2]=r.bottom-r.height()/4;
        });
        assertTrue(device.swipe(rect[0],rect[2],rect[0],rect[1],30));
        awaitMotionState(R.id.start_state);
        scenario.onActivity(a -> assertTrue("actual converter scrolling moved content",
            converterScroll(a).getScrollY()>initialScroll[0]));
        assertTrue(device.swipe(rect[0],rect[1],rect[0],rect[2],30));
        awaitMotionState(R.id.start_state);
        dragHistoryFromVisibleHandle();
    }
    private android.widget.ScrollView converterScroll(Calculator a) {
        android.view.ViewParent p=a.findViewById(R.id.unit_input_text).getParent();
        while(!(p instanceof android.widget.ScrollView)) {
            assertNotNull("converter has a scroll viewport",p);p=p.getParent();
        }
        return (android.widget.ScrollView)p;
    }
    @Test public void landscapeScientificKeyBounds() throws Exception {
        onView(withId(R.id.mode_segment_scientific)).perform(click());
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        evidence("scientific-normal");
        assertScientificBounds();
        onView(withId(R.id.toggle_inv)).perform(click());
        evidence("scientific-inverse");
        assertScientificBounds();
        device.setOrientationNatural();awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
        evidence("scientific-returned-portrait");
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        evidence("scientific-returned-landscape");
        assertScientificBounds();
        onView(withId(R.id.mode_segment_basic)).perform(click());
        evidence("landscape-basic-after-scientific");
        assertBasicKeyboardFillsStack();
        onView(withId(R.id.clr)).perform(click());
        onView(withId(R.id.digit_7)).perform(click());
        onView(withId(R.id.formula)).check(matches(withText("7")));
    }
    private void assertBasicKeyboardFillsStack() {
        scenario.onActivity(a -> {
            View advanced=a.findViewById(R.id.advanced_pad);
            assertEquals("no residual scientific panel",View.GONE,advanced.getVisibility());
            Rect stack=new Rect(),input=new Rect(),bar=new Rect();
            assertTrue(a.findViewById(R.id.pad_stack).getGlobalVisibleRect(stack));
            assertTrue(a.findViewById(R.id.input_pad).getGlobalVisibleRect(input));
            a.findViewById(R.id.tool_bar).getGlobalVisibleRect(bar);
            assertEquals("ordinary keyboard must use the whole keyboard region (no empty column)",stack,input);
            assertTrue("ordinary keyboard below toolbar",input.top>=bar.bottom);
        });
    }
    private void awaitMotionState(int state) {
        long until=SystemClock.uptimeMillis()+10000;
        int[] current={-1};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> current[0]=((DisplayMotionLayout)a.findViewById(R.id.main_calculator)).getCurrentState());
            if(current[0]==state) return;
            SystemClock.sleep(50);
        } while(SystemClock.uptimeMillis()<until);
        assertEquals("history motion must finish",state,current[0]);
    }
    @Test public void landscapeHistoryExpandCollapseKeepsKeyboardRegion() throws Exception {
        onView(withId(R.id.mode_segment_scientific)).perform(click());
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        assertScientificBounds();
        Rect[] before={new Rect()};
        scenario.onActivity(a -> a.findViewById(R.id.pad_stack).getGlobalVisibleRect(before[0]));
        // Real history navigation action exercises the same MotionLayout transition as dragging.
        // This is a basic transition check, not a claim of testing the full edge-to-edge gesture.
        onView(withContentDescription(R.string.menu_history)).perform(click());
        awaitMotionState(R.id.end_state);
        evidence("landscape-history-expanded");
        onView(withId(R.id.history_toolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.history_recycler_view)).check(matches(isDisplayed()));
        scenario.onActivity(a -> {
            Rect history=new Rect(),display=new Rect();
            assertTrue(a.findViewById(R.id.history_frame).getGlobalVisibleRect(history));
            a.findViewById(R.id.display).getGlobalVisibleRect(display);
            assertTrue("expanded history has a nonzero viewport",history.width()>0&&history.height()>0);
            assertTrue("history stays above collapsed display",history.bottom<=display.top);
        });
        device.pressBack();awaitMotionState(R.id.start_state);
        evidence("landscape-history-collapsed");
        assertScientificBounds();
        scenario.onActivity(a -> {
            Rect after=new Rect();a.findViewById(R.id.pad_stack).getGlobalVisibleRect(after);
            assertEquals("history close restores exact keyboard region",before[0],after);
        });
        onView(withId(R.id.mode_segment_basic)).perform(click());
        assertBasicKeyboardFillsStack();
    }
    @Test public void historyHandleAnchorsToDisplayAcrossPanelModes() throws Exception {
        java.util.List<String> errors=new java.util.ArrayList<>();
        for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}) {
            if(orientation==Configuration.ORIENTATION_LANDSCAPE) device.setOrientationLeft();
            else device.setOrientationNatural();
            awaitOrientation(orientation);
            key(R.id.mode_segment_basic);
            int expectedGap=handleGap("ordinary-"+orientation);
            int index=0;
            for(int mode:new int[]{R.id.mode_segment_convert,R.id.mode_segment_convert,0}) {
                if(mode==0) onView(withContentDescription(R.string.tool_programmer)).perform(click());
                else key(mode);
                String name=mode==0?"programmer":index++==0?"unit":"currency";
                int gap=handleGap(name+"-"+orientation);
                evidence("handle-"+name+"-"+orientation);
                if(expectedGap!=gap) errors.add(name+" orientation="+orientation+" expected="+expectedGap+" actual="+gap);
                if(mode==0) {
                    key(R.id.clr);digits("27");key(R.id.op_add);
                    int pending=handleGap("programmer-pending-"+orientation);
                    if(pending!=expectedGap) errors.add("pending="+pending+" expected="+expectedGap);
                    digits("3");key(R.id.eq);key(R.id.op_div);digits("0");key(R.id.eq);
                    int error=handleGap("programmer-error-"+orientation);
                    if(error!=expectedGap) errors.add("error="+error+" expected="+expectedGap);
                }
            }
        }
        // Compare only after collecting every mode's real bounds; do not hide a failed mode.
        assertTrue("all handle/display bottom gaps must agree: "+errors,errors.isEmpty());
    }
    @Test public void historyHandleRespondsToActualDragAcrossPanelModes() throws Exception {
        for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}) {
            if(orientation==Configuration.ORIENTATION_LANDSCAPE) device.setOrientationLeft();
            else device.setOrientationNatural();
            awaitOrientation(orientation);key(R.id.mode_segment_basic);
            dragHistoryFromVisibleHandle();
            for(int mode:new int[]{R.id.mode_segment_convert,R.id.mode_segment_convert,0}) {
                if(mode==0) onView(withContentDescription(R.string.tool_programmer)).perform(click());
                else key(mode);
                dragHistoryFromVisibleHandle();
            }
        }
    }
    private int handleGap(String name) {
        int[] gap={Integer.MIN_VALUE};
        scenario.onActivity(a -> {
            Rect display=new Rect(),handle=new Rect(),bar=new Rect();
            assertTrue(a.findViewById(R.id.display).getGlobalVisibleRect(display));
            View view=a.findViewById(R.id.drag_handle_view);
            boolean visible=view.getGlobalVisibleRect(handle);
            int[] location=new int[2];view.getLocationOnScreen(location);
            Rect measured=new Rect(location[0],location[1],location[0]+view.getWidth(),location[1]+view.getHeight());
            a.findViewById(R.id.tool_bar).getGlobalVisibleRect(bar);
            Log.i("WindowEvidence","HANDLE "+name+" display="+display+" measured="+measured+" visible="+handle+" toolbar="+bar);
            assertTrue("visible handle "+name+" measured="+measured+" display="+display,visible);
            assertTrue("handle inside display",display.contains(handle));
            assertTrue("handle above toolbar",handle.bottom<=bar.top);
            gap[0]=display.bottom-handle.bottom;
        });return gap[0];
    }
    private void dragHistoryFromVisibleHandle() {
        int[] point=new int[3];
        scenario.onActivity(a -> {
            Rect handle=new Rect(),window=new Rect();
            a.findViewById(R.id.drag_handle_view).getGlobalVisibleRect(handle);
            a.getWindow().getDecorView().getGlobalVisibleRect(window);
            point[0]=handle.centerX();point[1]=handle.centerY();
            point[2]=window.bottom-window.height()/10;
        });
        assertTrue("drag from the visible handle",device.swipe(point[0],point[1],point[0],point[2],35));
        awaitMotionState(R.id.end_state);
        onView(withId(R.id.history_toolbar)).check(matches(isDisplayed()));
        device.pressBack();awaitMotionState(R.id.start_state);
    }

    @Test public void bmiScaleIconFollowsThemeAndSelection() throws Exception {
        int original=androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        try {
            for(int night:new int[]{androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(night));
                awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
                for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}) {
                    if(orientation==Configuration.ORIENTATION_LANDSCAPE) device.setOrientationLeft();
                    else device.setOrientationNatural();
                    awaitOrientation(orientation);
                    key(R.id.mode_segment_basic);assertBmiIcon(false);
                    evidence("bmi-icon-"+night+"-"+orientation+"-unselected");
                    onView(withContentDescription(R.string.tool_bmi)).perform(click());assertBmiIcon(true);
                    evidence("bmi-icon-"+night+"-"+orientation+"-selected");
                    assertToolIcon(R.string.tool_tax,false);
                    evidence("tax-icon-"+night+"-"+orientation+"-unselected");
                    onView(withContentDescription(R.string.tool_tax)).perform(click());
                    assertToolIcon(R.string.tool_tax,true);assertBmiIcon(false);
                    evidence("tax-icon-"+night+"-"+orientation+"-selected");
                }
                device.setOrientationNatural();awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(original));
        }
    }
    private void assertBmiIcon(boolean selected) {
        assertToolIcon(R.string.tool_bmi,selected);
    }
    private void assertToolIcon(int name,boolean selected) {
        onView(withContentDescription(name)).check((view,error) -> {
            if(error!=null)throw error;
            android.widget.ImageView image=(android.widget.ImageView)view;
            assertEquals(selected,image.isSelected());assertTrue(image.isClickable());
            Rect visible=new Rect();assertTrue(image.getGlobalVisibleRect(visible));
            assertEquals(image.getWidth(),visible.width());assertEquals(image.getHeight(),visible.height());
            android.graphics.drawable.Drawable d=image.getDrawable();
            int px=Math.round(24*image.getResources().getDisplayMetrics().density);
            assertEquals(px,d.getIntrinsicWidth());assertEquals(px,d.getIntrinsicHeight());
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(px,px,android.graphics.Bitmap.Config.ARGB_8888);
            Rect old=new Rect(d.getBounds());d.setBounds(0,0,px,px);d.draw(new android.graphics.Canvas(bitmap));d.setBounds(old);
            int expected=androidx.core.content.ContextCompat.getColorStateList(image.getContext(),R.color.mode_segment_text_states)
                .getColorForState(image.getDrawableState(),0);
            boolean opaque=false;
            for(int y=0;y<px;y++)for(int x=0;x<px;x++) {
                int color=bitmap.getPixel(x,y);
                if(android.graphics.Color.alpha(color)==255) {opaque=true;assertEquals("theme state tint",expected,color);}
            }
            assertTrue("tool vector rendered",opaque);bitmap.recycle();
        });
    }
    private int splitTask;
    private void enterRealSplit() throws Exception {
        scenario.onActivity(a -> {
            splitTask=a.getTaskId();
            android.content.Intent settings=new android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            a.startActivity(settings);
        });
        boolean paired=awaitVerifiedPair();
        evidence("bmi-adjacent-attempt");
        Log.i("SplitPhase","pair NEW_TASK|LAUNCH_ADJACENT "+(paired?"VERIFIED ":"NOT_VERIFIED ")+safeSplitSample());
        if(!paired) {
            // One bounded fallback through the launcher's real split picker, including app two.
            device.executeShellCommand("am task focus "+splitTask);
            assertTrue("open real recent tasks",device.pressRecentApps());
            androidx.test.uiautomator.UiObject2 icon=device.wait(androidx.test.uiautomator.Until.findObject(
                androidx.test.uiautomator.By.res("com.android.launcher3","icon")),3000);
            if(icon==null)throw new AssertionError("ENVIRONMENT_BLOCKED: launcher task icon unavailable; adjacent launch not paired");
            icon.click();
            androidx.test.uiautomator.UiObject2 split=device.wait(androidx.test.uiautomator.Until.findObject(
                androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("(?i)split(?: top| screen)?"))),3000);
            if(split==null)throw new AssertionError("ENVIRONMENT_BLOCKED: no real Split entry in task menu");
            split.click();
            androidx.test.uiautomator.UiObject2 settings=device.wait(androidx.test.uiautomator.Until.findObject(
                androidx.test.uiautomator.By.text("Settings")),3000);
            if(settings==null)settings=device.findObject(androidx.test.uiautomator.By.desc("Settings"));
            if(settings==null)throw new AssertionError("ENVIRONMENT_BLOCKED: Settings cannot be selected as app two");
            settings.click();
            paired=awaitVerifiedPair();evidence("bmi-recents-pair-attempt");
            Log.i("SplitPhase","pair RECENTS app-two=Settings "+(paired?"VERIFIED ":"NOT_VERIFIED ")+safeSplitSample());
            assertTrue("ENVIRONMENT_BLOCKED: neither adjacent launch nor completed recents selection produced a verifiable pair",paired);
        }
        // Focus an inert part of the verified calculator window, not the input field.
        int[] point=new int[2];
        scenario.onActivity(a -> {Rect r=new Rect();assertTrue(a.findViewById(R.id.toolbar).getGlobalVisibleRect(r));
            point[0]=r.centerX();point[1]=r.centerY();});
        device.click(point[0],point[1]);awaitRealSplit();
    }
    private boolean awaitVerifiedPair() throws Exception {
        long until=SystemClock.uptimeMillis()+8000,since=0;String last="";
        do {
            SplitSample sample=splitSample();String key=sample.stableKey();long now=SystemClock.uptimeMillis();
            if(sample.multi&&sample.verifiedCompanion()&&key.equals(last)) {
                if(now-since>=700)return true;
            } else {last=key;since=now;}
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<until);
        return false;
    }
    private void awaitRealSplit() {
        long until=SystemClock.uptimeMillis()+15000;
        boolean[] ready={false};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> ready[0]=a.isInMultiWindowMode()&&a.getWindow().getDecorView().hasWindowFocus());
            if(ready[0]) return;
            try {device.executeShellCommand("am task focus "+splitTask);}
            catch(java.io.IOException e) {throw new AssertionError(e);}
            SystemClock.sleep(250);
        } while(SystemClock.uptimeMillis()<until);
        evidence("split-entry-failed");fail("real system split-screen and app focus required");
    }
    private void leaveRealSplit() throws Exception {
        // The Shell removeFromSideStage command reparents into main stage on this image;
        // it does not reliably leave multiwindow. Dismiss via the actual system divider.
        boolean[] activeSplit={false};scenario.onActivity(a -> activeSplit[0]=a.isInMultiWindowMode());
        if(!activeSplit[0])return;
        Rect divider=realDividerBounds();
        assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),device.getDisplayHeight()-1,80));
        long until=SystemClock.uptimeMillis()+10000;boolean[] split={true};
        do {
            scenario.onActivity(a -> split[0]=a.isInMultiWindowMode());
            if(!split[0])break;SystemClock.sleep(150);
        } while(SystemClock.uptimeMillis()<until);
        assertFalse("exit real split before closing scenario",split[0]);
        SystemClock.sleep(700);
    }
    private Rect realDividerBounds() throws Exception {
        // Embedded Shell divider nodes are not reliably in the accessibility tree.
        // Read BOTH real task rectangles from ActivityManager; the gap is the divider.
        String tasks=device.executeShellCommand("am stack list");
        Rect app=taskBounds(tasks,"taskId="+splitTask+":"),companion=new Rect();
        // ActivityManager may name the companion STAGE "unknown", not the leaf app.
        // Select the visible task immediately below our top stage by its measured bounds.
        for(String line:tasks.split("\n")) {
            Rect candidate=taskBounds(line,"taskId=");
            if(!candidate.isEmpty()&&candidate.top>=app.bottom&&candidate.left==app.left
                    &&candidate.right==app.right&&(companion.isEmpty()||candidate.top<companion.top))
                companion.set(candidate);
        }
        assertFalse("calculator task bounds: "+tasks,app.isEmpty());
        assertFalse("companion task bounds: "+tasks,companion.isEmpty());
        assertTrue("tasks separated by system divider: "+app+" / "+companion,app.bottom<companion.top);
        Rect r=new Rect(Math.max(app.left,companion.left),app.bottom,
            Math.min(app.right,companion.right),companion.top);
        Log.i("WindowEvidence","SPLIT task="+app+" companion="+companion+" divider="+r);
        return r;
    }
    private void resizeRealSplit(int thirds) throws Exception {
        Rect r=realDividerBounds();
        int[] size={0};scenario.onActivity(a -> size[0]=a.getResources().getConfiguration().screenHeightDp);
        // Drag the system divider to the adjacent snap target, without wm size/density changes.
        assertTrue(device.swipe(r.centerX(),r.centerY(),r.centerX(),device.getDisplayHeight()*thirds/3,45));
        long until=SystemClock.uptimeMillis()+10000;
        boolean[] changed={false};
        do {
            scenario.onActivity(a -> changed[0]=a.isInMultiWindowMode()
                && Math.abs(a.getResources().getConfiguration().screenHeightDp-size[0])>30);
            if(changed[0]) break;SystemClock.sleep(150);
        } while(SystemClock.uptimeMillis()<until);
        assertTrue("actual split window height changed by divider drag; tasks="+device.executeShellCommand("am stack list"),changed[0]);
        // Wait for the snap animation/layout, not just its first intermediate configuration.
        SystemClock.sleep(1200);
        device.executeShellCommand("am task focus "+splitTask);awaitRealSplit();
    }
    private Rect taskBounds(String tasks,String marker) {
        for(String line:tasks.split("\n")) {
            if(!line.contains(marker)||!line.contains("visible=true"))continue;
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                "bounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]").matcher(line);
            if(m.find())return new Rect(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)));
        }
        return new Rect();
    }
    private void assertSplitUsable() {
        onView(withId(R.id.tool_bar)).check(matches(isDisplayed()));
        onView(withId(R.id.digit_7)).check(matches(isDisplayed()));
        onView(withId(R.id.clr)).check(matches(isDisplayed()));
        scenario.onActivity(a -> {
            assertTrue("must remain a real multiwindow",a.isInMultiWindowMode());
            View slot=a.findViewById(R.id.tool_control_slot);
            if(slot.getVisibility()==View.VISIBLE) {
                Rect r=new Rect();assertTrue("tool viewport visible",slot.getGlobalVisibleRect(r));
                assertTrue("tool viewport nonzero",r.height()>0&&r.width()>0);
            }
            Rect bar=new Rect(),pad=new Rect();
            a.findViewById(R.id.tool_bar).getGlobalVisibleRect(bar);
            a.findViewById(R.id.input_pad).getGlobalVisibleRect(pad);
            assertFalse("toolbar and keyboard do not overlap: bar="+bar+" pad="+pad,Rect.intersects(bar,pad));
        });
    }
    private void splitTool(int name,boolean converter,boolean currency) throws Exception {
        int input;
        if(converter) {
            input=currency?R.id.currency_input_text:R.id.unit_input_text;
            key(R.id.mode_segment_convert);if(currency) key(R.id.mode_segment_convert);
            key(R.id.clr);digits("123");key(R.id.op_add);digits("45");
            selectTail(currency?R.id.currency_input_text:R.id.unit_input_text,2);
        } else { onView(withContentDescription(name)).perform(click());input=prepareTool(name); }
        int[] selected=input==0?null:selection(input);
        Map<String,String> model=modelSnapshot();Map<Integer,String> display=displaySnapshot();
        try (AutoCloseable split=() -> leaveRealSplit()) {
            enterRealSplit();evidence("split-half-"+name);assertSplitUsable();
            if(name==R.string.tool_bmi) assertBmiIcon(true);
            scenario.onActivity(a -> {
                int height=a.getWindowManager().getCurrentWindowMetrics().getBounds().height();
                float ratio=height/(float)device.getDisplayHeight();
                assertTrue("actual 50:50 task height ratio="+ratio,ratio>0.40f&&ratio<0.58f);
            });
            assertSplitInput(input,selected);
            assertEquals("model at 50:50",model,modelSnapshot());
            assertEquals("results at 50:50",display,displaySnapshot());
            for(int thirds:new int[]{2,1}) {
                resizeRealSplit(thirds);evidence("split-resized-"+thirds+"-"+name);assertSplitUsable();
                assertSplitInput(input,selected);
                assertEquals("model after real divider resize",model,modelSnapshot());
                assertEquals("results after real divider resize",display,displaySnapshot());
            }
        }
    }
    private void assertSplitInput(int input,int[] selected) {
        if(input==0)return;
        assertArrayEquals("selection retained in actual split window",selected,selection(input));
        onView(withId(input)).check(matches(isDisplayed()));
        scenario.onActivity(a -> assertTrue("input retains nonzero measured height",a.findViewById(input).getHeight()>0));
    }
    /** A single gated diagnostic; no later layout check runs after an earlier transition fails. */
    @Test public void bmiSurvivesRealSplitAndResize() throws Exception { fieldToolSplit(R.string.tool_bmi); }
    private void fieldToolSplit(int tool) throws Exception {
        onView(withContentDescription(tool)).perform(click());
        int input=prepareTool(tool);
        scenario.onActivity(a -> splitTask=a.getTaskId());
        Map<String,String> model=modelSnapshot();Map<Integer,String> display=displaySnapshot();
        int[] selected=selection(input);
        String phase="full";
        java.util.Set<String> completed=new java.util.HashSet<>();
        java.util.List<Throwable> issues=new java.util.ArrayList<>();
        try {
            SplitSample full=stableSample(false,null);
            Log.i("SplitPhase","full PASS "+full);completed.add("full");
            phase="entry";enterRealSplit();
            SplitSample half=stableSample(true,null);
            evidence("bmi-verified-half");Log.i("SplitPhase","entry IDENTITY "+half);
            assertTrue("real Settings companion required: "+half,half.verifiedCompanion());
            float ratio=half.app.height()/(float)device.getDisplayHeight();
            assertTrue("50:50 task ratio="+ratio,ratio>0.40f&&ratio<0.58f);
            recordRepresentativeStage("entry",half,input,selected,model,display,issues);completed.add("entry");
            for(int thirds:new int[]{2,1}) {
                phase=thirds==2?"enlarge":"reduce";
                SplitSample before=stableSample(true,null);
                Rect divider=verifiedDivider();
                assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),device.getDisplayHeight()*thirds/3,60));
                SplitSample after=stableSample(true,before.app);
                assertTrue("Settings remains the other app: "+after,after.verifiedCompanion());
                if(thirds==2)assertTrue("enlargement",after.app.height()>before.app.height());
                else assertTrue("reduction",after.app.height()<before.app.height());
                evidence("bmi-"+phase);
                recordRepresentativeStage(phase,after,input,selected,model,display,issues);completed.add(phase);
            }
        } catch(Throwable error) {
            issues.add(error);Log.i("SplitPhase",phase+" TRANSITION_FAIL "+error.getClass().getSimpleName()+":"+shortMessage(error)+" STATE "+safeSplitSample());
        } finally {
            for(String p:new String[]{"entry","enlarge","reduce"})
                if(!completed.contains(p)&&!p.equals(phase))Log.i("SplitPhase",p+" NOT_RUN (blocked by "+phase+")");
            try {
                SplitSample current=splitSample();
                if(current.multi) {
                    Rect divider=verifiedDivider();
                    assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),device.getDisplayHeight()-1,80));
                }
                SplitSample full=stableSample(false,null);
                assertEquals("model after returning fullscreen",model,modelSnapshot());
                assertEquals("outputs after returning fullscreen",display,displaySnapshot());
                assertArrayEquals("selection after returning fullscreen",selected,selection(input));
                Log.i("SplitPhase","exit PASS "+full);
            } catch(Throwable cleanup) {
                Log.i("SplitPhase","exit FAIL "+cleanup.getClass().getSimpleName()+":"+shortMessage(cleanup)+" STATE "+safeSplitSample());
                issues.add(cleanup);
            }
        }
        if(!issues.isEmpty()) {
            Throwable primary=issues.get(0);
            for(int i=1;i<issues.size();i++)primary.addSuppressed(issues.get(i));
            if(primary instanceof Exception)throw (Exception)primary;
            if(primary instanceof Error)throw (Error)primary;
        }
    }
    private void recordRepresentativeStage(String phase,SplitSample sample,int input,int[] selected,
            Map<String,String> model,Map<Integer,String> display,java.util.List<Throwable> issues) {
        String data="PASS",layout="PASS";
        try {
            assertArrayEquals("BMI selection",selected,selection(input));
            assertEquals("BMI model",model,modelSnapshot());assertEquals("BMI outputs",display,displaySnapshot());
        } catch(Throwable error) {issues.add(error);data="FAIL";}
        try {
            representativeLayout(input,selected,model,display);
            copyFieldToolOutput(R.id.formula);copyFieldToolOutput(R.id.result);
            if(phase.equals("reduce")) {
                assertTextReachable(input);assertArrayEquals(selected,selection(input));
                String field="field"+model.get("activeField");
                String old=model.get(field);String oldResult=display.get(R.id.formula);
                // Use the real keypad WITHOUT clicking or repositioning the restored field.
                key(R.id.digit_9);
                assertEquals("key edits original selected range",old.substring(0,old.length()-1)+"9",modelSnapshot().get(field));
                if(field.equals("field1")) { // BMI shared-input representative: consecutive input.
                    key(R.id.digit_2);assertEquals(old.substring(0,old.length()-1)+"92",modelSnapshot().get(field));
                }
                assertNotEquals("real result recomputed",oldResult,displaySnapshot().get(R.id.formula));
                Map<String,String> next=modelSnapshot();Map<Integer,String> outputs=displaySnapshot();
                model.clear();model.putAll(next);display.clear();display.putAll(outputs);
                int[] caret=selection(input);selected[0]=caret[0];selected[1]=caret[1];
            }
        } catch(Throwable error) {issues.add(error);layout="FAIL";}
        int[] heights=new int[2];scenario.onActivity(a -> {
            heights[0]=a.findViewById(input).getHeight();heights[1]=a.findViewById(R.id.result).getHeight();
        });
        Log.i("SplitPhase",phase+" TRANSITION=PASS MODEL_SELECTION="+data+" LAYOUT="+layout
            +" inputPx="+heights[0]+" resultPx="+heights[1]+" "+sample);
    }
    private void representativeLayout(int input,int[] selected,Map<String,String> model,Map<Integer,String> display) {
        assertSplitUsable();
        Rect viewport=new Rect();
        scenario.onActivity(a -> assertTrue(a.findViewById(R.id.main_calculator)
            .findViewWithTag("field_tool_viewport").getGlobalVisibleRect(viewport)));
        assertTrue("real field viewport scroll gesture",device.swipe(viewport.left+4,viewport.bottom-8,
            viewport.left+4,viewport.top+8,40));
        awaitMotionState(R.id.start_state);
        assertTextReachable(input);
        assertArrayEquals("selection retained after scrolling to active field",selected,selection(input));
        assertEquals("BMI model",model,modelSnapshot());assertEquals("BMI outputs",display,displaySnapshot());
        assertTextReachable(R.id.formula);assertTextReachable(R.id.result);
        scenario.onActivity(a -> {
            float density=a.getResources().getDisplayMetrics().density;
            for(int id:new int[]{R.id.digit_7,R.id.digit_9,R.id.clr,R.id.eq}) {
                View key=a.findViewById(id);Rect r=new Rect();assertTrue(key.getGlobalVisibleRect(r));
                assertTrue("key is clickable "+id,key.isEnabled()&&key.isClickable());
                assertTrue("key must not collapse to a thin strip: "+id+" "+r,
                    r.height()/density>=32&&r.width()/density>=32);
            }
            Log.i("SplitPhase","MEASURE display="+a.findViewById(R.id.display).getHeight()
                +" controls="+a.findViewById(R.id.tool_control_slot).getHeight()
                +" formula="+a.findViewById(R.id.formula).getHeight()+" result="+a.findViewById(R.id.result).getHeight()
                +" keypad="+a.findViewById(R.id.pad_stack).getHeight());
        });
        awaitMotionState(R.id.start_state);
    }
    private void copyFieldToolOutput(int id) throws Exception {
        assertTextReachable(id);
        onView(withId(id)).perform(androidx.test.espresso.action.ViewActions.longClick());
        awaitMotionState(R.id.start_state);
        scenario.onActivity(a -> {
            TextView out=a.findViewById(id);String expected=out.getText().toString();
            assertFalse(expected.isEmpty());
            Selection.selectAll((Spannable)out.getText());
            assertTrue("copy complete tool output",out.onTextContextMenuItem(android.R.id.copy));
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals(expected,clipboard.getPrimaryClip().getItemAt(0).coerceToText(a).toString());
            if(out instanceof CalculatorResult)((CalculatorResult)out).stopActionModeOrContextMenu();
            if(out instanceof CalculatorFormula)((CalculatorFormula)out).stopActionModeOrContextMenu();
        });
        awaitMotionState(R.id.start_state);
        device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.res("com.android.systemui","clipboard_ui")),20000);
    }
    private void assertTextReachable(int id) {
        onView(withId(id)).perform(new androidx.test.espresso.ViewAction() {
            public org.hamcrest.Matcher<View> getConstraints(){return isAssignableFrom(TextView.class);}
            public String getDescription(){return "scroll whole text rectangle, not Espresso's 90 percent visibility shortcut";}
            public void perform(androidx.test.espresso.UiController ui,View view) {
                View ancestor=view;
                while(ancestor!=null&&!"field_tool_viewport".equals(ancestor.getTag()))
                    ancestor=ancestor.getParent() instanceof View?(View)ancestor.getParent():null;
                assertNotNull("bounded field viewport",ancestor);
                android.widget.ScrollView scroll=(android.widget.ScrollView)ancestor;
                Rect rect=new Rect(0,0,view.getWidth(),view.getHeight());
                scroll.offsetDescendantRectToMyCoords(view,rect);
                scroll.scrollTo(scroll.getScrollX(),rect.top-scroll.getPaddingTop());
                ui.loopMainThreadUntilIdle();
            }
        }).check((view,error) -> {
            if(error!=null)throw error;
            TextView text=(TextView)view;Rect visible=new Rect();assertTrue(text.getGlobalVisibleRect(visible));
            assertNotNull("text layout measured",text.getLayout());
            int required=text.getLayout().getHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom();
            assertTrue("full vertical text measured "+id+" measured="+text.getHeight()+" required="+required,text.getHeight()>=required);
            StringBuilder chain=new StringBuilder();
            View node=text;
            while(node!=null) {
                chain.append(node.getClass().getSimpleName()).append("[top=").append(node.getTop())
                    .append(",h=").append(node.getHeight()).append(",scroll=").append(node.getScrollY())
                    .append(",ty=").append(node.getTranslationY()).append("]");
                node=node.getParent() instanceof View?(View)node.getParent():null;
            }
            assertEquals("text can be fully scrolled into viewport "+id+" visible="+visible+" ancestors="+chain,text.getHeight(),visible.height());
        });
    }
    private static class SplitSample {
        int task,width,height,otherTask=-1;boolean multi,focus;
        Rect app=new Rect(),other=new Rect();boolean otherWindowVisible,otherAccessibilityRoot;
        String leaves="",otherApp="",focusedWindow="";
        boolean verifiedCompanion(){return otherTask>=0&&otherApp.equals("com.android.settings")
            &&otherWindowVisible&&!other.isEmpty()&&!Rect.intersects(app,other);}
        public String toString(){return "task="+task+" bounds="+app+" dp="+width+"x"+height
            +" multi="+multi+" focus="+focus+" other="+otherApp+"#"+otherTask+" bounds="+other
            +" WMvisible="+otherWindowVisible+" a11yRoot="+otherAccessibilityRoot+" focused="+focusedWindow+" leaves="+leaves;}
        String stableKey(){return task+":"+app+":"+width+":"+height+":"+multi+":"+focus+":"+otherTask+":"+other;}
    }
    private SplitSample splitSample() throws Exception {
        SplitSample s=new SplitSample();
        scenario.onActivity(a -> {
            s.task=a.getTaskId();s.multi=a.isInMultiWindowMode();s.focus=a.getWindow().getDecorView().hasWindowFocus();
            Configuration c=a.getResources().getConfiguration();s.width=c.screenWidthDp;s.height=c.screenHeightDp;
            s.app.set(a.getWindowManager().getCurrentWindowMetrics().getBounds());
        });
        android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity("android.permission.REAL_GET_TASKS");
        try {
            Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
            android.app.ActivityManager am=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            for(android.app.ActivityManager.RunningTaskInfo info:am.getRunningTasks(30)) {
                String pkg=info.topActivity==null?"":info.topActivity.getPackageName();
                if(pkg.equals(context.getPackageName())||pkg.equals("com.android.settings")) {
                    if(s.leaves.length()<220)s.leaves+=info.taskId+":"+info.topActivity.flattenToShortString()+";";
                    if(pkg.equals("com.android.settings")&&s.otherTask<0){s.otherTask=info.taskId;s.otherApp=pkg;}
                }
            }
        } finally {automation.dropShellPermissionIdentity();}
        android.accessibilityservice.AccessibilityServiceInfo flags=automation.getServiceInfo();
        flags.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(flags);
        for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()) {
            android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();
            if(root!=null&&"com.android.settings".contentEquals(root.getPackageName()))s.otherAccessibilityRoot=true;
        }
        String windows=device.executeShellCommand("dumpsys window -a");
        // WindowManager visibility/frame evidence is independent of accessibility roots.
        java.util.regex.Matcher blocks=java.util.regex.Pattern.compile(
            "(?ms)^\\s*Window #\\d+ Window\\{([^\\n]*)\\}:\\s*\\n(.*?)(?=^\\s*Window #|\\z)").matcher(windows);
        while(blocks.find()) {
            String title=blocks.group(1),body=blocks.group(2);
            if(!title.contains("com.android.settings/")||!body.contains("isVisible=true")||!body.contains("mHasSurface=true"))continue;
            java.util.regex.Matcher frame=java.util.regex.Pattern.compile(
                "\\bframe=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]").matcher(body);
            if(frame.find()) {
                s.other.set(Integer.parseInt(frame.group(1)),Integer.parseInt(frame.group(2)),Integer.parseInt(frame.group(3)),Integer.parseInt(frame.group(4)));
                s.otherWindowVisible=true;
            }
        }
        for(String line:windows.split("\n"))if(line.contains("mCurrentFocus="))s.focusedWindow+=line.trim()+";";
        return s;
    }
    private String safeSplitSample() {
        try {return splitSample().toString();}catch(Exception e){return "snapshot unavailable: "+e;}
    }
    private String shortMessage(Throwable e) {
        String message=String.valueOf(e.getMessage()).replace('\n',' ');return message.substring(0,Math.min(250,message.length()));
    }
    private SplitSample stableSample(boolean multi,Rect previous) throws Exception {
        long until=SystemClock.uptimeMillis()+15000,since=0;String last="";SplitSample s=null;
        do {
            s=splitSample();String key=s.stableKey();long now=SystemClock.uptimeMillis();
            boolean matches=s.multi==multi&&s.focus&&(previous==null||Math.abs(s.app.height()-previous.height())>40);
            if(matches&&key.equals(last)) {if(now-since>=700)return s;}
            else {last=key;since=now;}
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<until);
        throw new AssertionError("stable actual window required: "+s);
    }
    private Rect verifiedDivider() throws Exception {
        // Require a real system divider node/window. A gap alone is not a hit target.
        androidx.test.uiautomator.UiObject2 handle=device.findObject(androidx.test.uiautomator.By.res(
            java.util.regex.Pattern.compile(".*:id/docked_divider_handle")));
        if(handle!=null)return handle.getVisibleBounds();
        for(android.view.accessibility.AccessibilityWindowInfo w:InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows()) {
            if(w.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
                Rect r=new Rect();w.getBoundsInScreen(r);if(!r.isEmpty())return r;
            }
        }
        throw new AssertionError("no verified system divider hit target; not substituting geometric gap");
    }
    /** No focus setters or scrollTo: locate every control by real finger swipes in this window. */
    private void mortgageWholeFormSplit() throws Exception {
        onView(withContentDescription(R.string.tool_mortgage)).perform(click());
        for(int id:new int[]{R.id.tool_field_0,R.id.tool_field_1,R.id.tool_field_2})
            assertMortgageTextVisible(id);
        evidence("mortgage-full-core");
        scenario.onActivity(a -> splitTask=a.getTaskId());
        java.util.List<Throwable> issues=new java.util.ArrayList<>();
        try {
            enterRealSplit();SplitSample half=stableSample(true,null);
            assertTrue(half.toString(),half.verifiedCompanion());
            Log.i("MortgageForm","half "+half);
            mortgageTouchSequence("half");
            mortgageConditionalTypes("half");
            Map<String,String> before=modelSnapshot();Map<Integer,String> outputs=displaySnapshot();
            Rect divider=verifiedDivider();
            assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),device.getDisplayHeight()/3,60));
            SplitSample shortWindow=stableSample(true,half.app);
            assertTrue(shortWindow.toString(),shortWindow.verifiedCompanion());
            assertEquals(before,modelSnapshot());assertEquals(outputs,displaySnapshot());
            Log.i("MortgageForm","short "+shortWindow);
            mortgageTouchSequence("short");
            mortgageConditionalTypes("short");
        } catch(Throwable failure) {issues.add(failure);evidence("mortgage-failure");}
        finally {
            try {
                SplitSample current=splitSample();
                if(current.multi) {
                    Rect divider=verifiedDivider();
                    assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),device.getDisplayHeight()-1,80));
                }
                Log.i("MortgageForm","EXIT_PASS "+stableSample(false,null));
            } catch(Throwable failure) {Log.i("MortgageForm","EXIT_FAIL "+failure);issues.add(failure);}
        }
        if(!issues.isEmpty()) {
            Throwable first=issues.get(0);for(int i=1;i<issues.size();i++)first.addSuppressed(issues.get(i));
            if(first instanceof Exception)throw (Exception)first;
            if(first instanceof Error)throw (Error)first;
        }
    }
    private void mortgageConditionalTypes(String stage) {
        // Every applicable conditional field, not just one prepared active field.
        fingerTap(R.id.mortgage_type_combined);fingerTap(R.id.mortgage_basis_toggle);
        assertEquals("2",modelSnapshot().get("mLoanType"));
        assertEquals("true",modelSnapshot().get("mLprBasis"));
        mortgageEdit(3,"25");mortgageEdit(4,"300000");
        evidence("mortgage-"+stage+"-combined");mortgageEdit(1,"3.5");
        fingerTap(R.id.mortgage_type_fund);
        assertEquals("1",modelSnapshot().get("mLoanType"));
        mortgageEdit(1,"3.1");evidence("mortgage-"+stage+"-fund-rate");
        Log.i("MortgageForm",stage+" CONDITIONAL_TYPES_PASS commercial/combined/fund LPR/spread/fund-amount/fund-rate");
        fingerTap(R.id.mortgage_type_commercial);
        // Return to benchmark for a second whole sequence in the SAME resized task.
        if("true".equals(modelSnapshot().get("mLprBasis")))fingerTap(R.id.mortgage_basis_toggle);
    }
    private void mortgageTouchSequence(String stage) throws Exception {
        mortgageEdit(0,"1200000");evidence("mortgage-"+stage+"-core");
        mortgageEdit(1,"4.2");mortgageEdit(2,"25");evidence("mortgage-"+stage+"-years");
        String old=modelSnapshot().get("mEqualPayment");Map<Integer,String> previous=displaySnapshot();
        fingerTap(R.id.mortgage_method);
        assertNotEquals(old,modelSnapshot().get("mEqualPayment"));
        assertNotEquals(previous,displaySnapshot());assertMortgageComputation();
        fingerFind(R.id.formula);evidence("mortgage-"+stage+"-results");
        mortgageFingerCopy(R.id.formula);
        fingerFind(R.id.result);evidence("mortgage-"+stage+"-aux-result");
        mortgageFingerCopy(R.id.result);
        mortgageEdit(1,"4.1");
        assertSplitUsable();
        scenario.onActivity(a -> {
            float density=a.getResources().getDisplayMetrics().density;
            for(int id:new int[]{R.id.digit_7,R.id.digit_9,R.id.clr,R.id.eq}) {
                Rect r=new Rect();assertTrue(a.findViewById(id).getGlobalVisibleRect(r));
                assertTrue("readable full keypad target "+r,r.width()/density>=39&&r.height()/density>=32);
            }
        });
        Log.i("MortgageForm",stage+" ALL_CORE_FIELDS_TOUCH_EDIT_METHOD_COPY_RETURN_RATE_PASS active="+modelSnapshot().get("activeField")+" rate="+modelSnapshot().get("field1"));
    }
    private boolean visibleOnScreen(View view,Rect out) {
        if(!view.getLocalVisibleRect(out))return false;
        int[] origin=new int[2];view.getLocationOnScreen(origin);
        out.offset(origin[0]-view.getScrollX(),origin[1]-view.getScrollY());
        return !out.isEmpty();
    }
    private Rect mortgageViewport() {
        Rect rect=new Rect();scenario.onActivity(a -> assertTrue(visibleOnScreen(a.findViewById(R.id.main_calculator)
            .findViewWithTag("field_tool_viewport"),rect)));return rect;
    }
    /** Actual touch drag with a short stationary tail, so release does not fling past a
     * narrow fully-visible interval. No view focus, accessibility scroll action or scrollTo. */
    private void pointerDrag(int x,int from,int to) {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        long down=SystemClock.uptimeMillis();
        injectPointer(instrumentation,down,android.view.MotionEvent.ACTION_DOWN,x,from);
        for(int step=1;step<=20;step++) {
            SystemClock.sleep(8);
            injectPointer(instrumentation,down,android.view.MotionEvent.ACTION_MOVE,x,from+(to-from)*step/20);
        }
        for(int held=0;held<3;held++) {SystemClock.sleep(70);injectPointer(instrumentation,down,android.view.MotionEvent.ACTION_MOVE,x,to);}
        injectPointer(instrumentation,down,android.view.MotionEvent.ACTION_UP,x,to);
        instrumentation.waitForIdleSync();
    }
    private void injectPointer(android.app.Instrumentation instrumentation,long down,int action,int x,int y) {
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try {instrumentation.sendPointerSync(event);} finally {event.recycle();}
    }
    private void fingerFind(int id) {
        for(int attempt=0;attempt<24;attempt++) {
            boolean[] complete={false};int[] target={0};Rect viewport=mortgageViewport();
            scenario.onActivity(a -> {
                View view=a.findViewById(id);assertEquals("applicable control "+id,View.VISIBLE,view.getVisibility());
                Rect visible=new Rect();int[] at=new int[2];view.getLocationOnScreen(at);target[0]=at[1];
                complete[0]=visibleOnScreen(view,visible)&&visible.height()==view.getHeight()
                    &&visible.width()>=Math.min(view.getWidth(),viewport.width())
                    &&visible.top>=viewport.top&&visible.bottom<=viewport.bottom;
            });
            if(complete[0]) {
                device.waitForIdle(2000);
                boolean[] stable={false};
                scenario.onActivity(a -> {
                    View view=a.findViewById(id);Rect actual=new Rect();visibleOnScreen(view,actual);
                    stable[0]=actual.top>=viewport.top&&actual.bottom<=viewport.bottom&&actual.height()==view.getHeight();
                });
                if(stable[0]) {assertMortgageTextVisible(id);return;}
            }
            int[] geometry=new int[5];
            scenario.onActivity(a -> {
                View view=a.findViewById(id);int[] location=new int[2];view.getLocationOnScreen(location);
                geometry[0]=location[1];geometry[1]=location[1]+view.getHeight();
                geometry[2]=android.view.ViewConfiguration.get(a).getScaledTouchSlop();
                geometry[3]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").getScrollY();
                geometry[4]=view.getHeight();
            });
            int overflow=geometry[0]<viewport.top?geometry[0]-viewport.top:geometry[1]-viewport.bottom;
            if(attempt<4||attempt==23)Log.i("TaxPosition","CONTENT_TARGET id="+id+" attempt="+attempt+" top="+geometry[0]+" bottom="+geometry[1]+" height="+geometry[4]+" viewport="+viewport+" scrollY="+geometry[3]+" overflow="+overflow);
            assertTrue("natural target must fit this viewport for whole-row assertion: id="+id+" height="+geometry[4]+" viewport="+viewport.height(),geometry[4]<=viewport.height());
            // A fixed 2/3-viewport swipe can oscillate across a tall result's much smaller
            // fit interval. Converge using actual overflow plus native touch slop instead.
            int distance=Math.min(viewport.height()/3,Math.max(2*geometry[2]+1,Math.abs(overflow)+geometry[2]+1));
            boolean above=overflow<0;
            int from=above?viewport.top+viewport.height()/3:viewport.bottom-viewport.height()/3;
            pointerDrag(viewport.right-6,from,from+(above?distance:-distance));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();awaitMotionState(R.id.start_state);
        }
        fail("control cannot be reached with finger scroll: "+id);
    }
    private void assertMortgageTextVisible(int id) {
        scenario.onActivity(a -> {
            TextView view=a.findViewById(id);Rect visible=new Rect();assertTrue("visible "+id,view.getGlobalVisibleRect(visible));
            assertEquals("whole row vertically visible "+id,view.getHeight(),visible.height());
            Rect viewport=new Rect();assertTrue(a.findViewById(R.id.main_calculator)
                .findViewWithTag("field_tool_viewport").getGlobalVisibleRect(viewport));
            assertTrue("row/caret stays inside actual scroll viewport "+id+" "+visible+" vs "+viewport,
                visible.top>=viewport.top&&visible.bottom<=viewport.bottom);
            assertNotNull(view.getLayout());
            assertTrue("no vertical text truncation "+id,view.getHeight()>=view.getLayout().getHeight()+view.getCompoundPaddingTop()+view.getCompoundPaddingBottom());
            {
                assertEquals("entire form/output row width "+id,view.getWidth(),visible.width());
                // Layout.getLineWidth includes trailing break whitespace. For tax's pure
                // wrapped output check visible advances (getLineMax), not invisible spaces.
                // All characters still remain in the actual view and copy assertion.
                for(int line=0;line<view.getLayout().getLineCount();line++)
                    assertTrue("no hidden form text horizontally id="+id+" line="+line+" lineWidth="+view.getLayout().getLineWidth(line)+" available="+(view.getWidth()-view.getCompoundPaddingLeft()-view.getCompoundPaddingRight())+" layoutWidth="+view.getLayout().getWidth()+" textSize="+view.getTextSize()+" scrollX="+view.getScrollX()+" text=["+view.getText()+"]",(id==R.id.formula&&active(a) instanceof com.android.calculator2.tools.modes.TaxMode
                        ?view.getLayout().getLineMax(line):view.getLayout().getLineWidth(line))<=view.getWidth()-view.getCompoundPaddingLeft()-view.getCompoundPaddingRight()+1);
            }
        });
    }
    private void fingerTap(int id) {
        fingerFind(id);Rect rect=new Rect();scenario.onActivity(a -> visibleOnScreen(a.findViewById(id),rect));
        Log.i("MortgageForm","TAP id="+id+" bounds="+rect+" before="+modelSnapshot().get("mEqualPayment"));
        scenario.onActivity(a -> Log.i("TaxPosition","CONTENT_TAP id="+id+" screen="+rect+" focused="+a.findViewById(id).isFocused()+" touchFocusable="+a.findViewById(id).isFocusableInTouchMode()+" state="+modelSnapshotForLog(a)));
        assertTrue(device.click(rect.centerX(),rect.centerY()));
        device.waitForIdle(2000);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private String modelSnapshotForLog(Calculator activity) {
        Object fields=member(active(activity),"mFields");
        return "bonus="+member(active(activity),"mBonusMode")+" active="+(fields instanceof ToolInputFields?((ToolInputFields)fields).getActive():-1);
    }
    private void mortgageEdit(int index,String number) {
        int id=new int[]{R.id.tool_field_0,R.id.tool_field_1,R.id.tool_field_2,R.id.tool_field_3,R.id.tool_field_4}[index];
        fingerTap(id);assertEquals("actual touched field",String.valueOf(index),modelSnapshot().get("activeField"));
        key(R.id.clr);assertEquals("",modelSnapshot().get("field"+index));
        String typed="";
        for(char c:number.toCharArray()) {
            if(c=='.')key(R.id.dec_point);else digits(String.valueOf(c));typed+=c;
            assertEquals("key changes true field model",typed,modelSnapshot().get("field"+index));
            assertMortgageTextVisible(id);
            scenario.onActivity(a -> {
                TextView field=a.findViewById(id);int caret=field.getSelectionEnd();assertTrue(caret>=0);
                int line=field.getLayout().getLineForOffset(caret);
                assertTrue("caret stays in edited field",field.getLayout().getLineBottom(line)+field.getCompoundPaddingTop()<=field.getHeight()-field.getCompoundPaddingBottom());
            });
        }
        assertMortgageComputation();
        Log.i("MortgageForm","EDIT field="+index+" value="+number+" model="+modelSnapshot());
    }
    private void assertMortgageComputation() {
        Map<String,String> state=modelSnapshot();
        java.math.MathContext mc=new java.math.MathContext(20,java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal principal=new java.math.BigDecimal(state.get("field0"));
        java.math.BigDecimal rate=new java.math.BigDecimal(state.get("field1"));
        int years=new java.math.BigDecimal(state.get("field2")).intValue();
        boolean equal=Boolean.parseBoolean(state.get("mEqualPayment"));
        if("true".equals(state.get("mLprBasis"))&&!"1".equals(state.get("mLoanType")))
            rate=com.android.calculator2.tools.model.MortgageCalculator.lprRate(rate,new java.math.BigDecimal(state.get("field3")),mc);
        com.android.calculator2.tools.model.MortgageCalculator.Result expected;
        if("2".equals(state.get("mLoanType"))) {
            java.math.BigDecimal fund=new java.math.BigDecimal(state.get("field4"));
            expected=com.android.calculator2.tools.model.MortgageCalculator.combined(principal.subtract(fund),rate,fund,new java.math.BigDecimal("3.1"),years,equal,mc);
        } else expected=equal?com.android.calculator2.tools.model.MortgageCalculator.equalPayment(principal,rate,years,mc)
            :com.android.calculator2.tools.model.MortgageCalculator.equalPrincipal(principal,rate,years,mc);
        String monthly=new java.text.DecimalFormat("0.##").format(expected.monthlyPayment.setScale(2,java.math.RoundingMode.HALF_UP));
        String formula=displaySnapshot().get(R.id.formula).replace(",","");
        assertTrue("real monthly computation "+monthly+" vs "+formula,formula.contains(monthly));
        java.text.DecimalFormat money=new java.text.DecimalFormat("0.##");
        String auxiliary=displaySnapshot().get(R.id.result).replace(",","");
        assertTrue("real interest recomputed",auxiliary.contains(money.format(expected.totalInterest.setScale(2,java.math.RoundingMode.HALF_UP))));
        assertTrue("real auxiliary amount recomputed",auxiliary.contains(money.format((equal?expected.totalPayment:expected.monthlyDecrease).setScale(2,java.math.RoundingMode.HALF_UP))));
    }
    private void mortgageFingerCopy(int id) throws Exception {
        fingerFind(id);Rect rect=new Rect();scenario.onActivity(a -> visibleOnScreen(a.findViewById(id),rect));
        String expected=displaySnapshot().get(id);
        assertTrue(device.swipe(rect.centerX(),rect.centerY(),rect.centerX(),rect.centerY(),90));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        if(id==R.id.formula) {
            androidx.test.uiautomator.UiObject2 all=device.wait(androidx.test.uiautomator.Until.findObject(
                androidx.test.uiautomator.By.text(context.getString(android.R.string.selectAll))),2000);
            if(all!=null)all.click();
        }
        androidx.test.uiautomator.UiObject2 copy=device.wait(androidx.test.uiautomator.Until.findObject(
            androidx.test.uiautomator.By.text(context.getString(android.R.string.copy))),5000);
        assertNotNull("actual floating Copy menu",copy);copy.click();
        scenario.onActivity(a -> {
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals("entire result copied with user menu",expected,clipboard.getPrimaryClip().getItemAt(0).coerceToText(a).toString());
        });
        awaitMotionState(R.id.start_state);
        assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.res("com.android.systemui","clipboard_ui")),20000));
    }
    @Test public void dateSurvivesRealSplitAndResize() throws Exception {splitTool(R.string.tool_date,false,false);}
    @Test public void relationshipSurvivesRealSplitAndResize() throws Exception {splitTool(R.string.tool_relationship,false,false);}
    @Test public void programmerSurvivesRealSplitAndResize() throws Exception {splitTool(R.string.tool_programmer,false,false);}
    @Test public void taxSurvivesRealSplitAndResize() throws Exception {splitTool(R.string.tool_tax,false,false);}
    @Test public void taxUpperQuarterSplitTouch() throws Exception {taxQuarter(false);}
    @Test public void taxLowerQuarterSplitTouch() throws Exception {taxQuarter(true);}
    private void establishQuarterWindow(int mode,boolean bottom,String stage) throws Exception {
        if(mode==R.string.tool_unit||mode==R.string.tool_currency) {
            key(R.id.mode_segment_convert);if(mode==R.string.tool_currency)key(R.id.mode_segment_convert);
        } else onView(withContentDescription(mode)).perform(click());
        enterRealSplit();
        SplitSample entry=splitSample();
        if((entry.app.top>entry.other.top)!=bottom) {
            Rect divider=verifiedDivider();
            assertTrue(device.click(divider.centerX(),divider.centerY()));SystemClock.sleep(90);
            assertTrue(device.click(divider.centerX(),divider.centerY()));
            long until=SystemClock.uptimeMillis()+10000;
            while(SystemClock.uptimeMillis()<until) {
                SplitSample now=splitSample();
                if(now.multi&&now.verifiedCompanion()&&(now.app.top>now.other.top)==bottom)break;
                SystemClock.sleep(100);
            }
        }
        device.executeShellCommand("am task focus "+splitTask);
        SplitSample before=stableSample(true,null);
        assertTrue("real target pair "+before,before.multi&&before.verifiedCompanion());
        assertEquals("target position",bottom,before.app.top>before.other.top);
        SplitSample target=before;
        for(int attempt=0;attempt<2;attempt++) {
            Rect divider=verifiedDivider();
            Log.i("TaxPosition",stage+" RESIZE attempt="+attempt+" divider="+divider+" before="+target.app);
            assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),
                Math.round(device.getDisplayHeight()*(bottom?0.75f:0.25f)),65));
            // Don't steal system focus halfway through the divider's animation.
            device.waitForIdle(2000);assertTrue("verified pair settles after real resize",awaitVerifiedPair());
            device.executeShellCommand("am task focus "+splitTask);
            target=stableSample(true,null);
            float actual=target.app.height()/(float)(target.app.height()+target.other.height());
            if(Math.abs(actual-0.25f)<0.04)break;
        }
        logPosition(stage,target);
        float share=target.app.height()/(float)(target.app.height()+target.other.height());
        assertTrue("actual quarter "+target,Math.abs(share-0.25f)<0.04);
        assertEquals(bottom,target.app.top>target.other.top);
    }
    @Test public void mortgageUpperQuarterSplitLoanTypes() throws Exception {
        establishQuarterWindow(R.string.tool_mortgage,false,"mortgage-upper-types-25");
        positionMortgageScroll("upper-target-25",false);
        resizePosition(false,0.5f);assertLoanTypeGroup("upper-50");
        resizePosition(false,0.25f);assertLoanTypeGroup("upper-return-25");
    }
    @Test public void taxModeSwitchIsAnActionNotSelectableText() throws Exception {
        onView(withContentDescription(R.string.tool_tax)).perform(click());
        assertTaxSwitchAction(false);
    }
    private void assertTaxSwitchAction(boolean scroll) throws Exception {
        if(scroll)fingerFind(R.id.tax_mode_toggle);
        Rect bounds=new Rect();Map<String,String> before=modelSnapshot();
        scenario.onActivity(a -> {
            TextView toggle=a.findViewById(R.id.tax_mode_toggle);
            assertFalse("switch text cannot be selected",toggle.isTextSelectable());
            assertFalse("switch has no caret",toggle.isCursorVisible());
            assertNull("switch has no editor key listener",toggle.getKeyListener());
            assertFalse("switch has no text long-click handler",toggle.isLongClickable());
            assertTrue(toggle.isClickable());assertTrue(toggle.isFocusable());
            assertFalse("a click action must not consume the first tap just taking touch focus",toggle.isFocusableInTouchMode());
            android.view.accessibility.AccessibilityNodeInfo node=toggle.createAccessibilityNodeInfo();
            assertEquals(android.widget.Button.class.getName(),node.getClassName().toString());
            assertTrue("named action",node.getText()!=null&&!node.getText().toString().isEmpty());
            assertTrue("accessible click action",(node.getActions()&android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)!=0);
            assertTrue(visibleOnScreen(toggle,bounds));
        });
        assertTrue(device.swipe(bounds.centerX(),bounds.centerY(),bounds.centerX(),bounds.centerY(),90));
        // A held button may dispatch its normal click on release, but never text selection.
        if(Boolean.parseBoolean(modelSnapshot().get("mBonusMode"))) {
            assertTrue(device.click(bounds.centerX(),bounds.centerY()));device.waitForIdle(2000);
        }
        for(int n=0;n<3;n++)assertEquals("hold cannot alter salary input",before.get("field"+n),modelSnapshot().get("field"+n));
        scenario.onActivity(a -> {
            TextView toggle=a.findViewById(R.id.tax_mode_toggle);
            assertFalse("long press cannot create a selection",toggle.getSelectionStart()>=0&&toggle.getSelectionStart()!=toggle.getSelectionEnd());
        });
        String copy=InstrumentationRegistry.getInstrumentation().getTargetContext().getString(android.R.string.copy);
        assertNull("no selection menu for the switch",device.findObject(androidx.test.uiautomator.By.text(copy)));
        assertTrue(device.click(bounds.centerX(),bounds.centerY()));device.waitForIdle(2000);
        assertEquals("single click activates bonus","true",modelSnapshot().get("mBonusMode"));
        if(scroll)fingerFind(R.id.tax_mode_toggle);
        Rect back=new Rect();scenario.onActivity(a -> assertTrue(visibleOnScreen(a.findViewById(R.id.tax_mode_toggle),back)));
        assertTrue(device.click(back.centerX(),back.centerY()));device.waitForIdle(2000);
        Map<String,String> expected=new TreeMap<>(before);expected.put("activeField","0");
        assertEquals("salary model restored; switch activates field zero by existing rule",expected,modelSnapshot());
    }
    private void assertLoanTypeGroup(String stage) {
        fingerFind(R.id.mortgage_type_combined);
        // Coarse field-finding swipes can overshoot the first row of a vertical group.
        // Align the whole group by its actual screen bounds, still using real slow swipes.
        for(int attempt=0;attempt<16;attempt++) {
            Rect viewport=mortgageViewport();int[] edge=new int[3];
            scenario.onActivity(a -> {
                View group=a.findViewById(R.id.mortgage_type_group);int[] origin=new int[2];group.getLocationOnScreen(origin);
                edge[0]=origin[1];edge[1]=origin[1]+group.getHeight();
                edge[2]=android.view.ViewConfiguration.get(a).getScaledTouchSlop();
            });
            if(edge[0]>=viewport.top&&edge[1]<=viewport.bottom)break;
            int delta=edge[0]<viewport.top?viewport.top-edge[0]:viewport.bottom-edge[1];
            int distance=Math.min(viewport.height()/3,Math.abs(delta)+edge[2]+1);
            int from=delta>0?viewport.top+viewport.height()/3:viewport.bottom-viewport.height()/3;
            assertTrue(device.swipe(viewport.right-6,from,viewport.right-6,from+(delta>0?distance:-distance),45));
            device.waitForIdle(1000);awaitMotionState(R.id.start_state);
        }
        scenario.onActivity(a -> {
            View group=a.findViewById(R.id.mortgage_type_group);Rect viewport=new Rect();
            assertTrue(visibleOnScreen(a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport"),viewport));
            for(int id:new int[]{R.id.mortgage_type_commercial,R.id.mortgage_type_fund,R.id.mortgage_type_combined}) {
                TextView button=a.findViewById(id);Rect r=new Rect();assertTrue(visibleOnScreen(button,r));
                assertEquals("whole loan option width",button.getWidth(),r.width());
                assertEquals("whole loan option height",button.getHeight(),r.height());
                assertTrue("all loan types simultaneously inside viewport "+r+" "+viewport,r.left>=viewport.left&&r.right<=viewport.right&&r.top>=viewport.top&&r.bottom<=viewport.bottom);
                assertTrue("loan type touch height",button.getHeight()/a.getResources().getDisplayMetrics().density>=44);
                for(int line=0;line<button.getLayout().getLineCount();line++) {
                    assertEquals("no shortened loan type label",0,button.getLayout().getEllipsisCount(line));
                    assertTrue("complete loan type text",button.getLayout().getLineWidth(line)<=button.getWidth()-button.getCompoundPaddingLeft()-button.getCompoundPaddingRight()+1);
                }
                Log.i("MortgagePosition",stage+" LOAN_TYPE id="+id+" text="+button.getText()+" bounds="+r+" groupWidth="+group.getWidth());
            }
        });
    }
    private void taxQuarter(boolean bottom) throws Exception {
        String stage=bottom?"tax-lower-25":"tax-upper-25";
        try {
            establishQuarterWindow(R.string.tool_tax,bottom,stage);
            evidence(stage+"-initial");
            scenario.onActivity(a -> {
                for(int id:new int[]{R.id.tool_field_0,R.id.tool_field_1,R.id.tool_field_2,R.id.formula,R.id.result}) {
                    View v=a.findViewById(id);Rect r=new Rect();v.getGlobalVisibleRect(r);
                    Log.i("TaxPosition",stage+" GEOMETRY id="+id+" measured="+v.getWidth()+"x"+v.getHeight()+" visible="+r);
                }
                assertNotNull("tax needs a bounded vertical viewport when content exceeds its small display",
                    a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport"));
            });
            int[] size=new int[3];scenario.onActivity(a -> {
                android.widget.ScrollView scroll=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport");
                size[0]=scroll.getChildAt(0).getHeight();size[1]=scroll.getHeight();size[2]=scroll.getScrollY();
            });
            Log.i("TaxPosition",stage+" CONTENT child="+size[0]+" viewport="+size[1]+" range="+(size[0]-size[1])+" y="+size[2]);
            assertTrue("fixture must have a scroll range",size[0]>size[1]);
            for(int n=0;n<24;n++) {
                int[] y={0};scenario.onActivity(a -> y[0]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").getScrollY());
                if(y[0]==0)break;swipeMortgageViewport(false);
            }
            int[] positions=new int[2];
            scenario.onActivity(a -> positions[0]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").getScrollY());
            swipeMortgageViewport(true);
            scenario.onActivity(a -> positions[1]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").getScrollY());
            Log.i("TaxPosition",stage+" REAL_SWIPE before="+positions[0]+" after="+positions[1]);
            assertTrue("real tax content moves",positions[1]>positions[0]);awaitMotionState(R.id.start_state);
            // Find all salary fields by actual finger gestures, with complete text/caret checks.
            taxEdit(0,"30000");taxEdit(1,"2500");taxEdit(2,"1500");
            assertTaxSwitchAction(true);
            Map<String,String> salary=modelSnapshot();
            for(int id:new int[]{R.id.formula,R.id.result}) {fingerFind(id);evidence(stage+"-salary-"+id);mortgageFingerCopy(id);}
            fingerTap(R.id.tax_mode_toggle);assertEquals("true",modelSnapshot().get("mBonusMode"));
            taxEdit(0,"45000");
            for(int id:new int[]{R.id.formula,R.id.result}) {fingerFind(id);evidence(stage+"-bonus-"+id);mortgageFingerCopy(id);}
            fingerTap(R.id.tax_mode_toggle);assertEquals("false",modelSnapshot().get("mBonusMode"));
            for(int n=0;n<3;n++)assertEquals("salary fields restored",salary.get("field"+n),modelSnapshot().get("field"+n));
            fingerFind(R.id.tool_field_1);fingerFind(R.id.tool_field_2);fingerFind(R.id.tool_field_0);
            taxEdit(0,"31000");assertTaxComputation();
            evidence(stage+"-returned-input");
            Log.i("TaxPosition",stage+" CORE_SALARY_BONUS_SWITCH_COPY_RETURN_EDIT_PASS");
            resizePosition(bottom,0.5f);resizePosition(bottom,0.25f);
            Log.i("TaxPosition",stage+" RESIZE_STATE_PASS");
            // Modal automation is independent of already-completed core operations.
            // Both dialogs are confirmed scrollable on the user's physical device.
            taxDetailDialog(stage,true);assertEquals("600.00",modelSnapshot().get("field1"));
            taxDetailDialog(stage,false);assertTaxComputation();
            Log.i("TaxPosition",stage+" SALARY_BONUS_FIELDS_DIALOGS_COPY_RETURN_PASS");
        } catch(Throwable failure) {
            Log.e("TaxPosition",stage+" FAIL first="+String.valueOf(failure).split("\n")[0]+" window="+safeSplitSample());
            evidence(stage+"-failure");
            if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;
        }
    }
    private void taxEdit(int field,String value) {
        int id=new int[]{R.id.tool_field_0,R.id.tool_field_1,R.id.tool_field_2}[field];
        fingerTap(id);assertEquals(String.valueOf(field),modelSnapshot().get("activeField"));
        key(R.id.clr);String typed="";
        for(char c:value.toCharArray()) {
            digits(String.valueOf(c));typed+=c;
            assertEquals("real tax field model",typed,modelSnapshot().get("field"+field));
            assertMortgageTextVisible(id);
        }
        assertTaxComputation();
    }
    private void taxDetailDialog(String stage,boolean insurance) throws Exception {
        fingerTap(insurance?R.id.tax_insurance_detail:R.id.tax_special_detail);
        int[] editors=insurance?new int[]{R.id.si_base_input,R.id.si_rate_0,R.id.si_rate_1,R.id.si_rate_2,R.id.si_rate_3,R.id.si_rate_4,R.id.si_rate_5}
            :new int[]{R.id.ded_edu_children,R.id.ded_medical,R.id.ded_elderly_shared,R.id.ded_infant_children};
        for(int id:editors) {
            dialogReach(id).click();device.waitForIdle(1000);
            assertTrue(device.pressKeyCode(android.view.KeyEvent.KEYCODE_A,android.view.KeyEvent.META_CTRL_ON));
            assertTrue(device.pressKeyCode(android.view.KeyEvent.KEYCODE_DEL));
            device.executeShellCommand("input text "+(id==R.id.si_base_input?"10000":"1"));
            device.waitForIdle(1000);
            // Back can first dismiss the editor's selection mode instead of the IME.
            // Close only a verified input-method window; never blindly dismiss the dialog.
            for(int attempt=0;attempt<3;attempt++) {
                boolean ime=false;
                for(android.view.accessibility.AccessibilityWindowInfo window:InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows())
                    if(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD)ime=true;
                Log.i("TaxPosition",stage+" DIALOG_INPUT id="+id+" ime="+ime+" closeAttempt="+attempt);
                if(!ime)break;
                assertTrue(device.pressBack());device.waitForIdle(1000);
            }
            onView(withId(android.R.id.button1)).check(matches(isDisplayed()));
        }
        if(!insurance) {
            for(int id:new int[]{R.id.ded_edu_share,R.id.ded_continuing,R.id.ded_mortgage_share,R.id.ded_rent,R.id.ded_infant_share}) {
                dialogReach(id).click();device.waitForIdle(1000);
                device.pressDPadDown();device.pressEnter();device.waitForIdle(1000);
            }
            for(int id:new int[]{R.id.ded_mortgage,R.id.ded_elderly_only})dialogReach(id).click();
        }
        evidence(stage+(insurance?"-insurance-detail":"-special-detail"));
        onView(withId(android.R.id.button1)).perform(click());awaitMotionState(R.id.start_state);
        Log.i("TaxPosition",stage+" DIALOG_ALL_ENTRIES_APPLY "+(insurance?"insurance":"special"));
    }
    private androidx.test.uiautomator.UiObject dialogReach(int id) throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String resource=context.getPackageName()+":id/"+context.getResources().getResourceEntryName(id);
        androidx.test.uiautomator.UiSelector selector=new androidx.test.uiautomator.UiSelector().resourceId(resource);
        androidx.test.uiautomator.UiScrollable scroll=new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector()
            .packageName(context.getPackageName()).className(android.widget.ScrollView.class.getName()).scrollable(true));
        // A center swipe can begin on a Spinner and open its forwarding popup instead of
        // moving the dialog. Use the empty inner gutter of this app's modal ScrollView.
        // This is a real pointer gesture, not an accessibility scroll action or scrollTo.
        boolean reached=false,forward=true;int still=0;String previous="";
        for(int attempt=0;attempt<90;attempt++) {
            Rect viewport=scroll.getBounds();
            androidx.test.uiautomator.UiObject target=device.findObject(selector);
            if(target.exists()) {
                Rect bounds=target.getBounds();
                if(viewport.contains(bounds)&&!bounds.isEmpty()) {reached=true;break;}
                forward=bounds.bottom>viewport.bottom;
            }
            StringBuilder signature=new StringBuilder();
            for(androidx.test.uiautomator.UiObject2 item:device.findObjects(androidx.test.uiautomator.By.pkg(context.getPackageName())
                    .clazz(java.util.regex.Pattern.compile("android.widget.(EditText|Spinner|CheckBox)"))))
                signature.append(item.getResourceName()).append(item.getVisibleBounds());
            String now=signature.toString();
            if(now.equals(previous))still++;else still=0;
            if(still>=2) {forward=!forward;still=0;}
            previous=now;
            int x=viewport.right-8,top=viewport.top+viewport.height()/5,bottom=viewport.bottom-viewport.height()/5;
            assertTrue("real pointer within current dialog gutter",device.swipe(x,forward?bottom:top,x,forward?top:bottom,24));
            device.waitForIdle(1000);
        }
        if(!reached) {
            for(androidx.test.uiautomator.UiObject2 node:device.findObjects(androidx.test.uiautomator.By.scrollable(true)))
                Log.e("TaxPosition","DIALOG_SCROLL_FAIL target="+resource+" scrollable="+node.getResourceName()+" bounds="+node.getVisibleBounds());
            device.dumpWindowHierarchy("/sdcard/window-tax-dialog-failure.xml");
        }
        assertTrue("dialog entry reachable by actual scrolling "+resource,reached);
        return device.findObject(selector);
    }
    private void assertTaxComputation() {
        Map<String,String> model=modelSnapshot();Map<Integer,String> outputs=displaySnapshot();
        java.math.BigDecimal amount=new java.math.BigDecimal(model.get("field0"));
        java.math.MathContext mc=new java.math.MathContext(20,java.math.RoundingMode.HALF_UP);
        com.android.calculator2.tools.data.TaxTable table=com.android.calculator2.tools.data.TaxTable.load(InstrumentationRegistry.getInstrumentation().getTargetContext());
        java.math.BigDecimal tax,after,last=null;
        if(Boolean.parseBoolean(model.get("mBonusMode"))) {
            com.android.calculator2.tools.model.IncomeTaxCalculator.BonusResult r=com.android.calculator2.tools.model.IncomeTaxCalculator.computeYearEndBonus(amount,table,mc);
            tax=r.tax;after=r.afterTax;
        } else {
            com.android.calculator2.tools.model.IncomeTaxCalculator.Result r=com.android.calculator2.tools.model.IncomeTaxCalculator.computeAnnual(amount,
                new java.math.BigDecimal(model.get("field1")),new java.math.BigDecimal(model.get("field2")),table,mc);
            tax=r.annualTax;after=r.annualAfterTax;last=r.lastMonthWithholding;
        }
        java.text.DecimalFormat money=new java.text.DecimalFormat("0.##");
        assertTrue("primary tax recomputed",outputs.get(R.id.formula).replace(",","").contains(money.format(tax.setScale(2,java.math.RoundingMode.HALF_UP))));
        assertTrue("after-tax result recomputed",outputs.get(R.id.result).replace(",","").contains(money.format(after.setScale(2,java.math.RoundingMode.HALF_UP))));
        if(last!=null)assertTrue("last-month auxiliary recomputed",outputs.get(R.id.result).replace(",","").contains(money.format(last.setScale(2,java.math.RoundingMode.HALF_UP))));
    }
    /** Targeted check: no upper-window editing, state checkpoints or exit gate this test. */
    @Test public void mortgageLowerQuarterRealScroll() throws Exception {
        String phase="ENTER_TARGET";
        try {
            onView(withContentDescription(R.string.tool_mortgage)).perform(click());
            enterRealSplit();
            // Only the system positioning required to establish the target, not the full chain.
            Rect divider=verifiedDivider();
            assertTrue(device.click(divider.centerX(),divider.centerY()));SystemClock.sleep(90);
            assertTrue(device.click(divider.centerX(),divider.centerY()));
            long until=SystemClock.uptimeMillis()+10000;
            while(SystemClock.uptimeMillis()<until) {
                SplitSample sample=splitSample();
                if(sample.multi&&sample.verifiedCompanion()&&sample.app.top>sample.other.top)break;
                SystemClock.sleep(100);
            }
            SplitSample lower=splitSample();
            assertTrue("verified calculator in lower system split "+lower,
                lower.multi&&lower.verifiedCompanion()&&lower.app.top>lower.other.top);
            divider=verifiedDivider();
            assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),
                Math.round(device.getDisplayHeight()*0.75f),65));
            // Shell focus is window activation, not direct field focus or scrolling.
            device.executeShellCommand("am task focus "+splitTask);
            SplitSample target=stableSample(true,null);logPosition("lower-target-25",target);
            float share=target.app.height()/(float)(target.app.height()+target.other.height());
            assertTrue("lower quarter actual share="+share+" "+target,
                target.app.top>target.other.top&&target.verifiedCompanion()&&Math.abs(share-0.25f)<0.04);
            phase="CONTENT_SCROLL";
            positionMortgageScroll("lower-target-25",false);
            Log.i("MortgageTarget","PASS content scroll/edit/copy/return; handle/state tested separately");
        } catch(Throwable failure) {
            String first=String.valueOf(failure).split("\n")[0];
            Log.e("MortgageTarget","FAIL phase="+phase+" first="+first+" window="+safeSplitSample());
            evidence("mortgage-lower-target-failure");
            if(failure instanceof Exception)throw (Exception)failure;
            throw (Error)failure;
        }
    }
    @Test public void mortgageScrollAtBothWindowPositions() throws Exception {
        onView(withContentDescription(R.string.tool_mortgage)).perform(click());
        evidence("mortgage-position-full");
        java.util.List<Throwable> issues=new java.util.ArrayList<>();
        try {
            enterRealSplit();
            SplitSample topHalf=stableSample(true,null);logPosition("upper-50",topHalf);
            assertTrue(topHalf.app.top<topHalf.other.top);
            recordMortgagePosition("upper-50",issues);
            resizePosition(false,0.25f);SplitSample topQuarter=stableSample(true,null);
            logPosition("upper-calculator-25-settings-75",topQuarter);
            recordMortgagePosition("upper-25",issues);
            swapSplitPosition(true);
            resizePosition(true,0.25f);SplitSample bottomQuarter=stableSample(true,null);
            logPosition("lower-calculator-25-settings-75",bottomQuarter);
            assertTrue("same-sized upper/lower comparison "+topQuarter+" vs "+bottomQuarter,
                Math.abs(topQuarter.app.height()-bottomQuarter.app.height())<=4);
            recordMortgagePosition("lower-25",issues);
            resizePosition(true,0.5f);SplitSample bottomHalf=stableSample(true,null);
            logPosition("lower-50",bottomHalf);
            assertTrue("same-sized 50:50 windows",Math.abs(topHalf.app.height()-bottomHalf.app.height())<=4);
            recordMortgagePosition("lower-50",issues);
            swapSplitPosition(false);logPosition("upper-return-50",stableSample(true,null));
            recordMortgagePosition("upper-return-50",issues);
        } catch(Throwable failure) {issues.add(failure);evidence("mortgage-position-failure");}
        finally {
            try {
                MortgageWindowCheckpoint checkpoint=new MortgageWindowCheckpoint();
                if(splitSample().multi) {
                    Rect divider=verifiedDivider();boolean bottom=splitSample().app.top>splitSample().other.top;
                    assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),bottom?1:device.getDisplayHeight()-1,90));
                }
                SplitSample full=stableSample(false,null);checkpoint.verify();
                logPosition("EXIT_PASS",full);
            } catch(Throwable failure) {issues.add(failure);Log.i("MortgagePosition","EXIT_FAIL "+failure);}
        }
        if(!issues.isEmpty()) {
            Throwable first=issues.get(0);for(int i=1;i<issues.size();i++)first.addSuppressed(issues.get(i));
            if(first instanceof Exception)throw (Exception)first;
            if(first instanceof Error)throw (Error)first;
        }
    }
    private void recordMortgagePosition(String stage,java.util.List<Throwable> issues) {
        try {positionMortgageScroll(stage);}
        catch(Throwable failure) {
            issues.add(failure);Log.i("MortgagePosition",stage+" FAIL "+failure);
            evidence("mortgage-"+stage+"-failure");
            int[] state={0};scenario.onActivity(a -> state[0]=((DisplayMotionLayout)a.findViewById(R.id.main_calculator)).getCurrentState());
            if(state[0]==R.id.end_state) {
                // Recorded failure remains FAIL. Real Back only cleans history for the next
                // independent position; it is never reported as a successful content swipe.
                assertTrue(device.pressBack());awaitMotionState(R.id.start_state);
                Log.i("MortgagePosition",stage+" DIAGNOSTIC_CLEANUP history Back (not a scroll pass)");
            }
        }
    }
    private void logPosition(String stage,SplitSample sample) {
        float ratio=sample.app.height()/(float)(sample.app.height()+sample.other.height());
        Log.i("MortgagePosition",stage+" calcPairFraction="+ratio+" calcDisplayFraction="
            +sample.app.height()/(float)device.getDisplayHeight()+" displayPx="+device.getDisplayWidth()+"x"+device.getDisplayHeight()+" "+sample);
    }
    private class MortgageWindowCheckpoint {
        final Map<String,String> model=modelSnapshot();
        final Map<Integer,String> outputs=displaySnapshot();
        final int input=new int[]{R.id.tool_field_0,R.id.tool_field_1,R.id.tool_field_2,R.id.tool_field_3,R.id.tool_field_4}
            [Integer.parseInt(model.get("activeField"))];
        final int[] selected=selection(input);
        void verify() {
            assertEquals("model/options survive real window transition",model,modelSnapshot());
            assertEquals("both results survive real window transition",outputs,displaySnapshot());
            assertArrayEquals("active field selection survives real window transition",selected,selection(input));
        }
    }
    private void resizePosition(boolean bottom,float fraction) throws Exception {
        MortgageWindowCheckpoint checkpoint=new MortgageWindowCheckpoint();
        SplitSample before=splitSample();
        float current=before.app.height()/(float)(before.app.height()+before.other.height());
        if(Math.abs(current-fraction)<0.015&&(before.app.top>before.other.top)==bottom)return;
        Rect divider=verifiedDivider();
        int y=Math.round(device.getDisplayHeight()*(bottom?1-fraction:fraction));
        assertTrue(device.swipe(divider.centerX(),divider.centerY(),divider.centerX(),y,65));
        SplitSample after=stableSample(true,before.app);
        float actual=after.app.height()/(float)(after.app.height()+after.other.height());
        assertTrue("requested calculator share="+fraction+" actual="+actual+" "+after,Math.abs(actual-fraction)<0.04);
        assertEquals("requested calculator screen position "+after,bottom,after.app.top>after.other.top);
        checkpoint.verify();
    }
    private void swapSplitPosition(boolean bottom) throws Exception {
        MortgageWindowCheckpoint checkpoint=new MortgageWindowCheckpoint();
        Rect divider=verifiedDivider();
        assertTrue(device.click(divider.centerX(),divider.centerY()));SystemClock.sleep(90);
        assertTrue(device.click(divider.centerX(),divider.centerY()));
        long until=SystemClock.uptimeMillis()+10000;boolean swapped=false;
        do {
            SplitSample now=splitSample();
            if(now.multi&&now.verifiedCompanion()&&(now.app.top>now.other.top)==bottom) {swapped=true;break;}
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<until);
        assertTrue("real divider double-tap swaps verified task positions: "+safeSplitSample(),swapped);
        // Re-focus an inert toolbar area only if the system moved focus to the companion.
        if(!splitSample().focus) {
            Rect toolbar=new Rect();scenario.onActivity(a -> assertTrue(visibleOnScreen(a.findViewById(R.id.toolbar),toolbar)));
            assertTrue(device.click(toolbar.centerX(),toolbar.centerY()));
        }
        stableSample(true,null);
        checkpoint.verify();
    }
    private void positionMortgageScroll(String stage) throws Exception {positionMortgageScroll(stage,true);}
    private void positionMortgageScroll(String stage,boolean testHistory) throws Exception {
        assertLoanTypeGroup(stage);
        for(int id:new int[]{R.id.mortgage_type_fund,R.id.mortgage_type_combined,R.id.mortgage_type_commercial}) {
            fingerTap(id);
            assertEquals("clicked loan type is the model",id==R.id.mortgage_type_fund?"1":id==R.id.mortgage_type_combined?"2":"0",modelSnapshot().get("mLoanType"));
            scenario.onActivity(a -> assertEquals("visible checked state",id,
                ((com.google.android.material.button.MaterialButtonToggleGroup)a.findViewById(R.id.mortgage_type_group)).getCheckedButtonId()));
            assertLoanTypeGroup(stage);
        }
        evidence("mortgage-"+stage+"-types");
        Rect viewport=mortgageViewport();int[] sizes=new int[4];
        scenario.onActivity(a -> {
            android.widget.ScrollView scroll=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport");
            sizes[0]=scroll.getChildAt(0).getHeight();sizes[1]=scroll.getHeight()-scroll.getPaddingTop()-scroll.getPaddingBottom();
            sizes[2]=scroll.getScrollY();sizes[3]=((DisplayMotionLayout)a.findViewById(R.id.main_calculator)).getCurrentState();
        });
        int range=sizes[0]-sizes[1];
        Log.i("MortgagePosition",stage+" CONTENT childPx="+sizes[0]+" viewportPx="+sizes[1]+" rangePx="+range+" scrollY="+sizes[2]+" fingerViewportScreen="+viewport);
        assertTrue("fixture actually needs vertical scrolling",range>0);
        // Reach the top through real downward finger drags, not scrollTo or focus setters.
        for(int n=0;n<20;n++) {
            boolean[] up={false};scenario.onActivity(a -> up[0]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").canScrollVertically(-1));
            if(!up[0])break;
            swipeMortgageViewport(false);awaitMotionState(R.id.start_state);
        }
        int[] y=new int[2];scenario.onActivity(a -> y[0]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").getScrollY());
        assertEquals("finger reaches content top",0,y[0]);
        swipeMortgageViewport(true);
        scenario.onActivity(a -> y[1]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").getScrollY());
        Log.i("MortgagePosition",stage+" REAL_SWIPE before="+y[0]+" after="+y[1]+" "+safeMotionState());
        assertTrue("content must scroll when it has range; "+stage+" before="+y[0]+" after="+y[1],y[1]>y[0]);
        awaitMotionState(R.id.start_state);
        fingerFind(R.id.result);evidence("mortgage-"+stage+"-scroll-result");
        fingerFind(R.id.tool_field_0);evidence("mortgage-"+stage+"-scroll-input");
        if(!"0".equals(modelSnapshot().get("mLoanType")))fingerTap(R.id.mortgage_type_commercial);
        if("true".equals(modelSnapshot().get("mLprBasis")))fingerTap(R.id.mortgage_basis_toggle);
        mortgageTouchSequence(stage);
        if(stage.endsWith("25"))mortgageConditionalTypes(stage);
        fingerFind(R.id.tool_field_1);fingerFind(R.id.tool_field_2);
        evidence("mortgage-"+stage+"-returned-rate-years");
        Log.i("MortgagePosition",stage+" CONTENT_SCROLL_EDIT_COPY_RETURN_PASS");
        if(!testHistory)return;
        // Leave the toolbar reachable for a possible system focus change during a swap.
        for(int n=0;n<20;n++) {
            boolean[] up={false};scenario.onActivity(a -> up[0]=a.findViewById(R.id.main_calculator).findViewWithTag("field_tool_viewport").canScrollVertically(-1));
            if(!up[0])break;swipeMortgageViewport(false);awaitMotionState(R.id.start_state);
        }
        Map<String,String> saved=modelSnapshot();Map<Integer,String> results=displaySnapshot();
        int field=Integer.parseInt(saved.get("activeField"));
        int fieldId=new int[]{R.id.tool_field_0,R.id.tool_field_1,R.id.tool_field_2,R.id.tool_field_3,R.id.tool_field_4}[field];
        int[] selected=selection(fieldId);int[] drag=new int[3];
        scenario.onActivity(a -> {
            Rect handle=new Rect();assertTrue(visibleOnScreen(a.findViewById(R.id.drag_handle_view),handle));
            View main=a.findViewById(R.id.main_calculator);int[] origin=new int[2];main.getLocationOnScreen(origin);
            drag[0]=handle.centerX();drag[1]=handle.centerY();drag[2]=origin[1]+main.getHeight()-main.getPaddingBottom()-1;
        });
        assertTrue("actual history handle remains draggable",device.swipe(drag[0],drag[1],drag[0],drag[2],35));
        awaitMotionState(R.id.end_state);
        onView(withId(R.id.history_toolbar)).check(matches(isDisplayed()));
        assertTrue(device.pressBack());awaitMotionState(R.id.start_state);
        assertEquals("model after history",saved,modelSnapshot());assertEquals("results after history",results,displaySnapshot());
        assertArrayEquals("field selection after history",selected,selection(fieldId));
        Log.i("MortgagePosition",stage+" SCROLL_EDIT_COPY_RETURN_HISTORY_PASS");
    }
    private String safeMotionState() {
        int[] state={0};scenario.onActivity(a -> state[0]=((DisplayMotionLayout)a.findViewById(R.id.main_calculator)).getCurrentState());
        return "motion="+state[0];
    }
    private void swipeMortgageViewport(boolean up) {
        Rect viewport=mortgageViewport();
        int top=viewport.top+viewport.height()/4,bottom=viewport.bottom-viewport.height()/4;
        assertTrue(device.swipe(viewport.right-12,up?bottom:top,viewport.right-12,up?top:bottom,40));
        device.waitForIdle(2000);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void mortgageSurvivesRealSplitAndResize() throws Exception {mortgageWholeFormSplit();}
    @Test public void unitFullAndHalfSplitTouch() throws Exception {converterWindows(false,false,false);}
    @Test public void unitUpperQuarterSplitTouch() throws Exception {converterWindows(false,true,false);}
    @Test public void unitLowerQuarterSplitTouch() throws Exception {converterWindows(false,true,true);}
    @Test public void currencyFullAndHalfSplitTouch() throws Exception {converterWindows(true,false,false);}
    @Test public void currencyUpperQuarterSplitTouch() throws Exception {converterWindows(true,true,false);}
    @Test public void currencyLowerQuarterSplitTouch() throws Exception {converterWindows(true,true,true);}
    private void converterWindows(boolean currency,boolean quarter,boolean bottom) throws Exception {
        String stage=(currency?"currency":"unit")+(quarter?(bottom?"-lower-25":"-upper-25"):"-full-half");
        try {
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("calc_tools",Context.MODE_PRIVATE)
                .edit().putBoolean(currency?"currency_secondary_output":"unit_secondary_output",true).commit();
            if(quarter)establishQuarterWindow(currency?R.string.tool_currency:R.string.tool_unit,bottom,stage);
            else {key(R.id.mode_segment_convert);if(currency)key(R.id.mode_segment_convert);}
            converterOperations(currency,stage+(quarter?"":"-full"));
            if(!quarter) {
                enterRealSplit();SplitSample half=stableSample(true,null);logPosition(stage+"-half",half);
                float share=half.app.height()/(float)(half.app.height()+half.other.height());
                assertTrue("actual half window "+half,Math.abs(share-0.5f)<0.04);
                converterOperations(currency,stage+"-half");
                leaveRealSplit();stableSample(false,null);device.waitForIdle(1500);
                keypadMeasurements(stage+"-restored-full",true);evidence(stage+"-restored-full");
                scenario.onActivity(a -> assertEquals("full window restores keypad",Boolean.FALSE,member(member(a,"mConverterKeypadLayout"),"compact")));
            }
        } catch(Throwable failure) {
            Log.e("TaxPosition",stage+" FAIL first="+String.valueOf(failure).split("\n")[0]+" window="+safeSplitSample());
            evidence(stage+"-failure");if(failure instanceof Exception)throw (Exception)failure;throw (Error)failure;
        }
    }
    private android.widget.ScrollView converterViewport(boolean currency) {
        android.widget.ScrollView[] result={null};scenario.onActivity(a -> {
            android.view.ViewParent parent=a.findViewById(currency?R.id.currency_input_text:R.id.unit_input_text).getParent();
            while(!(parent instanceof android.widget.ScrollView)) {assertNotNull(parent);parent=parent.getParent();}
            result[0]=(android.widget.ScrollView)parent;
        });return result[0];
    }
    private void converterFind(boolean currency,int id) {
        android.widget.ScrollView scroll=converterViewport(currency);
        int dragLoss=-1,lastY=0,lastDistance=0;
        for(int attempt=0;attempt<40;attempt++) {
            Rect viewport=new Rect();int[] box=new int[5];boolean[] ready={false};
            scenario.onActivity(a -> {
                assertTrue("bounded converter viewport",visibleOnScreen(scroll,viewport));View view=a.findViewById(id);
                assertNotNull("applicable converter view",view);assertEquals(View.VISIBLE,view.getVisibility());
                int[] at=new int[2];view.getLocationOnScreen(at);box[0]=at[1];box[1]=at[1]+view.getHeight();
                box[2]=view.getHeight();box[3]=android.view.ViewConfiguration.get(a).getScaledTouchSlop();box[4]=scroll.getScrollY();
                Rect actual=new Rect();ready[0]=visibleOnScreen(view,actual)&&actual.height()==view.getHeight()&&viewport.contains(actual)&&view.getHeight()>0;
                if(!ready[0] && !currency)
                    Log.i("TaxPosition","ENTRY id="+id+" screen="+new Rect(at[0],at[1],at[0]+view.getWidth(),at[1]+view.getHeight())+" visible="+actual+" viewport="+viewport+" y="+scroll.getScrollY()+" remaining="+Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight()-scroll.getScrollY())+" ownScroll="+view.getScrollX()+","+view.getScrollY()+" focus="+view.isFocused()+" localVisible="+view.getLocalVisibleRect(new Rect()));
            });
            if(ready[0]) {
                scenario.onActivity(a -> {
                    View view=a.findViewById(id);
                    if(view instanceof TextView) {
                        TextView text=(TextView)view;assertNotNull(text.getLayout());
                        assertTrue("converter row must not vertically truncate",text.getHeight()>=text.getLayout().getHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom());
                    }
                });return;
            }
            assertTrue("natural converter row fits viewport: id="+id+" height="+box[2]+" viewport="+viewport,box[2]>0&&box[2]<=viewport.height());
            int overflow=box[0]<viewport.top?box[0]-viewport.top:box[1]-viewport.bottom;
            // A 71px row can exactly fill a 71px viewport. A minimum 2*slop
            // drag oscillates over that fit interval. Learn the distance consumed by
            // interception from the previous real gesture; converge to actual overflow.
            if(dragLoss<0)dragLoss=box[3]+1;
            int moved=Math.abs(box[4]-lastY);
            if(lastDistance>0 && moved>0 && moved<=lastDistance)
                dragLoss=lastDistance-moved;
            int distance=Math.min(viewport.height()/3,Math.max(box[3]+1,Math.abs(overflow)+dragLoss));
            lastY=box[4];lastDistance=distance;
            boolean down=overflow<0;int from=down?viewport.top+viewport.height()/3:viewport.bottom-viewport.height()/3;
            if(attempt<4||attempt==39)Log.i("TaxPosition","CONVERTER_CONTENT_TARGET id="+id+" attempt="+attempt+" y="+box[4]+" top="+box[0]+" bottom="+box[1]+" height="+box[2]+" overflow="+overflow+" viewport="+viewport);
            pointerDrag(viewport.right-6,from,from+(down?distance:-distance));
            device.waitForIdle(1000);awaitMotionState(R.id.start_state);
        }
        fail("converter cannot be reached with actual pointer: "+id);
    }
    private void converterTap(boolean currency,int id) {
        converterFind(currency,id);device.waitForIdle(1500);converterFind(currency,id);
        Rect r=new Rect();scenario.onActivity(a -> assertTrue(visibleOnScreen(a.findViewById(id),r)));
        assertTrue(device.click(r.centerX(),r.centerY()));device.waitForIdle(1000);
    }
    private void converterKeypadAudit(String stage) throws Exception {
        // Same APK/window, only restore the legacy allocation for the BEFORE reference.
        scenario.onActivity(a -> ((ConverterKeypadLayout)member(a,"mConverterKeypadLayout")).setEnabled(false));
        device.waitForIdle(1500);SystemClock.sleep(500);
        keypadMeasurements(stage+"-before",false);evidence(stage+"-keypad-before");
        scenario.onActivity(a -> ((ConverterKeypadLayout)member(a,"mConverterKeypadLayout")).setEnabled(true));
        device.waitForIdle(1500);SystemClock.sleep(500);
        keypadMeasurements(stage+"-after",true);evidence(stage+"-keypad-after");
    }
    private void keypadMeasurements(String stage,boolean verify) {
        scenario.onActivity(a -> {
            float d=a.getResources().getDisplayMetrics().density,sp=a.getResources().getDisplayMetrics().scaledDensity;
            View pad=a.findViewById(R.id.input_pad);Object layout=member(a,"mConverterKeypadLayout");
            boolean compact=Boolean.TRUE.equals(member(layout,"compact"));
            StringBuilder report=new StringBuilder(stage+" KEYPAD padDp="+pad.getWidth()/d+"x"+pad.getHeight()/d+" compact="+compact+" keys=");
            java.util.List<Rect> targets=new java.util.ArrayList<>();
            for(int id:new int[]{R.id.digit_0,R.id.digit_1,R.id.digit_2,R.id.digit_3,R.id.digit_4,R.id.digit_5,R.id.digit_6,R.id.digit_7,R.id.digit_8,R.id.digit_9,R.id.dec_point,R.id.op_add,R.id.op_sub,R.id.op_mul,R.id.op_div,R.id.del,R.id.clr,R.id.paren,R.id.op_pct,R.id.eq}) {
                TextView key=a.findViewById(id);Rect visible=new Rect();visibleOnScreen(key,visible);
                report.append(a.getResources().getResourceEntryName(id)).append(':').append(key.getWidth()/d).append('x').append(key.getHeight()/d).append(" visible=").append(visible.width()/d).append('x').append(visible.height()/d).append(" sp=").append(key.getTextSize()/sp).append(';');
                if(verify && compact) {
                    assertTrue("48dp target "+id,key.getWidth()>=Math.ceil(48*d)&&key.getHeight()>=Math.ceil(48*d));
                    assertEquals("whole key height",key.getHeight(),visible.height());assertEquals("whole key width",key.getWidth(),visible.width());
                    for(Rect other:targets)assertFalse("no overlapping key targets",Rect.intersects(other,visible));
                    targets.add(visible);
                    if(key.getText().length()>0)assertTrue("readable fixed label",key.getTextSize()/sp>=21.9f);
                }
            }
            Log.i("TaxPosition",report.toString());
            if(verify){View slot=a.findViewById(R.id.tool_control_slot);Rect visible=new Rect();assertTrue("converter content remains reachable",visibleOnScreen(slot,visible));assertTrue("nonzero content",visible.height()>0&&visible.width()>0);}
        });
    }
    private void converterOperations(boolean currency,String stage) throws Exception {
        int input=currency?R.id.currency_input_text:R.id.unit_input_text;
        int primary=currency?R.id.currency_result_text:R.id.unit_result_text;
        int secondary=currency?R.id.currency_result_2_text:R.id.unit_result_2_text;
        android.widget.ScrollView scroll=converterViewport(currency);
        scenario.onActivity(a -> {
            Rect visible=new Rect();visibleOnScreen(scroll,visible);
            Log.i("TaxPosition",stage+" CONTENT child="+scroll.getChildAt(0).getHeight()+" measuredViewport="+scroll.getHeight()+" visibleViewport="+visible+" range="+(scroll.getChildAt(0).getHeight()-scroll.getHeight()));
            for(int id:new int[]{input,primary,secondary}) {
                View v=a.findViewById(id);Rect r=new Rect();visibleOnScreen(v,r);
                Log.i("TaxPosition",stage+" GEOMETRY id="+id+" measured="+v.getWidth()+"x"+v.getHeight()+" visible="+r);
            }
        });
        evidence(stage+"-initial");
        converterKeypadAudit(stage);
        converterTap(currency,input);key(R.id.clr);digits("12");key(R.id.dec_point);digits("34");key(R.id.del);
        assertEquals("continuous decimal then delete","12.3",modelSnapshot().get("expression"));
        key(R.id.clr);digits("123");
        // Place the caret with an actual screen tap at the middle text offset.
        converterFind(currency,input);int[] point=new int[2];
        scenario.onActivity(a -> {
            TextView text=a.findViewById(input);int[] at=new int[2];text.getLocationOnScreen(at);
            point[0]=Math.round(at[0]+text.getCompoundPaddingLeft()+text.getLayout().getPrimaryHorizontal(2)-text.getScrollX());
            point[1]=at[1]+text.getCompoundPaddingTop()+text.getLayout().getLineBottom(0)/2;
        });
        assertTrue(device.click(point[0],point[1]));device.waitForIdle(1000);assertArrayEquals(new int[]{2,2},selection(input));
        key(R.id.op_add);digits("45");assertEquals("middle operator and continuous digits","12+453",modelSnapshot().get("expression"));
        assertConverterComputation(currency);
        for(int id:new int[]{primary,secondary}) {
            converterFind(currency,id);evidence(stage+"-result-"+id);
            scenario.onActivity(a -> {
                TextView result=a.findViewById(id);assertFalse(result.isCursorVisible());assertNull(result.getKeyListener());assertTrue(result.isTextSelectable());
            });
            Rect r=new Rect();String[] text={null};scenario.onActivity(a -> {visibleOnScreen(a.findViewById(id),r);text[0]=((TextView)a.findViewById(id)).getText().toString();});
            assertTrue(device.swipe(r.centerX(),r.centerY(),r.centerX(),r.centerY(),90));
            Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
            androidx.test.uiautomator.UiObject2 all=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(context.getString(android.R.string.selectAll))),1000);if(all!=null)all.click();
            androidx.test.uiautomator.UiObject2 copy=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(context.getString(android.R.string.copy))),4000);
            assertNotNull("real converter copy action",copy);copy.click();
            scenario.onActivity(a -> assertEquals(text[0],((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).coerceToText(a).toString()));
            awaitMotionState(R.id.start_state);
            assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.res("com.android.systemui","clipboard_ui")),20000));
        }
        if(currency) {
            converterFind(true,R.id.currency_update_label);
            for(int refresh=0;refresh<2;refresh++) {
            final int refreshCase=refresh;
            int[] geometryBefore=new int[5];int[] geometryAfter=new int[5];String[] labelBefore={null};String[] focus=new String[2];
            scenario.onActivity(a -> { currencyGeometry(a,scroll,geometryBefore);focus[0]=String.valueOf(a.getCurrentFocus()==null?null:a.getCurrentFocus().getId());labelBefore[0]=((TextView)a.findViewById(R.id.currency_update_label)).getText().toString(); });
            int[] savedSelection=selection(input);int[] position=new int[2];scenario.onActivity(a -> position[0]=scroll.getScrollY());
            Map<String,String> savedModel=modelSnapshot();
            scenario.onActivity(a -> a.getWindow().getDecorView().post(() -> {
                try {
                    java.util.Map<String,java.math.BigDecimal> rates=new java.util.LinkedHashMap<>();
                    rates.put("EUR",java.math.BigDecimal.ONE);rates.put("CNY",new java.math.BigDecimal("20"));rates.put("USD",new java.math.BigDecimal("2"));rates.put("JPY",new java.math.BigDecimal("100"));
                    java.lang.reflect.Method callback=active(a).getClass().getDeclaredMethod("onRatesLoaded",com.android.calculator2.tools.data.CachedRates.class,com.android.calculator2.tools.data.ExchangeRateRepository.Source.class,CharSequence.class);
                    callback.setAccessible(true);callback.invoke(active(a),new com.android.calculator2.tools.data.CachedRates("EUR",System.currentTimeMillis(),"2026-01-02",rates),com.android.calculator2.tools.data.ExchangeRateRepository.Source.CACHE,refreshCase==0?labelBefore[0]:"test rates");
                } catch(Exception e) {throw new AssertionError(e);}
            }));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();device.waitForIdle(1500);
            scenario.onActivity(a -> position[1]=scroll.getScrollY());
            scenario.onActivity(a -> {currencyGeometry(a,scroll,geometryAfter);focus[1]=String.valueOf(a.getCurrentFocus()==null?null:a.getCurrentFocus().getId());});
            Log.i("TaxPosition",stage+" REFRESH case="+(refreshCase==0?"same-label":"changed-label")+" beforeY="+position[0]+" afterY="+position[1]+" before[content,viewport,range,anchorScreen,anchorContent]="+java.util.Arrays.toString(geometryBefore)+" after="+java.util.Arrays.toString(geometryAfter)+" focus="+java.util.Arrays.toString(focus)+" expressionPreserved="+savedModel.equals(modelSnapshot())+" selection="+java.util.Arrays.toString(savedSelection)+"->"+java.util.Arrays.toString(selection(input)));
            assertEquals("external rate callback preserves real expression",savedModel,modelSnapshot());
            assertArrayEquals("rate callback preserves native selection",savedSelection,selection(input));
            assertEquals("rate callback keeps scroll except necessary new-boundary clamp",Math.min(position[0],geometryAfter[2]),position[1]);
            assertEquals("visible result anchor moves only by legal boundary adjustment",geometryBefore[3]+position[0]-position[1],geometryAfter[3]);
            if(refreshCase==1)assertConverterComputation(true);
            Log.i("TaxPosition",stage+" ASYNC_RATE_SELECTION_SCROLL_PASS case="+refreshCase+" y="+position[1]);
            }
        }
        for(int id:currency?new int[]{R.id.currency_from,R.id.currency_to,R.id.currency_to_2}:new int[]{R.id.unit_from,R.id.unit_to,R.id.unit_to_2}) {
            converterTap(currency,id);
            androidx.test.uiautomator.UiObject2 list=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.clazz("android.widget.ListView")),4000);
            assertNotNull("anchored selection menu",list);
            int index=id==(currency?R.id.currency_from:R.id.unit_from)?0:id==(currency?R.id.currency_to:R.id.unit_to)?1:2;
            assertTrue(list.getChildren().size()>index);list.getChildren().get(index).click();device.waitForIdle(1000);
        }
        Map<String,String> before=modelSnapshot();converterTap(currency,currency?R.id.currency_swap:R.id.unit_swap);
        assertEquals(before.get("mFromIndex"),modelSnapshot().get("mToIndex"));assertEquals(before.get("mToIndex"),modelSnapshot().get("mFromIndex"));
        converterTap(currency,input);key(R.id.clr);digits("9");assertEquals("return and edit","9",modelSnapshot().get("expression"));
        evidence(stage+"-return-input");Log.i("TaxPosition",stage+" INSERT_MODELS_PRIMARY_SECONDARY_COPY_MENUS_SWAP_RETURN_PASS");
    }
    private void currencyGeometry(Calculator activity,android.widget.ScrollView scroll,int[] geometry) {
        geometry[0]=scroll.getChildAt(0).getHeight();
        geometry[1]=scroll.getHeight()-scroll.getPaddingTop()-scroll.getPaddingBottom();
        geometry[2]=Math.max(0,geometry[0]-geometry[1]);
        int[] at=new int[2];activity.findViewById(R.id.currency_result_2_text).getLocationOnScreen(at);geometry[3]=at[1];
        int[] root=new int[2];scroll.getLocationOnScreen(root);geometry[4]=at[1]-root[1]+scroll.getScrollY();
    }
    @SuppressWarnings("unchecked")
    private void assertConverterComputation(boolean currency) {
        scenario.onActivity(a -> {
            Object mode=active(a);java.math.BigDecimal input=((com.android.calculator2.tools.model.DecimalExpression)member(mode,"mInput")).getValue();
            int from=(Integer)member(mode,"mFromIndex");
            for(boolean secondary:new boolean[]{false,true}) {
                int to=(Integer)member(mode,secondary?"mToSecondaryIndex":"mToIndex");java.math.BigDecimal expected;
                if(currency) {
                    java.util.List<com.android.calculator2.tools.data.CurrencyDef> currencies=(java.util.List<com.android.calculator2.tools.data.CurrencyDef>)member(mode,"mCurrencies");
                    com.android.calculator2.tools.data.CachedRates rates=(com.android.calculator2.tools.data.CachedRates)member(mode,"mRates");assertNotNull("cached rates loaded",rates);
                    expected=com.android.calculator2.tools.model.CurrencyConversion.convert(input,rates.rateFor(currencies.get(from).getId()),rates.rateFor(currencies.get(to).getId()),new java.math.MathContext(20,java.math.RoundingMode.HALF_UP));
                    assertNotNull("deterministic selected currency has a test rate",expected);expected=expected.setScale(4,java.math.RoundingMode.HALF_UP);
                } else {
                    java.util.List<com.android.calculator2.tools.data.UnitCategory> categories=(java.util.List<com.android.calculator2.tools.data.UnitCategory>)member(mode,"mCategories");
                    com.android.calculator2.tools.data.UnitCategory category=categories.get((Integer)member(mode,"mCategoryIndex"));
                    assertFalse("linear baseline category",category.isAffine());
                    expected=input.multiply(category.getUnits().get(from).getFactor()).divide(category.getUnits().get(to).getFactor(),new java.math.MathContext(15,java.math.RoundingMode.HALF_UP)).round(new java.math.MathContext(12,java.math.RoundingMode.HALF_UP));
                }
                int id=currency?(secondary?R.id.currency_result_2_text:R.id.currency_result_text):(secondary?R.id.unit_result_2_text:R.id.unit_result_text);
                String actual=((TextView)a.findViewById(id)).getText().toString().replace(",","");
                if(Boolean.TRUE.equals(member(mode,"mUppercaseNumbers"))) {
                    String number=currency?new java.text.DecimalFormat("#,##0.00##").format(expected):expected.stripTrailingZeros().toPlainString();
                    assertEquals("uppercase conversion from actual selection",com.android.calculator2.tools.model.ChineseNumerals.toUppercase(number),((TextView)a.findViewById(id)).getText().toString());
                } else assertEquals("conversion from real selected units/rates "+actual,0,expected.compareTo(new java.math.BigDecimal(actual)));
            }
        });
    }
    @Test public void unitSurvivesRealSplitAndResize() throws Exception {splitTool(R.string.tool_unit,true,false);}
    @Test public void currencySurvivesRealSplitAndResize() throws Exception {splitTool(R.string.tool_currency,true,true);}
    @Test public void scientificSurvivesRealSplitAndResize() throws Exception {
        key(R.id.mode_segment_scientific);digits("123");
        try (AutoCloseable split=() -> leaveRealSplit()) {
            enterRealSplit();assertSplitUsable();evidence("split-half-scientific");
            assertScientificInWindow();
            for(int thirds:new int[]{2,1}) {
                resizeRealSplit(thirds);assertSplitUsable();
                evidence("split-resized-"+thirds+"-scientific");assertScientificInWindow();
            }
            onView(withId(R.id.formula)).check(matches(withText("123")));
        }
    }
    private void assertScientificInWindow() {
        scenario.onActivity(a -> {
            ViewGroup pad=a.findViewById(R.id.advanced_pad);
            Rect bounds=new Rect();assertTrue(pad.getGlobalVisibleRect(bounds));
            for(int id:new int[]{R.id.display,R.id.tool_bar,R.id.input_pad}) {
                Rect other=new Rect();a.findViewById(id).getGlobalVisibleRect(other);
                assertFalse("science outside other regions "+id,Rect.intersects(bounds,other));
            }
            for(int i=0;i<pad.getChildCount();i++) {
                View child=pad.getChildAt(i);if(!(child instanceof TextView)||child.getVisibility()!=View.VISIBLE)continue;
                Rect r=new Rect();assertTrue(child.getGlobalVisibleRect(r));
                assertEquals(child.getHeight(),r.height());assertEquals(child.getWidth(),r.width());
                assertTrue(bounds.contains(r));
            }
        });
    }
    private void assertScientificBounds() {
        scenario.onActivity(a -> {
            ViewGroup pad=a.findViewById(R.id.advanced_pad);
            Rect parent=new Rect();assertTrue(pad.getGlobalVisibleRect(parent));
            Rect input=new Rect(),bar=new Rect(),display=new Rect();
            a.findViewById(R.id.input_pad).getGlobalVisibleRect(input);
            a.findViewById(R.id.tool_bar).getGlobalVisibleRect(bar);
            a.findViewById(R.id.display).getGlobalVisibleRect(display);
            Log.i("WindowEvidence","scientific="+parent+" input="+input+" bar="+bar+" display="+display);
            assertTrue("scientific panel must be BELOW tool bar",parent.top>=bar.bottom);
            assertFalse("scientific panel overlaps display",Rect.intersects(parent,display));
            assertFalse("scientific panel overlaps toolbar",Rect.intersects(parent,bar));
            assertFalse("scientific panel overlaps main keypad",Rect.intersects(parent,input));
            assertEquals("landscape keypads share top edge",input.top,parent.top);
            assertEquals("landscape keypads share bottom edge",input.bottom,parent.bottom);
            assertTrue("science panel left of main keypad",parent.right<=input.left);
            for(int i=0;i<pad.getChildCount();i++) {
                View child=pad.getChildAt(i);
                if(!(child instanceof TextView)||child.getVisibility()!=View.VISIBLE) continue;
                Rect bounds=new Rect();assertTrue(child.getGlobalVisibleRect(bounds));
                String label=a.getResources().getResourceEntryName(child.getId());
                Log.i("WindowEvidence",label+" parent="+parent+" bounds="+bounds);
                assertEquals(label+" clipped width",child.getWidth(),bounds.width());
                assertEquals(label+" clipped height",child.getHeight(),bounds.height());
                assertTrue(label+" outside scientific pad",parent.contains(bounds));
                assertTrue(label+" touch enabled",child.isEnabled()&&child.isClickable());
                for(int j=0;j<i;j++) {
                    View other=pad.getChildAt(j);
                    if(!(other instanceof TextView)||other.getVisibility()!=View.VISIBLE) continue;
                    Rect otherBounds=new Rect();other.getGlobalVisibleRect(otherBounds);
                    assertFalse(label+" overlaps "+other.getId(),Rect.intersects(bounds,otherBounds));
                }
            }
        });
    }
}
