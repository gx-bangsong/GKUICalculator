/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.motion.widget.MotionLayout;

public class DisplayMotionLayout extends MotionLayout {
    private int mPointerId;
    private boolean mIsScrolling;
    private PointF mPreviousPoint;
    private MotionEvent mPreviousEvent;
    private final int mTouchSlop;
    private boolean mOutOfBounds;
    private boolean mHistoryDocked;

    void setHistoryDocked(boolean value) {
        mHistoryDocked=value;
        setInteractionEnabled(!value);
        mPointerId=-1;mIsScrolling=false;clearLastMotion();
    }

    public DisplayMotionLayout(@NonNull Context context) {
        this(context, null);
    }

    public DisplayMotionLayout(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public DisplayMotionLayout(@NonNull Context context, @Nullable AttributeSet attributeSet,
                               int i) {
        super(context, attributeSet, i);
        mPointerId = -1;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        FractionScrollDiagnostics.event(this,"root.dispatch.in",event,null);
        boolean handled=super.dispatchTouchEvent(event);
        FractionScrollDiagnostics.event(this,"root.dispatch.out",event,handled);
        return handled;
    }

    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        FractionScrollDiagnostics.disallow(this,"root",disallow);
        super.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        FractionScrollDiagnostics.event(this,"root.touch.in",event,null);
        boolean handled=super.onTouchEvent(event);
        FractionScrollDiagnostics.event(this,"root.touch.out",event,handled);
        return handled;
    }

    @Override
    public final boolean onInterceptTouchEvent(@NonNull MotionEvent motionEvent) {
        boolean intercept=interceptDisplayTouchEvent(motionEvent);
        FractionScrollDiagnostics.event(this,"root.intercept",motionEvent,intercept);
        return intercept;
    }

    private boolean interceptDisplayTouchEvent(@NonNull MotionEvent motionEvent) {
        if(mHistoryDocked)return false;
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // Protect interactions INSIDE the tool controls, not the entire display.
                // The history handle is a sibling below that slot and must still start a drag.
                View controlSlot = findViewWithTag("field_tool_viewport");
                if(controlSlot==null)controlSlot=findViewById(R.id.tool_control_slot);
                // getGlobalVisibleRect is ROOT-view-relative, not device-screen-relative.
                // Convert the MotionLayout-local event to that same root coordinate system.
                // The origin already accounts for all ancestors; self scroll is excluded from
                // event viewport coordinates but included in getGlobalVisibleRect's offset.
                Rect controls = new Rect(), parentVisible = new Rect();
                Point origin = new Point();
                boolean hasBounds=controlSlot!=null&&controlSlot.getVisibility()==View.VISIBLE
                    &&controlSlot.getGlobalVisibleRect(controls)
                    &&getGlobalVisibleRect(parentVisible,origin);
                float rootX=motionEvent.getX()+origin.x+getScrollX();
                float rootY=motionEvent.getY()+origin.y+getScrollY();
                boolean inside=hasBounds&&controls.contains((int)rootX,(int)rootY);
                if(hasBounds&&(getContext().getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0) {
                    int[] rootScreen=new int[2];getRootView().getLocationOnScreen(rootScreen);
                    android.util.Log.i("ToolTouchCoordinates","DOWN rootVisible="+controls+" rootScreen="+rootScreen[0]+","+rootScreen[1]
                        +" raw="+motionEvent.getRawX()+","+motionEvent.getRawY()+" rootPoint="+rootX+","+rootY+" rootHit="+inside);
                }
                if (inside) {
                    mPointerId = -1;
                    mIsScrolling = false;
                    mOutOfBounds = true;
                    clearLastMotion();
                    return false;
                }
                Rect hitRect = new Rect();

                findViewById(R.id.display).getHitRect(hitRect);
                if (hitRect.contains((int) motionEvent.getX(), (int) motionEvent.getY())) {
                    mPointerId = motionEvent.getPointerId(0);
                    mIsScrolling = false;
                    mOutOfBounds = false;
                    saveLastMotion(motionEvent);
                } else {
                    mPointerId = -1;
                    mIsScrolling = false;
                    mOutOfBounds = true;
                    clearLastMotion();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                mIsScrolling = false;
                mOutOfBounds = false;
                mPointerId = -1;
                clearLastMotion();
                break;
            case MotionEvent.ACTION_MOVE:
                if (mPointerId != -1 && !mOutOfBounds && mPreviousPoint != null) {
                    int pointerIndex = motionEvent.findPointerIndex(mPointerId);
                    if (pointerIndex != -1) {
                        float y = Math.abs(motionEvent.getY(pointerIndex) - mPreviousPoint.y);
                        if (y > mTouchSlop) {
                            mIsScrolling = true;
                            onTouchEvent(mPreviousEvent);
                        }
                    }
                }
                break;
        }
        // Keep the complete gesture with the child after a DOWN inside its controls.
        // MotionLayout's own OnSwipe must not intercept a later MOVE behind our gate.
        if (mOutOfBounds) return false;
        if (super.onInterceptTouchEvent(motionEvent)) {
            return true;
        }
        return mIsScrolling & !mOutOfBounds;
    }

    private void saveLastMotion(@NonNull MotionEvent motionEvent) {
        if (mPreviousEvent != null) {
            mPreviousEvent.recycle();
        }
        mPreviousEvent = MotionEvent.obtain(motionEvent);
        int pointerIndex = motionEvent.findPointerIndex(mPointerId);
        if (pointerIndex != -1) {
            if (mPreviousPoint == null) {
                mPreviousPoint = new PointF();
            }
            mPreviousPoint.set(motionEvent.getX(pointerIndex), motionEvent.getY(pointerIndex));
        }
    }

    private void clearLastMotion()
    {
        if (mPreviousEvent != null) {
            mPreviousEvent.recycle();
            mPreviousEvent = null;
        }
        mPreviousPoint = null;
    }
}
