package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class StudyReviewRequestPolicyTest {
    @Test
    fun mapsWritingOutcomeIntoReviewPayload() {
        val session = session("書", true, BridgeScheduler.TASK_WRITE_KANJI)

        val mapped = StudyReviewRequestPolicy.from(
            session,
            StudyReviewRequestPolicy.writingOutcome(true, false, StudyRatings.HARD),
            2,
            StudyRatings.EASY,
            false
        )
        val request = mapped.request()

        assertEquals(StudyRatings.HARD, mapped.ratingCode())
        assertEquals(StudyRatings.HARD, request.rating)
        assertEquals("書", request.kanji)
        assertEquals("session-token", request.token)
        assertTrue(request.writingRequired)
        assertTrue(request.writingPassed)
        assertFalse(request.writingClean)
        assertFalse(request.manualOverride)
        assertEquals(2, request.hintsUsed)
        assertEquals(BridgeScheduler.TASK_WRITE_KANJI, request.taskType)
        assertEquals("answer-signature", request.answerSignature)
        assertEquals("prompt text", request.prompt)
    }

    @Test
    fun respectsManualOverrideAndNonWritingTasks() {
        val writingSession = session("筆", true, BridgeScheduler.TASK_WRITE_KANJI)
        val readingSession = session("読", false, BridgeScheduler.TASK_WORD_READING)

        val override = StudyReviewRequestPolicy.from(writingSession, null, 0, StudyRatings.EASY, true)
        val nonWriting = StudyReviewRequestPolicy.from(readingSession, null, 0, StudyRatings.GOOD, false)

        assertEquals(StudyRatings.EASY, override.ratingCode())
        assertFalse(override.request().writingPassed)
        assertTrue(override.request().manualOverride)
        assertEquals(StudyRatings.GOOD, nonWriting.ratingCode())
        assertTrue(nonWriting.request().writingPassed)
        assertFalse(nonWriting.request().writingClean)
    }

    @Test
    fun distinguishesCleanPassAndFailedWritingOutcomes() {
        val writingSession = session("清", true, BridgeScheduler.TASK_WRITE_KANJI)

        val clean = StudyReviewRequestPolicy.from(
            writingSession,
            StudyReviewRequestPolicy.writingOutcome(true, true, StudyRatings.GOOD),
            1,
            StudyRatings.GOOD,
            false
        )
        val fail = StudyReviewRequestPolicy.from(
            writingSession,
            StudyReviewRequestPolicy.writingOutcome(false, false, StudyRatings.AGAIN),
            3,
            StudyRatings.GOOD,
            false
        )

        assertTrue(clean.request().writingPassed)
        assertTrue(clean.request().writingClean)
        assertEquals(StudyRatings.GOOD, clean.ratingCode())
        assertFalse(fail.request().writingPassed)
        assertFalse(fail.request().writingClean)
        assertEquals(StudyRatings.AGAIN, fail.ratingCode())
        assertEquals(3, fail.request().hintsUsed)
    }

    @Test
    fun defaultsUnknownRatingsToAgain() {
        val writingSession = session("迷", true, BridgeScheduler.TASK_WRITE_KANJI)

        val mapped = StudyReviewRequestPolicy.from(writingSession, null, 4, "not-a-rating", false)

        assertEquals(StudyRatings.AGAIN, mapped.ratingCode())
        assertEquals(StudyRatings.AGAIN, mapped.request().rating)
        assertFalse(mapped.request().writingPassed)
        assertEquals(4, mapped.request().hintsUsed)
    }

    @Test
    fun capsRequestedRatingAtWritingOutcomeCeiling() {
        val writingSession = session("線", true, BridgeScheduler.TASK_WRITE_KANJI)

        val hardCap = StudyReviewRequestPolicy.from(
            writingSession,
            StudyReviewRequestPolicy.writingOutcome(true, false, StudyRatings.HARD),
            0,
            StudyRatings.EASY,
            false
        )
        val goodCap = StudyReviewRequestPolicy.from(
            writingSession,
            StudyReviewRequestPolicy.writingOutcome(true, true, StudyRatings.GOOD),
            0,
            StudyRatings.EASY,
            false
        )
        val easyCap = StudyReviewRequestPolicy.from(
            writingSession,
            StudyReviewRequestPolicy.writingOutcome(true, true, StudyRatings.EASY),
            0,
            StudyRatings.EASY,
            false
        )

        assertEquals(StudyRatings.HARD, hardCap.ratingCode())
        assertEquals(StudyRatings.GOOD, goodCap.ratingCode())
        assertEquals(StudyRatings.EASY, easyCap.ratingCode())
    }

    @Test
    fun policyResultConstructorsStayPrivate() {
        assertTrue(Modifier.isPrivate(
            StudyReviewRequestPolicy.WritingOutcome::class.java
                .getDeclaredConstructor(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, String::class.java)
                .modifiers
        ))
        assertTrue(Modifier.isPrivate(
            StudyReviewRequestPolicy.MappedReview::class.java
                .getDeclaredConstructor(RecordsSchedulerModels.ReviewRequest::class.java, String::class.java)
                .modifiers
        ))
    }

    private fun session(kanji: String, writingRequired: Boolean, taskType: String): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(
            kanji,
            "new",
            1234L,
            0.0,
            0.0,
            0,
            0,
            0,
            0,
            0,
            0,
            0L,
            writingRequired,
            "",
            0L,
            0,
            "answer-signature",
            "active-token",
            100L
        )
        val row = RecordsImportModels.DashboardRow(
            kanji,
            null,
            "collection meaning",
            "ご",
            kanji,
            1,
            "reason",
            "Needs practice",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )
        return RecordsSchedulerModels.StudySession(item, row, "session-token", taskType, writingRequired, "prompt text")
    }
}
