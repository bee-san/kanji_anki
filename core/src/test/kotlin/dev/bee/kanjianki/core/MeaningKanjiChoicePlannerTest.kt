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

    private fun row(kanji: String, meaning: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 100, meaning, "reading", "search", 10, "reason", "reason text", 1, 0, 0, arrayListOf<RecordsImportModels.Example>())
    }

    private fun inventory(kanji: String?): RecordsImportModels.KanjiInventoryItem {
        return inventory(kanji, "meaning")
    }

    private fun inventory(kanji: String?, meaning: String): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(kanji, meaning, "reading", "search", 1, 1, false, 0L)
    }
}
