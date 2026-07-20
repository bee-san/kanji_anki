package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyItemComparatorsTest {
    @Test
    fun orderedAndUnorderedQueueComparisonsKeepTheirDistinctContracts() {
        val first = item("脱", "sig-a")
        val second = item("出", "sig-b")

        assertTrue(StudyItemComparators.sameStudyQueue(listOf(first, second), listOf(first, second)))
        assertFalse(StudyItemComparators.sameStudyQueue(listOf(first, second), listOf(second, first)))
        assertTrue(
            StudyItemComparators.sameStudyItemsIgnoringOrder(
                listOf(first, second),
                listOf(second, first),
            ),
        )
        assertFalse(StudyItemComparators.sameStudyItemsIgnoringOrder(listOf(first), listOf(first, second)))
        assertFalse(
            StudyItemComparators.sameStudyItemsIgnoringOrder(
                listOf(first, first),
                listOf(first, first),
            ),
        )
    }

    @Test
    fun persistedComparisonIgnoresReadAnnotationsAndRevisionOnly() {
        val current = item("脱", "sig")
        val annotated = current.copyBuilder()
            .schedulerRevision(99L)
            .hasSimilarKanji(true)
            .hasKanjiReading(true)
            .hasReadingKanji(true)
            .hasSentenceReading(true)
            .build()

        assertTrue(StudyItemComparators.samePersistedState(current, annotated))
        assertFalse(
            StudyItemComparators.samePersistedState(
                current,
                annotated.copyBuilder().dueAtMillis(current.dueAtMillis + 1L).build(),
            ),
        )
    }

    @Test
    fun appliedReviewSnapshotKeepsExactItems() {
        val before = item("脱", "sig")
        val after = before.copyBuilder().totalReviews(2).build()
        val snapshot = AppliedReviewSnapshot("token", before, after)

        assertSame(before, snapshot.beforeReview)
        assertSame(after, snapshot.afterReview)
        assertTrue(snapshot.token == "token")
    }

    private fun item(kanji: String, signature: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            2.0,
            5.0,
            1,
            0,
            0,
            1,
            null,
            10L,
        ).copyBuilder()
            .answerSignature(signature)
            .build()
    }
}
