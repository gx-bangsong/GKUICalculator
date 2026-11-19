/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.tools;

import android.content.Context;
import android.util.AttributeSet;

/** Programmer result: copying may end selection mode, but must never restore an input caret. */
public final class ProgrammerOutputTextView extends ToolOutputTextView {
    public ProgrammerOutputTextView(Context context, AttributeSet attrs) { super(context,attrs); }

    @Override public void setCursorVisible(boolean visible) { super.setCursorVisible(false); }
}
