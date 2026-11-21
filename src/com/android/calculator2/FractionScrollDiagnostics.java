/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Developer-opt-in only, bounded geometry/gesture trace (off in ordinary debug APKs). No expressions, result strings, clipboard data,
 * screenshots, network, permissions or touch/layout changes. Downloads export is off the UI thread.
 */
final class FractionScrollDiagnostics {
    // Deliberate source opt-in for a dedicated developer diagnostic build only.
    // Debuggable APKs and saved export URIs MUST NOT enable tracing/export by themselves.
    static final boolean DEVELOPER_ENABLED=false;
    private static final String TAG="FractionScroll";
    private static final int LIMIT=96*1024;
    private static final StringBuilder trace=new StringBuilder();
    private static final HashMap<String,Long> moves=new HashMap<>();
    private static final ExecutorService io=Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"fraction-trace");t.setDaemon(true);return t;
    });
    private static boolean writing, active;
    private static int sequence, gesture, rootIdentity;
    private static long lastScrollExport;
    private static boolean debug(View v) {
        return DEVELOPER_ENABLED&&(v.getContext().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;
    }
    private static boolean showing(View v) {
        View fraction=v.getRootView().findViewById(R.id.fraction_viewport);
        return fraction!=null&&fraction.getVisibility()==View.VISIBLE;
    }
    private static Activity activity(View v) {
        Context c=v.getContext();
        while(c instanceof ContextWrapper) {
            if(c instanceof Activity)return (Activity)c;
            Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;
        }
        return null;
    }
    static Rect raw(View v) {
        Matrix m=new Matrix();v.transformMatrixToGlobal(m);
        RectF f=new RectF(0,0,v.getWidth(),v.getHeight());m.mapRect(f);
        Rect r=new Rect();f.roundOut(r);return r;
    }
    static Rect visible(View v) {
        Rect result=raw(v);
        for(View node=v;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null) {
            if(node.getVisibility()!=View.VISIBLE||node.getAlpha()==0)return new Rect();
            Rect ownClip=node.getClipBounds();
            if(ownClip!=null) {
                Matrix m=new Matrix();node.transformMatrixToGlobal(m);RectF box=new RectF(ownClip);m.mapRect(box);
                Rect mapped=new Rect();box.roundOut(mapped);if(!result.intersect(mapped))return new Rect();
            }
            if(node!=v&&node instanceof ViewGroup) {
                ViewGroup group=(ViewGroup)node;
                if(group.getClipChildren()&&!result.intersect(raw(group)))return new Rect();
                if(group.getClipToPadding()&&(group.getPaddingLeft()!=0||group.getPaddingTop()!=0
                        ||group.getPaddingRight()!=0||group.getPaddingBottom()!=0)) {
                    Matrix m=new Matrix();group.transformMatrixToGlobal(m);
                    RectF f=new RectF(group.getPaddingLeft(),group.getPaddingTop(),
                            group.getWidth()-group.getPaddingRight(),group.getHeight()-group.getPaddingBottom());
                    m.mapRect(f);Rect padded=new Rect();f.roundOut(padded);
                    if(!result.intersect(padded))return new Rect();
                }
            }
        }
        if(!result.intersect(raw(v.getRootView())))return new Rect();
        Activity a=activity(v);
        if(a!=null)try {
            if(!result.intersect(a.getWindowManager().getCurrentWindowMetrics().getBounds()))return new Rect();
        }catch(RuntimeException detachedWindow){/* trace can outlive the last layout during rotation */}
        return result;
    }
    static Rect firstLine(TextView v) {
        if(v.getLayout()==null||v.getLayout().getLineCount()==0)return new Rect();
        android.text.Layout l=v.getLayout();
        int top=v.getBaseline()-l.getLineBaseline(0)-v.getScrollY();
        RectF f=new RectF(v.getCompoundPaddingLeft()+l.getLineLeft(0)-v.getScrollX(),top+l.getLineTop(0),
                v.getCompoundPaddingLeft()+l.getLineRight(0)-v.getScrollX(),top+l.getLineBottom(0));
        Matrix m=new Matrix();v.transformMatrixToGlobal(m);m.mapRect(f);Rect r=new Rect();f.roundOut(r);return r;
    }
    static int range(ScrollView s) {
        if(s.getChildCount()==0)return 0;
        View child=s.getChildAt(0);int margins=0;
        if(child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)child.getLayoutParams();margins=p.topMargin+p.bottomMargin;
        }
        return Math.max(0,child.getHeight()+margins-(s.getHeight()-s.getPaddingTop()-s.getPaddingBottom()));
    }
    private static String name(View v) {
        String id="none";
        if(v.getId()!=View.NO_ID)try{id=v.getResources().getResourceEntryName(v.getId());}catch(Exception ignored){}
        return v.getClass().getSimpleName()+"#"+id+"@"+Integer.toHexString(System.identityHashCode(v));
    }
    private static synchronized void append(String line) {
        String text=SystemClock.uptimeMillis()+" g="+gesture+" "+line;
        Log.i(TAG,text);trace.append(text).append('\n');sequence++;
        if(trace.length()>LIMIT) {
            int cut=trace.indexOf("\n",trace.length()-LIMIT);trace.delete(0,cut<0?trace.length()-LIMIT:cut+1);
        }
    }
    static void state(View v,String message) {
        if(debug(v))append(message);
    }
    static void snapshot(View anchor,String reason) {
        if(!debug(anchor))return;
        try {snapshotAttached(anchor,reason);}
        catch(RuntimeException detachedWindow){append("SNAPSHOT unavailable during window transition: "+detachedWindow.getClass().getSimpleName());}
    }
    private static void snapshotAttached(View anchor,String reason) {
        View root=anchor.getRootView();View outer=root.findViewWithTag("fraction_display_scroll");
        TextView text=root.findViewById(R.id.fraction_result);if(text==null)return;
        Activity a=activity(anchor);
        append("SNAPSHOT "+reason+" SDK="+Build.VERSION.SDK_INT+" model="+Build.MODEL
                +" multi="+(a!=null&&a.isInMultiWindowMode())+" window="+(a==null?"?":a.getWindowManager().getCurrentWindowMetrics().getBounds())
                +" outerMounted="+(outer!=null&&outer.isAttachedToWindow())+" fractionVisible="+showing(anchor));
        if(text.getLayout()!=null) {
            Rect line=firstLine(text),clip=visible(text);
            View inner=root.findViewById(R.id.fraction_viewport);
            View carrier=outer!=null&&outer.isAttachedToWindow()?outer:inner;
            Rect carrierClip=carrier==null?new Rect():visible(carrier);
            append("LINE fontPx="+text.getTextSize()+" layoutH="+text.getLayout().getHeight()+" firstRaw="+line+" actualClip="+clip
                    +" fullFirstLine="+(!line.isEmpty()&&clip.contains(line))+" carrierClip="+carrierClip
                    +" carrierTooShort="+(carrierClip.height()<line.height()));
        }
        for(View node=text;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null) {
            String info="NODE "+name(node)+" measuredH="+node.getMeasuredHeight()+" h="+node.getHeight()
                    +" raw="+raw(node)+" clip="+visible(node)+" ownClip="+node.getClipBounds()+" scrollY="+node.getScrollY();
            if(node instanceof ScrollView) {
                ScrollView s=(ScrollView)node;
                info+=" viewportH="+(s.getHeight()-s.getPaddingTop()-s.getPaddingBottom())
                        +" contentH="+(s.getChildCount()==0?0:s.getChildAt(0).getHeight())+" maxY="+range(s)
                        +" up="+s.canScrollVertically(-1)+" down="+s.canScrollVertically(1);
            }
            if(node instanceof ViewGroup)info+=" clipChildren="+((ViewGroup)node).getClipChildren()+" clipPadding="+((ViewGroup)node).getClipToPadding();
            append(info);
        }
        flush(anchor.getContext());
    }
    static void event(View view,String layer,MotionEvent e,Boolean handled) {
        if(!debug(view))return;
        int identity=System.identityHashCode(view.getRootView());
        if(layer.equals("root.dispatch.in")&&e.getActionMasked()==MotionEvent.ACTION_DOWN) {
            View display=view.getRootView().findViewById(R.id.display);
            // Do not filter by our calculated ancestor clip: an incorrect clip is one of
            // the things being diagnosed. Include candidate display/text/outer raw bounds.
            View text=view.getRootView().findViewById(R.id.fraction_result);
            View outer=view.getRootView().findViewWithTag("fraction_display_scroll");
            int x=(int)e.getRawX(),y=(int)e.getRawY();
            active=showing(view)&&display!=null&&(raw(display).contains(x,y)
                    ||text!=null&&raw(text).contains(x,y)||outer!=null&&raw(outer).contains(x,y));
            rootIdentity=identity;
            if(active) {gesture++;moves.clear();snapshot(view,"before DOWN");}
        }
        if(!active||rootIdentity!=identity)return;
        int action=e.getActionMasked();
        String key=layer+":"+handled;
        if(action==MotionEvent.ACTION_MOVE) {
            long now=SystemClock.uptimeMillis(),last=moves.containsKey(key)?moves.get(key):0;
            if(now-last<100)return;moves.put(key,now);
        }
        append("EVENT "+layer+" "+MotionEvent.actionToString(action)+" raw="+Math.round(e.getRawX())+","+Math.round(e.getRawY())
                +" y="+view.getScrollY()+" handled="+handled);
        if(layer.equals("root.dispatch.out")&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)) {
            snapshot(view,"after "+MotionEvent.actionToString(action));active=false;
        }
    }
    static void disallow(View view,String layer,boolean value) {
        if(debug(view)&&active&&rootIdentity==System.identityHashCode(view.getRootView()))append("DISALLOW "+layer+" value="+value);
    }
    static void scroll(View view,String layer,int oldY,int y) {
        if(!debug(view)||!showing(view))return;
        append("SCROLL "+layer+" oldY="+oldY+" y="+y+(view instanceof ScrollView?" maxY="+range((ScrollView)view):""));
        if(SystemClock.uptimeMillis()-lastScrollExport>300) {lastScrollExport=SystemClock.uptimeMillis();flush(view.getContext());}
    }
    private static synchronized void flush(Context context) {
        if(writing)return;writing=true;Context app=context.getApplicationContext();
        io.execute(() -> {
            for(;;) {
                final String data;final int saved;
                synchronized(FractionScrollDiagnostics.class){data=trace.toString();saved=sequence;}
                export(app,data);
                synchronized(FractionScrollDiagnostics.class){if(sequence==saved){writing=false;return;}}
            }
        });
    }
    private static void export(Context c,String data) {
        try {
            long version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();
            String filename="GKUI-fraction-scroll-"+version+".txt";
            byte[] bytes=("Diagnostic only; geometry and touch routing, no calculation text.\npackage="+c.getPackageName()+" versionCode="+version+"\n"+data).getBytes(StandardCharsets.UTF_8);
            File directory=c.getExternalFilesDir("fraction-diagnostics");
            if(directory!=null) {directory.mkdirs();try(OutputStream out=new FileOutputStream(new File(directory,filename))){out.write(bytes);}}
            android.content.SharedPreferences prefs=c.getSharedPreferences("fraction_diagnostic_export",Context.MODE_PRIVATE);
            String key="download_"+version;String previous=prefs.getString(key,null);Uri uri=previous==null?null:Uri.parse(previous);
            if(uri==null) {
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
                uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(uri==null)throw new java.io.IOException("Downloads insert returned null");prefs.edit().putString(key,uri.toString()).apply();
            }
            try(OutputStream out=c.getContentResolver().openOutputStream(uri,"wt")) {
                if(out==null)throw new java.io.IOException("Downloads output unavailable");out.write(bytes);
            }
        }catch(Exception e){Log.w(TAG,"Diagnostic export failed (app-specific copy may still exist)",e);}
    }
    private FractionScrollDiagnostics(){}
}
