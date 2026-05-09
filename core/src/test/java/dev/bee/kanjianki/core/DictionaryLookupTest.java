package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DictionaryLookupTest {
    @Test
    public void studyCueUsesKanjiMeaningEvenWhenSourceWordMatchesJmdict() {
        DictionaryLookup lookup = new DictionaryLookup(
                Collections.singletonList(word("悲しみ", "カナシミ", Arrays.asList("sadness", "grief"), 1)),
                Collections.singletonList(kanji("悲", Arrays.asList("sorrow", "despair"), Arrays.asList("ヒ"), Arrays.asList("かな.しい")))
        );

        StudyCue cue = lookup.studyCue("悲", "word-level sadness note", "", "悲しみ", "カナシミ");

        assertEquals("Sorrow, despair", cue.meaning);
        assertEquals("かなしみ", cue.reading);
        assertEquals("悲しみ", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_KANJIDIC2, cue.meaningSource);
    }

    @Test
    public void studyCueKeepsDictionaryMeaningWhenWordExpressionIsAmbiguous() {
        DictionaryLookup lookup = new DictionaryLookup(
                Arrays.asList(
                        word("橋", "はし", Collections.singletonList("bridge"), 3),
                        word("橋", "はし", Collections.singletonList("chopsticks"), 4)
                ),
                Collections.singletonList(kanji("橋", Collections.singletonList("bridge"), Arrays.asList("キョウ"), Arrays.asList("はし")))
        );

        StudyCue cue = lookup.studyCue("橋", "collection bridge clue", "はし", "橋", "");

        assertEquals("Bridge", cue.meaning);
        assertEquals("はし", cue.reading);
        assertEquals("橋", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_KANJIDIC2, cue.meaningSource);
    }

    @Test
    public void studyCueFallsBackToCollectionClueWhenKanjiIsMissing() {
        DictionaryLookup lookup = new DictionaryLookup(
                Collections.singletonList(word("鿃語", "そうご", Collections.singletonList("rare word gloss"), 1)),
                Collections.emptyList()
        );

        StudyCue cue = lookup.studyCue("鿃", "(noun) collection-only rare shape", "ソウ", "鿃語", "そうご");

        assertEquals("Collection-only rare shape", cue.meaning);
        assertEquals("そうご", cue.reading);
        assertEquals("鿃語", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_ANKI, cue.meaningSource);
    }

    @Test
    public void fromTsvLoadsCompactGeneratedAssets() throws Exception {
        String words = ""
                + "# generated\n"
                + "expression\treading\tglosses\tpos\tpriority\tcommonness\n"
                + "悲しみ\tかなしみ\tsadness\u001fgrief\tnoun\tichi1\t1\n";
        String kanji = ""
                + "# generated\n"
                + "literal\tmeanings\ton_readings\tkun_readings\tnanori_readings\tstroke_count\tgrade\tradical\tfrequency\n"
                + "悲\tsorrow\u001fdespair\tヒ\tかな.しい\t\t12\t3\t61\t1014\n";

        DictionaryLookup lookup = DictionaryLookup.fromTsv(stream(words), stream(kanji));

        assertEquals(1, lookup.wordCount());
        assertEquals(1, lookup.kanjiCount());
        assertNotNull(lookup.lookupWord("悲しみ", "かなしみ"));
        assertNotNull(lookup.lookupKanji("悲"));
    }

    @Test
    public void formatterBuildsMeaningReadingAndFromLines() {
        StudyCue cue = new StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2);
        List<String> lines = StudyCueFormatter.answerLines(cue);

        assertEquals(Arrays.asList("sorrow", "Reading: ひ", "From: 悲しみ"), lines);
        assertEquals("Movement", StudyCueFormatter.cleanFallbackMeaning("JMdict [x] (noun) movement", "", 96));
        assertEquals("Fallback", StudyCueFormatter.cleanFallbackMeaning("", "fallback", 96));
        assertTrue(new StudyCue("sorrow", "ヒ", "悲しみ", "KANJIDIC2").toString().contains("悲しみ"));
        assertEquals(cue, new StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2));
        assertEquals(cue.hashCode(), new StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2).hashCode());
    }

    private static DictionaryLookup.WordEntry word(String expression, String reading, List<String> glosses, int commonness) {
        return new DictionaryLookup.WordEntry(expression, reading, glosses, Collections.singletonList("noun"), Collections.emptyList(), commonness);
    }

    private static DictionaryLookup.KanjiEntry kanji(
            String literal,
            List<String> meanings,
            List<String> onReadings,
            List<String> kunReadings
    ) {
        return new DictionaryLookup.KanjiEntry(literal, meanings, onReadings, kunReadings, Collections.emptyList(), 12, 3, 61, 1014);
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
