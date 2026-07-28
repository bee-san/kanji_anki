package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MeaningKanjiChoicePlannerTest {
    @Test
    fun buildsFourLocalKanjiChoicesIncludingTarget() {
        val card = MeaningKanjiChoicePlanner().buildChoiceCard(
            row("裂", "split"),
            listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow")),
            listOf(inventory("腕"), inventory("謎")),
            Random(7)
        )

        assertNotNull(card)
        assertEquals("裂", card!!.targetKanji)
        assertEquals("split", card.primaryMeaning)
        assertEquals(4, card.choices.size)
        assertTrue(card.choices.contains("裂"))
        assertTrue(card.isCorrect("裂"))
    }

    @Test
    fun returnsNullWhenFewerThanFourLocalKanjiExist() {
        val card = MeaningKanjiChoicePlanner().buildChoiceCard(
            row("裂", "split"),
            listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow")),
            listOf(inventory("浅")),
            Random(7)
        )

        assertNull(card)
    }

    @Test
    fun trimsChoicesAndSkipsNullInventoryKanji() {
        val card = MeaningKanjiChoicePlanner().buildChoiceCard(
            row(" 裂 ", " split "),
            listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow")),
            listOf(null, inventory(null), inventory(" 腕 ", "arm")),
            Random(7)
        )

        assertNotNull(card)
        assertEquals("裂", card!!.targetKanji)
        assertEquals("split", card.primaryMeaning)
        assertEquals(4, card.choices.size)
        assertTrue(card.choices.contains("腕"))
        assertTrue(card.isCorrect(" 裂 "))
    }

    @Test
    fun excludesDecoysWithSamePrimaryMeaning() {
        val card = MeaningKanjiChoicePlanner().buildChoiceCard(
            row("裂", "split"),
            listOf(row("裂", "split"), row("割", " split "), row("提", "present"), row("浅", "shallow")),
            listOf(inventory("腕", "arm"), inventory("謎", "mystery")),
            Random(7)
        )

        assertNotNull(card)
        assertFalse(card!!.choices.contains("割"))
        assertEquals(4, card.choices.size)
    }

    @Test
    fun emptyWrongPickMapProducesIdenticalChoicesForSameSeed() {
        val rows = listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow"), row("腕", "arm"), row("謎", "mystery"))

        val baseline = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "split"), rows, emptyList(), Random(7))
        val withEmptyMap = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "split"), rows, emptyList(), Random(7), emptyMap())
        val withNullMap = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "split"), rows, emptyList(), Random(7), null)

        assertEquals(baseline!!.choices, withEmptyMap!!.choices)
        assertEquals(baseline.choices, withNullMap!!.choices)
    }

    @Test
    fun confusedDecoysAreAlwaysSeededIntoChoices() {
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        rows.add(row("裂", "split"))
        for (index in 0 until 30) {
            rows.add(row(('一' + index).toString(), "meaning-$index"))
        }
        val wrongPicks = mapOf("裂" to mapOf("一" to 4, "三" to 2))

        for (seed in 0L until 20L) {
            val card = MeaningKanjiChoicePlanner().buildChoiceCard(
                row("裂", "split"),
                rows,
                emptyList(),
                Random(seed),
                wrongPicks,
            )
            assertNotNull(card)
            assertTrue(card!!.choices.contains("裂"))
            assertTrue("seed $seed missing most-confused decoy", card.choices.contains("一"))
            assertTrue("seed $seed missing second confused decoy", card.choices.contains("三"))
            assertEquals(4, card.choices.size)
        }
    }

    @Test
    fun confusedKanjiNotEligibleAsDecoyIsNotForcedIn() {
        val rows = listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow"), row("腕", "arm"))
        val wrongPicks = mapOf("裂" to mapOf("外" to 9))

        val card = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "split"), rows, emptyList(), Random(7), wrongPicks)

        assertNotNull(card)
        assertFalse(card!!.choices.contains("外"))
        assertEquals(4, card.choices.size)
    }

    @Test
    fun gateReturnsNullWhenTargetKanjiHasNoDictionaryGloss() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(entry("提", "present"), entry("浅", "shallow"), entry("腕", "arm")),
        )
        val rows = listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow"), row("腕", "arm"))

        val card = MeaningKanjiChoicePlanner().buildChoiceCard(
            row("裂", "split"),
            rows,
            emptyList(),
            Random(7),
            emptyMap(),
            lookup,
        )

        assertNull(card)
    }

    @Test
    fun gatePassesWhenTargetHasGlossAndNullLookupStaysUngated() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(entry("裂", "split", "rend"), entry("提", "present"), entry("浅", "shallow"), entry("腕", "arm")),
        )
        val rows = listOf(row("裂", "split"), row("提", "present"), row("浅", "shallow"), row("腕", "arm"))

        val gated = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "split"), rows, emptyList(), Random(7), emptyMap(), lookup)
        assertNotNull(gated)
        assertTrue(gated!!.choices.contains("裂"))
        assertEquals(4, gated.choices.size)

        // A null lookup leaves the planner ungated: it still builds even with no dictionary.
        val ungated = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "split"), rows, emptyList(), Random(7), emptyMap(), null)
        assertNotNull(ungated)
        assertTrue(ungated!!.choices.contains("裂"))
    }

    @Test
    fun decoyGuardExcludesDictionaryGlossCollisionEvenWhenWordMeaningDiffers() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(
                entry("裂", "split"),
                entry("割", "split"),
                entry("提", "present"),
                entry("浅", "shallow"),
                entry("腕", "arm"),
            ),
        )
        // 割's word meaning ("divide") differs from the target's ("tear"), so the
        // word-level dedup keeps it; only the dictionary-gloss guard ("split" == "split")
        // can remove it.
        val rows = listOf(
            row("裂", "tear"),
            row("割", "divide"),
            row("提", "present"),
            row("浅", "shallow"),
            row("腕", "arm"),
        )

        val card = MeaningKanjiChoicePlanner().buildChoiceCard(row("裂", "tear"), rows, emptyList(), Random(3), emptyMap(), lookup)

        assertNotNull(card)
        assertFalse(card!!.choices.contains("割"))
        assertTrue(card.choices.contains("裂"))
        assertEquals(4, card.choices.size)
    }

    private fun row(kanji: String, meaning: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 100, meaning, "reading", "search", 10, "reason", "reason text", 1, 0, 0, arrayListOf<RecordsImportModels.Example>())
    }

    private fun inventory(kanji: String?): RecordsImportModels.KanjiInventoryItem {
        return inventory(kanji, "meaning")
    }

    private fun inventory(kanji: String?, meaning: String): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(kanji, meaning, "reading", "search", 1, 1, false, 0L)
    }

    private fun entry(literal: String, vararg meanings: String): DictionaryLookup.KanjiEntry =
        DictionaryLookup.KanjiEntry(
            DictionaryLookup.KanjiEntryFields(
                literal,
                meanings.asList(),
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                0,
                0,
                0,
                null,
            ),
        )
}
