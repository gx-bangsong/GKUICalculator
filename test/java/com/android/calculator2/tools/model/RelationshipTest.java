/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.android.calculator2.tools.model.RelationshipCalculator.Dialect;
import com.android.calculator2.tools.model.RelationshipCalculator.Hint;
import com.android.calculator2.tools.model.RelationshipCalculator.Step;

public class RelationshipTest {

    private static List<Step> chain(Step... steps) {
        return Arrays.asList(steps);
    }

    private static String term(Step... steps) {
        return RelationshipCalculator.resolve(chain(steps), RelationshipCalculator.Dialect.NORTH, false).terms.get(0);
    }

    @Test
    public void singleStepsAreTheWordsThemselves() {
        assertEquals("爸爸", term(Step.FATHER));
        assertEquals("妈妈", term(Step.MOTHER));
        assertEquals("哥哥", term(Step.ELDER_BROTHER));
        assertEquals("弟弟", term(Step.YOUNGER_BROTHER));
        assertEquals("姐姐", term(Step.ELDER_SISTER));
        assertEquals("妹妹", term(Step.YOUNGER_SISTER));
        assertEquals("丈夫", term(Step.HUSBAND));
        assertEquals("妻子", term(Step.WIFE));
        assertEquals("儿子", term(Step.SON));
        assertEquals("女儿", term(Step.DAUGHTER));
    }

    @Test
    public void resolvesParentsSiblings() {
        assertEquals("伯父", term(Step.FATHER, Step.ELDER_BROTHER));
        assertEquals("叔叔", term(Step.FATHER, Step.YOUNGER_BROTHER));
        assertEquals("姑妈", term(Step.FATHER, Step.ELDER_SISTER));
        assertEquals("姑姑", term(Step.FATHER, Step.YOUNGER_SISTER));
        assertEquals("舅舅", term(Step.MOTHER, Step.ELDER_BROTHER));
        assertEquals("舅舅", term(Step.MOTHER, Step.YOUNGER_BROTHER));
        assertEquals("姨妈", term(Step.MOTHER, Step.ELDER_SISTER));
    }

    @Test
    public void grandparentsFollowTheSelectedDialect() {
        assertEquals("爷爷", term(Step.FATHER, Step.FATHER));
        assertEquals("奶奶", term(Step.FATHER, Step.MOTHER));
        assertEquals("外公", term(Step.MOTHER, Step.FATHER));
        assertEquals("外婆", term(Step.MOTHER, Step.MOTHER));

        assertEquals("阿公", RelationshipCalculator.resolve(chain(Step.FATHER, Step.FATHER),
                RelationshipCalculator.Dialect.SOUTH, false).terms.get(0));
        assertEquals("阿嬷", RelationshipCalculator.resolve(chain(Step.FATHER, Step.MOTHER),
                RelationshipCalculator.Dialect.SOUTH, false).terms.get(0));
        assertEquals("姥爷", RelationshipCalculator.resolve(chain(Step.MOTHER, Step.FATHER),
                RelationshipCalculator.Dialect.SOUTH, false).terms.get(0));
        assertEquals("姥姥", RelationshipCalculator.resolve(chain(Step.MOTHER, Step.MOTHER),
                RelationshipCalculator.Dialect.SOUTH, false).terms.get(0));
    }

    @Test
    public void resolvesSpousesAndInLaws() {
        assertEquals("嫂子", term(Step.ELDER_BROTHER, Step.WIFE));
        assertEquals("弟媳", term(Step.YOUNGER_BROTHER, Step.WIFE));
        assertEquals("姐夫", term(Step.ELDER_SISTER, Step.HUSBAND));
        assertEquals("妹夫", term(Step.YOUNGER_SISTER, Step.HUSBAND));
        assertEquals("公公", term(Step.HUSBAND, Step.FATHER));
        assertEquals("婆婆", term(Step.HUSBAND, Step.MOTHER));
        assertEquals("岳父", term(Step.WIFE, Step.FATHER));
        assertEquals("岳母", term(Step.WIFE, Step.MOTHER));
        assertEquals("儿媳", term(Step.SON, Step.WIFE));
        assertEquals("女婿", term(Step.DAUGHTER, Step.HUSBAND));
        assertEquals("伯母", term(Step.FATHER, Step.ELDER_BROTHER, Step.WIFE));
        assertEquals("婶婶", term(Step.FATHER, Step.YOUNGER_BROTHER, Step.WIFE));
        assertEquals("舅妈", term(Step.MOTHER, Step.ELDER_BROTHER, Step.WIFE));
    }

    @Test
    public void resolvesChildrenAndGrandchildren() {
        assertEquals("侄子", term(Step.ELDER_BROTHER, Step.SON));
        assertEquals("侄女", term(Step.YOUNGER_BROTHER, Step.DAUGHTER));
        assertEquals("外甥", term(Step.ELDER_SISTER, Step.SON));
        assertEquals("外甥女", term(Step.YOUNGER_SISTER, Step.DAUGHTER));
        assertEquals("孙子", term(Step.SON, Step.SON));
        assertEquals("孙女", term(Step.SON, Step.DAUGHTER));
        assertEquals("外孙", term(Step.DAUGHTER, Step.SON));
        // The 外 prefix comes from a daughter *above* the last person, not from her own sex.
        assertEquals("曾孙女", term(Step.SON, Step.SON, Step.DAUGHTER));
        assertEquals("外曾孙女", term(Step.SON, Step.DAUGHTER, Step.DAUGHTER));
        assertEquals("曾孙", term(Step.SON, Step.SON, Step.SON));
        assertEquals("侄孙", term(Step.ELDER_BROTHER, Step.SON, Step.SON));
        assertEquals("外甥孙", term(Step.ELDER_SISTER, Step.SON, Step.SON));
    }

    @Test
    public void cousinsDependOnAgeAndAreBothShown() {
        final RelationshipCalculator.Answer paternal =
                RelationshipCalculator.resolve(
                        chain(Step.FATHER, Step.ELDER_BROTHER, Step.SON), RelationshipCalculator.Dialect.NORTH, false);
        assertEquals(Arrays.asList("堂兄", "堂弟"), paternal.terms);
        assertEquals(RelationshipCalculator.Hint.AGE, paternal.hint);

        final RelationshipCalculator.Answer maternal =
                RelationshipCalculator.resolve(
                        chain(Step.MOTHER, Step.YOUNGER_BROTHER, Step.SON), RelationshipCalculator.Dialect.NORTH, false);
        assertEquals(Arrays.asList("表兄", "表弟"), maternal.terms);

        assertEquals(Arrays.asList("堂姐", "堂妹"), RelationshipCalculator.resolve(
                chain(Step.FATHER, Step.YOUNGER_BROTHER, Step.DAUGHTER),
                RelationshipCalculator.Dialect.NORTH, false).terms);
        assertEquals(Arrays.asList("表姐", "表妹"), RelationshipCalculator.resolve(
                chain(Step.FATHER, Step.ELDER_SISTER, Step.DAUGHTER),
                RelationshipCalculator.Dialect.NORTH, false).terms);
        // 堂兄's son.
        assertEquals("堂侄", term(Step.FATHER, Step.ELDER_BROTHER, Step.SON, Step.SON));
        // 表姐's daughter.
        assertEquals("表侄女", term(Step.MOTHER, Step.ELDER_SISTER, Step.DAUGHTER,
                Step.DAUGHTER));
    }

    @Test
    public void siblingsOfGrandparentsAndDeepAncestors() {
        assertEquals("伯祖父", term(Step.FATHER, Step.FATHER, Step.ELDER_BROTHER));
        assertEquals("叔祖父", term(Step.FATHER, Step.FATHER, Step.YOUNGER_BROTHER));
        assertEquals("姑奶奶", term(Step.FATHER, Step.FATHER, Step.ELDER_SISTER));
        assertEquals("舅爷", term(Step.FATHER, Step.MOTHER, Step.ELDER_BROTHER));
        assertEquals("曾祖父", term(Step.FATHER, Step.FATHER, Step.FATHER));
        assertEquals("曾祖母", term(Step.FATHER, Step.FATHER, Step.MOTHER));
        assertEquals("外曾祖父", term(Step.MOTHER, Step.FATHER, Step.FATHER));
        assertEquals("高祖父", term(Step.FATHER, Step.FATHER, Step.FATHER, Step.FATHER));
    }

    @Test
    public void reverseLookUpAnswersWhatTheyCallTheUser() {
        final RelationshipCalculator.Answer father =
                RelationshipCalculator.resolve(chain(Step.FATHER), RelationshipCalculator.Dialect.NORTH, true);
        assertEquals(Arrays.asList("儿子", "女儿"), father.terms);
        assertEquals(RelationshipCalculator.Hint.SEX, father.hint);

        final RelationshipCalculator.Answer uncle =
                RelationshipCalculator.resolve(
                        chain(Step.FATHER, Step.ELDER_BROTHER), RelationshipCalculator.Dialect.NORTH, true);
        assertEquals(Arrays.asList("侄子", "侄女"), uncle.terms);

        final RelationshipCalculator.Answer brother =
                RelationshipCalculator.resolve(chain(Step.ELDER_BROTHER), RelationshipCalculator.Dialect.NORTH, true);
        assertEquals(Arrays.asList("弟弟", "妹妹"), brother.terms);

        // A spouse fixes the sex of the user, so there is exactly one answer.
        final RelationshipCalculator.Answer wife =
                RelationshipCalculator.resolve(chain(Step.WIFE), RelationshipCalculator.Dialect.NORTH, true);
        assertEquals(Collections.singletonList("丈夫"), wife.terms);
        assertEquals(RelationshipCalculator.Hint.NONE, wife.hint);
    }

    @Test
    public void chainTextJoinsStepsWithDe() {
        assertEquals("爸爸的哥哥的儿子",
                RelationshipCalculator.chainText(
                        chain(Step.FATHER, Step.ELDER_BROTHER, Step.SON)));
        assertEquals("", RelationshipCalculator.chainText(Collections.<Step>emptyList()));
    }

    @Test
    public void shortcutsExpandToTheirAtomicSteps() {
        assertEquals(Arrays.asList(Step.FATHER, Step.FATHER),
                RelationshipCalculator.Key.PATERNAL_GRANDFATHER.steps());
        assertEquals("爷", RelationshipCalculator.Key.PATERNAL_GRANDFATHER.label);
        assertEquals("爷爷", RelationshipCalculator.Key.PATERNAL_GRANDFATHER.description);
        // 妈妈的哥哥 — the 舅 shortcut — resolves to the same term as 妈妈的弟弟.
        assertEquals(Arrays.asList(Step.MOTHER, Step.ELDER_BROTHER),
                RelationshipCalculator.Key.MATERNAL_UNCLE.steps());
        assertEquals("舅舅", term(Step.MOTHER, Step.ELDER_BROTHER));
        // 爸爸的妹妹 — the 姑 shortcut — and 爸爸的弟弟 — the 叔 shortcut.
        assertEquals("姑姑", term(Step.FATHER, Step.YOUNGER_SISTER));
        assertEquals("叔叔", term(Step.FATHER, Step.YOUNGER_BROTHER));
    }

    @Test
    public void everyPadKeyHasASingleCharacterLabel() {
        for (RelationshipCalculator.Key key : RelationshipCalculator.Key.values()) {
            assertEquals(key.name(), 1, key.label.length());
            assertTrue(key.name(), key.description.length() >= key.label.length());
        }
    }

    @Test
    public void emptyChainHasNoTermsAndUnknownChainsStillReadWell() {
        assertTrue(RelationshipCalculator.resolve(Collections.<Step>emptyList(),
                RelationshipCalculator.Dialect.NORTH, false).terms.isEmpty());
        // No rule covers 哥哥的哥哥的哥哥, so the longest known prefix is used.
        final String deep = RelationshipCalculator.resolve(
                chain(Step.ELDER_BROTHER, Step.ELDER_BROTHER, Step.ELDER_BROTHER),
                RelationshipCalculator.Dialect.NORTH, false).terms.get(0);
        assertTrue(deep, deep.contains("的"));
    }
}
