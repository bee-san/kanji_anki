package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnswerSubmissionCoordinatorTest {
    @Test
    fun persistenceFailureNeverEnqueuesAndRestoresUnansweredState() {
        val stateStore = FakeStateStore("token")
        val persistence = FakePersistence(persisted = false)
        var enqueueCount = 0
        val coordinator = StudyAnswerSubmissionCoordinator(stateStore, persistence)

        val accepted = coordinator.submit(true, "good") {
            enqueueCount += 1
            true
        }

        assertFalse(accepted)
        assertEquals(0, enqueueCount)
        assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, stateStore.feedback.snapshot().phase)
    }

    @Test
    fun rejectedEnqueueRestoresRecoveryAndAllowsRetry() {
        val stateStore = FakeStateStore("token")
        val persistence = FakePersistence(persisted = true)
        val coordinator = StudyAnswerSubmissionCoordinator(stateStore, persistence)

        assertFalse(coordinator.submit(false, "again") { false })

        assertEquals(listOf("token"), persistence.restoredTokens)
        assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, stateStore.feedback.snapshot().phase)
        assertTrue(coordinator.submit(true, "good") { true })
        assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, stateStore.feedback.snapshot().phase)
    }

    @Test
    fun duplicateSubmitIsRejectedWhileFirstReviewIsPending() {
        val stateStore = FakeStateStore("token")
        val coordinator = StudyAnswerSubmissionCoordinator(
            stateStore,
            FakePersistence(persisted = true),
        )

        assertTrue(coordinator.submit(true, "good") { true })
        assertFalse(coordinator.submit(true, "good") { true })
    }

    private class FakeStateStore(token: String) : StudyAnswerStateStore {
        private val token = token
        val feedback = StudyAnswerFeedbackState(token)

        override fun activeSessionToken(): String = token

        override fun feedbackFor(sessionToken: String): StudyAnswerFeedbackState = feedback

        override fun feedbackChanged() = Unit
    }

    private class FakePersistence(
        private val persisted: Boolean,
    ) : StudyAnswerPersistence {
        val restoredTokens = mutableListOf<String>()

        override fun persistPending(state: StudyAnswerFeedbackState): Boolean = persisted

        override fun restoreAfterRejectedAnswer(sessionToken: String) {
            restoredTokens += sessionToken
        }
    }
}
