package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarKanjiChoicePlannerTest {
    @Test
    fun emptyOrTinyInventoryProducesNoChoices() {
        val planner = SimilarKanjiChoicePlanner()

        assertTrue(planner.buildCandidates(null, null).isEmpty())
        assertTrue(planner.buildCandidates(listOf(null, item("", "blank"), item("拉", "pull")), null).isEmpty())
    }

    @Test
    fun buildsDirectLocalChoicesAndSkipsMissingMeaningTargets() {
        val planner = SimilarKanjiChoicePlanner()
        val inventory = listOf(
            item("拉", "pull"),
            item("提", "carry"),
            item("謎", "riddle"),
            item("麺", "")
        )
        val pairs = listOf(
            pair("拉", "提"),
            pair("拉", "謎"),
            pair("提", "外"),
            pair("麺", "提"),
            null,
            pair("拉", "拉"),
            pair("", "提")
        )

        val cards = planner.buildCandidates(inventory, pairs)

        val pull = find(cards, "拉")
        assertEquals("pull", pull.primaryMeaning)
        assertEquals(listOf("拉", "提", "謎"), pull.choices)
        assertEquals("拉\t提\t謎", pull.choiceSignature)
        assertEquals(listOf("拉", "提", "麺"), find(cards, "提").choices)
        assertEquals(listOf("拉", "謎"), find(cards, "謎").choices)
        assertFalse(hasTarget(cards, "麺"))
        assertFalse(hasTarget(cards, "外"))
    }

    @Test
    fun wrongSelectionQueuesOnlyTargetAndSelectedNeighbor() {
        val planner = SimilarKanjiChoicePlanner()
        val card = RecordsImportModels.SimilarKanjiChoiceCard(
            "拉",
            "pull",
            listOf("拉", "提", "謎", "麺"),
            "拉\t提\t謎\t麺"
        )

        val wrong = planner.evaluateSelection(card, "謎")
        val correct = planner.evaluateSelection(card, "拉")
        val outsideChoice = planner.evaluateSelection(card, "外")

        assertFalse(wrong.correct)
        assertEquals(listOf("拉", "謎"), wrong.repairKanji)
        assertTrue(correct.correct)
        assertTrue(correct.repairKanji.isEmpty())
        assertEquals(listOf("拉"), outsideChoice.repairKanji)
    }

    @Test
    fun nullCardAndChoiceSignatureHandleSparseValues() {
        val planner = SimilarKanjiChoicePlanner()

        val nullCard = planner.evaluateSelection(null, " 拉 ")
        val nullSelection = planner.evaluateSelection(
            RecordsImportModels.SimilarKanjiChoiceCard("拉", "pull", listOf("拉", "提"), "拉\t提"),
            null
        )

        assertFalse(nullCard.correct)
        assertEquals(" 拉 ", nullCard.selectedKanji)
        assertTrue(nullCard.repairKanji.isEmpty())
        assertEquals("", nullSelection.selectedKanji)
        assertEquals(listOf("拉"), nullSelection.repairKanji)
        assertEquals("拉\t謎", SimilarKanjiChoicePlanner.choiceSignature(listOf(" 謎 ", null, "", "拉", "謎")))
        assertEquals("", SimilarKanjiChoicePlanner.choiceSignature(null))
    }

    @Test
    fun fallbackChoicesKeepTargetAndFirstThreeNeighborsInStoreOrder() {
        val choices = SimilarKanjiChoicePlanner.fallbackChoices(
            "裂",
            listOf(
                pair("裂", "列"),
                pair("裂", "烈"),
                pair("劣", "裂"),
                pair("裂", "例"),
                pair("裂", "列")
            )
        )

        assertEquals(listOf("裂", "列", "烈", "劣"), choices)
    }

    @Test
    fun fallbackChoicesAllowMissingPairs() {
        assertEquals(listOf("裂"), SimilarKanjiChoicePlanner.fallbackChoices("裂", null))
    }

    @Test
    fun choiceCardForSessionPrefersStoredDueCard() {
        val stored = RecordsImportModels.SimilarKanjiChoiceCard(
            "裂",
            "stored meaning",
            listOf("裂", "列"),
            "stored-signature"
        )

        val card = SimilarKanjiChoicePlanner.choiceCardForSession(
            stored,
            "謎",
            "fallback meaning",
            listOf(pair("謎", "迷"))
        )

        assertSame(stored, card)
    }

    @Test
    fun choiceCardForSessionBuildsFallbackCardFromPairsAndMeaning() {
        val card = SimilarKanjiChoicePlanner.choiceCardForSession(
            null,
            "裂",
            "split",
            listOf(
                pair("裂", "列"),
                pair("裂", "烈"),
                pair("劣", "裂"),
                pair("裂", "例"),
                pair("裂", "戻")
            )
        )

        assertEquals("裂", card.targetKanji)
        assertEquals("split", card.primaryMeaning)
        assertEquals(listOf("裂", "列", "烈", "劣"), card.choices)
        assertEquals("列\t劣\t烈\t裂", card.choiceSignature)
    }

    @Test
    fun sparsePairsAndMissingNeighborsAreSkipped() {
        val planner = SimilarKanjiChoicePlanner()
        val inventory = listOf(
            item("拉", "pull"),
            item("提", "carry"),
            item("謎", "riddle")
        )

        assertTrue(planner.buildCandidates(inventory, null).isEmpty())
        assertTrue(planner.buildCandidates(
            inventory,
            listOf(
                pair("拉", ""),
                pair("", "提"),
                pair("拉", "外"),
                pair("外", "提")
            )
        ).isEmpty())
    }

    private fun item(kanji: String, meaning: String): RecordsImportModels.KanjiInventoryItem =
        RecordsImportModels.KanjiInventoryItem(kanji, meaning, "", "", 1, 1, false, 0L)

    private fun pair(first: String, second: String): RecordsImportModels.SimilarKanjiPair =
        RecordsImportModels.SimilarKanjiPair(first, second, "fixture", 0L, 0L)

    private fun find(cards: List<RecordsImportModels.SimilarKanjiChoiceCard>, target: String): RecordsImportModels.SimilarKanjiChoiceCard =
        cards.firstOrNull { it.targetKanji == target } ?: error("No card for $target in ${cards.size}")

    private fun hasTarget(cards: List<RecordsImportModels.SimilarKanjiChoiceCard>, target: String): Boolean =
        cards.any { it.targetKanji == target }
}
