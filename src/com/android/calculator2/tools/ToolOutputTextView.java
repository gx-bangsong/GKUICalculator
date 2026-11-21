/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

/** A selectable/copyable result, never an editable buffer or an IME target. */
public class ToolOutputTextView extends AppCompatTextView {
    public ToolOutputTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setKeyListener(null);
        setTextIsSelectable(true);
        setCursorVisible(false);
    }
    @Override public boolean onTextContextMenuItem(int id) {
        if (id != android.R.id.copy && id != android.R.id.selectAll) return false;
        return super.onTextContextMenuItem(id);
    }
}
