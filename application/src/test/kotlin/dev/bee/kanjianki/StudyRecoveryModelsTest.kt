package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRecoveryModelsTest {
    @Test
    fun activeAndPendingRecoveryRetainTheirDurableIdentityAndFallback() {
        val active = StudyActiveSessionSnapshot(
            sessionToken = "active-token",
            kanji = "裂",
            answerSignatureDigest = "digest",
            schedulerRevision = 7L,
            routingVersion = 2,
            taskType = "type_meaning",
            promptSource = StudyPromptSource.REASON_TEXT,
            sourceSyncFinishedAtMillis = 9_000L,
            similarChoiceSignatureDigest = "choices",
            typedDraft = "split",
            revealed = true,
        )
        val storedActive = StoredActiveStudyRecovery(
            snapshot = active,
            writeEpoch = "epoch-1",
            resumeOnOrdinaryLaunch = true,
            raw = "active-raw",
        )
        val pending = StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = "answered-token",
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = "split",
            ),
            kanji = "裂",
            taskType = "type_meaning",
            writingRequired = false,
            prompt = "meaning",
            answerSignature = "裂|分裂|ぶんれつ|split",
            schedulerRevision = 7L,
        )
        val storedPending = StoredPendingStudyRecovery(
            snapshot = pending,
            fallbackActive = active,
            fallbackWriteEpoch = storedActive.writeEpoch,
            resumeOnOrdinaryLaunch = false,
            raw = "pending-raw",
        )

        val recoveries: List<StoredStudyRecovery> = listOf(storedActive, storedPending)
        assertTrue(recoveries.first().resumeOnOrdinaryLaunch)
        assertEquals("pending-raw", recoveries.last().raw)
        assertSame(active, storedPending.fallbackActive)
        assertEquals(
            StudyPromptSource.PRIMARY_MEANING,
            active.copy(promptSource = StudyPromptSource.PRIMARY_MEANING).promptSource,
        )
    }
}
