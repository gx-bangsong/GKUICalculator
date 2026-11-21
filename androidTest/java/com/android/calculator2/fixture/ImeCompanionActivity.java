/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2.fixture;

/** Test-only separate task, never included in the production APK. */
public class ImeCompanionActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.EditText input=new android.widget.EditText(this);
        input.setHint("Companion text input");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        setContentView(input);input.requestFocus();
        input.post(() -> getSystemService(android.view.inputmethod.InputMethodManager.class)
                .showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));
    }
}
