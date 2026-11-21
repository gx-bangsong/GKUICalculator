/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.util.ArrayList;
import org.junit.*;
import org.junit.runner.RunWith;

/** Auxiliary height diagnostic ONLY, not real split acceptance. Neither reveal nor probes
 * call scrollTo, accessibility scroll actions, focus setters or test-only edge drags.
 */
@RunWith(AndroidJUnit4.class)
public class FractionGestureDiagnosticsTest {
    private ActivityScenario<Calculator> scenario;
    private UiDevice device;
    @Before public void launch() throws Exception {
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.setOrientationNatural();
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        c.getSharedPreferences("calc_tools",Context.MODE_PRIVATE).edit().clear().commit();
        scenario=ActivityScenario.launch(Calculator.class);
        key(R.id.mode_segment_basic);key(R.id.clr);
        key(R.id.digit_1);key(R.id.op_div);key(R.id.digit_3);key(R.id.eq);
        long end=SystemClock.uptimeMillis()+8000;boolean[] completed={false};
        do {
            scenario.onActivity(a -> {
                android.view.Menu m=new android.widget.PopupMenu(a,a.findViewById(R.id.toolbar)).getMenu();
                a.onCreateOptionsMenu(m);a.onPrepareOptionsMenu(m);completed[0]=m.findItem(R.id.menu_leading).isVisible();
            });
            if(completed[0])return;SystemClock.sleep(40);
        }while(SystemClock.uptimeMillis()<end);
        fail("equals did not finish");
    }
    @After public void close() throws Exception {
        device.executeShellCommand("wm size reset");
        if(scenario!=null)scenario.close();device.unfreezeRotation();
    }
    private void key(int id){onView(withId(id)).perform(click());}
    private void toggle() {
        openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
        onData(new org.hamcrest.TypeSafeMatcher<Object>() {
            @Override protected boolean matchesSafely(Object x){return x instanceof android.view.MenuItem&&((android.view.MenuItem)x).getItemId()==R.id.menu_show_fraction;}
            @Override public void describeTo(org.hamcrest.Description d){d.appendText("fraction menu row");}
        }).perform(click());
    }
    private void awaitSurface() {
        long end=SystemClock.uptimeMillis()+10000;boolean[] ready={false};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                TextView t=a.findViewById(R.id.fraction_result);
                ready[0]="1/3".contentEquals(t.getText())&&t.getLayout()!=null
                        &&a.getWindow().getDecorView().hasWindowFocus()&&!a.findViewById(R.id.display).isLayoutRequested();
            });
            if(ready[0])return;SystemClock.sleep(50);
        }while(SystemClock.uptimeMillis()<end);
        fail("fraction/window did not settle");
    }
    private void awaitResized() {
        long end=SystemClock.uptimeMillis()+8000;boolean[] ready={false};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> ready[0]=a.getWindow().getDecorView().hasWindowFocus()
                    &&a.getWindowManager().getCurrentWindowMetrics().getBounds().height()==650
                    &&!a.findViewById(R.id.display).isLayoutRequested());
            if(ready[0])return;SystemClock.sleep(50);
        }while(SystemClock.uptimeMillis()<end);
        fail("auxiliary display resize did not settle; NOT real split");
    }
    private Rect surface() {
        Rect out=new Rect();scenario.onActivity(a -> {
            View outer=a.findViewById(R.id.display).findViewWithTag("fraction_display_scroll");
            out.set(FractionScrollDiagnostics.visible(outer==null?a.findViewById(R.id.fraction_viewport):outer));
        });return out;
    }
    private int[] positions() {
        int[] state=new int[4];scenario.onActivity(a -> {
            View outer=a.findViewById(R.id.display).findViewWithTag("fraction_display_scroll");
            ScrollView inner=a.findViewById(R.id.fraction_viewport);
            state[0]=outer==null?-1:outer.getScrollY();state[1]=inner.getScrollY();
            state[2]=outer instanceof ScrollView?FractionScrollDiagnostics.range((ScrollView)outer):0;
            state[3]=FractionScrollDiagnostics.range(inner);
        });return state;
    }
    private void revealWithCenterDrags() {
        for(int attempt=0;attempt<8;attempt++) {
            boolean[] full={false},above={false};Rect viewport=surface();int[] lineH={0};
            scenario.onActivity(a -> {
                TextView text=a.findViewById(R.id.fraction_result);Rect line=FractionScrollDiagnostics.firstLine(text);
                full[0]=FractionScrollDiagnostics.visible(text).contains(line);above[0]=line.top<viewport.top;lineH[0]=line.height();
            });
            if(full[0])return;
            assertTrue("MEASUREMENT: actual viewport "+viewport+" cannot carry a complete line h="+lineH[0],viewport.height()>=lineH[0]);
            int from=above[0]?viewport.top+viewport.height()/4:viewport.bottom-viewport.height()/4;
            int to=above[0]?viewport.bottom-viewport.height()/4:viewport.top+viewport.height()/4;
            assertTrue(device.swipe(viewport.centerX(),from,viewport.centerX(),to,45));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        fail("GESTURE: center drags did not reveal a complete fraction line");
    }
    private void probe(boolean onText) throws Exception {
        Rect viewport=surface();int[] point=new int[2],slop={0};int[] before=positions();
        scenario.onActivity(a -> {
            TextView text=a.findViewById(R.id.fraction_result);
            Rect line=FractionScrollDiagnostics.firstLine(text),visible=FractionScrollDiagnostics.visible(text);
            Rect hit=new Rect(line);assertTrue("visible fraction required for actual origin",hit.intersect(visible));
            point[1]=hit.centerY();
            if(onText)point[0]=hit.centerX();
            else {
                int left=line.left-visible.left,right=visible.right-line.right;
                point[0]=right>=left?(line.right+visible.right)/2:(visible.left+line.left)/2;
                assertFalse("blank origin must not be glyph bounds",line.contains(point[0],point[1]));
            }
            slop[0]=ViewConfiguration.get(a).getScaledTouchSlop();
            FractionScrollDiagnostics.state(text,"PROBE origin="+(onText?"TEXT":"ROW_BLANK")+" raw="+point[0]+","+point[1]+" line="+line+" actual="+visible);
            FractionScrollDiagnostics.snapshot(text,"probe before "+(onText?"TEXT":"ROW_BLANK"));
        });
        assertTrue("MEASUREMENT: clipped content must have a nonzero range",before[2]>0||before[3]>0);
        boolean down=before[2]>0?before[0]>0:before[1]>0;
        int room=down?viewport.bottom-point[1]-2:point[1]-viewport.top-2;
        int distance=Math.min(room,Math.max(slop[0]*2+8,viewport.height()/3));
        assertTrue("origin needs normal touch travel beyond slop",distance>slop[0]);
        assertTrue(device.swipe(point[0],point[1],point[0],point[1]+(down?distance:-distance),45));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        int[] after=positions();
        scenario.onActivity(a -> {
            FractionScrollDiagnostics.state(a.findViewById(R.id.display),"PROBE_RESULT origin="+(onText?"TEXT":"ROW_BLANK")
                    +" before="+java.util.Arrays.toString(before)+" after="+java.util.Arrays.toString(after));
            FractionScrollDiagnostics.snapshot(a.findViewById(R.id.display),"probe after "+(onText?"TEXT":"ROW_BLANK"));
        });
        assertTrue("GESTURE: range exists but "+(onText?"text":"blank")+" drag did not move either layer; before="+java.util.Arrays.toString(before)+" after="+java.util.Arrays.toString(after),before[0]!=after[0]||before[1]!=after[1]);
    }
    private void exercise(boolean enableFirst) throws Exception {
        if(enableFirst){toggle();awaitSurface();}
        device.executeShellCommand("wm size 1080x650");awaitResized();
        if(!enableFirst)toggle();awaitSurface();
        ArrayList<Throwable> failures=new ArrayList<>();
        for(boolean onText:new boolean[]{true,false}) {
            try {revealWithCenterDrags();probe(onText);}catch(Throwable e){failures.add(e);}
        }
        try {
            revealWithCenterDrags();onView(withId(R.id.fraction_result)).perform(longClick());
            scenario.onActivity(a -> {
                assertEquals("1/3",a.getSystemService(ClipboardManager.class).getPrimaryClip().getItemAt(0).coerceToText(a).toString());
                TextView t=a.findViewById(R.id.fraction_result);
                assertTrue("ALL ancestors must admit the full line",FractionScrollDiagnostics.visible(t).contains(FractionScrollDiagnostics.firstLine(t)));
            });
        }catch(Throwable e){failures.add(e);}
        finally {scenario.onActivity(a -> FractionScrollDiagnostics.snapshot(a.findViewById(R.id.display),"end diagnostic NOT REAL SPLIT enableFirst="+enableFirst));}
        try {awaitDownloadsLog(enableFirst);}catch(Throwable e){failures.add(e);}
        if(!failures.isEmpty()) {
            AssertionError error=new AssertionError("Auxiliary diagnostic failures="+failures.size()+" first="+failures.get(0));
            for(Throwable e:failures)error.addSuppressed(e);throw error;
        }
    }
    private void awaitDownloadsLog(boolean order) throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        long version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();
        long end=SystemClock.uptimeMillis()+8000;
        do {
            String uri=c.getSharedPreferences("fraction_diagnostic_export",Context.MODE_PRIVATE).getString("download_"+version,null);
            if(uri!=null)try(java.io.InputStream in=c.getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[4096];int count;
                while(in!=null&&(count=in.read(block))!=-1)bytes.write(block,0,count);
                if(bytes.toString("UTF-8").contains("end diagnostic NOT REAL SPLIT enableFirst="+order))return;
            }catch(java.io.IOException ignored){}
            SystemClock.sleep(50);
        }while(SystemClock.uptimeMillis()<end);
        fail("diagnostic log was not readable from Downloads");
    }
    @Test public void enableThenResizeTextAndBlank() throws Exception {exercise(true);}
    @Test public void resizeThenEnableTextAndBlank() throws Exception {exercise(false);}
}
