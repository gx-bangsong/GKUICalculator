/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import android.os.Bundle;
import android.text.Selection;
import android.text.Spannable;
import android.view.View;
import android.widget.TextView;

/** Configuration-state helpers only; never used to reposition an ordinary key press. */
public final class ToolState {
    private ToolState() {}
    public static Bundle selection(View view) {
        Bundle state=new Bundle();
        if(view instanceof TextView) {
            TextView text=(TextView)view;
            state.putInt("start",text.getSelectionStart());state.putInt("end",text.getSelectionEnd());
            state.putBoolean("focused",text.hasFocus());
        }
        return state;
    }
    public static void restoreSelection(View view,Bundle state) {
        if(!(view instanceof TextView)||state==null) return;
        TextView text=(TextView)view;
        int start=state.getInt("start",-1),end=state.getInt("end",-1);
        if(start<0||end<0||!(text.getText() instanceof Spannable)) return;
        if(state.getBoolean("focused")) text.requestFocus();
        Selection.setSelection((Spannable)text.getText(),Math.min(start,text.length()),Math.min(end,text.length()));
    }
}
