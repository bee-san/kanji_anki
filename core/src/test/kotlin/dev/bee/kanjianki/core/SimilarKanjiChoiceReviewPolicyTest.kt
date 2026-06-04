package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimilarKanjiChoiceReviewPolicyTest {
    @Test
    fun correctChoicePassesCardAndIncrementsCorrectCount() {
        val update = SimilarKanjiChoiceReviewPolicy.reviewUpdate(
            card(2, 3),
            result(true),
            5000L,
        )

        assertEquals(5000L, update.lastReviewedAtMillis())
        assertEquals(5000L, update.passedAtMillis())
        assertNull(update.dueAtMillis())
        assertEquals(Integer.valueOf(3), update.correctCount())
        assertNull(update.wrongCount())
    }

    @Test
    fun wrongChoiceKeepsCardDueAndIncrementsWrongCount() {
        val update = SimilarKanjiChoiceReviewPolicy.reviewUpdate(
            card(2, 3),
            result(false),
            6000L,
        )

        assertEquals(6000L, update.lastReviewedAtMillis())
        assertEquals(0L, update.passedAtMillis())
        assertEquals(6000L, update.dueAtMillis())
        assertNull(update.correctCount())
        assertEquals(Integer.valueOf(4), update.wrongCount())
    }

    private fun card(correctCount: Int, wrongCount: Int): RecordsImportModels.SimilarKanjiChoiceCard {
        return RecordsImportModels.SimilarKanjiChoiceCard(
            "拉",
            "pull",
            listOf("拉", "提"),
            "拉\t提",
            1000L,
            0L,
            0L,
            correctCount,
            wrongCount,
        )
    }

    private fun result(correct: Boolean): RecordsImportModels.SimilarKanjiChoiceResult {
        return RecordsImportModels.SimilarKanjiChoiceResult(
            card(2, 3),
            if (correct) "拉" else "提",
            correct,
            emptyList(),
        )
    }
}
