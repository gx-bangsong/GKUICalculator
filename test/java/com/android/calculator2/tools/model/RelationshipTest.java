/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2.tools.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.calculator2.tools.kinship.RelationshipData;
import com.android.calculator2.tools.model.RelationshipCalculator.Dialect;
import com.android.calculator2.tools.model.RelationshipCalculator.Step;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks the kinship tool against the answers of the reference implementation
 * (https://github.com/mumuy/relationship, MIT). Every expectation below was taken from that
 * engine, and the whole set of chains up to four steps was compared with it while the engine was
 * ported.
 */
public class RelationshipTest {

    private static RelationshipData sData;

    @BeforeClass
    public static void loadData() throws Exception {
        final byte[] json = Files.readAllBytes(
                Paths.get("assets", "tools", "relationship.json"));
        sData = RelationshipData.parse(new String(json, StandardCharsets.UTF_8));
    }

    @Test
    public void singleSteps() {
        assertEquals(list("爸爸"), call(Step.FATHER));
        assertEquals(list("妈妈"), call(Step.MOTHER));
        assertEquals(list("哥哥"), call(Step.ELDER_BROTHER));
        assertEquals(list("弟弟"), call(Step.YOUNGER_BROTHER));
        assertEquals(list("姐姐"), call(Step.ELDER_SISTER));
        assertEquals(list("妹妹"), call(Step.YOUNGER_SISTER));
        assertEquals(list("老公"), call(Step.HUSBAND));
        assertEquals(list("老婆"), call(Step.WIFE));
        assertEquals(list("儿子"), call(Step.SON));
        assertEquals(list("女儿"), call(Step.DAUGHTER));
    }

    @Test
    public void parentsSiblingsAndTheirSpouses() {
        assertEquals(list("伯父"), call(Step.FATHER, Step.ELDER_BROTHER));
        assertEquals(list("叔叔"), call(Step.FATHER, Step.YOUNGER_BROTHER));
        assertEquals(list("大姑"), call(Step.FATHER, Step.ELDER_SISTER));
        assertEquals(list("小姑"), call(Step.FATHER, Step.YOUNGER_SISTER));
        assertEquals(list("大舅"), call(Step.MOTHER, Step.ELDER_BROTHER));
        assertEquals(list("小舅"), call(Step.MOTHER, Step.YOUNGER_BROTHER));
        assertEquals(list("大姨"), call(Step.MOTHER, Step.ELDER_SISTER));
        assertEquals(list("小姨"), call(Step.MOTHER, Step.YOUNGER_SISTER));
        assertEquals(list("伯母"), call(Step.FATHER, Step.ELDER_BROTHER, Step.WIFE));
        assertEquals(list("大舅妈"), call(Step.MOTHER, Step.ELDER_BROTHER, Step.WIFE));
        assertEquals(list("大姑丈"), call(Step.FATHER, Step.ELDER_SISTER, Step.HUSBAND));
        assertEquals(list("大姨丈"), call(Step.MOTHER, Step.ELDER_SISTER, Step.HUSBAND));
    }

    @Test
    public void grandparentsAndGreatGrandparents() {
        assertEquals(list("爷爷"), call(Step.FATHER, Step.FATHER));
        assertEquals(list("奶奶"), call(Step.FATHER, Step.MOTHER));
        assertEquals(list("外公"), call(Step.MOTHER, Step.FATHER));
        assertEquals(list("外婆"), call(Step.MOTHER, Step.MOTHER));
        assertEquals(list("伯公"), call(Step.FATHER, Step.FATHER, Step.ELDER_BROTHER));
        assertEquals(list("叔公"), call(Step.FATHER, Step.FATHER, Step.YOUNGER_BROTHER));
        assertEquals(list("姑奶奶"), call(Step.FATHER, Step.FATHER, Step.ELDER_SISTER));
        assertEquals(list("大舅爷"), call(Step.FATHER, Step.MOTHER, Step.ELDER_BROTHER));
        assertEquals(list("伯外公"), call(Step.MOTHER, Step.FATHER, Step.ELDER_BROTHER));
        assertEquals(list("舅外公"), call(Step.MOTHER, Step.MOTHER, Step.ELDER_BROTHER));
        assertEquals(list("姨外婆"), call(Step.MOTHER, Step.MOTHER, Step.ELDER_SISTER));
        assertEquals(list("曾祖父"), call(Step.FATHER, Step.FATHER, Step.FATHER));
        assertEquals(list("外曾祖父"), call(Step.MOTHER, Step.FATHER, Step.FATHER));
        assertEquals(list("外曾外祖母"), call(Step.MOTHER, Step.MOTHER, Step.MOTHER));
    }

    @Test
    public void cousinsKeepTheLinkThatJoinsThem() {
        assertEquals(list("堂哥", "堂弟"), call(Step.FATHER, Step.ELDER_BROTHER, Step.SON));
        assertEquals(list("堂姐", "堂妹"), call(Step.FATHER, Step.ELDER_BROTHER, Step.DAUGHTER));
        assertEquals(list("姑表哥", "姑表弟"),
                call(Step.FATHER, Step.ELDER_SISTER, Step.SON));
        assertEquals(list("舅表哥", "舅表弟"),
                call(Step.MOTHER, Step.ELDER_BROTHER, Step.SON));
        assertEquals(list("姨哥", "姨弟"), call(Step.MOTHER, Step.ELDER_SISTER, Step.SON));
        // 奶奶的姐姐的女儿 — the aunt is linked through a 姨, so the answer carries 姨.
        assertEquals(list("姨姑母"),
                call(Step.FATHER, Step.MOTHER, Step.ELDER_SISTER, Step.DAUGHTER));
        assertEquals(list("堂侄"),
                call(Step.FATHER, Step.ELDER_BROTHER, Step.SON, Step.SON));
    }

    @Test
    public void childrenAndGrandchildren() {
        assertEquals(list("孙子"), call(Step.SON, Step.SON));
        assertEquals(list("孙女"), call(Step.SON, Step.DAUGHTER));
        assertEquals(list("外孙"), call(Step.DAUGHTER, Step.SON));
        assertEquals(list("外孙女"), call(Step.DAUGHTER, Step.DAUGHTER));
        assertEquals(list("曾孙"), call(Step.SON, Step.SON, Step.SON));
        assertEquals(list("侄子"), call(Step.ELDER_BROTHER, Step.SON));
        assertEquals(list("侄女"), call(Step.ELDER_BROTHER, Step.DAUGHTER));
        assertEquals(list("外甥"), call(Step.ELDER_SISTER, Step.SON));
        assertEquals(list("外甥女"), call(Step.YOUNGER_SISTER, Step.DAUGHTER));
    }

    @Test
    public void inLaws() {
        assertEquals(list("岳父"), call(Step.WIFE, Step.FATHER));
        assertEquals(list("岳母"), call(Step.WIFE, Step.MOTHER));
        assertEquals(list("公公"), call(Step.HUSBAND, Step.FATHER));
        assertEquals(list("婆婆"), call(Step.HUSBAND, Step.MOTHER));
        assertEquals(list("大舅子"), call(Step.WIFE, Step.ELDER_BROTHER));
        assertEquals(list("小姨子"), call(Step.WIFE, Step.YOUNGER_SISTER));
        assertEquals(list("儿媳"), call(Step.SON, Step.WIFE));
        assertEquals(list("女婿"), call(Step.DAUGHTER, Step.HUSBAND));
        assertEquals(list("祖岳父"), call(Step.WIFE, Step.FATHER, Step.FATHER));
    }

    @Test
    public void ambiguousChainsListEveryCandidate() {
        // 爸爸的儿子 is a brother or the user.
        assertEquals(list("哥哥", "弟弟", "自己"), call(Step.FATHER, Step.SON));
        assertEquals(list("姐姐", "妹妹", "自己"), call(Step.FATHER, Step.DAUGHTER));
        // And 外婆的姐姐的哥哥的妹妹 is 外婆's sister, or 外婆.
        assertEquals(list("姨外婆", "外婆"), call(Step.MOTHER, Step.MOTHER,
                Step.ELDER_SISTER, Step.ELDER_BROTHER, Step.YOUNGER_SISTER));
        assertTrue(RelationshipCalculator.resolve(sData, chain(Step.FATHER, Step.SON),
                Dialect.SOUTH, false).isAmbiguous());
    }

    @Test
    public void reverseAnswersWhatTheyCallTheUser() {
        assertEquals(list("儿子", "女儿"), reverse(Step.FATHER));
        assertEquals(list("侄子", "侄女"), reverse(Step.FATHER, Step.ELDER_BROTHER));
        assertEquals(list("弟弟", "妹妹"), reverse(Step.ELDER_BROTHER));
        assertEquals(list("孙子", "孙女"), reverse(Step.FATHER, Step.FATHER));
        assertEquals(list("外孙", "外孙女"), reverse(Step.MOTHER, Step.MOTHER));
        // A spouse fixes the user's sex, so there is a single answer.
        assertEquals(list("女婿"), reverse(Step.WIFE, Step.FATHER));
        assertEquals(list("儿媳"), reverse(Step.HUSBAND, Step.MOTHER));
        assertEquals(list("老婆"), reverse(Step.HUSBAND));
    }

    @Test
    public void northernVocabulary() {
        final List<Step> grandpa = chain(Step.MOTHER, Step.FATHER);
        assertEquals(list("外公"), RelationshipCalculator.resolve(
                sData, grandpa, Dialect.SOUTH, false).terms);
        assertEquals(list("姥爷"), RelationshipCalculator.resolve(
                sData, grandpa, Dialect.NORTH, false).terms);
        assertEquals(list("姥姥"), RelationshipCalculator.resolve(
                sData, chain(Step.MOTHER, Step.MOTHER), Dialect.NORTH, false).terms);
        assertEquals(list("大爷"), RelationshipCalculator.resolve(
                sData, chain(Step.FATHER, Step.ELDER_BROTHER), Dialect.NORTH, false).terms);
        assertEquals(list("大娘"), RelationshipCalculator.resolve(
                sData, chain(Step.FATHER, Step.ELDER_BROTHER, Step.WIFE),
                Dialect.NORTH, false).terms);
        assertEquals(list("大姥爷"), RelationshipCalculator.resolve(
                sData, chain(Step.MOTHER, Step.FATHER, Step.ELDER_BROTHER),
                Dialect.NORTH, false).terms);
        assertEquals(list("姨姥姥"), RelationshipCalculator.resolve(
                sData, chain(Step.MOTHER, Step.MOTHER, Step.ELDER_SISTER),
                Dialect.NORTH, false).terms);
        // 爷爷 and 奶奶 are the same in both regions.
        assertEquals(list("爷爷"), RelationshipCalculator.resolve(
                sData, chain(Step.FATHER, Step.FATHER), Dialect.NORTH, false).terms);
    }

    @Test
    public void chainsThatNameNobodyComeBackEmpty() {
        // 爷爷的丈夫, 丈夫的丈夫 and friends describe nobody.
        assertTrue(call(Step.FATHER, Step.FATHER, Step.HUSBAND).isEmpty());
        assertTrue(call(Step.HUSBAND, Step.HUSBAND).isEmpty());
        assertTrue(call(Step.MOTHER, Step.MOTHER, Step.WIFE).isEmpty());
        assertTrue(RelationshipCalculator.resolve(sData, Collections.<Step>emptyList(),
                Dialect.SOUTH, false).terms.isEmpty());
        assertTrue(RelationshipCalculator.resolve(null, chain(Step.FATHER),
                Dialect.SOUTH, false).terms.isEmpty());
    }

    @Test
    public void chainWordingAndPadKeys() {
        assertEquals("爸爸的哥哥的儿子",
                RelationshipCalculator.chainText(
                        chain(Step.FATHER, Step.ELDER_BROTHER, Step.SON)));
        assertEquals("父的兄的子",
                RelationshipCalculator.compactText(
                        chain(Step.FATHER, Step.ELDER_BROTHER, Step.SON)));
        assertEquals("", RelationshipCalculator.chainText(Collections.<Step>emptyList()));
        assertEquals("父", RelationshipCalculator.shortWord(Step.FATHER));
        assertEquals("兄", RelationshipCalculator.shortWord(Step.ELDER_BROTHER));
        assertEquals("子", RelationshipCalculator.shortWord(Step.SON));
        for (RelationshipCalculator.Key key : RelationshipCalculator.Key.values()) {
            assertEquals(key.name(), 1, key.label.length());
            assertTrue(key.name(), key.description.length() >= key.label.length());
        }
        assertEquals(Arrays.asList(Step.FATHER, Step.FATHER),
                RelationshipCalculator.Key.PATERNAL_GRANDFATHER.steps());
        assertEquals("爷", RelationshipCalculator.Key.PATERNAL_GRANDFATHER.label);
        assertEquals("爷爷", RelationshipCalculator.Key.PATERNAL_GRANDFATHER.description);
    }

    @NonNull
    private static List<Step> chain(@NonNull Step... steps) {
        return Arrays.asList(steps);
    }

    @NonNull
    private static List<String> call(@NonNull Step... steps) {
        return RelationshipCalculator.resolve(sData, chain(steps), Dialect.SOUTH, false).terms;
    }

    @NonNull
    private static List<String> reverse(@NonNull Step... steps) {
        return RelationshipCalculator.resolve(sData, chain(steps), Dialect.SOUTH, true).terms;
    }

    @NonNull
    private static List<String> list(@NonNull String... values) {
        return Arrays.asList(values);
    }
}
