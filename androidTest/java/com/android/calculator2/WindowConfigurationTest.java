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
        // Shell screencap can write /sdcard under scoped storage; the instrumentation UID cannot.
        try {
            String path="/sdcard/window-"+name+".png";
            device.executeShellCommand("screencap -p "+path);
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
            key(R.id.mortgage_type_combined);key(R.id.mortgage_basis_toggle);key(R.id.mortgage_method);
            key(R.id.tool_field_0);key(R.id.clr);digits("1234567");
            key(R.id.tool_field_2);key(R.id.clr);digits("17");selectTail(R.id.tool_field_2,1);return R.id.tool_field_2;
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
        String result=displaySnapshot().get(R.id.result);
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        assertArrayEquals(before,selection(R.id.tool_field_1));
        digits("9");
        Map<String,String> after=modelSnapshot();
        assertEquals("193",after.get("field1"));
        assertEquals("active field retained",model.get("activeField"),after.get("activeField"));
        assertEquals("other field unchanged",model.get("field0"),after.get("field0"));
        assertNotEquals("BMI recomputed",result,displaySnapshot().get(R.id.result));
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
        onView(withId(android.R.id.copy)).perform(click());
        awaitMotionState(R.id.start_state);
        scenario.onActivity(a -> {
            android.content.ClipboardManager cb=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            assertTrue("real copy populated clipboard",cb.hasPrimaryClip());
        });
        // Vertical scroll starts inside tool content, not from the handle.
        scenario.onActivity(a -> {
            Rect r=new Rect();assertTrue(a.findViewById(R.id.tool_control_slot).getGlobalVisibleRect(r));
            rect[0]=r.centerX();rect[1]=r.top+r.height()/4;rect[2]=r.bottom-r.height()/4;
        });
        assertTrue(device.swipe(rect[0],rect[2],rect[0],rect[1],30));
        awaitMotionState(R.id.start_state);
        assertTrue(device.swipe(rect[0],rect[1],rect[0],rect[2],30));
        awaitMotionState(R.id.start_state);
        dragHistoryFromVisibleHandle();
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
