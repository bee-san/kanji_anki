package dev.bee.kanjianki.study

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudySession

/**
 * What a key press means in a study session, as a pure decision.
 *
 * Goal 195's keyboard shortcuts with their focus guards, separated from the Compose
 * plumbing so the decision is testable without a window: given the visible session,
 * the pressed key, the modifiers, and whether a text field holds focus, this returns
 * the action to dispatch — or null when the key is not a study shortcut and must fall
 * through to whatever else wants it (the shell's Escape-is-back, a text field's own
 * typing).
 *
 * The guards are the point, not the mapping:
 * - **Escape never commits a grade.** It is the shell's back, so this returns null for
 *   it and lets the shell claim it — a study shortcut that graded on Escape would
 *   turn "leave this card" into "fail this card".
 * - **P/F fire only where Pass/Fail is visible**, which means only on a self-graded
 *   card whose answer is shown, and never while a text field has focus — otherwise
 *   typing "possible" into the typed-meaning card would grade it twice.
 * - **Space/Enter is the visible primary action**, which is Reveal before an answer
 *   and Continue after one — the same button the mouse would click.
 * - **Ctrl/Cmd+Z is undo**, only when the session reports a reversible card.
 *
 * Double-commit safety is the session's `acceptsGrade`/`acceptsContinue`, checked here
 * as it is at the button: a key-repeat that fires twice cannot grade twice because the
 * second event sees a session that no longer accepts one.
 */
internal object StudyKeyboard {
    fun actionFor(
        event: StudyKeyEvent,
        session: StudySession,
        textFieldFocused: Boolean,
    ): KaniAction? {
        // Only key-down, so a press-and-release is one action, not two.
        if (!event.isKeyDown) return null

        // Undo first: Ctrl/Cmd+Z is unambiguous and independent of the card state,
        // beyond the session reporting something to reverse.
        if ((event.ctrl || event.meta) && event.key == Key.Z) {
            return if (session.undoable) KaniAction.Study.Undo else null
        }

        // Every remaining shortcut is an unmodified key; a modified combination is not
        // ours, so it falls through rather than being swallowed.
        if (event.ctrl || event.meta) return null

        return when (event.key) {
            // The visible primary action. Continue once a grade is applied; otherwise
            // the card's own primary — Reveal for a self-graded card that has not been
            // turned over. A typed card has no keyboard primary here: Enter is the
            // field's submit, handled by its own IME action while it holds focus.
            Key.Spacebar, Key.Enter, Key.NumPadEnter -> primaryAction(session, textFieldFocused)

            // Pass/Fail only where they are visible and only when not typing.
            Key.P -> gradeIfPassFailVisible(session, textFieldFocused) { it.passRating() }
            Key.F -> gradeIfPassFailVisible(session, textFieldFocused) { it.failRating() }

            else -> null
        }
    }

    private fun primaryAction(session: StudySession, textFieldFocused: Boolean): KaniAction? {
        if (session.acceptsContinue) return KaniAction.Study.Continue
        // A focused text field owns Space and Enter for its own input.
        if (textFieldFocused) return null
        // Reveal is the primary on an unrevealed self-graded card. A choice or writing
        // card has no reveal, and a typed card's primary is its field, so neither has a
        // Space/Enter primary here.
        val card = session.card
        return if (card is StudyCard.Flashcard && session.acceptsGrade) KaniAction.Study.Reveal else null
    }

    private inline fun gradeIfPassFailVisible(
        session: StudySession,
        textFieldFocused: Boolean,
        rating: (StudyCard) -> String?,
    ): KaniAction? {
        if (textFieldFocused || !session.acceptsGrade) return null
        val card = session.card ?: return null
        // Pass/Fail is shown on a self-graded flashcard once revealed and on a writing
        // card; a choice card grades by picking and a typed card by submitting, so
        // neither offers a P/F key. The reveal state is UI-local, so P before reveal is
        // dropped rather than grading a face-down card.
        val r = rating(card) ?: return null
        return KaniAction.Study.Grade(rating = r)
    }

    private fun StudyCard.passRating(): String? = when (this) {
        is StudyCard.Flashcard -> pass.rating
        is StudyCard.Writing -> (saveHard ?: pass).rating
        is StudyCard.Choice, is StudyCard.Typed -> null
    }

    private fun StudyCard.failRating(): String? = when (this) {
        is StudyCard.Flashcard -> fail.rating
        is StudyCard.Writing -> fail.rating
        is StudyCard.Choice, is StudyCard.Typed -> null
    }
}

/**
 * A key press reduced to what [StudyKeyboard] needs.
 *
 * A plain value rather than a Compose [KeyEvent] so the decision is testable without
 * constructing platform key events, which differ between the Skiko and Robolectric
 * hosts and cannot be built portably.
 */
internal data class StudyKeyEvent(
    val key: Key,
    val isKeyDown: Boolean,
    val ctrl: Boolean = false,
    val meta: Boolean = false,
)

internal fun KeyEvent.toStudyKeyEvent(): StudyKeyEvent = StudyKeyEvent(
    key = key,
    isKeyDown = type == KeyEventType.KeyDown,
    ctrl = isCtrlPressed,
    meta = isMetaPressed,
)

/**
 * Applies the study keyboard shortcuts to a subtree.
 *
 * [textFieldFocused] gates the letter and space keys: while a typed card's field holds
 * focus it is `true`, so typing reaches the field instead of grading the card. The
 * handler claims an event only when it produced an action, so a key it does not map
 * (Escape, an arrow, a modifier chord) still reaches the shell and the focused field.
 */
internal fun Modifier.studyKeyboardShortcuts(
    session: StudySession,
    textFieldFocused: Boolean,
    dispatch: (KaniAction) -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    val action = StudyKeyboard.actionFor(event.toStudyKeyEvent(), session, textFieldFocused)
    if (action != null) {
        dispatch(action)
        true
    } else {
        false
    }
}
