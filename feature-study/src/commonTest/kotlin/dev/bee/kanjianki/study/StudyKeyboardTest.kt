package dev.bee.kanjianki.study

import androidx.compose.ui.input.key.Key
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.StudyFeedback
import dev.bee.kanjianki.presentation.StudyFeedbackPhase
import dev.bee.kanjianki.presentation.StudyOutcome
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudyKeyboardTest {
    @Test
    fun spaceRevealsAnUnrevealedFlashcardAndEnterDoesTheSame() {
        val session = card(flashcard())
        assertEquals(KaniAction.Study.Reveal, action(Key.Spacebar, session))
        assertEquals(KaniAction.Study.Reveal, action(Key.Enter, session))
        assertEquals(KaniAction.Study.Reveal, action(Key.NumPadEnter, session))
    }

    @Test
    fun spaceContinuesOnceAGradeHasBeenApplied() {
        val applied = card(
            flashcard(),
            feedback = StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.CORRECT),
        )
        // Continue is the visible primary once answered, and it wins over reveal.
        assertEquals(KaniAction.Study.Continue, action(Key.Spacebar, applied))
    }

    @Test
    fun pAndFGradeAFlashcardPassAndFail() {
        val session = card(flashcard())
        assertEquals(KaniAction.Study.Grade(rating = "good"), action(Key.P, session))
        assertEquals(KaniAction.Study.Grade(rating = "again"), action(Key.F, session))
    }

    @Test
    fun pAndFDoNothingWhileATextFieldHasFocus() {
        // The focus guard: typing "possible" or "fine" into the typed-meaning card must
        // not grade it. This is the whole reason the guard exists.
        val session = card(typedCard())
        assertNull(action(Key.P, session, textFieldFocused = true))
        assertNull(action(Key.F, session, textFieldFocused = true))
        // And Space/Enter reach the field rather than revealing.
        assertNull(action(Key.Spacebar, session, textFieldFocused = true))
    }

    @Test
    fun pAndFDoNothingOnACardThatHasNoPassFail() {
        // A choice card grades by picking and a typed card by submitting; neither
        // offers a P/F key, so the letters fall through rather than grading.
        assertNull(action(Key.P, card(choiceCard())))
        assertNull(action(Key.F, card(choiceCard())))
        assertNull(action(Key.P, card(typedCard())))
    }

    @Test
    fun pOnACloseWritingAttemptSubmitsHard() {
        // The primary pass on a CLOSE writing attempt is Save hard, submitting hard —
        // the keyboard follows the button.
        val session = card(writingCard(close = true))
        assertEquals(KaniAction.Study.Grade(rating = "hard"), action(Key.P, session))
        assertEquals(KaniAction.Study.Grade(rating = "again"), action(Key.F, session))
    }

    @Test
    fun aSecondGradeIsDroppedOnceOneIsInFlight() {
        // The double-commit guard, as the keyboard sees it: a key-repeat that fires
        // while a grade is submitting hits a session that no longer accepts one.
        val submitting = card(flashcard(), feedback = StudyFeedback(StudyFeedbackPhase.SUBMITTING))
        assertNull(action(Key.P, submitting))
        assertNull(action(Key.F, submitting))
    }

    @Test
    fun escapeIsNeverAStudyAction() {
        // Escape is the shell's back; grading on it would turn leaving into failing.
        assertNull(action(Key.Escape, card(flashcard())))
        assertNull(action(Key.Escape, card(flashcard()), textFieldFocused = true))
        assertNull(
            action(Key.Escape, card(flashcard(), feedback = StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.CORRECT))),
        )
    }

    @Test
    fun ctrlOrCmdZUndoesOnlyWhenTheSessionReportsAReversibleCard() {
        val undoable = card(flashcard()).copy(undoable = true)
        assertEquals(KaniAction.Study.Undo, action(Key.Z, undoable, ctrl = true))
        assertEquals(KaniAction.Study.Undo, action(Key.Z, undoable, meta = true))
        // Nothing to undo: the chord falls through.
        assertNull(action(Key.Z, card(flashcard()), ctrl = true))
        // Undo survives a text field having focus — it is not a typing key.
        assertEquals(KaniAction.Study.Undo, action(Key.Z, undoable, ctrl = true, textFieldFocused = true))
    }

    @Test
    fun aModifiedShortcutKeyThatIsNotUndoFallsThrough() {
        // Ctrl+P is a print chord, not a pass; swallowing it would be surprising.
        assertNull(action(Key.P, card(flashcard()), ctrl = true))
        assertNull(action(Key.Spacebar, card(flashcard()), meta = true))
    }

    @Test
    fun aKeyUpProducesNoActionSoAPressAndReleaseIsOneEvent() {
        assertNull(
            StudyKeyboard.actionFor(
                StudyKeyEvent(key = Key.P, isKeyDown = false),
                card(flashcard()),
                textFieldFocused = false,
            ),
        )
    }

    private fun action(
        key: Key,
        session: StudySession,
        ctrl: Boolean = false,
        meta: Boolean = false,
        textFieldFocused: Boolean = false,
    ): KaniAction? = StudyKeyboard.actionFor(
        StudyKeyEvent(key = key, isKeyDown = true, ctrl = ctrl, meta = meta),
        session,
        textFieldFocused,
    )

    private fun card(
        card: dev.bee.kanjianki.presentation.StudyCard,
        feedback: StudyFeedback = StudyFeedback(),
    ) = StudySession(state = StudySessionState.CARD, card = card, feedback = feedback)
}
