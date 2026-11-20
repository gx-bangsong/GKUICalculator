/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExactFractionUiTest {
    private ActivityScenario<Calculator> scenario;
    @Before public void launch() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.getSharedPreferences("calc_tools",Context.MODE_PRIVATE).edit().clear().commit();
        scenario=ActivityScenario.launch(Calculator.class);
        key(R.id.mode_segment_basic);key(R.id.clr);
    }
    @After public void close(){if(scenario!=null)scenario.close();}
    private void key(int id){onView(withId(id)).perform(click());}
    private Menu menu(Calculator a) {
        Menu m=new PopupMenu(a,a.findViewById(R.id.toolbar)).getMenu();
        a.onCreateOptionsMenu(m);a.onPrepareOptionsMenu(m);return m;
    }
    private void toggle() {
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
        onView(withText(R.string.menu_show_fraction)).perform(click());
    }
    private void await(String expected) {
        long end=SystemClock.uptimeMillis()+8000;
        do {
            boolean[] match={false};
            scenario.onActivity(a -> match[0]=expected.equals(((TextView)a.findViewById(R.id.fraction_result)).getText().toString())
                    &&a.findViewById(R.id.fraction_viewport).getVisibility()==(expected.isEmpty()?View.GONE:View.VISIBLE));
            if(match[0])return;
            SystemClock.sleep(40);
        }while(SystemClock.uptimeMillis()<end);
        fail("Expected fraction: "+expected);
    }
    private void completed() {
        long end=SystemClock.uptimeMillis()+8000;
        do {
            boolean[] done={false};scenario.onActivity(a -> done[0]=menu(a).findItem(R.id.menu_leading).isVisible());
            if(done[0])return;
            SystemClock.sleep(40);
        }while(SystemClock.uptimeMillis()<end);
        fail("Equals did not reach RESULT");
    }
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File dir=c.getExternalFilesDir("fraction-evidence");assertNotNull(dir);dir.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }finally{image.recycle();}
    }
    @Test public void toggleCompletedResultWithoutReevaluationAndPersist() throws Exception {
        key(R.id.digit_1);key(R.id.op_div);key(R.id.digit_3);key(R.id.eq);
        completed();await("");
        final UnifiedReal[] value={null};final long[] history={0};final String[] decimal={null};
        scenario.onActivity(a -> {
            assertFalse(menu(a).findItem(R.id.menu_show_fraction).isChecked());
            value[0]=Evaluator.getInstance(a).getResult(Evaluator.MAIN_INDEX);
            assertNotNull(value[0]);history[0]=Evaluator.getInstance(a).getMaxIndex();
            decimal[0]=((TextView)a.findViewById(R.id.result)).getText().toString();
        });
        toggle();await("1/3");screenshot("fraction-basic");
        onView(withId(R.id.fraction_result)).perform(longClick());
        scenario.onActivity(a -> {
            ClipboardManager cb=a.getSystemService(ClipboardManager.class);
            assertEquals("1/3",cb.getPrimaryClip().getItemAt(0).coerceToText(a).toString());
        });
        toggle();await("");toggle();await("1/3");
        scenario.onActivity(a -> {
            assertSame(value[0],Evaluator.getInstance(a).getResult(Evaluator.MAIN_INDEX));
            assertEquals(history[0],Evaluator.getInstance(a).getMaxIndex());
            assertEquals(decimal[0],((TextView)a.findViewById(R.id.result)).getText().toString());
            assertTrue(menu(a).findItem(R.id.menu_show_fraction).isChecked());
        });
        scenario.recreate();await("1/3");
        scenario.onActivity(a -> assertTrue(menu(a).findItem(R.id.menu_show_fraction).isChecked()));
        key(R.id.mode_segment_scientific);await("1/3");screenshot("fraction-scientific");
        key(R.id.mode_segment_convert);await("");
        scenario.onActivity(a -> {
            assertTrue(a.getSharedPreferences("calc_tools",Context.MODE_PRIVATE).getBoolean("show_exact_fraction",false));
            assertFalse(menu(a).findItem(R.id.menu_show_fraction).isVisible());
        });
    }
    @Test public void previewIntegerIrrationalErrorAndClearNeverLeaveStaleFraction() {
        toggle();key(R.id.digit_1);key(R.id.op_div);key(R.id.digit_3);await("");key(R.id.eq);await("1/3");
        key(R.id.clr);await("");key(R.id.digit_4);key(R.id.op_div);key(R.id.digit_2);key(R.id.eq);completed();await("");
        scenario.onActivity(a -> assertTrue(menu(a).findItem(R.id.menu_show_fraction).isChecked()));
        key(R.id.clr);key(R.id.digit_1);key(R.id.op_div);key(R.id.digit_0);key(R.id.eq);
        long errorEnd=SystemClock.uptimeMillis()+8000;boolean[] error={false};
        do {
            scenario.onActivity(a -> error[0]=a.getString(R.string.error_zero_divide).equals(((TextView)a.findViewById(R.id.result)).getText().toString()));
            if(error[0])break;SystemClock.sleep(40);
        }while(SystemClock.uptimeMillis()<errorEnd);
        assertTrue("Original division-by-zero error",error[0]);await("");
        key(R.id.clr);key(R.id.mode_segment_scientific);key(R.id.op_sqrt);key(R.id.digit_2);key(R.id.eq);completed();await("");
        scenario.onActivity(a -> assertTrue(menu(a).findItem(R.id.menu_show_fraction).isChecked()));
    }
}
