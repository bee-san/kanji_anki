package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StudyAnswerOutcome {
    CORRECT,
    INCORRECT,
}

enum class StudyAnswerFeedbackPhase {
    UNANSWERED,
    SUBMITTING,
    APPLIED,
    CONTINUED,
}

data class StudyAnswerFeedbackSnapshot(
    val sessionToken: String,
    val phase: StudyAnswerFeedbackPhase,
    val outcome: StudyAnswerOutcome?,
    val selectedAnswer: String,
)

/**
 * One-card UI gate between grading and navigation.
 *
 * The scheduler remains token-idempotent; this state additionally keeps the
 * answered card mounted until one explicit Continue action is accepted.
 */
class StudyAnswerFeedbackState private constructor(
    val sessionToken: String,
    initialPhase: StudyAnswerFeedbackPhase,
    initialOutcome: StudyAnswerOutcome?,
    initialSelectedAnswer: String,
) {
    constructor(sessionToken: String) : this(
        sessionToken,
        StudyAnswerFeedbackPhase.UNANSWERED,
        null,
        "",
    )

    private var phase by mutableStateOf(initialPhase)

    var outcome by mutableStateOf(initialOutcome)
        private set

    var selectedAnswer by mutableStateOf(initialSelectedAnswer)
        private set

    val feedbackVisible: Boolean
        get() = phase != StudyAnswerFeedbackPhase.UNANSWERED

    val continueEnabled: Boolean
        get() = phase == StudyAnswerFeedbackPhase.APPLIED

    fun begin(answerOutcome: StudyAnswerOutcome, selectedAnswer: String = ""): Boolean {
        if (phase != StudyAnswerFeedbackPhase.UNANSWERED) {
            return false
        }
        outcome = answerOutcome
        this.selectedAnswer = selectedAnswer
        phase = StudyAnswerFeedbackPhase.SUBMITTING
        return true
    }

    fun markApplied(token: String): Boolean {
        if (token != sessionToken || phase != StudyAnswerFeedbackPhase.SUBMITTING) {
            return false
        }
        phase = StudyAnswerFeedbackPhase.APPLIED
        return true
    }

    fun resetForRetry(token: String): Boolean {
        if (token != sessionToken || phase != StudyAnswerFeedbackPhase.SUBMITTING) {
            return false
        }
        outcome = null
        selectedAnswer = ""
        phase = StudyAnswerFeedbackPhase.UNANSWERED
        return true
    }

    fun tryContinue(): Boolean {
        if (phase != StudyAnswerFeedbackPhase.APPLIED) {
            return false
        }
        phase = StudyAnswerFeedbackPhase.CONTINUED
        return true
    }

    fun snapshot(): StudyAnswerFeedbackSnapshot {
        return StudyAnswerFeedbackSnapshot(sessionToken, phase, outcome, selectedAnswer)
    }

    companion object {
        fun restore(snapshot: StudyAnswerFeedbackSnapshot): StudyAnswerFeedbackState {
            return StudyAnswerFeedbackState(
                snapshot.sessionToken,
                snapshot.phase,
                snapshot.outcome,
                snapshot.selectedAnswer,
            )
        }
    }
}
