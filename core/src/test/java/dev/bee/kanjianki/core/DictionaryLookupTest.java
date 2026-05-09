package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DictionaryLookupTest {
    @Test
    public void studyCueUsesKanjiMeaningAndAnkiExampleOnly() {
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(
                Collections.singletonList(kanji("悲", Arrays.asList("sorrow", "despair"), Arrays.asList("ヒ"), Arrays.asList("かな.しい"), 1014, 500))
        );

        StudyCue cue = lookup.studyCue("悲", "word-level sadness note", "", "悲しみ", "カナシミ");

        assertEquals("Sorrow, despair", cue.meaning);
        assertEquals("カナシミ", cue.reading);
        assertEquals("悲しみ", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_KANJIDIC2, cue.meaningSource);
    }

    @Test
    public void studyCueFallsBackToCollectionClueWhenKanjiIsMissing() {
        DictionaryLookup lookup = DictionaryLookup.empty();

        StudyCue cue = lookup.studyCue("鿃", "(noun) collection-only rare shape", "ソウ", "鿃語", "そうご");

        assertEquals("Collection-only rare shape", cue.meaning);
        assertEquals("そうご", cue.reading);
        assertEquals("鿃語", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_ANKI, cue.meaningSource);
    }

    @Test
    public void inMemoryLookupIndexesOnlyKanjiEntries() {
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(
                Collections.singletonList(kanji("悲", Arrays.asList("sorrow", "despair"), Arrays.asList("ヒ"), Arrays.asList("かな.しい"), 1014, 500))
        );

        DictionaryLookup.KanjiEntry entry = lookup.lookupKanji("悲");

        assertEquals(1, lookup.kanjiCount());
        assertNotNull(entry);
        assertEquals("悲", entry.literal);
        assertEquals(12, entry.strokeCount);
        assertEquals(3, entry.grade);
        assertEquals(61, entry.radical);
        assertEquals(1014, entry.kanjidicFrequency);
        assertEquals(Integer.valueOf(500), entry.jitenRank);
        assertNull(lookup.lookupKanji("謎"));
        assertEquals(0, lookup.jitenRanks().size());
    }

    @Test
    public void formatterBuildsMeaningReadingAndFromLines() {
        StudyCue cue = new StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2);
        List<String> lines = StudyCueFormatter.answerLines(cue);

        assertEquals(Arrays.asList("sorrow", "Reading: ひ", "From: 悲しみ"), lines);
        assertEquals("Movement", StudyCueFormatter.cleanFallbackMeaning("Jitendex.org (noun) movement", "", 96));
        assertEquals("Fallback", StudyCueFormatter.cleanFallbackMeaning("", "fallback", 96));
        assertTrue(new StudyCue("sorrow", "ヒ", "悲しみ", "KANJIDIC2").toString().contains("悲しみ"));
        assertEquals(cue, new StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2));
        assertEquals(cue.hashCode(), new StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2).hashCode());
    }

    @Test
    public void typingAnswerMatchesAnyDictionaryMeaningWithCaseAndPunctuation() {
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(
                Collections.singletonList(kanji("拉", Arrays.asList("Latin", "kidnap"), Arrays.asList("ラ"), Collections.emptyList(), 1800, null))
        );

        assertTrue(TypingAnswerMatcher.matches(lookup, "拉", "KIDNAP!", "archive example"));
        assertTrue(TypingAnswerMatcher.matches(lookup, "拉", " latin ", "archive example"));
    }

    @Test
    public void typingAnswerUsesCleanedCommaSeparatedCollectionFallback() {
        DictionaryLookup lookup = DictionaryLookup.empty();

        assertTrue(TypingAnswerMatcher.matches(lookup, "鿃", "rare shape", "(noun) rare shape, collection-only clue"));
        assertTrue(TypingAnswerMatcher.matches(lookup, "鿃", "collection only clue", "(noun) rare shape, collection-only clue"));
    }

    @Test
    public void typingAnswerRejectsWrongMeaning() {
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(
                Collections.singletonList(kanji("悲", Arrays.asList("sorrow", "despair"), Arrays.asList("ヒ"), Arrays.asList("かな.しい"), 1014, 500))
        );

        assertTrue(TypingAnswerMatcher.acceptedMeanings(lookup, "悲", "fallback").contains("sorrow"));
        assertFalse(TypingAnswerMatcher.matches(lookup, "悲", "joy", "fallback"));
    }

    private static DictionaryLookup.KanjiEntry kanji(
            String literal,
            List<String> meanings,
            List<String> onReadings,
            List<String> kunReadings,
            int kanjidicFrequency,
            Integer jitenRank
    ) {
        return new DictionaryLookup.KanjiEntry(literal, meanings, onReadings, kunReadings, Collections.emptyList(), 12, 3, 61, kanjidicFrequency, jitenRank);
    }
}
