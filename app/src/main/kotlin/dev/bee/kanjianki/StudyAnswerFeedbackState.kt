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
 * answered card mounted until one Continue action is accepted — an explicit
 * tap, or the automatic continue that self-graded submits arm via
 * [autoContinueOnApply]. The flag is transient (never snapshotted): a
 * process-death restore always falls back to the manual Continue button.
 */
class StudyAnswerFeedbackState private constructor(
    val sessionToken: String,
    initialPhase: StudyAnswerFeedbackPhase,
    initialOutcome: StudyAnswerOutcome?,
    initialSelectedAnswer: String,
) {
    private var changeObserver: (() -> Unit)? = null

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

    var autoContinueOnApply: Boolean = false
        private set

    val feedbackVisible: Boolean
        get() = phase != StudyAnswerFeedbackPhase.UNANSWERED

    val continueEnabled: Boolean
        get() = phase == StudyAnswerFeedbackPhase.APPLIED

    fun begin(
        answerOutcome: StudyAnswerOutcome,
        selectedAnswer: String = "",
        autoContinue: Boolean = false,
    ): Boolean {
        if (phase != StudyAnswerFeedbackPhase.UNANSWERED) {
            return false
        }
        outcome = answerOutcome
        this.selectedAnswer = selectedAnswer
        autoContinueOnApply = autoContinue
        phase = StudyAnswerFeedbackPhase.SUBMITTING
        notifyChanged()
        return true
    }

    fun markApplied(token: String): Boolean {
        if (token != sessionToken || phase != StudyAnswerFeedbackPhase.SUBMITTING) {
            return false
        }
        phase = StudyAnswerFeedbackPhase.APPLIED
        notifyChanged()
        return true
    }

    fun resetForRetry(token: String): Boolean {
        if (token != sessionToken || phase != StudyAnswerFeedbackPhase.SUBMITTING) {
            return false
        }
        outcome = null
        selectedAnswer = ""
        autoContinueOnApply = false
        phase = StudyAnswerFeedbackPhase.UNANSWERED
        notifyChanged()
        return true
    }

    fun tryContinue(): Boolean {
        if (phase != StudyAnswerFeedbackPhase.APPLIED) {
            return false
        }
        phase = StudyAnswerFeedbackPhase.CONTINUED
        notifyChanged()
        return true
    }

    /** Keep the mounted observable state retryable when the durable Continue transition loses CAS. */
    fun rollbackContinue(): Boolean {
        if (phase != StudyAnswerFeedbackPhase.CONTINUED) {
            return false
        }
        phase = StudyAnswerFeedbackPhase.APPLIED
        notifyChanged()
        return true
    }

    fun snapshot(): StudyAnswerFeedbackSnapshot {
        return StudyAnswerFeedbackSnapshot(sessionToken, phase, outcome, selectedAnswer)
    }

    /** The retained state holder uses this to mirror mutable Compose state into StateFlow. */
    internal fun observeChanges(observer: (() -> Unit)?) {
        changeObserver = observer
    }

    private fun notifyChanged() {
        changeObserver?.invoke()
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
