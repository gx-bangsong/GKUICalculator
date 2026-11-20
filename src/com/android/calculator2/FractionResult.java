/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/** Owns only the auxiliary view/job. No evaluator writes, history writes, or editable text. */
final class FractionResult {
    private final View viewport;
    private final TextView text;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> pending;
    private int generation;
    private String complete;
    FractionResult(Calculator activity,TextView preview) {
        viewport=activity.findViewById(R.id.fraction_viewport);text=activity.findViewById(R.id.fraction_result);
        // Copy the unanimated preview typography once, not RESULT scale/translation/color.
        text.setTypeface(preview.getTypeface());text.setTextSize(TypedValue.COMPLEX_UNIT_PX,preview.getTextSize());
        text.setLetterSpacing(preview.getLetterSpacing());text.setFontFeatureSettings(preview.getFontFeatureSettings());
        text.setTextColor(preview.getTextColors());text.setGravity(preview.getGravity());text.setTextAlignment(preview.getTextAlignment());
        text.setLineSpacing(preview.getLineSpacingExtra(),preview.getLineSpacingMultiplier());
        text.setKeyListener(null);text.setCursorVisible(false);text.setFocusable(false);
        text.setOnLongClickListener(v -> {
            if(complete==null)return false;
            activity.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("",complete));
            Toast.makeText(activity,R.string.fraction_copied,Toast.LENGTH_SHORT).show();return true;
        });
    }
    void clear() {
        generation++;if(pending!=null)pending.cancel(true);pending=null;complete=null;
        text.setText("");viewport.setVisibility(View.GONE);
    }
    void show(UnifiedReal value,BooleanSupplier stillCurrent) {
        clear();final int request=generation;
        pending=worker.submit(() -> {
            String fraction=null;boolean tooLong=false;
            try {fraction=ExactFraction.format(value);}catch(ExactFraction.TooLong e){tooLong=true;}
            if(Thread.currentThread().isInterrupted())return;
            final String exact=fraction;final boolean limited=tooLong;
            text.post(() -> {
                if(request!=generation||!stillCurrent.getAsBoolean())return;
                complete=exact;
                if(exact==null&&!limited)return;
                text.setText(limited?text.getContext().getString(R.string.fraction_too_long):exact);
                viewport.scrollTo(0,0);viewport.setVisibility(View.VISIBLE);
            });
        });
    }
    void close(){clear();worker.shutdownNow();}
}
