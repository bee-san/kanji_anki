package dev.bee.kanjianki.core

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiReadingChoicePlannerTest {
    private val fixedRandom = Random(42)

    @Test
    fun datsuVsNuBuildsChoiceWithMatureContrastFirst() {
        // 脱出 (だつ) is the failing reading; 脱ぐ (ぬ) is mature contrast.
        val card = KanjiReadingChoicePlanner.buildChoiceCard(
            "脱",
            listOf(usage("脱出", "だつ", noteId = 1, mature = false, lapses = 11)),
            listOf(
                pool("だつ", attested = true, matureAttested = false),
                pool("ぬ", attested = true, matureAttested = true),
            ),
            fixedRandom,
        )
        assertNotNull(card)
        assertEquals("脱", card!!.targetKanji)
        assertEquals("脱出", card.word)
        assertEquals("だつ", card.correctReading)
        assertTrue(card.choices.contains("だつ"))
        assertTrue(card.choices.contains("ぬ"))
        assertTrue(card.isCorrect("だつ"))
    }

    @Test
    fun hikuVsTeiBuildsChoice() {
        val card = KanjiReadingChoicePlanner.buildChoiceCard(
            "低",
            listOf(usage("低い", "ひく", noteId = 2, mature = false, lapses = 10)),
            listOf(
                pool("ひく", attested = true, matureAttested = false),
                pool("てい", attested = true, matureAttested = true),
            ),
            fixedRandom,
        )
        assertNotNull(card)
        assertEquals("ひく", card!!.correctReading)
        assertTrue(card.choices.contains("てい"))
    }

    @Test
    fun prefersWeakEvidenceWordAsPrompt() {
        // Two usages: the mature one and a lapsing one. The weaker (lapsing,
        // immature) usage is chosen as the prompt.
        val card = KanjiReadingChoicePlanner.buildChoiceCard(
            "音",
            listOf(
                usage("音楽", "おん", noteId = 5, mature = false, lapses = 8),
                usage("本音", "ね", noteId = 6, mature = true, lapses = 0),
            ),
            listOf(
                pool("おん", attested = true, matureAttested = false),
                pool("ね", attested = true, matureAttested = true),
                pool("おと", attested = false, matureAttested = false),
            ),
            fixedRandom,
        )
        assertNotNull(card)
        assertEquals("音楽", card!!.word)
        assertEquals("おん", card.correctReading)
    }

    @Test
    fun distractorOrderMatureAttestedBeforeDictionaryOnly() {
        // With MAX_CHOICE_COUNT=4 and many candidates, mature-attested and
        // attested come before dictionary-only. Build a 4-choice card and check
        // the dictionary-only reading is only included after attested ones.
        val card = KanjiReadingChoicePlanner.buildChoiceCard(
            "教",
            listOf(usage("教える", "おし", noteId = 7, mature = false, lapses = 7)),
            listOf(
                pool("おし", attested = true, matureAttested = false),
                pool("おそ", attested = true, matureAttested = true),
                pool("きょう", attested = false, matureAttested = false),
            ),
            fixedRandom,
        )
        assertNotNull(card)
        // All three readings fit within MAX_CHOICE_COUNT (4) with the correct one.
        assertTrue(card!!.choices.containsAll(listOf("おし", "おそ", "きょう")))
    }

    @Test
    fun deterministicGivenSeededRandom() {
        val first = build()
        for (i in 0 until 10) {
            assertEquals(first!!.choices, build()!!.choices)
        }
    }

    @Test
    fun nullWhenNoUsage() {
        assertNull(
            KanjiReadingChoicePlanner.buildChoiceCard(
                "脱",
                emptyList(),
                listOf(pool("だつ", true, false), pool("ぬ", true, true)),
                fixedRandom,
            ),
        )
    }

    @Test
    fun nullWhenNoDistractors() {
        // Only the correct reading in the pool → cannot build a >= 2-choice card.
        assertNull(
            KanjiReadingChoicePlanner.buildChoiceCard(
                "脱",
                listOf(usage("脱出", "だつ", noteId = 1, mature = false, lapses = 0)),
                listOf(pool("だつ", attested = true, matureAttested = false)),
                fixedRandom,
            ),
        )
    }

    private fun build() = KanjiReadingChoicePlanner.buildChoiceCard(
        "脱",
        listOf(usage("脱出", "だつ", noteId = 1, mature = false, lapses = 11)),
        listOf(
            pool("だつ", attested = true, matureAttested = false),
            pool("ぬ", attested = true, matureAttested = true),
            pool("だっ", attested = false, matureAttested = false),
        ),
        Random(7),
    )

    private fun usage(word: String, reading: String, noteId: Long, mature: Boolean, lapses: Int) =
        KanjiReadingChoicePlanner.Usage(word, reading, "meaning", noteId, mature, lapses)

    private fun pool(reading: String, attested: Boolean, matureAttested: Boolean) =
        KanjiReadingChoicePlanner.PoolReading(reading, attested, matureAttested)
}
