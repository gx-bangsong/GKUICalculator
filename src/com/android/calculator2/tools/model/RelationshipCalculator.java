/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Chinese kinship calculator (亲戚关系计算): resolves a chain of relationship steps such as
 * "爸爸的哥哥的儿子" into the term used to address that relative ("堂兄" / "堂弟").
 * <p>
 * The engine is pure: it knows nothing about Android, the display, or the pad. A chain is a list
 * of atomic {@link Step}s read left to right, starting from the user; every step moves to a person
 * related to the previous one ("爸爸的哥哥" is {@code [FATHER, ELDER_BROTHER]}).
 * <p>
 * Terms are Chinese words by nature, so they live here as literals instead of string resources:
 * there is no meaningful translation for 伯父 / 堂兄, and keeping them next to the rules keeps a
 * single source of truth for both the result line and the pad labels.
 * <p>
 * Two kinds of ambiguity are reported instead of being guessed:
 * <ul>
 *   <li>{@link Hint#AGE}: the term depends on whether the relative is older or younger than the
 *       user (伯父的哥哥 is 堂兄, 伯父的弟弟 is 堂弟, but "伯父的儿子" can be either).</li>
 *   <li>{@link Hint#SEX}: in reverse (互查) mode the term depends on the sex of the user, which
 *       the tool does not know (伯父 calls a man 侄子 and a woman 侄女).</li>
 * </ul>
 * Callers show every returned term at once; never pick one arbitrarily.
 */
public final class RelationshipCalculator {

    /** Regional vocabulary. Only a handful of terms differ between the two regions. */
    public enum Dialect {
        /** 北方话: 爷爷、奶奶、外公、外婆… */
        NORTH,
        /** 南方话: 阿公、阿嬷、姥爷、姥姥… */
        SOUTH
    }

    /** Why a result contains more than one term. */
    public enum Hint {
        /** A single, unambiguous term. */
        NONE,
        /** The terms differ by whether the relative is older or younger than the user. */
        AGE,
        /** The terms differ by the sex of the user (reverse look-up only). */
        SEX
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

    private enum Sex {
        MALE,
        FEMALE
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

    /** A resolved relationship: one or more terms plus the reason there is more than one. */
    public static final class Answer {

        /** Every term that applies, in a stable order. Never empty for a non-empty chain. */
        @NonNull
        public final List<String> terms;

        @NonNull
        public final Hint hint;

        Answer(@NonNull List<String> terms, @NonNull Hint hint) {
            this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
            this.hint = hint;
        }

        @Override
        @NonNull
        public String toString() {
            return "Answer" + terms;
        }
    }

    /** Name used for the user themselves, e.g. for "丈夫的妻子". */
    private static final String SELF = "我";

    private static final String[] ANCESTOR_PREFIXES = {"曾", "高", "天", "烈", "太", "远", "鼻"};
    private static final String[] DESCENDANT_PREFIXES = {"曾", "玄", "来", "晜", "仍", "云", "耳"};

    private RelationshipCalculator() {
    }

    /**
     * Resolve the chain.
     *
     * @param chain   relationship steps, starting from the user
     * @param dialect regional vocabulary
     * @param reverse {@code true} for 互查: answer "what does that person call me?" instead of
     *                "what do I call that person?"
     */
    @NonNull
    public static Answer resolve(@NonNull List<Step> chain, @NonNull Dialect dialect,
            boolean reverse) {
        if (chain.isEmpty()) {
            return new Answer(Collections.<String>emptyList(), Hint.NONE);
        }
        if (!reverse) {
            return resolveForward(chain, dialect);
        }
        final List<List<Step>> candidates = inverted(chain);
        final List<String> terms = new ArrayList<>();
        Hint hint = Hint.NONE;
        for (List<Step> candidate : candidates) {
            final Answer answer = resolveForward(candidate, dialect);
            for (String term : answer.terms) {
                if (!terms.contains(term)) {
                    terms.add(term);
                }
            }
            if (answer.hint != Hint.NONE) {
                hint = answer.hint;
            }
        }
        // Several candidates means the term depends on the sex of the user, which is unknown.
        if (candidates.size() > 1) {
            hint = Hint.SEX;
        }
        return new Answer(terms, hint);
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

    /**
     * Walk the chain backwards: the chains that describe the user from the last person's point of
     * view. Every step except the last one is unambiguous, because the intermediate people's sexes
     * follow from the steps themselves; the last step depends on the sex of the user, so two
     * chains are returned whenever that matters.
     */
    @NonNull
    public static List<List<Step>> inverted(@NonNull List<Step> chain) {
        final int size = chain.size();
        final List<Step> head = new ArrayList<>();
        for (int i = size - 1; i >= 1; i--) {
            head.add(invertStep(chain.get(i), sexOf(chain.get(i - 1))));
        }
        final Step first = chain.get(0);
        if (first == Step.HUSBAND || first == Step.WIFE) {
            // "我的丈夫" / "我的妻子" already fixes the sex of the user.
            final List<Step> single = new ArrayList<>(head);
            single.add(first == Step.HUSBAND ? Step.WIFE : Step.HUSBAND);
            return Collections.singletonList(single);
        }
        final List<List<Step>> out = new ArrayList<>();
        for (Sex sex : Sex.values()) {
            final List<Step> candidate = new ArrayList<>(head);
            candidate.add(invertStep(first, sex));
            out.add(candidate);
        }
        return out;
    }

    // ---- Forward resolution ---------------------------------------------------------------

    @NonNull
    private static Answer resolveForward(@NonNull List<Step> chain, @NonNull Dialect dialect) {
        final int size = chain.size();
        if (size == 1) {
            return one(word(chain.get(0)));
        }
        if (size == 2) {
            final Answer pair = pair(chain.get(0), chain.get(1), dialect);
            if (pair != null) {
                return pair;
            }
        } else if (size == 3) {
            final Answer triple = triple(chain.get(0), chain.get(1), chain.get(2), dialect);
            if (triple != null) {
                return triple;
            }
        } else if (size == 4) {
            final Answer quad = quad(chain, dialect);
            if (quad != null) {
                return quad;
            }
        }
        if (isAncestorChain(chain)) {
            return one(ancestorTerm(chain, dialect));
        }
        if (isDescendantChain(chain)) {
            return one(descendantTerm(chain));
        }
        return composed(chain, dialect);
    }

    /** Two-step chains: parents, parents' siblings, spouses, children, and in-laws. */
    private static Answer pair(Step a, Step b, Dialect dialect) {
        switch (a) {
            case FATHER:
                switch (b) {
                    case FATHER:
                        return one(grandfather(false, dialect));
                    case MOTHER:
                        return one(grandmother(false, dialect));
                    case ELDER_BROTHER:
                        return one("伯父");
                    case YOUNGER_BROTHER:
                        return one("叔叔");
                    case ELDER_SISTER:
                        return one("姑妈");
                    case YOUNGER_SISTER:
                        return one("姑姑");
                    case WIFE:
                        return one("妈妈");
                    case SON:
                        return two("哥哥", "弟弟", Hint.AGE);
                    case DAUGHTER:
                        return two("姐姐", "妹妹", Hint.AGE);
                    default:
                        return null;
                }
            case MOTHER:
                switch (b) {
                    case FATHER:
                        return one(grandfather(true, dialect));
                    case MOTHER:
                        return one(grandmother(true, dialect));
                    case ELDER_BROTHER:
                    case YOUNGER_BROTHER:
                        return one("舅舅");
                    case ELDER_SISTER:
                    case YOUNGER_SISTER:
                        return one("姨妈");
                    case HUSBAND:
                        return one("爸爸");
                    case SON:
                        return two("哥哥", "弟弟", Hint.AGE);
                    case DAUGHTER:
                        return two("姐姐", "妹妹", Hint.AGE);
                    default:
                        return null;
                }
            case ELDER_BROTHER:
            case YOUNGER_BROTHER:
                switch (b) {
                    case WIFE:
                        return one(a == Step.ELDER_BROTHER ? "嫂子" : "弟媳");
                    case SON:
                        return one("侄子");
                    case DAUGHTER:
                        return one("侄女");
                    default:
                        return null;
                }
            case ELDER_SISTER:
            case YOUNGER_SISTER:
                switch (b) {
                    case HUSBAND:
                        return one(a == Step.ELDER_SISTER ? "姐夫" : "妹夫");
                    case SON:
                        return one("外甥");
                    case DAUGHTER:
                        return one("外甥女");
                    default:
                        return null;
                }
            case HUSBAND:
                switch (b) {
                    case FATHER:
                        return one("公公");
                    case MOTHER:
                        return one("婆婆");
                    case ELDER_BROTHER:
                        return one("大伯子");
                    case YOUNGER_BROTHER:
                        return one("小叔子");
                    case ELDER_SISTER:
                        return one("大姑子");
                    case YOUNGER_SISTER:
                        return one("小姑子");
                    case WIFE:
                        return one(SELF);
                    case SON:
                        return one("儿子");
                    case DAUGHTER:
                        return one("女儿");
                    default:
                        return null;
                }
            case WIFE:
                switch (b) {
                    case FATHER:
                        return one("岳父");
                    case MOTHER:
                        return one("岳母");
                    case ELDER_BROTHER:
                        return one("大舅子");
                    case YOUNGER_BROTHER:
                        return one("小舅子");
                    case ELDER_SISTER:
                        return one("大姨子");
                    case YOUNGER_SISTER:
                        return one("小姨子");
                    case HUSBAND:
                        return one(SELF);
                    case SON:
                        return one("儿子");
                    case DAUGHTER:
                        return one("女儿");
                    default:
                        return null;
                }
            case SON:
                switch (b) {
                    case SON:
                        return one("孙子");
                    case DAUGHTER:
                        return one("孙女");
                    case WIFE:
                        return one("儿媳");
                    case FATHER:
                        return one(SELF);
                    case MOTHER:
                        return one("妻子");
                    default:
                        return null;
                }
            case DAUGHTER:
                switch (b) {
                    case SON:
                        return one("外孙");
                    case DAUGHTER:
                        return one("外孙女");
                    case HUSBAND:
                        return one("女婿");
                    case FATHER:
                    case MOTHER:
                        return one(SELF);
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    /** Three-step chains: cousins, uncles' spouses, grandparents' siblings, grand-nephews. */
    private static Answer triple(Step a, Step b, Step c, Dialect dialect) {
        final boolean paternal = a == Step.FATHER;
        if (isParent(a) && isSibling(b)) {
            final boolean brother = isBrother(b);
            if (isSpouse(c)) {
                if (paternal) {
                    return one(brother ? (b == Step.ELDER_BROTHER ? "伯母" : "婶婶") : "姑父");
                }
                return one(brother ? "舅妈" : "姨父");
            }
            if (isChild(c)) {
                // 堂 for the children of a father's brother, 表 for everybody else.
                final String prefix = paternal && brother ? "堂" : "表";
                return two(prefix + (c == Step.SON ? "兄" : "姐"),
                        prefix + (c == Step.SON ? "弟" : "妹"), Hint.AGE);
            }
            if (isParent(c)) {
                // 伯父的爸爸 is 爷爷; 舅舅的爸爸 is 外公.
                return one(c == Step.MOTHER
                        ? grandmother(!paternal, dialect)
                        : grandfather(!paternal, dialect));
            }
            return null;
        }
        if (isParent(a) && isParent(b) && isSibling(c)) {
            // Grandparents' siblings.
            if (a == Step.FATHER && b == Step.FATHER) {
                if (c == Step.ELDER_BROTHER) {
                    return one("伯祖父");
                }
                if (c == Step.YOUNGER_BROTHER) {
                    return one("叔祖父");
                }
                return one(dialect == Dialect.SOUTH ? "姑婆" : "姑奶奶");
            }
            if (a == Step.MOTHER && b == Step.FATHER) {
                if (c == Step.ELDER_BROTHER) {
                    return one("外伯祖父");
                }
                if (c == Step.YOUNGER_BROTHER) {
                    return one("外叔祖父");
                }
                return one("外姑婆");
            }
            // Grandmothers' siblings (奶奶 / 外婆).
            if (isBrother(c)) {
                return one(dialect == Dialect.SOUTH ? "舅公" : "舅爷");
            }
            return one(dialect == Dialect.SOUTH ? "姨婆" : "姨奶奶");
        }
        if (isSibling(a) && isChild(b) && isChild(c)) {
            // 哥哥的儿子 → 侄子, so 哥哥的儿子的儿子 → 侄孙.
            final String prefix = isBrother(a) ? "侄" : "外甥";
            return one(prefix + (c == Step.SON ? "孙" : "孙女"));
        }
        if (isSibling(a) && isSpouse(b) && isChild(c)) {
            // 嫂子的儿子 is still 侄子; 姐夫的女儿 is still 外甥女.
            final String prefix = isBrother(a) ? "侄" : "外甥";
            return one(prefix + (c == Step.SON ? "子" : "女"));
        }
        if (isChild(a) && isSpouse(b) && isChild(c)) {
            // 儿媳的儿子 → 孙子; 女婿的女儿 → 外孙女.
            return one((a == Step.SON ? "" : "外") + (c == Step.SON ? "孙子" : "孙女"));
        }
        if (isSibling(a) && isParent(b)) {
            // 哥哥的爸爸 is 爸爸.
            return one(b == Step.FATHER ? "爸爸" : "妈妈");
        }
        return null;
    }

    /** Four-step chains: the children of cousins (堂侄 / 表侄). */
    private static Answer quad(@NonNull List<Step> chain, Dialect dialect) {
        final Step a = chain.get(0);
        final Step b = chain.get(1);
        final Step c = chain.get(2);
        final Step d = chain.get(3);
        if (isParent(a) && isSibling(b) && isChild(c) && isChild(d)) {
            final String prefix = a == Step.FATHER && isBrother(b) ? "堂" : "表";
            return one(prefix + (d == Step.SON ? "侄" : "侄女"));
        }
        return null;
    }

    /**
     * Last resort for chains without a rule: name the longest prefix that does resolve and append
     * the remaining steps, e.g. "堂侄的儿子". Always readable, never empty.
     */
    @NonNull
    private static Answer composed(@NonNull List<Step> chain, @NonNull Dialect dialect) {
        for (int split = chain.size() - 1; split >= 1; split--) {
            final Answer head = resolveForward(chain.subList(0, split), dialect);
            if (head.terms.size() == 1) {
                final String tail = chainText(chain.subList(split, chain.size()));
                return one(head.terms.get(0) + "的" + tail);
            }
        }
        return one(chainText(chain));
    }

    // ---- Ancestors and descendants ---------------------------------------------------------

    private static boolean isAncestorChain(@NonNull List<Step> chain) {
        for (Step step : chain) {
            if (!isParent(step)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDescendantChain(@NonNull List<Step> chain) {
        for (Step step : chain) {
            if (!isChild(step)) {
                return false;
            }
        }
        return true;
    }

    /** 爸爸, 爷爷, 曾祖父, 外曾祖母… */
    @NonNull
    private static String ancestorTerm(@NonNull List<Step> chain, @NonNull Dialect dialect) {
        final int size = chain.size();
        final boolean female = chain.get(size - 1) == Step.MOTHER;
        // A mother anywhere above the last person makes the line "外" (外公, 外曾祖父, …).
        final boolean outer = chain.subList(0, size - 1).contains(Step.MOTHER);
        if (size == 1) {
            return female ? "妈妈" : "爸爸";
        }
        if (size == 2) {
            return female ? grandmother(outer, dialect) : grandfather(outer, dialect);
        }
        final String prefix = prefix(ANCESTOR_PREFIXES, size - 3);
        return (outer ? "外" : "") + prefix + (female ? "祖母" : "祖父");
    }

    /** 儿子, 孙子, 外孙女, 曾孙… */
    @NonNull
    private static String descendantTerm(@NonNull List<Step> chain) {
        final int size = chain.size();
        final boolean female = chain.get(size - 1) == Step.DAUGHTER;
        // A daughter anywhere above the last person makes the line "外" (外孙, 外曾孙女, …); the
        // sex of the last person is described by 孙 / 孙女 instead.
        final boolean outer = chain.subList(0, size - 1).contains(Step.DAUGHTER);
        if (size == 1) {
            return female ? "女儿" : "儿子";
        }
        if (size == 2) {
            return (outer ? "外" : "") + (female ? "孙女" : "孙子");
        }
        final String prefix = prefix(DESCENDANT_PREFIXES, size - 3);
        return (outer ? "外" : "") + prefix + (female ? "孙女" : "孙");
    }

    @NonNull
    private static String prefix(@NonNull String[] prefixes, int index) {
        if (index < 0) {
            return "";
        }
        return index < prefixes.length ? prefixes[index] : prefixes[prefixes.length - 1];
    }

    @NonNull
    private static String grandfather(boolean maternal, @NonNull Dialect dialect) {
        if (maternal) {
            return dialect == Dialect.SOUTH ? "姥爷" : "外公";
        }
        return dialect == Dialect.SOUTH ? "阿公" : "爷爷";
    }

    @NonNull
    private static String grandmother(boolean maternal, @NonNull Dialect dialect) {
        if (maternal) {
            return dialect == Dialect.SOUTH ? "姥姥" : "外婆";
        }
        return dialect == Dialect.SOUTH ? "阿嬷" : "奶奶";
    }

    // ---- Steps -----------------------------------------------------------------------------

    private static boolean isParent(Step step) {
        return step == Step.FATHER || step == Step.MOTHER;
    }

    private static boolean isChild(Step step) {
        return step == Step.SON || step == Step.DAUGHTER;
    }

    private static boolean isSpouse(Step step) {
        return step == Step.HUSBAND || step == Step.WIFE;
    }

    private static boolean isSibling(Step step) {
        return isBrother(step) || isSister(step);
    }

    private static boolean isBrother(Step step) {
        return step == Step.ELDER_BROTHER || step == Step.YOUNGER_BROTHER;
    }

    private static boolean isSister(Step step) {
        return step == Step.ELDER_SISTER || step == Step.YOUNGER_SISTER;
    }

    /** The sex of the person a step leads to. */
    @NonNull
    private static Sex sexOf(@NonNull Step step) {
        switch (step) {
            case FATHER:
            case ELDER_BROTHER:
            case YOUNGER_BROTHER:
            case HUSBAND:
            case SON:
                return Sex.MALE;
            default:
                return Sex.FEMALE;
        }
    }

    /**
     * The step that leads back from the person reached by {@code step}: the inverse of
     * "my father" is "his son" or "his daughter", depending on who is walking back.
     */
    @NonNull
    private static Step invertStep(@NonNull Step step, @NonNull Sex sex) {
        final boolean male = sex == Sex.MALE;
        switch (step) {
            case FATHER:
            case MOTHER:
                return male ? Step.SON : Step.DAUGHTER;
            case ELDER_BROTHER:
            case ELDER_SISTER:
                // Reached from an older sibling, so the walker is the younger one.
                return male ? Step.YOUNGER_BROTHER : Step.YOUNGER_SISTER;
            case YOUNGER_BROTHER:
            case YOUNGER_SISTER:
                return male ? Step.ELDER_BROTHER : Step.ELDER_SISTER;
            case SON:
            case DAUGHTER:
                return male ? Step.FATHER : Step.MOTHER;
            case HUSBAND:
                return Step.WIFE;
            case WIFE:
            default:
                return Step.HUSBAND;
        }
    }

    @NonNull
    private static Answer one(@NonNull String term) {
        return new Answer(Collections.singletonList(term), Hint.NONE);
    }

    @NonNull
    private static Answer two(@NonNull String first, @NonNull String second, @NonNull Hint hint) {
        return new Answer(Arrays.asList(first, second), hint);
    }
}
