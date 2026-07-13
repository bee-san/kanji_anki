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
}
