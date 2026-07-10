package dev.bee.kanjianki.core

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingKanjiChoicePlannerTest {
    private val rng = Random(3)

    @Test
    fun buildsHomophoneCardWithPoolOfThree() {
        // し is read by 校/私/詩; target 校, two same-reading distractors.
        val card = KanjiReadingChoicePlannerBuild()
        assertNotNull(card)
        assertEquals("校", card!!.targetKanji)
        assertEquals("こう", card.reading)
        // Blanked word replaces the target kanji with 〇.
        assertTrue(card.blankedWord.contains(ReadingKanjiChoicePlanner.BLANK))
        assertTrue(card.choices.size >= 3)
        assertTrue(card.choices.contains("校"))
        assertTrue(card.isCorrect("校"))
    }

    @Test
    fun matureAttestedDistractorsComeFirst() {
        val card = ReadingKanjiChoicePlanner.buildChoiceCard(
            "校",
            listOf(usage("学校", "こう", noteId = 1)),
            mapOf(
                "こう" to listOf(
                    candidate("高", matureAttested = true),
                    candidate("光", matureAttested = false),
                    candidate("公", matureAttested = false),
                ),
            ),
            rng,
        )
        assertNotNull(card)
        // Target + up to 3 distractors, mature-attested 高 preferred first.
        assertTrue(card!!.choices.contains("高"))
    }

    @Test
    fun nullWhenPoolBelowThree() {
        // Only one other kanji shares the reading → pool of 2 total, below the
        // stricter 3-choice minimum for homophones.
        assertNull(
            ReadingKanjiChoicePlanner.buildChoiceCard(
                "校",
                listOf(usage("学校", "こう", noteId = 1)),
                mapOf("こう" to listOf(candidate("高", matureAttested = true))),
                rng,
            ),
        )
    }

    @Test
    fun nullWhenNoUsage() {
        assertNull(
            ReadingKanjiChoicePlanner.buildChoiceCard(
                "校",
                emptyList(),
                mapOf("こう" to listOf(candidate("高", true), candidate("光", false))),
                rng,
            ),
        )
    }

    @Test
    fun deterministicGivenSeededRandom() {
        val first = KanjiReadingChoicePlannerBuild()!!.choices
        for (i in 0 until 10) {
            assertEquals(first, KanjiReadingChoicePlannerBuild()!!.choices)
        }
    }

    @Test
    fun blanksOnlyTheTargetKanjiOccurrence() {
        val card = ReadingKanjiChoicePlanner.buildChoiceCard(
            "配",
            listOf(usage("心配", "はい", noteId = 2)),
            mapOf("はい" to listOf(candidate("敗", true), candidate("俳", false), candidate("肺", false))),
            rng,
        )
        assertNotNull(card)
        assertEquals("心〇", card!!.blankedWord)
    }

    private fun KanjiReadingChoicePlannerBuild(): RecordsImportModels.ReadingKanjiChoiceCard? =
        ReadingKanjiChoicePlanner.buildChoiceCard(
            "校",
            listOf(usage("学校", "こう", noteId = 1)),
            mapOf(
                "こう" to listOf(
                    candidate("高", matureAttested = true),
                    candidate("光", matureAttested = false),
                    candidate("公", matureAttested = false),
                ),
            ),
            Random(9),
        )

    private fun usage(word: String, reading: String, noteId: Long) =
        ReadingKanjiChoicePlanner.TargetUsage(word, reading, "meaning", noteId, false, 1)

    private fun candidate(kanji: String, matureAttested: Boolean) =
        ReadingKanjiChoicePlanner.Candidate(kanji, matureAttested)
}
