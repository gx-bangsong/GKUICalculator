/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileWriter;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses the real system palette installed by the shell harness, not a local theme overlay. */
@RunWith(AndroidJUnit4.class)
public class MonetPaletteTest {
    private void key(int id) { onView(withId(id)).perform(click()); }
    private void shot(Context c, String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        File dir=c.getExternalFilesDir("monet-evidence");assertNotNull(dir);dir.mkdirs();
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(new File(dir,name+".png")));
    }
    private void contrast(int fg, int bg, String role) {
        assertTrue(role+" contrast "+ColorUtils.calculateContrast(fg,bg),
                ColorUtils.calculateContrast(fg,bg)>=4.5);
    }
    @Test public void realSystemPaletteSurfaces() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bundle args=InstrumentationRegistry.getArguments();
        String label=args.getString("palette_label","unspecified");
        boolean dark=Boolean.parseBoolean(args.getString("expected_dark","false"));
        c.getSharedPreferences("calc_tools",Context.MODE_PRIVATE).edit().clear()
                .putBoolean("show_exact_fraction",true).commit();
        JSONObject report=new JSONObject();
        try(ActivityScenario<Calculator> scenario=ActivityScenario.launch(Calculator.class)) {
            scenario.onActivity(a -> {
                assertEquals("actual system night mode",dark,
                    (a.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
                try {
                    report.put("label",label).put("api",Build.VERSION.SDK_INT).put("dark",dark);
                    if(Build.VERSION.SDK_INT>=31) report.put("systemAccent",a.getColor(android.R.color.system_accent1_500));
                    for(int id:new int[]{R.id.digit_1,R.id.op_add,R.id.clr,R.id.eq}) {
                        MaterialButton b=a.findViewById(id);assertNotNull(b);
                        int bg=b.getBackgroundTintList().getColorForState(b.getDrawableState(),0);
                        contrast(b.getCurrentTextColor(),bg,"key "+id);
                    }
                    int bg=a.getColor(R.color.display_background_color);
                    contrast(a.getColor(R.color.display_formula_text_color),bg,"formula");
                    contrast(a.getColor(R.color.display_result_text_color),bg,"result/fraction");
                    contrast(a.getColor(R.color.calculator_chip_selected_text),a.getColor(R.color.calculator_chip_selected_background),"selected pill");
                    contrast(a.getColor(R.color.calculator_chip_text),a.getColor(R.color.calculator_chip_background),"pill");
                    report.put("display",bg).put("equals",a.getColor(R.color.calculator_key_equals))
                        .put("chip",a.getColor(R.color.calculator_chip_selected_background));
                    if(Build.VERSION.SDK_INT>=34) assertEquals(a.getColor(dark?android.R.color.system_tertiary_dark:android.R.color.system_tertiary_light),a.getColor(R.color.calculator_key_equals));
                    else if(Build.VERSION.SDK_INT>=31) assertEquals(a.getColor(dark?android.R.color.system_accent3_200:android.R.color.system_accent3_600),a.getColor(R.color.calculator_key_equals));
                }catch(org.json.JSONException e){throw new AssertionError(e);}
            });
            key(R.id.mode_segment_basic);key(R.id.clr);key(R.id.digit_1);key(R.id.op_div);key(R.id.digit_3);key(R.id.eq);
            long end=SystemClock.uptimeMillis()+8000;boolean[] shown={false};
            do {scenario.onActivity(a -> shown[0]="1/3".contentEquals(((TextView)a.findViewById(R.id.fraction_result)).getText()));
                if(shown[0])break;SystemClock.sleep(80);
            }while(SystemClock.uptimeMillis()<end);
            assertTrue("exact fraction rendered",shown[0]);shot(c,label+"-fraction");
            key(R.id.mode_segment_scientific);shot(c,label+"-science");
            onView(withContentDescription(R.string.menu_history)).perform(click());
            onView(withId(R.id.history_toolbar)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            shot(c,label+"-history");pressBack();
            key(R.id.mode_segment_basic);key(R.id.mode_segment_convert);shot(c,label+"-units");
            key(R.id.unit_from);shot(c,label+"-unit-menu");pressBack();
            key(R.id.mode_segment_convert);shot(c,label+"-currency");
            key(R.id.mode_segment_basic);
            onView(withContentDescription(R.string.tool_tax)).perform(click());
            key(R.id.tax_insurance_detail);shot(c,label+"-dialog");pressBack();
            File dir=c.getExternalFilesDir("monet-evidence");
            try(FileWriter out=new FileWriter(new File(dir,label+".json"))){out.write(report.toString(2));}
        }
    }
}
