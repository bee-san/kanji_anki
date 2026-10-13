package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCommitModelsTest {
    @Test
    fun resultFactoriesExposeTheirDisposition() {
        val item = studyItem()
        val applied = ReviewCommitResult.applied(item)
        val duplicate = ReviewCommitResult.duplicate()
        val stale = ReviewCommitResult.stale()

        assertEquals(ReviewCommitDisposition.APPLIED, applied.disposition)
        assertSame(item, applied.item)
        assertTrue(applied.applied())
        assertEquals(ReviewCommitDisposition.DUPLICATE, duplicate.disposition)
        assertNull(duplicate.item)
        assertFalse(duplicate.applied())
        assertEquals(ReviewCommitDisposition.STALE, stale.disposition)
        assertNull(stale.item)
        assertFalse(stale.applied())
        assertEquals(
            ReviewCommitDisposition.STALE,
            ReviewCommitDisposition.valueOf("STALE"),
        )
        assertEquals(3, ReviewCommitDisposition.entries.size)
    }

    @Test
    fun commandCarriesEvidenceAndPersistsTheNextRevision() {
        val before = studyItem().copyBuilder()
            .answerSignature("signature")
            .schedulerRevision(8L)
            .build()
        val after = before.copyBuilder().totalReviews(2).build()
        val timing = ReviewTaskTiming(
            "task-key",
            "休",
            "kanji_meaning",
            1_000L,
            2_000L,
            750L,
            "good",
        )
        val choiceLog = ReviewChoiceLog(
            "休",
            "休|体|木",
            "休",
            true,
            "similar_kanji",
            2_000L,
        )
        val similarChoice = SimilarChoiceCommit(
            RecordsImportModels.SimilarKanjiChoiceCard(
                "休",
                "rest",
                listOf("休", "体", "木"),
                "休|体|木",
            ),
            "休",
            2_000L,
        )
        val command = ReviewCommitCommand(
            afterReview = after,
            request = request(before, "review-token"),
            appliedRating = "good",
            reviewedAtMillis = 2_000L,
            beforeReview = before,
            taskTiming = timing,
            choiceLog = choiceLog,
            similarChoice = similarChoice,
        )

        assertEquals(8L, command.expectedRevision)
        assertEquals("task-key", command.taskTiming?.taskKey)
        assertTrue(command.choiceLog?.correct == true)
        assertEquals("休", command.similarChoice?.selectedAnswer)
        assertEquals(9L, command.persistedItem().schedulerRevision)
        assertEquals(2, command.persistedItem().totalReviews)
    }

    @Test
    fun commandRejectsEmptyToken() {
        val before = studyItem()

        val error = assertThrows(IllegalArgumentException::class.java) {
            ReviewCommitCommand(
                afterReview = before,
                request = request(before, ""),
                appliedRating = "good",
                reviewedAtMillis = 2_000L,
                beforeReview = before,
            )
        }

        assertEquals("A review commit requires a non-empty token", error.message)
    }

    @Test
    fun commandRejectsChangedItemIdentity() {
        val before = studyItem()
        val after = before.copyBuilder().apply { kanji = "体" }.build()

        val error = assertThrows(IllegalArgumentException::class.java) {
            ReviewCommitCommand(
                afterReview = after,
                request = request(before, "review-token"),
                appliedRating = "good",
                reviewedAtMillis = 2_000L,
                beforeReview = before,
            )
        }

        assertEquals("Review item identity changed", error.message)
    }

    @Test
    fun commandRejectsChangedAnswerSignature() {
        val before = studyItem().copyBuilder().answerSignature("before").build()
        val after = before.copyBuilder().answerSignature("after").build()

        val error = assertThrows(IllegalArgumentException::class.java) {
            ReviewCommitCommand(
                afterReview = after,
                request = request(before, "review-token"),
                appliedRating = "good",
                reviewedAtMillis = 2_000L,
                beforeReview = before,
            )
        }

        assertEquals("Review answer signature changed", error.message)
    }

    @Test
    fun persistedRevisionFailsClosedOnOverflow() {
        val before = studyItem().copyBuilder().schedulerRevision(Long.MAX_VALUE).build()
        val command = ReviewCommitCommand(
            afterReview = before,
            request = request(before, "review-token"),
            appliedRating = "good",
            reviewedAtMillis = 2_000L,
            beforeReview = before,
        )

        assertThrows(ArithmeticException::class.java) {
            command.persistedItem()
        }
    }

    private fun request(
        item: RecordsStudyModels.StudyItem,
        token: String,
    ): RecordsSchedulerModels.ReviewRequest =
        RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = item.kanji,
                token = token,
                rating = "good",
                writingRequired = false,
                writingPassed = true,
                writingClean = true,
                manualOverride = false,
                hintsUsed = 0,
                taskType = "kanji_meaning",
                answerSignature = item.answerSignature,
                prompt = "rest",
            ),
        )

    private fun studyItem(): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            "休",
            "review",
            1_000L,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "",
            900L,
        )
}
