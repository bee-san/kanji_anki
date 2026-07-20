package dev.bee.kanjianki

/** Mutable Study answer state owned by the retained session holder. */
internal interface StudyAnswerStateStore {
    fun activeSessionToken(): String?

    fun feedbackFor(sessionToken: String): StudyAnswerFeedbackState

    fun feedbackChanged()
}

/** Durable recovery operations required before a review is allowed to enter the IO queue. */
internal interface StudyAnswerPersistence {
    fun persistPending(state: StudyAnswerFeedbackState): Boolean

    fun restoreAfterRejectedAnswer(sessionToken: String)
}

/**
 * Serializes the UI-side answer gate before the existing transactional review pipeline.
 * Scheduler state is still advanced only by an APPLIED review commit; a rejected enqueue or
 * failed recovery CAS restores the same mounted card to UNANSWERED.
 */
internal class StudyAnswerSubmissionCoordinator(
    private val stateStore: StudyAnswerStateStore,
    private val persistence: StudyAnswerPersistence,
) {
    fun submit(
        correct: Boolean,
        selectedAnswer: String,
        enqueueReview: () -> Boolean,
    ): Boolean {
        val token = stateStore.activeSessionToken() ?: return false
        val state = stateStore.feedbackFor(token)
        val outcome = if (correct) StudyAnswerOutcome.CORRECT else StudyAnswerOutcome.INCORRECT
        if (!state.begin(outcome, selectedAnswer)) return false
        stateStore.feedbackChanged()

        if (!persistence.persistPending(state)) {
            state.resetForRetry(token)
            stateStore.feedbackChanged()
            return false
        }

        val accepted = enqueueReview()
        if (!accepted) {
            state.resetForRetry(token)
            stateStore.feedbackChanged()
            persistence.restoreAfterRejectedAnswer(token)
        }
        return accepted
    }
}
