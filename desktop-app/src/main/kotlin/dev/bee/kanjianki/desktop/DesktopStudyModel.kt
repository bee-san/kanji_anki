package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.StudyAnswerFeedbackPhase
import dev.bee.kanjianki.StudyAnswerFeedbackSnapshot
import dev.bee.kanjianki.StudyAnswerOutcome
import dev.bee.kanjianki.StudyRouteSnapshot
import dev.bee.kanjianki.StudySessionPhase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTaskCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyFeedback
import dev.bee.kanjianki.presentation.StudyFeedbackPhase
import dev.bee.kanjianki.presentation.StudyGradeAction
import dev.bee.kanjianki.presentation.StudyOutcome
import dev.bee.kanjianki.presentation.StudyProgress
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import dev.bee.kanjianki.presentation.UiText

/**
 * Turns the scheduler's session plus the route snapshot into the portable
 * [StudySession] the shared surface renders.
 *
 * Every string is Android's, reached through the same `:core` copy Android's
 * `MainActivityStudyFlashcard` called — `StudyTextCopy`, `StudyTaskCopy`. The scheduler
 * decides which card is next and what a grade means; this only names what that card
 * shows. That shared call path is the parity claim: both hosts render the same card
 * from the same functions.
 *
 * This maps the deterministic non-writing families the selector produces without an
 * extra query — recognition, font, word/sentence reading, and typed meaning/reading.
 * The multiple-choice families (`meaning_kanji`, `similar_kanji`, `reading_kanji`,
 * `kanji_reading`) need the separate choice-data snapshot and are mapped in a later
 * step; until then a choice task falls back to a self-graded flashcard on the same
 * subject, which is what the Android flashcard path does when choice data is absent.
 * The writing rung is Goal 196's ink surface; a writing task maps to the portable
 * [StudyCard.Writing] carrying its grades, but desktop cannot present one until the
 * capability work lands.
 */
internal object DesktopStudyModel {
    /**
     * The portable session for the current route.
     *
     * [session] is the scheduler's current card, or null when the queue is drained.
     * [route] carries the phase, feedback, and progress the state machine tracks —
     * the same snapshot Android renders the top bar and completion state from.
     */
    fun session(
        session: RecordsSchedulerModels.StudySession?,
        route: StudyRouteSnapshot,
        undoable: Boolean,
    ): StudySession {
        val feedback = feedback(route.feedback)
        return StudySession(
            state = state(session, route),
            progress = progress(route),
            card = session?.let(::card),
            feedback = feedback,
            undoable = undoable,
        )
    }

    private fun state(
        session: RecordsSchedulerModels.StudySession?,
        route: StudyRouteSnapshot,
    ): StudySessionState = when {
        route.phase == StudySessionPhase.LOADING -> StudySessionState.LOADING
        route.isComplete -> StudySessionState.DONE
        session != null -> StudySessionState.CARD
        // No card and not loading or complete is a drained queue that never had one —
        // nothing was due. A load failure is the shell's ERROR, recorded before this.
        else -> StudySessionState.EMPTY
    }

    private fun progress(route: StudyRouteSnapshot): StudyProgress {
        val progress = route.progress
        return StudyProgress(
            completed = progress.completedCount,
            target = progress.targetCount.coerceAtLeast(progress.completedCount),
            activeCard = progress.activeTask,
        )
    }

    private fun feedback(snapshot: StudyAnswerFeedbackSnapshot?): StudyFeedback {
        if (snapshot == null) return StudyFeedback()
        return StudyFeedback(
            phase = when (snapshot.phase) {
                StudyAnswerFeedbackPhase.UNANSWERED -> StudyFeedbackPhase.UNANSWERED
                StudyAnswerFeedbackPhase.SUBMITTING -> StudyFeedbackPhase.SUBMITTING
                StudyAnswerFeedbackPhase.APPLIED -> StudyFeedbackPhase.APPLIED
                StudyAnswerFeedbackPhase.CONTINUED -> StudyFeedbackPhase.CONTINUED
            },
            outcome = when (snapshot.outcome) {
                StudyAnswerOutcome.CORRECT -> StudyOutcome.CORRECT
                StudyAnswerOutcome.INCORRECT -> StudyOutcome.INCORRECT
                null -> null
            },
            selected = snapshot.selectedAnswer,
        )
    }

    private fun card(session: RecordsSchedulerModels.StudySession): StudyCard {
        val subject = session.item?.kanji ?: session.prompt
        return when {
            StudyTaskCopy.isRepairWritingTask(session) ||
                session.writingRequired -> writingCard(session, subject)
            StudyTaskCopy.isTypingMeaningTask(session) ||
                StudyTaskCopy.isTypingReadingTask(session) -> typedCard(session, subject)
            StudyTaskCopy.isSentenceReadingTask(session) -> sentenceCard(session, subject)
            else -> flashcard(session, subject)
        }
    }

    /** A recognition/reading flashcard: a prompt to recall, an answer to reveal. */
    private fun flashcard(
        session: RecordsSchedulerModels.StudySession,
        subject: String,
    ): StudyCard.Flashcard = StudyCard.Flashcard(
        prompt = UiText.Literal(promptGlyph(session, subject)),
        subject = subject,
        answer = UiText.Literal(answerText(session)),
        pass = pass(),
        fail = fail(),
    )

    /** The sentence-reading ceiling: a small mined-sentence front. */
    private fun sentenceCard(
        session: RecordsSchedulerModels.StudySession,
        subject: String,
    ): StudyCard.Flashcard = StudyCard.Flashcard(
        prompt = UiText.Literal(StudyTextCopy.sentencePrompt(session)),
        subject = subject,
        answer = UiText.Literal(sentenceAnswer(session)),
        pass = pass(),
        fail = fail(),
        emphasizeSubjectInPrompt = true,
    )

    private fun typedCard(
        session: RecordsSchedulerModels.StudySession,
        subject: String,
    ): StudyCard.Typed = StudyCard.Typed(
        prompt = UiText.Literal(StudyTextCopy.heroQuestion(session)),
        subject = subject,
        answer = UiText.Literal(answerText(session)),
        // One submit: the host resolves it to good/again from the typed text against
        // the expected answer. Carrying `good` here names the pass side; the review
        // path downgrades to `again` on a mismatch, matching Android's submit.
        submit = grade(StudyRatings.GOOD),
        inputLabel = UiText.Literal(
            if (StudyTaskCopy.isTypingReadingTask(session)) StudyTextCopy.readingLabel() else StudyTextCopy.meaningLabel(),
        ),
    )

    private fun writingCard(
        session: RecordsSchedulerModels.StudySession,
        subject: String,
    ): StudyCard.Writing = StudyCard.Writing(
        prompt = UiText.Literal(StudyTextCopy.heroQuestion(session)),
        subject = subject,
        pass = pass(),
        fail = fail(),
    )

    /** The glyph or word on the front, from the same selector Android uses. */
    private fun promptGlyph(
        session: RecordsSchedulerModels.StudySession,
        subject: String,
    ): String = if (StudyTaskCopy.isWordReadingTask(session)) {
        StudyTextCopy.wordPrompt(session)
    } else {
        subject
    }

    /** The revealed back: reading for a reading task, meaning otherwise. */
    private fun answerText(session: RecordsSchedulerModels.StudySession): String =
        if (StudyTaskCopy.isWordReadingTask(session) || StudyTaskCopy.isTypingReadingTask(session)) {
            StudyTextCopy.collectionReadingForSession(session)
        } else {
            StudyTextCopy.collectionMeaningForSession(session)
        }

    private fun sentenceAnswer(session: RecordsSchedulerModels.StudySession): String {
        val reading = StudyTextCopy.collectionReadingForSession(session)
        val word = StudyTextCopy.sentenceReadingWord(session)
        return listOf(reading, word).filter { it.isNotBlank() }.joinToString("  ")
    }

    /** Pass maps to good, Fail to again — the boundary the shell keeps opaque. */
    private fun pass(): StudyGradeAction = grade(StudyRatings.GOOD)

    private fun fail(): StudyGradeAction = grade(StudyRatings.AGAIN)

    private fun grade(rating: String): StudyGradeAction =
        StudyGradeAction(label = UiText.EMPTY, rating = rating)
}
