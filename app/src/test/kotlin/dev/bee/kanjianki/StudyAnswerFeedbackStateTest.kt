package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnswerFeedbackStateTest {
    @Test
    fun answeredCardWaitsForAppliedReviewAndAdvancesOnlyOnce() {
        val state = StudyAnswerFeedbackState("token-裂")

        assertTrue(state.begin(StudyAnswerOutcome.CORRECT))
        assertEquals(StudyAnswerOutcome.CORRECT, state.outcome)
        assertTrue(state.feedbackVisible)
        assertFalse(state.continueEnabled)
        assertFalse(state.begin(StudyAnswerOutcome.INCORRECT))

        assertTrue(state.markApplied("token-裂"))
        assertTrue(state.continueEnabled)
        assertTrue(state.tryContinue())
        assertFalse(state.tryContinue())
    }

    @Test
    fun failedOrStaleReviewCannotUnlockContinue() {
        val state = StudyAnswerFeedbackState("token-裂")

        assertTrue(state.begin(StudyAnswerOutcome.INCORRECT))
        assertFalse(state.markApplied("another-token"))
        assertFalse(state.continueEnabled)

        assertTrue(state.resetForRetry("token-裂"))
        assertNull(state.outcome)
        assertFalse(state.feedbackVisible)
        assertTrue(state.begin(StudyAnswerOutcome.CORRECT))
    }

    @Test
    fun appliedAnswerSnapshotRestoresFeedbackAndSelectedAnswer() {
        val state = StudyAnswerFeedbackState("token-裂")
        assertTrue(state.begin(StudyAnswerOutcome.INCORRECT, selectedAnswer = "列"))
        assertTrue(state.markApplied("token-裂"))

        val restored = StudyAnswerFeedbackState.restore(state.snapshot())

        assertEquals("token-裂", restored.sessionToken)
        assertEquals(StudyAnswerOutcome.INCORRECT, restored.outcome)
        assertEquals("列", restored.selectedAnswer)
        assertTrue(restored.feedbackVisible)
        assertTrue(restored.continueEnabled)
        assertTrue(restored.tryContinue())
        assertFalse(restored.tryContinue())
    }

    @Test
    fun autoContinueFlagIsSetByBeginAndClearedByRetry() {
        val state = StudyAnswerFeedbackState("token-裂")
        assertFalse(state.autoContinueOnApply)

        assertTrue(state.begin(StudyAnswerOutcome.CORRECT, autoContinue = true))
        assertTrue(state.autoContinueOnApply)

        assertTrue(state.resetForRetry("token-裂"))
        assertFalse(state.autoContinueOnApply)

        assertTrue(state.begin(StudyAnswerOutcome.CORRECT))
        assertFalse(state.autoContinueOnApply)
    }

    @Test
    fun autoContinueFlagIsTransientAcrossSnapshotRestore() {
        val state = StudyAnswerFeedbackState("token-裂")
        assertTrue(state.begin(StudyAnswerOutcome.CORRECT, autoContinue = true))
        assertTrue(state.markApplied("token-裂"))

        val restored = StudyAnswerFeedbackState.restore(state.snapshot())

        assertFalse(restored.autoContinueOnApply)
        assertTrue(restored.continueEnabled)
    }

    @Test
    fun failedDurableContinueCanRollbackTheMountedStateForRetry() {
        val state = StudyAnswerFeedbackState("token-裂")
        assertTrue(state.begin(StudyAnswerOutcome.CORRECT))
        assertTrue(state.markApplied("token-裂"))
        assertTrue(state.tryContinue())

        assertTrue(state.rollbackContinue())

        assertTrue(state.continueEnabled)
        assertFalse(state.rollbackContinue())
        assertTrue(state.tryContinue())
        assertFalse(state.continueEnabled)
    }
}
