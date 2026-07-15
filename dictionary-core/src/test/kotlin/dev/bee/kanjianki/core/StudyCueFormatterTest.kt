package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCueFormatterTest {
    @Test
    fun answerLinesFallbackWhenCueHasNoVisibleText() {
        assertEquals(listOf("Collection clue"), StudyCueFormatter.answerLines(null))
        assertEquals(listOf("Collection clue"), StudyCueFormatter.answerLines(StudyCue("", "", "", "")))
    }

    @Test
    fun answerLineLabelsTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals(
                listOf("sorrow", "読み：ひ", "例：悲しみ"),
                StudyCueFormatter.answerLines(StudyCue("sorrow", "ヒ", "悲しみ", DictionaryLookup.SOURCE_KANJIDIC2)),
            )
            assertEquals(listOf("コレクションのヒント"), StudyCueFormatter.answerLines(StudyCue("", "", "", "")))
            assertEquals("コレクションのヒント", StudyCueFormatter.cleanFallbackMeaning(null, " ", 96))
            assertEquals(
                "個別の漢字の意味：Undress, removing",
                StudyCueFormatter.individualKanjiMeaningsLine("Undress, removing"),
            )
            assertTrue(StudyCueFormatter.isReadingLine("読み：ひ"))
            assertTrue(StudyCueFormatter.isCollectionClue("コレクションのヒント"))
        }
    }

    @Test
    fun displayGlossesDeduplicatesCleansAndHonorsMinimumLimit() {
        assertEquals("", StudyCueFormatter.displayGlosses(null, 2))
        assertEquals("Pull", StudyCueFormatter.displayGlosses(listOf(" pull ", "pull", "\n", "drag"), 0))
        assertEquals("Pull, drag", StudyCueFormatter.displayGlosses(listOf("pull", "drag", "haul"), 2))
        assertEquals("Drag", StudyCueFormatter.displayGlosses(listOf(null, "\t", "drag"), 3))
        assertEquals("Pull, drag, haul", StudyCueFormatter.displayGlosses(listOf("pull", "drag", "haul"), 3))
        assertEquals("Pull, drag", StudyCueFormatter.displayGlosses(listOf("pull", "pull", "drag"), 3))
    }

    @Test
    fun cleanFallbackMeaningStripsDictionaryMetadataAndUsesFallbacks() {
        assertEquals(
            "To pull",
            StudyCueFormatter.cleanFallbackMeaning(
                "[2026-05-12] JMdict [v1] (priority news) 1. godan transitive to pull",
                "",
                96,
            ),
        )
        assertEquals("Use fallback", StudyCueFormatter.cleanFallbackMeaning("noun", "use fallback", 96))
        assertEquals("Collection clue", StudyCueFormatter.cleanFallbackMeaning(null, " ", 96))
        assertEquals(
            "Quiet",
            StudyCueFormatter.cleanFallbackMeaning("(form note) (suru verb) Jitendex.org na-adjective quiet", "", 96),
        )
        assertEquals("(unknown) keep this", StudyCueFormatter.cleanFallbackMeaning("(unknown) keep this", "", 96))
        assertEquals("Keep this", StudyCueFormatter.cleanFallbackMeaning("(noun keep this", "", 96))
        assertEquals("Use fallback", StudyCueFormatter.cleanFallbackMeaning("", " use fallback ", 96))
        assertEquals("Bright", StudyCueFormatter.cleanFallbackMeaning("5-dan no-adjective bright", "", 96))
        assertEquals("Blue", StudyCueFormatter.cleanFallbackMeaning("noun i-adjective blue", "", 96))
        assertEquals("Move", StudyCueFormatter.cleanFallbackMeaning("(verb) (transitive) (suru) move", "", 96))
        assertEquals("Clean", StudyCueFormatter.cleanFallbackMeaning("(jitendex marker) clean", "", 96))
        assertEquals("Movement", StudyCueFormatter.cleanFallbackMeaning("Meaning: Jitendex (noun) movement", "", 96))
        assertEquals("movement", StudyCueFormatter.cleanCollectionMeaning("Meaning: Jitendex (noun) movement", 96))
        assertEquals("Collection clue", StudyCueFormatter.cleanFallbackMeaning("", null, 96))
        assertEquals(
            "(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa) keep",
            StudyCueFormatter.cleanFallbackMeaning(
                "(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa) keep",
                "",
                220,
            ),
        )
        assertEquals("Red", StudyCueFormatter.cleanFallbackMeaning("noun na-adjective red", "", 96))
        assertEquals(
            "Burden load responsibility",
            StudyCueFormatter.cleanFallbackMeaning("(★,) noun burden load responsibility 彼 は、 両 親 の 負担 になった?", "", 96),
        )
        assertEquals(
            "Escape getting away (from) getting out (of)",
            StudyCueFormatter.cleanFallbackMeaning(
                "Escape getting away (from) getting out (of) 爺ちゃんはやっとのことで 脱出 した",
                "",
                96,
            ),
        )
        assertEquals(
            "Dumbfounded overcome with surprise in blank amazement",
            StudyCueFormatter.cleanFallbackMeaning("Taru to-adverb dumbfounded overcome with surprise in blank amazement", "", 96),
        )
        assertEquals(
            "Mystery something inexplicable wonder miracle",
            StudyCueFormatter.cleanFallbackMeaning(
                "Na-adj noun yoji mystery something inexplicable wonder miracle See also 不思議",
                "",
                96,
            ),
        )
        assertEquals(
            "Mystery something inexplicable wonder miracle",
            StudyCueFormatter.cleanFallbackMeaning(
                "Na-adj noun yoji mystery something inexplicable wonder miracle See also",
                "",
                96,
            ),
        )
        assertEquals("Fast", StudyCueFormatter.cleanFallbackMeaning("i-adj fast", "", 96))
        assertEquals("Unique", StudyCueFormatter.cleanFallbackMeaning("no-adj unique", "", 96))
    }

    @Test
    fun cleanFallbackMeaningCompactsAtWordOrHardLimit() {
        val wordy = "meaning ".repeat(30)
        assertEquals("Meaning meaning meaning...", StudyCueFormatter.cleanFallbackMeaning(wordy, "", 27))
        assertEquals(
            "Supercalifragilistice...",
            StudyCueFormatter.cleanFallbackMeaning("supercalifragilisticexpialidocious long tail", "", 24),
        )
        assertEquals("Meaning meaning meaning meaning meani...", StudyCueFormatter.cleanFallbackMeaning(wordy, "", 40))
        assertEquals("Meaning meaning meaning meaning meaning meaning meaning...", StudyCueFormatter.cleanFallbackMeaning(wordy, "", 60))
    }

    @Test
    fun compactHandlesNullShortWordBoundariesAndHardLimit() {
        assertEquals("", StudyCueFormatter.compact(null, 12))
        assertEquals("short", StudyCueFormatter.compact("short", 12))
        assertEquals("a very long s...", StudyCueFormatter.compact("a very long sentence that should be shortened", 16))
    }

    @Test
    fun hiraganaReadingHandlesNullEmptyKatakanaAndMixedText() {
        assertEquals("", StudyCueFormatter.hiraganaReading(null))
        assertEquals("", StudyCueFormatter.hiraganaReading(""))
        assertEquals("カタかなA", StudyCueFormatter.hiraganaReading("カタかなA").replace("かた", "カタ"))
        assertEquals("かなゖ", StudyCueFormatter.hiraganaReading("カナヶ"))
        assertEquals("ぁヷ", StudyCueFormatter.hiraganaReading("ぁヷ"))
    }

    @Test
    fun studyCueNormalizesEqualityAndStringOutput() {
        val cue = StudyCue(" meaning ", " ヒ ", " 悲しみ ", " KANJIDIC2 ")

        assertEquals(StudyCue("meaning", "ヒ", "悲しみ", "KANJIDIC2"), cue)
        assertEquals(cue.hashCode().toLong(), StudyCue("meaning", "ヒ", "悲しみ", "KANJIDIC2").hashCode().toLong())
        assertEquals(cue, cue)
        assertEquals("", StudyCue(null, null, null, null).meaning)
        assertEquals(false, cue.equals("meaning"))
        assertEquals(false, cue == StudyCue("other", "ヒ", "悲しみ", "KANJIDIC2"))
        assertEquals(false, cue == StudyCue("meaning", "オン", "悲しみ", "KANJIDIC2"))
        assertEquals(false, cue == StudyCue("meaning", "ヒ", "別", "KANJIDIC2"))
        assertEquals(false, cue == StudyCue("meaning", "ヒ", "悲しみ", "ANKI"))
        assertEquals(
            "StudyCue{meaning='meaning', reading='ヒ', fromExpression='悲しみ', meaningSource='KANJIDIC2'}",
            cue.toString(),
        )
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
