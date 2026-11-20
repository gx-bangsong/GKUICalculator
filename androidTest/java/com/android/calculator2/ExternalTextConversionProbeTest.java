/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemClock;
import android.text.Selection;
import android.view.View;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassificationManager;
import android.webkit.WebView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Observation only: no receiver, classifier replacement, resolver changes or synthetic action. */
@RunWith(AndroidJUnit4.class)
public class ExternalTextConversionProbeTest {
    private final JSONObject report=new JSONObject();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void hold(int x,int y) {
        android.app.Instrumentation i=InstrumentationRegistry.getInstrumentation();long down=SystemClock.uptimeMillis();
        pointer(i,down,android.view.MotionEvent.ACTION_DOWN,x,y);
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+350);
        pointer(i,down,android.view.MotionEvent.ACTION_UP,x,y);
    }
    private void pointer(android.app.Instrumentation i,long down,int action,int x,int y) {
        android.view.MotionEvent e=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);try{i.sendPointerSync(e);}finally{e.recycle();}
    }
    private String js(ActivityScenario<Calculator> scenario,WebView web,String expression) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] result={null};
        scenario.onActivity(a -> web.evaluateJavascript(expression,value -> {result[0]=value;done.countDown();}));
        if(!done.await(8,TimeUnit.SECONDS))throw new IllegalStateException("WebView JavaScript callback timeout");
        return result[0];
    }
    private void save() throws Exception {
        String json=report.toString(2);
        try(FileWriter writer=new FileWriter(new File(context.getFilesDir(),"external-text-probe.json"))){writer.write(json);}
        // Keep observations outside app data, which the test runner may remove on teardown.
        String encoded=android.util.Base64.encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
        device.executeShellCommand("printf %s '"+encoded+"' | base64 -d > /sdcard/external-text-probe.json");
    }
    private void classify() throws Exception {
        JSONArray rows=new JSONArray();report.put("classifications",rows);
        TextClassificationManager manager=context.getSystemService(TextClassificationManager.class);
        report.put("classifierClass",manager.getTextClassifier().getClass().getName());
        for(String locale:new String[]{"en-US","zh-CN"})for(String text:new String[]{"10 km","10 USD"}) {
            JSONObject row=new JSONObject().put("requestedLocale",locale).put("text",text);rows.put(row);
            try {
                TextClassification result=manager.getTextClassifier().classifyText(new TextClassification.Request.Builder(text,0,text.length())
                        .setDefaultLocales(LocaleList.forLanguageTags(locale)).build());
                JSONArray entities=new JSONArray(),actions=new JSONArray();row.put("entities",entities).put("actions",actions);
                for(int n=0;n<result.getEntityCount();n++)entities.put(new JSONObject().put("type",result.getEntity(n)).put("confidence",result.getConfidenceScore(result.getEntity(n))));
                for(RemoteAction action:result.getActions())actions.put(new JSONObject().put("title",action.getTitle()).put("description",action.getContentDescription())
                        .put("pendingIntentCreatorPackage",action.getActionIntent().getCreatorPackage()));
                // Deprecated but PUBLIC primary-action field, when supplied by this classifier.
                Intent primary=result.getIntent();row.put("primaryIntent",primary==null?JSONObject.NULL:primary.toUri(Intent.URI_INTENT_SCHEME));
                row.put("extrasKeys",result.getExtras().keySet().toString());
            } catch(Exception error){row.put("error",error.toString());}
        }
    }
    private void menu(String stage,ActivityScenario<Calculator> scenario,TextView nativeText,WebView web) throws Exception {
        JSONObject row=new JSONObject();report.put(stage,row);
        SystemClock.sleep(1800);
        UiObject2 all=device.findObject(By.text(Pattern.compile("(?i)^(select all|全选)$")));
        if(all!=null){all.click();SystemClock.sleep(1800);}
        if(nativeText!=null)scenario.onActivity(a -> {
            try {row.put("selectionStart",nativeText.getSelectionStart()).put("selectionEnd",nativeText.getSelectionEnd());
                int start=nativeText.getSelectionStart(),end=nativeText.getSelectionEnd();
                row.put("selectedText",start>=0&&end>=start?nativeText.getText().subSequence(start,end).toString():"");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        if(web!=null)row.put("selectedTextJs",js(scenario,web,"window.getSelection().toString()"));
        ByteArrayOutputStream xml=new ByteArrayOutputStream();device.dumpWindowHierarchy(xml);
        row.put("menuHierarchy",xml.toString("UTF-8"));
        device.executeShellCommand("screencap -p /sdcard/external-probe-"+stage+".png");
        UiObject2 convert=device.findObject(By.text(Pattern.compile("(?i)^(convert|转换|换算)$")));
        row.put("convertMenuVisible",convert!=null);
        if(convert!=null) {
            row.put("clickedOriginalMenuTitle",convert.getText());convert.click();SystemClock.sleep(2000);
            row.put("afterClickActivities",device.executeShellCommand("dumpsys activity activities"));
            row.put("afterClickActivityLog",device.executeShellCommand("logcat -d -v brief -s ActivityTaskManager:I ActivityManager:I"));
        }
        device.pressBack();save();
    }
    @Test public void observeUnmodifiedPlatformSelection() throws Exception {
        report.put("purpose","Observation, not GKUI integration or takeover PASS");
        report.put("sdk",Build.VERSION.SDK_INT).put("fingerprint",Build.FINGERPRINT);
        report.put("webviewProvider",String.valueOf(WebView.getCurrentWebViewPackage()));
        report.put("systemTextClassifier",device.executeShellCommand("dumpsys textclassification"));
        report.put("viaPackages",device.executeShellCommand("pm list packages -f mark.via"));
        report.put("webSearchResolver",device.executeShellCommand("cmd package resolve-activity --brief -a android.intent.action.WEB_SEARCH"));
        report.put("httpsResolver",device.executeShellCommand("cmd package resolve-activity --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://example.invalid/"));
        try {classify();}catch(Exception e){report.put("classificationError",e.toString());}save();
        try(ActivityScenario<Calculator> scenario=ActivityScenario.launch(Calculator.class)) {
            try {
                TextView[] text={null};int[] xy=new int[2];
                scenario.onActivity(a -> {TextView t=new TextView(a);t.setText("10 km");t.setTextSize(32);t.setTextIsSelectable(true);
                    t.setPadding(40,180,40,80);a.setContentView(t);text[0]=t;});
                SystemClock.sleep(800);
                scenario.onActivity(a -> {text[0].getLocationOnScreen(xy);xy[0]+=text[0].getTotalPaddingLeft()+12;xy[1]+=text[0].getTotalPaddingTop()+text[0].getLineHeight()/2;});
                hold(xy[0],xy[1]);menu("native",scenario,text[0],null);
            }catch(Exception e){report.put("nativeError",e.toString());save();}
        }
        try(ActivityScenario<Calculator> scenario=ActivityScenario.launch(Calculator.class)) {
            WebView[] web={null};
            try {
                scenario.onActivity(a -> {WebView w=new WebView(a);w.getSettings().setJavaScriptEnabled(true);a.setContentView(w);web[0]=w;
                    w.loadDataWithBaseURL("https://example.invalid/","<meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:40px;padding-top:80px;font-size:32px'><span id='probe'>10 km</span></body>","text/html","UTF-8",null);});
                SystemClock.sleep(1600);
                JSONArray bounds=new JSONArray(js(scenario,web[0],"(function(){var r=document.getElementById('probe').getBoundingClientRect();return [r.left,r.top,r.width,r.height,window.devicePixelRatio];})()"));
                int[] origin=new int[2];scenario.onActivity(a -> web[0].getLocationOnScreen(origin));
                hold(origin[0]+(int)((bounds.getDouble(0)+8)*bounds.getDouble(4)),origin[1]+(int)((bounds.getDouble(1)+bounds.getDouble(3)/2)*bounds.getDouble(4)));
                menu("webview",scenario,null,web[0]);
            }catch(Exception e){report.put("webviewError",e.toString());save();}
            finally {scenario.onActivity(a -> {if(web[0]!=null)web[0].destroy();});}
        }
        save();
    }
}
