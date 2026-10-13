package dev.bee.kanjianki.presentation

/**
 * A study session, as portable data both hosts render.
 *
 * The Android host drove study from `MainActivityStudy` and a dozen `*Model` types
 * built inline; this is the same session as one value. The scheduler that decides
 * what the next card is, what a rating means, and when the session is complete stays
 * in `:application`/`:core`/`:sync-engine` — a leaf feature module never reaches it.
 * A host maps its authoritative snapshot (`StudyRouteSnapshot`, the current
 * `StudySession`, the feedback state) to this, and the surface only lays it out.
 *
 * [state] is the whole session's shape; [card] is what to show when a card is active.
 * They are separate because the empty, done, and loading states have no card, and a
 * card with no session state could not say whether its grade had been applied yet.
 */
data class StudySession(
    val state: StudySessionState,
    val progress: StudyProgress = StudyProgress(),
    val card: StudyCard? = null,
    val feedback: StudyFeedback = StudyFeedback(),
    /** Undo is offered only when the host reports a committed card it can reverse. */
    val undoable: Boolean = false,
) {
    /**
     * True when a grade may be submitted right now.
     *
     * A card must be present and unanswered: a second grade on an answered card is
     * the double-commit the guarded controls exist to prevent, and the reducer must
     * not let a key-repeat or a double-click through.
     */
    val acceptsGrade: Boolean
        get() = state == StudySessionState.CARD &&
            card != null &&
            feedback.phase == StudyFeedbackPhase.UNANSWERED

    /** True when the one Continue action past a graded card should be offered. */
    val acceptsContinue: Boolean
        get() = feedback.phase == StudyFeedbackPhase.APPLIED
}

/**
 * The session's top-level shape.
 *
 * Mirrors the reachable-to-the-UI subset of `:application`'s `StudySessionPhase`:
 * loading before the first card, a card to grade, done when the target is met, empty
 * when nothing was due, and error when a load failed. The intermediate submitting and
 * advancing phases are folded into [StudyFeedback.phase] on the card, because the UI
 * shows them as a state of the visible card, not a separate screen.
 */
enum class StudySessionState {
    LOADING,
    CARD,
    DONE,
    EMPTY,
    ERROR,
}

/**
 * How far through the session the user is.
 *
 * [displayedTarget] and [remaining] restate the arithmetic `StudyRouteSnapshot`
 * exposed, so the surface does not recompute it and the two hosts cannot disagree on
 * the count above the card.
 */
data class StudyProgress(
    val completed: Int = 0,
    val target: Int = 0,
    val activeCard: Boolean = false,
) {
    init {
        require(completed >= 0) { "completed must not be negative" }
        require(target >= completed) { "target must be at least completed" }
    }

    /**
     * The target as shown, which is one past the stored target while a card beyond it
     * is still active — the "N of N+1" the Android snapshot showed so a learn-ahead
     * repeat does not read as already finished.
     */
    val displayedTarget: Int
        get() = if (activeCard && completed == target && target < Int.MAX_VALUE) target + 1 else target

    val remaining: Int
        get() = target - completed
}

/**
 * The one-card gate between grading and navigation.
 *
 * The portable shape of `:application`'s `StudyAnswerFeedbackState`: the scheduler is
 * token-idempotent, and this additionally keeps the answered card mounted until one
 * explicit Continue is accepted. [outcome] is null until a grade is applied;
 * [selected] is the choice the user picked, for the multiple-choice cards that
 * highlight it.
 */
data class StudyFeedback(
    val phase: StudyFeedbackPhase = StudyFeedbackPhase.UNANSWERED,
    val outcome: StudyOutcome? = null,
    val selected: String = "",
) {
    /** True once a card has been answered, whatever the outcome. */
    val visible: Boolean
        get() = phase != StudyFeedbackPhase.UNANSWERED
}

/** The feedback lifecycle, matching `StudyAnswerFeedbackPhase`. */
enum class StudyFeedbackPhase {
    UNANSWERED,
    SUBMITTING,
    APPLIED,
    CONTINUED,
}

/** Whether the answer was right, for the feedback colouring. */
enum class StudyOutcome {
    CORRECT,
    INCORRECT,
}
