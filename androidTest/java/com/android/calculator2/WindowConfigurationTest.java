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
            assertTrue("screenshot "+name,device.executeShellCommand("test -s "+path+" && echo SAVED").contains("SAVED"));
        } catch(java.io.IOException e) { throw new AssertionError("screenshot "+name,e); }
    }
    private void rotateTool(int name) throws Exception {
        onView(withContentDescription(name)).perform(click());
        onView(withContentDescription(name)).check(matches(isSelected()));
        evidence(name+"-portrait");
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        evidence(name+"-landscape");
        onView(withContentDescription(name)).check(matches(isSelected()));
        onView(withId(R.id.mode_segment_scientific)).check(matches(org.hamcrest.Matchers.not(isSelected())));
        device.setOrientationNatural();awaitOrientation(Configuration.ORIENTATION_PORTRAIT);
        evidence(name+"-returned");
        onView(withContentDescription(name)).check(matches(isSelected()));
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
        device.setOrientationLeft();awaitOrientation(Configuration.ORIENTATION_LANDSCAPE);
        evidence(currency?"currency-landscape":"unit-landscape");
        onView(withId(input)).check(matches(isDisplayed()));
        onView(withId(R.id.mode_segment_convert)).check(matches(isSelected()));
    }
    @Test public void unitSurvivesActualRotation() throws Exception { rotateConverter(false); }
    @Test public void currencySurvivesActualRotation() throws Exception { rotateConverter(true); }
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
