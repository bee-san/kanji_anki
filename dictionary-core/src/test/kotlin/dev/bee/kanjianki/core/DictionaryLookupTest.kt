package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryLookupTest {
    @Test
    fun studyCueUsesKanjiMeaningAndAnkiExampleOnly() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("悲", listOf("sorrow", "despair"), listOf("ヒ"), listOf("かな.しい"), 1014, 500)),
        )

        val cue = lookup.studyCue("悲", "word-level sadness note", "", "悲しみ", "カナシミ")

        assertEquals("Sorrow, despair", cue.meaning)
        assertEquals("カナシミ", cue.reading)
        assertEquals("悲しみ", cue.fromExpression)
        assertEquals(DictionaryLookup.SOURCE_KANJIDIC2, cue.meaningSource)
    }

    @Test
    fun studyCueFallsBackToCollectionClueWhenKanjiIsMissing() {
        val cue = DictionaryLookup.empty().studyCue("鿃", "(noun) collection-only rare shape", "ソウ", "鿃語", "そうご")

        assertEquals("Collection-only rare shape", cue.meaning)
        assertEquals("そうご", cue.reading)
        assertEquals("鿃語", cue.fromExpression)
        assertEquals(DictionaryLookup.SOURCE_ANKI, cue.meaningSource)
    }

    @Test
    fun inMemoryLookupIndexesOnlyKanjiEntries() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("悲", listOf("sorrow", "despair"), listOf("ヒ"), listOf("かな.しい"), 1014, 500)),
        )

        val entry = lookup.lookupKanji("悲")

        assertEquals(1, lookup.kanjiCount())
        assertNotNull(entry)
        assertEquals("悲", entry!!.literal)
        assertEquals(12, entry.strokeCount)
        assertEquals(3, entry.grade)
        assertEquals(61, entry.radical)
        assertEquals(1014, entry.kanjidicFrequency)
        assertEquals(500, entry.jitenRank)
        assertNull(lookup.lookupKanji("謎"))
        assertEquals(0, lookup.jitenRanks().size())
    }

    @Test
    fun normalizeTreatsNullAsEmptyText() {
        assertEquals("", DictionaryLookup.normalize(null))
    }

    @Test
    fun formatterBuildsMeaningReadingAndFromLines() {
        val cue = StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2)
        val lines = StudyCueFormatter.answerLines(cue)

        assertEquals(listOf("sorrow", "Reading: ひ", "From: 悲しみ"), lines)
        assertEquals("Movement", StudyCueFormatter.cleanFallbackMeaning("Jitendex.org (noun) movement", "", 96))
        assertEquals("Fallback", StudyCueFormatter.cleanFallbackMeaning("", "fallback", 96))
        assertTrue(StudyCue("sorrow", "ヒ", "悲しみ", "KANJIDIC2").toString().contains("悲しみ"))
        assertEquals(cue, StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2))
        assertEquals(cue.hashCode().toLong(), StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2).hashCode().toLong())
    }

    @Test
    fun typingAnswerMatchesAnyDictionaryMeaningWithCaseAndPunctuation() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("拉", listOf("Latin", "kidnap"), listOf("ラ"), emptyList(), 1800, null)),
        )

        assertTrue(TypingAnswerMatcher.matches(lookup, "拉", "KIDNAP!", "archive example"))
        assertTrue(TypingAnswerMatcher.matches(lookup, "拉", " latin ", "archive example"))
    }

    @Test
    fun typingAnswerUsesCleanedCommaSeparatedCollectionFallback() {
        val lookup = DictionaryLookup.empty()

        assertTrue(TypingAnswerMatcher.matches(lookup, "鿃", "rare shape", "(noun) rare shape, collection-only clue"))
        assertTrue(TypingAnswerMatcher.matches(lookup, "鿃", "collection only clue", "(noun) rare shape, collection-only clue"))
    }

    @Test
    fun typingAnswerRejectsWrongMeaning() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("悲", listOf("sorrow", "despair"), listOf("ヒ"), listOf("かな.しい"), 1014, 500)),
        )

        assertTrue(TypingAnswerMatcher.acceptedMeanings(lookup, "悲", "fallback").contains("sorrow"))
        assertFalse(TypingAnswerMatcher.matches(lookup, "悲", "joy", "fallback"))
    }

    @Test
    fun typingAnswerHandlesNullLookupEmptyAnswersAndDuplicateMeanings() {
        assertFalse(TypingAnswerMatcher.matches(null, "悲", " ", "sorrow"))
        assertTrue(TypingAnswerMatcher.matches(null, "悲", "sorrow", "sorrow; sorrow / grief"))
        assertEquals(listOf("Sorrow", "grief"), TypingAnswerMatcher.acceptedMeanings(null, "悲", "sorrow, grief"))
    }

    @Test
    fun typingAnswerIgnoresNullPlaceholderAndEmptyMeaningVariants() {
        @Suppress("UNCHECKED_CAST")
        val meaningsWithJavaNull = listOf(null, "Collection clue", " , / ") as List<String>
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("謎", meaningsWithJavaNull, emptyList(), emptyList(), 0, null)),
        )

        assertTrue(TypingAnswerMatcher.acceptedMeanings(lookup, "謎", "Collection clue").isEmpty())
        assertFalse(TypingAnswerMatcher.matches(lookup, "謎", null, "mystery"))
    }

    @Test
    fun studyCueFallsBackThroughRowAndDictionaryReadings() {
        val withKun = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("悲", listOf("sorrow"), listOf("ヒ"), listOf("かな.しい"), 1014, 500)),
        )
        val withOn = DictionaryLookup.fromKanjiEntries(
            listOf(kanji("拉", listOf("pull"), listOf("ラ"), emptyList(), 1800, null)),
        )
        val withNanori = DictionaryLookup.fromKanjiEntries(
            listOf(
                DictionaryLookup.KanjiEntry(
                    DictionaryLookup.KanjiEntryFields(
                        "名",
                        listOf("name"),
                        emptyList(),
                        emptyList(),
                        listOf("な"),
                        6,
                        1,
                        30,
                        100,
                        null,
                    ),
                ),
            ),
        )

        assertEquals("row reading", withKun.studyCue("悲", "", "row   reading", "", "").reading)
        assertEquals("かな.しい", withKun.studyCue("悲", "", "", "", "").reading)
        assertEquals("ラ", withOn.studyCue("拉", "", "", "", "").reading)
        assertEquals("な", withNanori.studyCue("名", "", "", "", "").reading)
        assertEquals("", DictionaryLookup.fromKanjiEntries(null).studyCue("謎", "", "", null, null).fromExpression)
    }

    @Test
    fun nullKanjiEntryFieldsUseEmptyEntryDefaults() {
        val empty = DictionaryLookup.KanjiEntry(null)
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(empty))

        assertEquals("", empty.literal)
        assertTrue(empty.meanings.isEmpty())
        assertEquals("", lookup.studyCue("", "fallback", "", "", "").reading)
        assertEquals("", lookup.studyCue("", "fallback", "", "", "").meaning)
    }

    private fun kanji(
        literal: String,
        meanings: List<String>,
        onReadings: List<String>,
        kunReadings: List<String>,
        kanjidicFrequency: Int,
        jitenRank: Int?,
    ): DictionaryLookup.KanjiEntry = DictionaryLookup.KanjiEntry(
        DictionaryLookup.KanjiEntryFields(
            literal,
            meanings,
            onReadings,
            kunReadings,
            emptyList(),
            12,
            3,
            61,
            kanjidicFrequency,
            jitenRank,
        ),
    )
}
