package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarKanjiExplanationPolicyTest {
    @Test
    fun explanationUsesPairEvidenceAndFailedSourceWords() {
        val explanation = SimilarKanjiExplanationPolicy.explain(
            " 拉 ",
            listOf(
                item("拉", "pull", "ら"),
                item("提", "carry", "てい"),
                item("謎", "riddle", "なぞ"),
            ),
            listOf(
                pair("拉", "提"),
                pair("拉", "謎"),
                pair("提", "外"),
                pair("拉", "拉"),
            ),
            listOf("  source one ", null, "", "source two", "source one"),
        )

        assertEquals("拉", explanation.targetKanji)
        assertEquals(listOf("提", "謎"), explanation.confusedWith)
        assertTrue(explanation.sharedComponents.isEmpty())
        assertTrue(explanation.differingComponents.isEmpty())
        assertEquals(listOf("拉: pull", "提: carry", "謎: riddle"), explanation.meaningClues)
        assertEquals(listOf("拉: ら", "提: てい", "謎: なぞ"), explanation.readingClues)
        assertEquals(listOf("source one", "source two"), explanation.failedSourceWords)
        assertEquals("Watch how 拉 differs from 提 and 謎.", explanation.watchThisPart)
        assertEquals(ExplanationConfidence.HIGH, explanation.confidence)
    }

    @Test
    fun explanationFallsBackConservativelyWhenMetadataIsMissing() {
        val explanation = SimilarKanjiExplanationPolicy.explain(
            "拉",
            null,
            listOf(pair("拉", "提")),
            null,
        )

        assertEquals("拉", explanation.targetKanji)
        assertEquals(listOf("提"), explanation.confusedWith)
        assertTrue(explanation.sharedComponents.isEmpty())
        assertTrue(explanation.differingComponents.isEmpty())
        assertTrue(explanation.meaningClues.isEmpty())
        assertTrue(explanation.readingClues.isEmpty())
        assertTrue(explanation.failedSourceWords.isEmpty())
        assertEquals("Watch how 拉 differs from 提.", explanation.watchThisPart)
        assertEquals(ExplanationConfidence.LOW, explanation.confidence)
    }

    @Test
    fun explanationFallsBackWhenNoPairEvidenceExists() {
        val explanation = SimilarKanjiExplanationPolicy.explain(
            "拉",
            listOf(item("拉", "pull", "ら")),
            emptyList(),
            emptyList(),
        )

        assertEquals("拉", explanation.targetKanji)
        assertTrue(explanation.confusedWith.isEmpty())
        assertTrue(explanation.sharedComponents.isEmpty())
        assertTrue(explanation.differingComponents.isEmpty())
        assertEquals(listOf("拉: pull"), explanation.meaningClues)
        assertEquals(listOf("拉: ら"), explanation.readingClues)
        assertTrue(explanation.failedSourceWords.isEmpty())
        assertEquals("Watch this kanji closely.", explanation.watchThisPart)
        assertEquals(ExplanationConfidence.LOW, explanation.confidence)
    }

    private fun item(kanji: String, meaning: String, readings: String): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(kanji, meaning, readings, "", 1, 1, false, 0L)
    }

    private fun pair(first: String, second: String): RecordsImportModels.SimilarKanjiPair {
        return RecordsImportModels.SimilarKanjiPair(first, second, "fixture", 0L, 0L)
    }
}
