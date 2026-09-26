/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calculator2.tools.kinship.RelationshipData;
import com.android.calculator2.tools.kinship.RelationshipEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Chinese kinship (亲戚称呼) calculator: turns a chain of relationship steps such as
 * 爸爸的哥哥的儿子 into the terms used to address that relative (堂哥 / 堂弟).
 * <p>
 * Resolution itself lives in {@link RelationshipEngine}, which follows the reference
 * implementation of this problem,
 * <a href="https://github.com/mumuy/relationship">mumuy/relationship</a> (MIT — the engine behind
 * MIUI's calculator). Because the reference resolves a chain by rewriting it and then looking the
 * result up in a relation table, this tool covers the same ground it does — including chains that
 * nobody can name exactly, which come back as several terms (爸爸的儿子的儿子 is 侄子 or 儿子), and
 * chains that name nobody at all (爸爸的丈夫), which come back empty.
 * <p>
 * This class owns the parts that belong to the app: the pad's step vocabulary, the wording shown
 * for a chain, and the two regional wordings.
 */
public final class RelationshipCalculator {

    /**
     * Regional vocabulary. Only a handful of terms differ, and they follow the same split the
     * reference uses for its 北方 locale: the north says 姥爷、姥姥、大爷、大娘、舅姥爷、姨姥姥, while
     * 外公、外婆、伯父、伯母 are the common forms used everywhere else — they are what the south says,
     * so they are what "South China version" selects.
     */
    public enum Dialect {
        /** 北方: 姥爷、姥姥、大爷、大娘、大姥爷、小姥爷、姑姥姥、舅姥爷、姨姥姥… */
        NORTH,
        /** 通用（南方）: 外公、外婆、爷爷、奶奶、伯父、伯母… */
        SOUTH
    }

    /** One atomic move from one person to the next. */
    public enum Step {
        FATHER,
        MOTHER,
        ELDER_BROTHER,
        YOUNGER_BROTHER,
        ELDER_SISTER,
        YOUNGER_SISTER,
        HUSBAND,
        WIFE,
        SON,
        DAUGHTER
    }

    /**
     * The pad keys: the ten atomic steps plus six shortcuts for the relatives that are looked up
     * most often. A shortcut simply expands to the atomic chain it stands for, so the resolver
     * only ever deals with atomic steps.
     * <p>
     * Labels are a single character: a two-character CJK label does not fit a pad button next to
     * the Material button paddings. The full word is kept as the key's content description.
     */
    public enum Key {
        FATHER("父", "爸爸", Step.FATHER),
        MOTHER("母", "妈妈", Step.MOTHER),
        ELDER_BROTHER("兄", "哥哥", Step.ELDER_BROTHER),
        YOUNGER_BROTHER("弟", "弟弟", Step.YOUNGER_BROTHER),
        ELDER_SISTER("姐", "姐姐", Step.ELDER_SISTER),
        YOUNGER_SISTER("妹", "妹妹", Step.YOUNGER_SISTER),
        HUSBAND("夫", "丈夫", Step.HUSBAND),
        WIFE("妻", "妻子", Step.WIFE),
        SON("子", "儿子", Step.SON),
        DAUGHTER("女", "女儿", Step.DAUGHTER),
        PATERNAL_GRANDFATHER("爷", "爷爷", Step.FATHER, Step.FATHER),
        PATERNAL_GRANDMOTHER("奶", "奶奶", Step.FATHER, Step.MOTHER),
        MATERNAL_UNCLE("舅", "舅舅", Step.MOTHER, Step.ELDER_BROTHER),
        MATERNAL_AUNT("姨", "姨妈", Step.MOTHER, Step.ELDER_SISTER),
        PATERNAL_AUNT("姑", "姑姑", Step.FATHER, Step.YOUNGER_SISTER),
        PATERNAL_UNCLE("叔", "叔叔", Step.FATHER, Step.YOUNGER_BROTHER);

        /** Single character shown on the pad. */
        @NonNull
        public final String label;

        /** Full word, used as the key's content description. */
        @NonNull
        public final String description;

        @NonNull
        private final Step[] mSteps;

        Key(@NonNull String label, @NonNull String description, @NonNull Step... steps) {
            this.label = label;
            this.description = description;
            mSteps = steps;
        }

        /** The atomic steps this key stands for. */
        @NonNull
        public List<Step> steps() {
            return Collections.unmodifiableList(Arrays.asList(mSteps));
        }
    }

    /** A resolved relationship: every term that applies, in the reference's own order. */
    public static final class Answer {

        /** Empty when the chain names nobody. */
        @NonNull
        public final List<String> terms;

        Answer(@NonNull List<String> terms) {
            this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
        }

        /** True when the chain is ambiguous and every term is worth showing. */
        public boolean isAmbiguous() {
            return terms.size() > 1;
        }

        @Override
        @NonNull
        public String toString() {
            return "Answer" + terms;
        }
    }

    private static final Answer EMPTY = new Answer(Collections.<String>emptyList());

    private RelationshipCalculator() {
    }

    /**
     * Resolve a chain.
     *
     * @param data    parsed relation tables; {@code null} yields an empty answer
     * @param chain   relationship steps, starting from the user
     * @param dialect regional vocabulary
     * @param reverse {@code true} for 互查: answer "what does that person call me?"
     */
    @NonNull
    public static Answer resolve(@Nullable RelationshipData data, @NonNull List<Step> chain,
            @NonNull Dialect dialect, boolean reverse) {
        if (data == null || chain.isEmpty()) {
            return EMPTY;
        }
        final RelationshipEngine engine = RelationshipEngine.get(data,
                dialect == Dialect.NORTH ? "north" : null);
        if (engine == null) {
            return EMPTY;
        }
        return new Answer(engine.resolve(tokens(chain), reverse));
    }

    /** "爸爸的哥哥" for {@code [FATHER, ELDER_BROTHER]}; empty for an empty chain. */
    @NonNull
    public static String chainText(@NonNull List<Step> chain) {
        final StringBuilder sb = new StringBuilder();
        for (Step step : chain) {
            if (sb.length() > 0) {
                sb.append("的");
            }
            sb.append(word(step));
        }
        return sb.toString();
    }

    /**
     * Compact rendering of a chain: the single character for each step, joined with 的
     * ("父的兄的子"). Used instead of {@link #chainText} once a chain is long enough that the
     * full words no longer fit the formula line.
     */
    @NonNull
    public static String compactText(@NonNull List<Step> chain) {
        final StringBuilder sb = new StringBuilder();
        for (Step step : chain) {
            if (sb.length() > 0) {
                sb.append("的");
            }
            sb.append(shortWord(step));
        }
        return sb.toString();
    }

    /** Single character for one step (父, 兄, 子, …), matching the pad key that enters it. */
    @NonNull
    public static String shortWord(@NonNull Step step) {
        switch (step) {
            case FATHER:
                return "父";
            case MOTHER:
                return "母";
            case ELDER_BROTHER:
                return "兄";
            case YOUNGER_BROTHER:
                return "弟";
            case ELDER_SISTER:
                return "姐";
            case YOUNGER_SISTER:
                return "妹";
            case HUSBAND:
                return "夫";
            case WIFE:
                return "妻";
            case SON:
                return "子";
            case DAUGHTER:
            default:
                return "女";
        }
    }

    /** The word used for one step inside a chain (爸爸, 哥哥, …). */
    @NonNull
    public static String word(@NonNull Step step) {
        switch (step) {
            case FATHER:
                return "爸爸";
            case MOTHER:
                return "妈妈";
            case ELDER_BROTHER:
                return "哥哥";
            case YOUNGER_BROTHER:
                return "弟弟";
            case ELDER_SISTER:
                return "姐姐";
            case YOUNGER_SISTER:
                return "妹妹";
            case HUSBAND:
                return "丈夫";
            case WIFE:
                return "妻子";
            case SON:
                return "儿子";
            case DAUGHTER:
            default:
                return "女儿";
        }
    }

    /** Relationship steps as the token names used by the relation tables. */
    @NonNull
    private static List<String> tokens(@NonNull List<Step> chain) {
        final List<String> tokens = new ArrayList<>(chain.size());
        for (Step step : chain) {
            tokens.add(token(step));
        }
        return tokens;
    }

    @NonNull
    private static String token(@NonNull Step step) {
        switch (step) {
            case FATHER:
                return "f";
            case MOTHER:
                return "m";
            case ELDER_BROTHER:
                return "ob";
            case YOUNGER_BROTHER:
                return "lb";
            case ELDER_SISTER:
                return "os";
            case YOUNGER_SISTER:
                return "ls";
            case HUSBAND:
                return "h";
            case WIFE:
                return "w";
            case SON:
                return "s";
            case DAUGHTER:
            default:
                return "d";
        }
    }
}
