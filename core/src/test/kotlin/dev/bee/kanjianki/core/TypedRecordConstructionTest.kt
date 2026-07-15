package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedRecordConstructionTest {
    @Test
    fun typedTaskMemoryFieldsMatchLegacyConstructorAndEncoding() {
        val legacy = RecordsStudyModels.TaskMemory(
            null,
            -10L,
            2.5,
            6.5,
            -3,
            -4,
            -5,
            null,
            -6,
            -7,
            -8L,
            -9L,
        )
        val typed = RecordsStudyModels.TaskMemory.fromFields(
            RecordsStudyModels.TaskMemory.Fields(
                state = null,
                dueAtMillis = -10L,
                stability = 2.5,
                difficulty = 6.5,
                totalReviews = -3,
                lapses = -4,
                learningStep = -5,
                lastRating = null,
                matureIntervalDays = -6,
                consecutivePasses = -7,
                lastPassedDueAtMillis = -8L,
                lastReviewedAtMillis = -9L,
            )
        )

        assertEquals(legacy.encode(), typed.encode())
        assertEquals("new\t0\t2.5\t6.5\t0\t0\t0\t\t0\t0\t0\t0", typed.encode())
    }

    @Test
    fun typedTaskMemoryFieldsMatchLegacyStudyFieldProjection() {
        val legacy = RecordsStudyModels.TaskMemory.fromStudyFields(
            "review",
            2_000L,
            3.5,
            4.5,
            6,
            2,
            3,
            12,
        )
        val typed = RecordsStudyModels.TaskMemory.fromFields(
            RecordsStudyModels.TaskMemory.Fields(
                state = "review",
                dueAtMillis = 2_000L,
                stability = 3.5,
                difficulty = 4.5,
                totalReviews = 6,
                lapses = 2,
                learningStep = 3,
                lastRating = "",
                matureIntervalDays = 12,
            )
        )

        assertEquals(legacy.encode(), typed.encode())
    }

    @Test
    fun typedReviewRequestFieldsMatchFullLegacyLayout() {
        val evidence = RecordsSchedulerModels.ReviewRequest.ReviewEvidence(
            "recognition",
            "visual_confusion",
            "choice",
            "列",
            "裂",
            "{\"version\":1}",
        )
        val legacy = RecordsSchedulerModels.ReviewRequest(
            "裂",
            "token",
            "hard",
            true,
            true,
            false,
            true,
            2,
            "write_kanji",
            "signature",
            "prompt",
            evidence,
        )
        val typed = RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = "裂",
                token = "token",
                rating = "hard",
                writingRequired = true,
                writingPassed = true,
                writingClean = false,
                manualOverride = true,
                hintsUsed = 2,
                taskType = "write_kanji",
                answerSignature = "signature",
                prompt = "prompt",
                evidence = evidence,
            )
        )

        assertReviewRequestEquals(legacy, typed)
    }

    @Test
    fun typedReviewRequestFieldsMatchShortLegacyLayout() {
        val legacy = RecordsSchedulerModels.ReviewRequest(
            "裂",
            "token",
            "good",
            true,
            true,
            false,
            3,
        )
        val typed = RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = "裂",
                token = "token",
                rating = "good",
                writingRequired = true,
                writingPassed = true,
                writingClean = true,
                manualOverride = false,
                hintsUsed = 3,
            )
        )

        assertReviewRequestEquals(legacy, typed)
    }

    private fun assertReviewRequestEquals(
        expected: RecordsSchedulerModels.ReviewRequest,
        actual: RecordsSchedulerModels.ReviewRequest,
    ) {
        assertEquals(expected.kanji, actual.kanji)
        assertEquals(expected.token, actual.token)
        assertEquals(expected.rating, actual.rating)
        assertEquals(expected.writingRequired, actual.writingRequired)
        assertEquals(expected.writingPassed, actual.writingPassed)
        assertEquals(expected.writingClean, actual.writingClean)
        assertEquals(expected.manualOverride, actual.manualOverride)
        assertEquals(expected.hintsUsed, actual.hintsUsed)
        assertEquals(expected.taskType, actual.taskType)
        assertEquals(expected.answerSignature, actual.answerSignature)
        assertEquals(expected.prompt, actual.prompt)
        assertEquals(expected.coreSkill, actual.coreSkill)
        assertEquals(expected.failureCause, actual.failureCause)
        assertEquals(expected.evidenceSource, actual.evidenceSource)
        assertEquals(expected.selectedAnswer, actual.selectedAnswer)
        assertEquals(expected.correctAnswer, actual.correctAnswer)
        assertEquals(expected.answerEvidenceJson, actual.answerEvidenceJson)
    }
}
