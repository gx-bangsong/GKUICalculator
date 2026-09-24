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
 * <p>
 * The regional split follows the reference implementation of this problem,
 * <a href="https://github.com/mumuy/relationship">mumuy/relationship</a> (MIT): the north says
 * 姥爷、姥姥、大爷、大娘、舅姥爷、姨姥姥、姑姥姥、大姥爷、小姥爷, while 外公、外婆、伯父、伯母 are
 * the common forms used everywhere else — they are what the south says, so they are what
 * "South China version" selects. Only the vocabulary is shared; the rules below are this app's
 * own and the reference data is not bundled here.
 */
public final class RelationshipCalculator {

    /**
     * Regional vocabulary. Only a handful of terms differ, and they follow the same split the
     * reference calculator (mumuy/relationship, MIT) uses for its 北方 locale: 北方 says
     * 姥爷、姥姥、大爷, while the common forms 外公、外婆、伯父 are used everywhere else — they
     * are what most of the south says, so that is what "South China version" selects here.
     */
    public enum Dialect {
        /** 北方: 姥爷、姥姥、大爷、大娘、舅姥爷、姨姥姥、姑姥姥… */
        NORTH,
        /** 通用（南方）: 外公、外婆、爷爷、奶奶、伯父… */
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
        final List<Step> steps = normalize(chain);
        if (steps.isEmpty()) {
            return one(SELF);
        }
        final int size = steps.size();
        if (size == 1) {
            return one(word(steps.get(0)));
        }
        if (size == 2) {
            final Answer pair = pair(steps.get(0), steps.get(1), dialect);
            if (pair != null) {
                return pair;
            }
        } else if (size == 3) {
            final Answer triple = triple(steps.get(0), steps.get(1), steps.get(2), dialect);
            if (triple != null) {
                return triple;
            }
        } else if (size == 4) {
            final Answer quad = quad(steps, dialect);
            if (quad != null) {
                return quad;
            }
        }
        if (isAncestorChain(steps)) {
            return one(ancestorTerm(steps, dialect));
        }
        if (isDescendantChain(steps)) {
            return one(descendantTerm(steps));
        }
        return composed(steps, dialect);
    }

    /**
     * Rewrite a chain into the shortest chain that describes the same person, so the rules below
     * do not have to restate the obvious: 爸爸的妻子 is 妈妈, 哥哥的爸爸 is 爸爸 again, and a
     * child's parent is the person we came from. Every rewrite drops two steps, so this settles
     * after at most {@code chain.size()} passes.
     */
    @NonNull
    static List<Step> normalize(@NonNull List<Step> chain) {
        List<Step> current = new ArrayList<>(chain);
        for (int pass = 0; pass <= chain.size(); pass++) {
            final List<Step> next = simplifyOnce(current);
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        return current;
    }

    @NonNull
    private static List<Step> simplifyOnce(@NonNull List<Step> chain) {
        final List<Step> out = new ArrayList<>();
        int i = 0;
        while (i < chain.size()) {
            if (i + 1 < chain.size()) {
                final Step a = chain.get(i);
                final Step b = chain.get(i + 1);
                // 爸爸的妻子 is 妈妈, and 妈妈的丈夫 is 爸爸.
                if (a == Step.FATHER && b == Step.WIFE) {
                    out.add(Step.MOTHER);
                    i += 2;
                    continue;
                }
                if (a == Step.MOTHER && b == Step.HUSBAND) {
                    out.add(Step.FATHER);
                    i += 2;
                    continue;
                }
                // 丈夫的妻子 and 妻子的丈夫 are the user again.
                if ((a == Step.HUSBAND && b == Step.WIFE)
                        || (a == Step.WIFE && b == Step.HUSBAND)) {
                    i += 2;
                    continue;
                }
                // A child's parent is the person we came from, or their spouse.
                if (isChild(a) && isParent(b)) {
                    if (out.isEmpty()) {
                        // My child's parent: me when the sexes line up, otherwise my spouse.
                        if (!((a == Step.SON && b == Step.FATHER)
                                || (a == Step.DAUGHTER && b == Step.MOTHER))) {
                            out.add(a == Step.SON ? Step.WIFE : Step.HUSBAND);
                        }
                    } else {
                        final Sex previous = sexOf(out.get(out.size() - 1));
                        if (previous != sexOf(b)) {
                            out.add(previous == Sex.MALE ? Step.WIFE : Step.HUSBAND);
                        }
                    }
                    i += 2;
                    continue;
                }
                // A spouse's child is that person's child, and so is a child's sibling.
                if (isSpouse(a) && isChild(b)) {
                    out.add(b);
                    i += 2;
                    continue;
                }
                if (isChild(a) && isSibling(b)) {
                    out.add(sexOf(b) == Sex.MALE ? Step.SON : Step.DAUGHTER);
                    i += 2;
                    continue;
                }
                // A sibling's parent is my parent again.
                if (isSibling(a) && isParent(b)) {
                    out.add(b);
                    i += 2;
                    continue;
                }
                // An older sibling's older sibling is just that sibling (哥哥的哥哥 = 哥哥).
                if (isSibling(a) && isSibling(b) && isElder(a) == isElder(b)) {
                    out.add(b);
                    i += 2;
                    continue;
                }
            }
            out.add(chain.get(i));
            i++;
        }
        return out;
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
                        return one(dialect == Dialect.NORTH ? "大爷" : "伯父");
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
                        // Normalization already collapsed the unambiguous cases; what is left
                        // (哥哥的弟弟, 妹妹的哥哥, …) is either that sibling or the user.
                        return isSibling(b) ? siblingOfSibling(a, b) : null;
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
                        return isSibling(b) ? siblingOfSibling(a, b) : null;
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
                    if (!brother) {
                        return one("姑父");
                    }
                    return one(b == Step.ELDER_BROTHER
                            ? (dialect == Dialect.NORTH ? "大娘" : "伯母")
                            : "婶婶");
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
            if (isSibling(c)) {
                // 伯父的弟弟 is my father or another uncle; 姨妈的姐姐 is my mother or another aunt.
                return parentsGeneration(paternal, sexOf(c));
            }
            return null;
        }
        if (isParent(a) && isParent(b) && isChild(c)) {
            // 爷爷的儿子 is my father or one of his brothers; 外婆的女儿 is my mother or an aunt.
            return parentsGeneration(a == Step.FATHER, sexOf(c));
        }
        if (isParent(a) && isChild(b) && isChild(c)) {
            // 爸爸的儿子的儿子 is a brother's son; 妈妈的女儿的女儿 is a sister's daughter.
            return one((b == Step.SON ? "侄" : "外甥") + (c == Step.SON ? "子" : "女"));
        }
        if (isParent(a) && isParent(b) && isSibling(c)) {
            return one(grandparentSibling(a, b, c, dialect));
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
        return null;
    }

    /** Four-step chains: cousins of a parent, and the grandparents' generation again. */
    private static Answer quad(@NonNull List<Step> chain, Dialect dialect) {
        final Step a = chain.get(0);
        final Step b = chain.get(1);
        final Step c = chain.get(2);
        final Step d = chain.get(3);
        if (isParent(a) && isParent(b) && isSibling(c) && isSpouse(d)) {
            // The husband or wife of a grandparent's sibling: 大姥姥, 舅姥姥, 姨姥爷, …
            return one(grandparentSiblingSpouse(a, b, c, d, dialect));
        }
        if (isParent(a) && isParent(b) && isSibling(c) && isChild(d)) {
            // A grandparent's sibling's child is a cousin of my parent.
            return parentsCousin(a == Step.FATHER, b, c, sexOf(d));
        }
        if (isParent(a) && isParent(b) && isSibling(c) && isSibling(d)) {
            // A grandparent's sibling's sibling is the grandparent again, or another sibling of
            // theirs: 外婆的姐姐的哥哥的妹妹 is 姨外祖母 or 外婆.
            return grandparentsGeneration(a == Step.FATHER, b, sexOf(d), dialect);
        }
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

    /**
     * 爷爷 and 奶奶 are the same in both regions; only the mother's parents are named
     * differently: 姥爷、姥姥 in the north, 外公、外婆 elsewhere.
     */
    @NonNull
    private static String grandfather(boolean maternal, @NonNull Dialect dialect) {
        if (!maternal) {
            return "爷爷";
        }
        return dialect == Dialect.NORTH ? "姥爷" : "外公";
    }

    @NonNull
    private static String grandmother(boolean maternal, @NonNull Dialect dialect) {
        if (!maternal) {
            return "奶奶";
        }
        return dialect == Dialect.NORTH ? "姥姥" : "外婆";
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

    private static boolean isElder(Step step) {
        return step == Step.ELDER_BROTHER || step == Step.ELDER_SISTER;
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

    /**
     * A sibling's sibling: unambiguous when both are older or both are younger than the people
     * between them (哥哥的哥哥 = 哥哥), and otherwise either that sibling or the user
     * (哥哥的弟弟 = 弟弟 or me).
     */
    @NonNull
    private static Answer siblingOfSibling(@NonNull Step a, @NonNull Step b) {
        return isElder(a) == isElder(b)
                ? one(word(b))
                : new Answer(Arrays.asList(word(b), SELF), Hint.NONE);
    }

    /**
     * Everybody in my parents' generation on one side: for a father's family that is my father
     * and his brothers (爸爸、伯父、叔叔) or his sisters (姑妈、姑姑); for a mother's family it is
     * just 舅舅, or my mother and her sisters (妈妈、姨妈). Used whenever a chain lands on that
     * generation without saying which one — 爷爷的儿子, 伯父的弟弟, 外婆的女儿 …
     */
    @NonNull
    private static Answer parentsGeneration(boolean paternal, @NonNull Sex sex) {
        if (!paternal) {
            return sex == Sex.MALE ? one("舅舅")
                    : new Answer(Arrays.asList("妈妈", "姨妈"), Hint.NONE);
        }
        return sex == Sex.MALE
                ? new Answer(Arrays.asList("爸爸", "伯父", "叔叔"), Hint.NONE)
                : new Answer(Arrays.asList("姑妈", "姑姑"), Hint.NONE);
    }

    /**
     * A grandparent's sibling's child, i.e. a cousin of my parent: 爷爷的哥哥的儿子 is 堂伯父 or
     * 堂叔父, and 奶奶的姐姐的女儿 is 姨表姑母. The prefix says how they are cousins — through a
     * 堂 sibling, or through a 姑, 舅 or 姨 of my parent.
     */
    @NonNull
    private static Answer parentsCousin(boolean paternal, @NonNull Step grandparent,
            @NonNull Step sibling, @NonNull Sex sex) {
        final String prefix;
        if (grandparent == Step.FATHER && isBrother(sibling)) {
            // A grandfather's brother's children are my father's 堂 siblings.
            prefix = "堂";
        } else if (!paternal) {
            // A mother's cousins: only a maternal grandfather's brother's children are 堂, the
            // rest are plain 表 because 表舅父 / 表姨母 already names the side.
            prefix = "表";
        } else if (isBrother(sibling)) {
            prefix = grandparent == Step.FATHER ? "堂" : "舅表";
        } else {
            prefix = grandparent == Step.FATHER ? "姑表" : "姨表";
        }
        if (!paternal) {
            return one(prefix + (sex == Sex.MALE ? "舅父" : "姨母"));
        }
        return sex == Sex.MALE
                ? new Answer(Arrays.asList(prefix + "伯父", prefix + "叔父"), Hint.NONE)
                : one(prefix + "姑母");
    }

    /**
     * The siblings of one grandparent: 爷爷's, 奶奶's, 外公's and 外婆's each have their own set,
     * and the northern vocabulary replaces the maternal ones (舅姥爷, 姨姥姥, 姑姥姥, 大姥爷,
     * 小姥爷), following the 北方 locale of the reference calculator.
     */
    @NonNull
    private static String grandparentSibling(@NonNull Step a, @NonNull Step b, @NonNull Step c,
            @NonNull Dialect dialect) {
        final boolean brother = isBrother(c);
        if (a == Step.FATHER && b == Step.FATHER) {                 // 爷爷's siblings
            if (c == Step.ELDER_BROTHER) {
                return "伯祖父";
            }
            if (c == Step.YOUNGER_BROTHER) {
                return "叔祖父";
            }
            return "姑奶奶";
        }
        if (a == Step.FATHER) {                                     // 奶奶's siblings
            return brother ? "舅爷" : "姨奶奶";
        }
        if (b == Step.FATHER) {                                     // 外公's siblings
            if (dialect == Dialect.NORTH) {
                if (c == Step.ELDER_BROTHER) {
                    return "大姥爷";
                }
                if (c == Step.YOUNGER_BROTHER) {
                    return "小姥爷";
                }
                return "姑姥姥";
            }
            if (c == Step.ELDER_BROTHER) {
                return "外伯祖父";
            }
            if (c == Step.YOUNGER_BROTHER) {
                return "外叔祖父";
            }
            return "外姑婆";
        }
        if (dialect == Dialect.NORTH) {                             // 外婆's siblings
            return brother ? "舅姥爷" : "姨姥姥";
        }
        return brother ? "舅外祖父" : "姨外祖母";
    }

    /** The husband or wife of a {@link #grandparentSibling}'s relative. */
    @NonNull
    private static String grandparentSiblingSpouse(@NonNull Step a, @NonNull Step b,
            @NonNull Step c, @NonNull Step spouse, @NonNull Dialect dialect) {
        final boolean brother = isBrother(c);
        final boolean husband = spouse == Step.HUSBAND;
        if (a == Step.FATHER && b == Step.FATHER) {                 // 爷爷's siblings' spouses
            if (brother) {
                return c == Step.ELDER_BROTHER ? "伯祖母" : "叔祖母";
            }
            return "姑爷爷";
        }
        if (a == Step.FATHER) {                                     // 奶奶's siblings' spouses
            return brother ? "舅奶奶" : "姨爷爷";
        }
        if (b == Step.FATHER) {                                     // 外公's siblings' spouses
            if (husband) {
                return dialect == Dialect.NORTH ? "姑姥爷" : "外姑父";
            }
            if (c == Step.ELDER_BROTHER) {
                return dialect == Dialect.NORTH ? "大姥姥" : "外伯祖母";
            }
            return dialect == Dialect.NORTH ? "小姥姥" : "外叔祖母";
        }
        if (husband) {                                              // 外婆's siblings' spouses
            return dialect == Dialect.NORTH ? "姨姥爷" : "姨外祖父";
        }
        return dialect == Dialect.NORTH ? "舅姥姥" : "舅外祖母";
    }

    /**
     * Everybody in a grandparent's generation reached without saying which one, e.g.
     * 外婆的姐姐的哥哥的妹妹. That is the grandparent's siblings of that sex plus — when the sexes
     * match — the grandparent: 姨外祖母 or 外婆.
     */
    @NonNull
    private static Answer grandparentsGeneration(boolean paternal, @NonNull Step grandparent,
            @NonNull Sex sex, @NonNull Dialect dialect) {
        final boolean male = sex == Sex.MALE;
        final List<String> terms = new ArrayList<>();
        if (paternal && grandparent == Step.FATHER) {                       // 爷爷's generation
            if (male) {
                terms.add("伯祖父");
                terms.add("叔祖父");
                terms.add(grandfather(false, dialect));
            } else {
                terms.add("姑奶奶");
            }
        } else if (paternal) {                                              // 奶奶's generation
            if (male) {
                terms.add("舅爷");
            } else {
                terms.add("姨奶奶");
                terms.add(grandmother(false, dialect));
            }
        } else if (grandparent == Step.FATHER) {                            // 外公's generation
            if (male) {
                terms.add(grandparentSibling(Step.MOTHER, Step.FATHER, Step.ELDER_BROTHER,
                        dialect));
                terms.add(grandparentSibling(Step.MOTHER, Step.FATHER, Step.YOUNGER_BROTHER,
                        dialect));
                terms.add(grandfather(true, dialect));
            } else {
                terms.add(grandparentSibling(Step.MOTHER, Step.FATHER, Step.ELDER_SISTER,
                        dialect));
            }
        } else {                                                            // 外婆's generation
            if (male) {
                terms.add(grandparentSibling(Step.MOTHER, Step.MOTHER, Step.ELDER_BROTHER,
                        dialect));
            } else {
                terms.add(grandparentSibling(Step.MOTHER, Step.MOTHER, Step.ELDER_SISTER,
                        dialect));
                terms.add(grandmother(true, dialect));
            }
        }
        return new Answer(terms, Hint.NONE);
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
