package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyCommandsTest {
    @Test
    fun theAnkiCompatibleDefaultsBindTheKeysAnAnkiUserWouldReachFor() {
        // Pinned literally because these are the keys muscle memory presses: 1 is
        // Again/Fail and 3 is Good/Pass in Anki's reviewer, and space is the answer.
        assertEquals(StudyCommand.PRIMARY, command(StudyKey.SPACE))
        assertEquals(StudyCommand.PRIMARY, command(StudyKey.ENTER))
        assertEquals(StudyCommand.PRIMARY, command(StudyKey.NUMPAD_ENTER))
        assertEquals(StudyCommand.GRADE_FAIL, command(StudyKey.DIGIT_1))
        assertEquals(StudyCommand.GRADE_PASS, command(StudyKey.DIGIT_3))
        // Goal 195's letters stay bound: they are already shipped, and dropping them
        // would break a shortcut a user has learned.
        assertEquals(StudyCommand.GRADE_FAIL, command(StudyKey.F))
        assertEquals(StudyCommand.GRADE_PASS, command(StudyKey.P))
    }

    @Test
    fun kaniExposesNoSelectableHardOrEasySoTwoAndFourStayUnbound() {
        // Anki's 2 and 4 are Hard and Easy. Kani's study UI offers Pass and Fail only,
        // and its one `hard` is the writing rung's Save hard — which is the pass button
        // relabelled by the ink evaluator, not a rating the user picks. Binding 2 or 4
        // to anything would make the keyboard grade differently from every button.
        assertNull(command(StudyKey.DIGIT_2))
        assertNull(command(StudyKey.DIGIT_4))
        assertNull(command(StudyKey.NUMPAD_2))
        assertNull(command(StudyKey.NUMPAD_4))
        assertEquals(
            listOf("primary", "grade_pass", "grade_fail", "undo"),
            StudyCommand.entries.map(StudyCommand::id),
        )
    }

    @Test
    fun theNumpadIsBoundAlongsideTheNumberRow() {
        // A user on a numeric keypad is pressing the same key as far as intent goes,
        // and matching on the digit keeps the mapping independent of layout.
        for (command in listOf(StudyCommand.GRADE_FAIL, StudyCommand.GRADE_PASS)) {
            val digits = StudyKeybindings.DEFAULT.strokesFor(command)
                .mapNotNull { it.key.digit }
            assertEquals(2, digits.size, command.id)
            assertEquals(1, digits.toSet().size, command.id)
        }
    }

    @Test
    fun everyKeystrokeAsksForExactlyOneCommandAndBindingsAreStablyOrdered() {
        val bindings = StudyKeybindings.DEFAULT.bindings
        // The map shape is the conflict guarantee: two commands cannot claim one
        // keystroke, because a stroke is a key in the map.
        assertEquals(bindings.size, bindings.keys.size)
        // A Settings row and a menu accelerator must not name a different key on each
        // launch, so the order the strokes come back in is fixed.
        assertEquals(
            listOf(StudyKey.SPACE, StudyKey.ENTER, StudyKey.NUMPAD_ENTER),
            StudyKeybindings.DEFAULT.strokesFor(StudyCommand.PRIMARY).map(StudyKeystroke::key),
        )
        assertEquals(emptyList(), StudyKeybindings(emptyMap()).strokesFor(StudyCommand.UNDO))
    }

    @Test
    fun undoIsBoundToBothCtrlZAndCmdZSoNeitherHostNeedsToAsk() {
        // Meta is Command on macOS and Super elsewhere; accepting both is correct on
        // every host, because neither chord means anything else in a session.
        assertEquals(
            StudyCommand.UNDO,
            StudyKeybindings.DEFAULT.commandFor(StudyKeystroke(StudyKey.Z, ctrl = true)),
        )
        assertEquals(
            StudyCommand.UNDO,
            StudyKeybindings.DEFAULT.commandFor(StudyKeystroke(StudyKey.Z, meta = true)),
        )
        // The bare letter is not undo: Z on its own is a printable key.
        assertNull(command(StudyKey.Z))
    }

    @Test
    fun escapeIsNotEvenABindableStudyKey() {
        // Escape is the shell's back. A study binding that graded on it would turn
        // "leave this card" into "fail this card", so the key does not exist here.
        assertNull(StudyKey.fromToken("escape"))
        assertNull(StudyKey.fromToken("esc"))
    }

    @Test
    fun storedIdsAndTokensRoundTripAndFailClosedOnAnythingElse() {
        for (entry in StudyCommand.entries) {
            assertEquals(entry, StudyCommand.fromId(entry.id))
            assertEquals(entry, StudyCommand.fromId("  ${entry.id.uppercase()}  "))
        }
        for (key in StudyKey.entries) {
            assertEquals(key, StudyKey.fromToken(key.token))
            assertEquals(key, StudyKey.fromToken(" ${key.token.uppercase()} "))
        }
        // A stored binding this build no longer knows must fall open to the reviewed
        // defaults, never resolve to a neighbouring command that would grade instead.
        for (unknown in listOf(null, "", "  ", "grade_hard", "GRADE-PASS")) {
            assertNull(StudyCommand.fromId(unknown))
        }
        for (unknown in listOf(null, "", "  ", "f13", "NUMPAD-1")) {
            assertNull(StudyKey.fromToken(unknown))
        }
    }

    @Test
    fun onlyEnterIsNonPrintableSoOnlyEnterMayFireInsideAnEditor() {
        assertFalse(StudyKey.ENTER.isTextInput)
        assertFalse(StudyKey.NUMPAD_ENTER.isTextInput)
        for (key in StudyKey.entries - setOf(StudyKey.ENTER, StudyKey.NUMPAD_ENTER)) {
            assertTrue(key.isTextInput, key.token)
        }
    }

    @Test
    fun aReleaseOrAnAutoRepeatIsTheSameIntentAndIsDropped() {
        val session = revealedFlashcard()
        // A press and its release are one intent, and a held key is one intent too;
        // either duplicate would commit a second grade.
        assertNull(
            StudyKeyboardPolicy.actionFor(
                StudyKeyPress(StudyKeystroke(StudyKey.DIGIT_3), isKeyDown = false),
                session,
                revealed(),
            ),
        )
        assertNull(
            StudyKeyboardPolicy.actionFor(
                StudyKeyPress(StudyKeystroke(StudyKey.DIGIT_3), isRepeat = true),
                session,
                revealed(),
            ),
        )
        assertEquals(grade("good"), action(StudyKey.DIGIT_3, session, revealed()))
    }

    @Test
    fun aTextFieldAnImeAndAModalOwnTheirOwnKeysFirst() {
        val typed = session(typedCard())
        // Typing "possible" into the typed-meaning card must not pass it, and typing
        // "3" must not fail it.
        assertNull(action(StudyKey.P, typed, StudyInputContext(textFieldFocused = true)))
        assertNull(action(StudyKey.DIGIT_3, typed, StudyInputContext(textFieldFocused = true)))
        assertNull(action(StudyKey.SPACE, typed, StudyInputContext(textFieldFocused = true)))
        // Mid-composition, Enter and Space commit the IME candidate, not the card.
        assertNull(
            action(
                StudyKey.ENTER,
                typed,
                StudyInputContext(textFieldFocused = true, imeComposing = true),
            ),
        )
        // A dialog or an open menu owns everything, including the chords.
        val modal = StudyInputContext(modalActive = true)
        assertNull(action(StudyKey.DIGIT_1, revealedFlashcard(), modal))
        assertNull(action(StudyKey.Z, undoable(), modal, ctrl = true))
    }

    @Test
    fun undoStillWorksInsideATextFieldBecauseAChordIsNotTyping() {
        // The card that most needs undo is the one with a focused field; dropping every
        // key there would take it away.
        assertEquals(
            KaniAction.Study.Undo,
            action(
                StudyKey.Z,
                undoable(),
                StudyInputContext(textFieldFocused = true),
                ctrl = true,
            ),
        )
        assertNull(action(StudyKey.Z, session(flashcard()), revealed(), ctrl = true))
    }

    @Test
    fun spaceRevealsAFaceDownCardAndThenSubmitsThePassItReveals() {
        val faceDown = session(flashcard())
        assertEquals(KaniAction.Study.Reveal, action(StudyKey.SPACE, faceDown))
        assertEquals(KaniAction.Study.Reveal, action(StudyKey.ENTER, faceDown))
        // Space on the answer side is Good, exactly as in Anki's reviewer.
        assertEquals(grade("good"), action(StudyKey.SPACE, faceDown, revealed()))
    }

    @Test
    fun noKeyGradesAnAnswerTheUserHasNotSeenYet() {
        // The reveal state is UI-local, so a digit pressed at a face-down card is
        // dropped rather than judging a card whose answer is still hidden.
        val faceDown = session(flashcard())
        assertNull(action(StudyKey.DIGIT_1, faceDown))
        assertNull(action(StudyKey.DIGIT_3, faceDown))
        assertNull(action(StudyKey.P, faceDown))
        assertNull(action(StudyKey.F, faceDown))
    }

    @Test
    fun theSameKeyContinuesOncePersistedFeedbackIsApplied() {
        val applied = session(
            flashcard(),
            feedback = StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.CORRECT),
        )
        // Continue is what the visible primary button became, and it wins over the
        // grade the same key submits before an answer.
        assertEquals(KaniAction.Study.Continue, action(StudyKey.SPACE, applied, revealed()))
        assertEquals(KaniAction.Study.Continue, action(StudyKey.ENTER, applied))
    }

    @Test
    fun aSecondGradeIsDroppedWhileTheFirstIsStillInFlight() {
        // The double-commit guard as the keyboard sees it: a session that is submitting
        // accepts no grade, so a second press reaches nothing.
        val submitting = session(
            flashcard(),
            feedback = StudyFeedback(StudyFeedbackPhase.SUBMITTING),
        )
        assertNull(action(StudyKey.DIGIT_3, submitting, revealed()))
        assertNull(action(StudyKey.DIGIT_1, submitting, revealed()))
        assertNull(action(StudyKey.SPACE, submitting, revealed()))
    }

    @Test
    fun digitsSelectTheVisibleChoicesAndNothingBeyondThem() {
        val choice = session(choiceCard())
        // On a choice card picking *is* grading, so a digit takes the option's own
        // grade rather than the pass or fail the same digit means elsewhere.
        assertEquals(grade("again"), action(StudyKey.DIGIT_1, choice))
        assertEquals(grade("again"), action(StudyKey.DIGIT_2, choice))
        assertEquals(grade("good"), action(StudyKey.DIGIT_3, choice))
        assertEquals(grade("good"), action(StudyKey.NUMPAD_3, choice))
        // A digit past the end of a three-choice card is not a fourth answer.
        assertNull(action(StudyKey.DIGIT_4, choice))
        // And nothing else on a choice card grades it: there is no reveal and no
        // pass/fail key, because every grade is a visible option.
        assertNull(action(StudyKey.SPACE, choice))
        assertNull(action(StudyKey.P, choice))
        assertNull(action(StudyKey.F, choice))
    }

    @Test
    fun aModifiedDigitDoesNotPickAChoice() {
        // Ctrl+3 is somebody else's chord — a tab switch, a zoom — not the third answer.
        assertNull(action(StudyKey.DIGIT_3, session(choiceCard()), ctrl = true))
        assertNull(action(StudyKey.DIGIT_3, session(choiceCard()), StudyInputContext(), meta = true))
    }

    @Test
    fun enterSubmitsATypedAnswerOnlyFromTheFieldHoldingIt() {
        val typed = session(typedCard())
        // Enter is the field's submit and is not printable, so it reaches the card.
        assertEquals(
            grade("good"),
            action(StudyKey.ENTER, typed, StudyInputContext(textFieldFocused = true)),
        )
        assertEquals(
            grade("good"),
            action(StudyKey.NUMPAD_ENTER, typed, StudyInputContext(textFieldFocused = true)),
        )
        // With nothing focused there is no input to submit, so there is nothing to do.
        assertNull(action(StudyKey.ENTER, typed))
        // And a typed card has no pass or fail key at all: it is graded from its text.
        assertNull(action(StudyKey.DIGIT_3, typed))
        assertNull(action(StudyKey.DIGIT_1, typed))
    }

    @Test
    fun theWritingRungOffersOnlyWhatItsVisibleRowOffers() {
        val writing = session(writingCard())
        assertEquals(grade("good"), action(StudyKey.DIGIT_3, writing))
        assertEquals(grade("again"), action(StudyKey.DIGIT_1, writing))
        // A CLOSE attempt's primary is Save hard, chosen by the ink evaluator; the
        // keyboard follows the button rather than exposing a second rating.
        assertEquals(grade("hard"), action(StudyKey.DIGIT_3, session(writingCard(close = true))))
        // No reveal and no primary: a space that graded a writing card would be a way
        // to pass without writing.
        assertNull(action(StudyKey.SPACE, writing))
        assertNull(action(StudyKey.ENTER, writing))
    }

    @Test
    fun aSessionWithNoCardOffersNothingButUndo() {
        val done = StudySession(state = StudySessionState.DONE, undoable = true)
        assertNull(action(StudyKey.SPACE, done))
        assertNull(action(StudyKey.DIGIT_3, done))
        assertEquals(KaniAction.Study.Undo, action(StudyKey.Z, done, ctrl = true))
        // A CARD state with no card is not a card either, however the host got there.
        val cardless = StudySession(state = StudySessionState.CARD)
        assertNull(action(StudyKey.SPACE, cardless))
        assertNull(action(StudyKey.DIGIT_1, cardless, revealed()))
    }

    @Test
    fun commandLookupAnswersWithoutASessionSoMenusCanLabelAccelerators() {
        assertEquals(
            StudyCommand.GRADE_PASS,
            StudyKeyboardPolicy.commandFor(press(StudyKey.DIGIT_3)),
        )
        // The precedence rules apply to the lookup too, or a menu would advertise an
        // accelerator that an editor is currently swallowing.
        assertNull(
            StudyKeyboardPolicy.commandFor(
                press(StudyKey.DIGIT_3),
                StudyInputContext(textFieldFocused = true),
            ),
        )
        assertNull(StudyKeyboardPolicy.commandFor(press(StudyKey.DIGIT_2)))
    }

    @Test
    fun aRemappedBindingDispatchesTheSameGuardedActionAsTheDefaultOne() {
        // The point of the command layer: remapping changes which key asks, never what
        // happens, and never which guard applies.
        val remapped = StudyKeybindings(
            mapOf(StudyKeystroke(StudyKey.J) to StudyCommand.GRADE_PASS),
        )
        assertEquals(
            grade("good"),
            StudyKeyboardPolicy.actionFor(
                press(StudyKey.J),
                revealedFlashcard(),
                revealed(),
                remapped,
            ),
        )
        // Still not before the reveal, and still not while typing.
        assertNull(
            StudyKeyboardPolicy.actionFor(press(StudyKey.J), revealedFlashcard(), bindings = remapped),
        )
        assertNull(
            StudyKeyboardPolicy.actionFor(
                press(StudyKey.J),
                revealedFlashcard(),
                StudyInputContext(textFieldFocused = true, answerRevealed = true),
                remapped,
            ),
        )
        // And the default key it replaced no longer asks for anything.
        assertNull(
            StudyKeyboardPolicy.actionFor(
                press(StudyKey.DIGIT_3),
                revealedFlashcard(),
                revealed(),
                remapped,
            ),
        )
    }

    @Test
    fun everyActionTheKeyboardCanProduceIsOneAVisibleControlAlsoDispatches() {
        // The parity claim, checked rather than asserted in prose: the pass, fail, and
        // submit actions come off the card the buttons are built from, and the
        // remaining two are the reveal and continue buttons.
        val flashcard = flashcard()
        val produced = setOf(
            action(StudyKey.SPACE, session(flashcard)),
            action(StudyKey.DIGIT_3, session(flashcard), revealed()),
            action(StudyKey.DIGIT_1, session(flashcard), revealed()),
            action(StudyKey.ENTER, session(typedCard()), StudyInputContext(textFieldFocused = true)),
            action(StudyKey.DIGIT_1, session(choiceCard())),
            action(
                StudyKey.SPACE,
                session(flashcard, feedback = StudyFeedback(StudyFeedbackPhase.APPLIED)),
                revealed(),
            ),
            action(StudyKey.Z, undoable(), ctrl = true),
        )
        val fromControls = setOf(
            KaniAction.Study.Reveal,
            flashcard.pass.action,
            flashcard.fail.action,
            typedCard().submit.action,
            choiceCard().choices.first().grade.action,
            KaniAction.Study.Continue,
            KaniAction.Study.Undo,
        )
        assertEquals(fromControls, produced)
    }

    private fun command(key: StudyKey): StudyCommand? =
        StudyKeybindings.DEFAULT.commandFor(StudyKeystroke(key))

    private fun press(key: StudyKey, ctrl: Boolean = false, meta: Boolean = false) =
        StudyKeyPress(StudyKeystroke(key, ctrl = ctrl, meta = meta))

    private fun action(
        key: StudyKey,
        session: StudySession,
        context: StudyInputContext = StudyInputContext(),
        ctrl: Boolean = false,
        meta: Boolean = false,
    ): KaniAction? = StudyKeyboardPolicy.actionFor(press(key, ctrl, meta), session, context)

    private fun revealed() = StudyInputContext(answerRevealed = true)

    private fun grade(rating: String) = KaniAction.Study.Grade(rating = rating)

    private fun session(
        card: StudyCard,
        feedback: StudyFeedback = StudyFeedback(),
    ) = StudySession(state = StudySessionState.CARD, card = card, feedback = feedback)

    private fun revealedFlashcard() = session(flashcard())

    private fun undoable() = session(flashcard()).copy(undoable = true)

    private fun flashcard() = StudyCard.Flashcard(
        prompt = UiText.Literal("脱"),
        subject = "脱",
        answer = UiText.Literal("escape"),
        pass = StudyGradeAction(UiText.EMPTY, "good"),
        fail = StudyGradeAction(UiText.EMPTY, "again"),
    )

    private fun typedCard() = StudyCard.Typed(
        prompt = UiText.Literal("脱"),
        subject = "脱",
        answer = UiText.Literal("escape"),
        submit = StudyGradeAction(UiText.EMPTY, "good"),
    )

    private fun choiceCard() = StudyCard.Choice(
        prompt = UiText.Literal("だつ"),
        subject = "脱",
        choices = listOf("説", "鋭", "脱").map { value ->
            StudyChoice(
                value = value,
                label = UiText.Literal(value),
                grade = StudyGradeAction(UiText.EMPTY, if (value == "脱") "good" else "again"),
            )
        },
        correct = "脱",
    )

    private fun writingCard(close: Boolean = false) = StudyCard.Writing(
        prompt = UiText.Literal("write 脱"),
        subject = "脱",
        pass = StudyGradeAction(UiText.EMPTY, "good"),
        fail = StudyGradeAction(UiText.EMPTY, "again"),
        saveHard = if (close) StudyGradeAction(UiText.EMPTY, "hard") else null,
    )
}
