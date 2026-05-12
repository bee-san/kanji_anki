package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class StudyCueFormatterTest {
    @Test
    public void answerLinesFallbackWhenCueHasNoVisibleText() {
        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCueFormatter.answerLines(null)
        );
        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCueFormatter.answerLines(new StudyCue("", "", "", ""))
        );
    }

    @Test
    public void displayGlossesDeduplicatesCleansAndHonorsMinimumLimit() {
        assertEquals("", StudyCueFormatter.displayGlosses(null, 2));
        assertEquals(
                "Pull",
                StudyCueFormatter.displayGlosses(Arrays.asList(" pull ", "pull", "\n", "drag"), 0)
        );
        assertEquals(
                "Pull, drag",
                StudyCueFormatter.displayGlosses(Arrays.asList("pull", "drag", "haul"), 2)
        );
        assertEquals(
                "Drag",
                StudyCueFormatter.displayGlosses(Arrays.asList(null, "\t", "drag"), 3)
        );
        assertEquals(
                "Pull, drag, haul",
                StudyCueFormatter.displayGlosses(Arrays.asList("pull", "drag", "haul"), 3)
        );
        assertEquals(
                "Pull, drag",
                StudyCueFormatter.displayGlosses(Arrays.asList("pull", "pull", "drag"), 3)
        );
    }

    @Test
    public void cleanFallbackMeaningStripsDictionaryMetadataAndUsesFallbacks() {
        assertEquals(
                "To pull",
                StudyCueFormatter.cleanFallbackMeaning(
                        "[2026-05-12] JMdict [v1] (priority news) 1. godan transitive to pull",
                        "",
                        96
                )
        );
        assertEquals(
                "Use fallback",
                StudyCueFormatter.cleanFallbackMeaning("noun", "use fallback", 96)
        );
        assertEquals(
                "Collection clue",
                StudyCueFormatter.cleanFallbackMeaning(null, " ", 96)
        );
        assertEquals(
                "Quiet",
                StudyCueFormatter.cleanFallbackMeaning(
                        "(form note) (suru verb) Jitendex.org na-adjective quiet",
                        "",
                        96
                )
        );
        assertEquals(
                "(unknown) keep this",
                StudyCueFormatter.cleanFallbackMeaning("(unknown) keep this", "", 96)
        );
        assertEquals(
                "Keep this",
                StudyCueFormatter.cleanFallbackMeaning("(noun keep this", "", 96)
        );
        assertEquals(
                "Use fallback",
                StudyCueFormatter.cleanFallbackMeaning("", " use fallback ", 96)
        );
        assertEquals(
                "Bright",
                StudyCueFormatter.cleanFallbackMeaning("5-dan no-adjective bright", "", 96)
        );
        assertEquals(
                "Blue",
                StudyCueFormatter.cleanFallbackMeaning("noun i-adjective blue", "", 96)
        );
        assertEquals(
                "Move",
                StudyCueFormatter.cleanFallbackMeaning("(verb) (transitive) (suru) move", "", 96)
        );
        assertEquals(
                "Clean",
                StudyCueFormatter.cleanFallbackMeaning("(jitendex marker) clean", "", 96)
        );
        assertEquals(
                "Collection clue",
                StudyCueFormatter.cleanFallbackMeaning("", null, 96)
        );
        assertEquals(
                "(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa) keep",
                StudyCueFormatter.cleanFallbackMeaning("(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa) keep", "", 220)
        );
        assertEquals(
                "Red",
                StudyCueFormatter.cleanFallbackMeaning("noun na-adjective red", "", 96)
        );
    }

    @Test
    public void cleanFallbackMeaningCompactsAtWordOrHardLimit() {
        String wordy = "meaning ".repeat(30);
        assertEquals(
                "Meaning meaning meaning...",
                StudyCueFormatter.cleanFallbackMeaning(wordy, "", 27)
        );
        assertEquals(
                "Supercalifragilistice...",
                StudyCueFormatter.cleanFallbackMeaning("supercalifragilisticexpialidocious long tail", "", 24)
        );
        assertEquals(
                "Meaning meaning meaning meaning meani...",
                StudyCueFormatter.cleanFallbackMeaning(wordy, "", 40)
        );
        assertEquals(
                "Meaning meaning meaning meaning meaning meaning meaning...",
                StudyCueFormatter.cleanFallbackMeaning(wordy, "", 60)
        );
    }

    @Test
    public void hiraganaReadingHandlesNullEmptyKatakanaAndMixedText() {
        assertEquals("", StudyCueFormatter.hiraganaReading(null));
        assertEquals("", StudyCueFormatter.hiraganaReading(""));
        assertEquals("カタかなA", StudyCueFormatter.hiraganaReading("カタかなA").replace("かた", "カタ"));
        assertEquals("かなゖ", StudyCueFormatter.hiraganaReading("カナヶ"));
        assertEquals("ぁヷ", StudyCueFormatter.hiraganaReading("ぁヷ"));
    }

    @Test
    public void studyCueNormalizesEqualityAndStringOutput() {
        StudyCue cue = new StudyCue(" meaning ", " ヒ ", " 悲しみ ", " KANJIDIC2 ");

        assertEquals(new StudyCue("meaning", "ヒ", "悲しみ", "KANJIDIC2"), cue);
        assertEquals(cue.hashCode(), new StudyCue("meaning", "ヒ", "悲しみ", "KANJIDIC2").hashCode());
        assertEquals(cue, cue);
        assertEquals("", new StudyCue(null, null, null, null).meaning);
        Object otherType = "meaning";
        boolean equalsOtherType = cue.equals(otherType);
        boolean equalsDifferentMeaning = cue.equals(new StudyCue("other", "ヒ", "悲しみ", "KANJIDIC2"));
        boolean equalsDifferentReading = cue.equals(new StudyCue("meaning", "オン", "悲しみ", "KANJIDIC2"));
        boolean equalsDifferentExpression = cue.equals(new StudyCue("meaning", "ヒ", "別", "KANJIDIC2"));
        boolean equalsDifferentSource = cue.equals(new StudyCue("meaning", "ヒ", "悲しみ", "ANKI"));
        assertEquals(false, equalsOtherType);
        assertEquals(false, equalsDifferentMeaning);
        assertEquals(false, equalsDifferentReading);
        assertEquals(false, equalsDifferentExpression);
        assertEquals(false, equalsDifferentSource);
        assertEquals(
                "StudyCue{meaning='meaning', reading='ヒ', fromExpression='悲しみ', meaningSource='KANJIDIC2'}",
                cue.toString()
        );
    }
}
