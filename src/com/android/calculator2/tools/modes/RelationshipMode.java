/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.modes;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.R;
import com.android.calculator2.tools.ToolHost;
import com.android.calculator2.tools.ToolId;
import com.android.calculator2.tools.ToolMode;
import com.android.calculator2.tools.model.RelationshipCalculator;
import com.android.calculator2.tools.model.RelationshipCalculator.Answer;
import com.android.calculator2.tools.model.RelationshipCalculator.Dialect;
import com.android.calculator2.tools.model.RelationshipCalculator.Hint;
import com.android.calculator2.tools.model.RelationshipCalculator.Key;
import com.android.calculator2.tools.model.RelationshipCalculator.Step;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chinese kinship tool (亲戚称呼): the pad turns into a wall of relationship nouns, the formula
 * line shows the chain that was entered, and the result line shows how to address that relative.
 * <p>
 * Pad layout while this tool is active ({@link #padMapping} owns the assignment). Every key is a
 * single character so the label fits the button; its content description is the full word:
 *
 * <pre>
 *   AC   舅  姨  爷
 *   父   母  兄  奶
 *   弟   姐  妹  姑
 *   子   女  夫  叔
 *   妻  互查 del  =
 * </pre>
 * <p>
 * "互查" (the decimal-point key) flips the question around: instead of "what do I call them?" it
 * answers "what do they call me?". Both questions may have more than one answer (older/younger
 * brother, or the unknown sex of the user); in that case every term is shown at once.
 */
public final class RelationshipMode implements ToolMode {

    private static final String PREFS_NAME = "calc_tools";
    private static final String PREF_DIALECT = "relationship_dialect";

    /** Guard so a long chain cannot push the display into an unreadable state. */
    private static final int MAX_STEPS = 10;

    private static final int[] DIGIT_IDS = {
            R.id.digit_0, R.id.digit_1, R.id.digit_2, R.id.digit_3, R.id.digit_4,
            R.id.digit_5, R.id.digit_6, R.id.digit_7, R.id.digit_8, R.id.digit_9
    };

    private static final Map<Integer, Key> PAD_KEYS = padMapping();

    private final List<Step> mChain = new ArrayList<>();
    private boolean mReverse;
    @NonNull
    private Dialect mDialect = Dialect.NORTH;
    @Nullable
    private ToolHost mHost;

    /**
     * Pad key id to relationship key. The activity uses the same map (in this order) to relabel
     * the pad, so the label a user sees always matches the step that is entered.
     */
    @NonNull
    public static Map<Integer, Key> padMapping() {
        final Map<Integer, Key> map = new LinkedHashMap<>();
        map.put(R.id.paren, Key.MATERNAL_UNCLE);            // 舅 — 舅舅
        map.put(R.id.op_pct, Key.MATERNAL_AUNT);            // 姨 — 姨妈
        map.put(R.id.op_div, Key.PATERNAL_GRANDFATHER);     // 爷 — 爷爷
        map.put(R.id.op_mul, Key.PATERNAL_GRANDMOTHER);     // 奶 — 奶奶
        map.put(R.id.op_sub, Key.PATERNAL_AUNT);            // 姑 — 姑姑
        map.put(R.id.op_add, Key.PATERNAL_UNCLE);           // 叔 — 叔叔
        map.put(R.id.digit_7, Key.FATHER);                  // 父 — 爸爸
        map.put(R.id.digit_8, Key.MOTHER);                  // 母 — 妈妈
        map.put(R.id.digit_9, Key.ELDER_BROTHER);           // 兄 — 哥哥
        map.put(R.id.digit_4, Key.YOUNGER_BROTHER);         // 弟 — 弟弟
        map.put(R.id.digit_5, Key.ELDER_SISTER);            // 姐 — 姐姐
        map.put(R.id.digit_6, Key.YOUNGER_SISTER);          // 妹 — 妹妹
        map.put(R.id.digit_1, Key.SON);                     // 子 — 儿子
        map.put(R.id.digit_2, Key.DAUGHTER);                // 女 — 女儿
        map.put(R.id.digit_3, Key.HUSBAND);                 // 夫 — 丈夫
        map.put(R.id.digit_0, Key.WIFE);                    // 妻 — 妻子
        return map;
    }

    @NonNull
    @Override
    public String getId() {
        return ToolId.RELATIONSHIP;
    }

    @Override
    public int getNameRes() {
        return R.string.tool_relationship;
    }

    @Override
    public int getIconRes() {
        return 0;
    }

    @Override
    public boolean wantsExpandedDisplay() {
        // The chain and its terms need the room; this also hides the scientific pad.
        return true;
    }

    /** Regional vocabulary used for 爷爷/阿公, 外公/姥爷, … Currently selected in the menu. */
    @NonNull
    public Dialect getDialect() {
        return mDialect;
    }

    /** Switch between the North and South China vocabulary and refresh the display. */
    public void setDialect(@NonNull Dialect dialect) {
        if (mDialect == dialect) {
            return;
        }
        mDialect = dialect;
        if (mHost != null) {
            mHost.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(PREF_DIALECT, dialect.name()).apply();
        }
        redisplay();
    }

    @Override
    public void onActivate(@NonNull ToolHost host, @Nullable String carryValue) {
        mHost = host;
        mChain.clear();
        mReverse = false;
        mDialect = readDialect(host.getContext());
        host.setRelationshipPadMode(true, false);
        host.setToolResultTextSizeSp(32f);
        redisplay();
    }

    @Override
    public void onDeactivate(@NonNull ToolHost host) {
        host.setRelationshipPadMode(false, false);
        mHost = null;
    }

    @Override
    public boolean onPadKey(int viewId) {
        final Key key = PAD_KEYS.get(viewId);
        if (key != null) {
            append(key);
            return true;
        }
        if (viewId == R.id.dec_point) {
            setReverse(!mReverse);
            return true;
        }
        if (viewId == R.id.clr) {
            onClear();
            return true;
        }
        if (viewId == R.id.del) {
            onDelete();
            return true;
        }
        // "=" re-runs the look-up; every other key is meaningless here and is swallowed.
        if (viewId == R.id.eq) {
            redisplay();
        }
        return true;
    }

    @Override
    public void onDigit(int digit) {
        if (digit >= 0 && digit < DIGIT_IDS.length) {
            final Key key = PAD_KEYS.get(DIGIT_IDS[digit]);
            if (key != null) {
                append(key);
            }
        }
    }

    @Override
    public void onDecimalPoint() {
        setReverse(!mReverse);
    }

    @Override
    public void onDelete() {
        if (!mChain.isEmpty()) {
            mChain.remove(mChain.size() - 1);
            redisplay();
        }
    }

    @Override
    public void onClear() {
        mChain.clear();
        mReverse = false;
        if (mHost != null) {
            mHost.setRelationshipPadMode(true, false);
        }
        redisplay();
    }

    private void append(@NonNull Key key) {
        final List<Step> steps = key.steps();
        if (mChain.size() + steps.size() > MAX_STEPS) {
            return;
        }
        mChain.addAll(steps);
        redisplay();
    }

    private void setReverse(boolean reverse) {
        mReverse = reverse;
        if (mHost != null) {
            mHost.setRelationshipPadMode(true, mReverse);
        }
        redisplay();
    }

    private void redisplay() {
        final ToolHost host = mHost;
        if (host == null) {
            return;
        }
        final Context context = host.getContext();
        if (mChain.isEmpty()) {
            host.setToolFormula("");
            host.setToolResult(context.getString(R.string.relationship_empty));
            return;
        }
        final Answer answer = RelationshipCalculator.resolve(mChain, mDialect, mReverse);
        final String chain = RelationshipCalculator.chainText(mChain);
        final String formula = mReverse
                ? context.getString(R.string.relationship_reverse_formula, chain)
                : chain;
        host.setToolFormula(withHint(context, formula, answer));
        host.setToolResult(join(answer.terms));
    }

    @NonNull
    private static String withHint(@NonNull Context context, @NonNull String formula,
            @NonNull Answer answer) {
        // The hint text names both terms, so it only fits a two-way split. A chain that is
        // ambiguous in two ways at once (say a cousin whose sex and age are both unknown) simply
        // lists every term without extra explanation.
        if (answer.hint == Hint.NONE || answer.terms.size() != 2) {
            return formula;
        }
        final String hint = answer.hint == Hint.AGE
                ? context.getString(R.string.relationship_hint_age,
                        answer.terms.get(0), answer.terms.get(1))
                : context.getString(R.string.relationship_hint_sex,
                        answer.terms.get(0), answer.terms.get(1));
        return formula + "（" + hint + "）";
    }

    /** Every applicable term, side by side: "堂兄 / 堂弟". */
    @NonNull
    private static String join(@NonNull List<String> terms) {
        final StringBuilder sb = new StringBuilder();
        for (String term : terms) {
            if (sb.length() > 0) {
                sb.append("  /  ");
            }
            sb.append(term);
        }
        return sb.toString();
    }

    @NonNull
    private static Dialect readDialect(@NonNull Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
        final String stored = prefs.getString(PREF_DIALECT, Dialect.NORTH.name());
        if (Dialect.SOUTH.name().equals(stored)) {
            return Dialect.SOUTH;
        }
        return Dialect.NORTH;
    }
}
