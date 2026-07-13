package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StudyAnswerOutcome {
    CORRECT,
    INCORRECT,
}

/**
 * One-card UI gate between grading and navigation.
 *
 * The scheduler remains token-idempotent; this state additionally keeps the
 * answered card mounted until one explicit Continue action is accepted.
 */
class StudyAnswerFeedbackState(val sessionToken: String) {
    private enum class Phase {
        UNANSWERED,
        SUBMITTING,
        APPLIED,
        CONTINUED,
    }

    private var phase by mutableStateOf(Phase.UNANSWERED)

    var outcome by mutableStateOf<StudyAnswerOutcome?>(null)
        private set

    val feedbackVisible: Boolean
        get() = phase != Phase.UNANSWERED

    val continueEnabled: Boolean
        get() = phase == Phase.APPLIED

    fun begin(answerOutcome: StudyAnswerOutcome): Boolean {
        if (phase != Phase.UNANSWERED) {
            return false
        }
        outcome = answerOutcome
        phase = Phase.SUBMITTING
        return true
    }

    fun markApplied(token: String): Boolean {
        if (token != sessionToken || phase != Phase.SUBMITTING) {
            return false
        }
        phase = Phase.APPLIED
        return true
    }

    fun resetForRetry(token: String): Boolean {
        if (token != sessionToken || phase != Phase.SUBMITTING) {
            return false
        }
        outcome = null
        phase = Phase.UNANSWERED
        return true
    }

    fun tryContinue(): Boolean {
        if (phase != Phase.APPLIED) {
            return false
        }
        phase = Phase.CONTINUED
        return true
    }
}
