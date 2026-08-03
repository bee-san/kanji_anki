package dev.bee.kanjianki.study

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.StudyFeedback
import dev.bee.kanjianki.presentation.StudyFeedbackPhase
import dev.bee.kanjianki.presentation.StudyOutcome
import dev.bee.kanjianki.presentation.StudyProgress
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The study session's render assertions, shared by both hosts exactly as Home's are.
 *
 * They assert structure, reachability, which action each control dispatches, and the
 * one property that matters most for study — that a card cannot be double-committed.
 * Not pixels: what a screen owes its host is the action, and what the action means is
 * `:presentation-api`'s to decide and test.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertLoadingShowsASpinnerAndNoCard() {
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.LOADING), studyCopy(), TestUiTextResolver, dispatch = {}) },
    ) {
        onNodeWithTag(STUDY_LOADING_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(STUDY_CARD_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STUDY_PROGRESS_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheDoneScreenOffersOnlyAWayHome() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.DONE), studyCopy(), TestUiTextResolver, dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(STUDY_DONE_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(STUDY_DONE_HOME_TEST_TAG).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Navigation.Open(KaniDestination.Home)),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheEmptyScreenNamesItsOwnCause() {
    val copy = studyCopy()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.EMPTY), copy, TestUiTextResolver, dispatch = {}) },
    ) {
        assertEquals(
            "${copy.emptyTitle}. ${copy.emptyBody}",
            onNodeWithTag(STUDY_EMPTY_TEST_TAG).contentDescriptionOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnErrorStateDrawsNothingLeavingTheShellsBannerToShow() {
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.ERROR), studyCopy(), TestUiTextResolver, dispatch = {}) },
    ) {
        onNodeWithTag(STUDY_CARD_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STUDY_DONE_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STUDY_EMPTY_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheProgressLineCountsTowardTheDisplayedTarget() {
    val copy = studyCopy()
    renderStudy(
        content = {
            StudySessionScreen(
                session(StudySessionState.CARD, card = flashcard(), progress = StudyProgress(completed = 2, target = 5)),
                copy,
                TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        assertEquals("2 of 5", onNodeWithTag(STUDY_PROGRESS_TEST_TAG).contentDescriptionOrEmpty())
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAFlashcardRevealsBeforeItGradesAndGradesGoodOnPass() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.CARD, card = flashcard()), studyCopy(), TestUiTextResolver, dispatch = { recorded += it }) },
    ) {
        // Pass is not offered until the answer is shown; reveal comes first.
        onNodeWithTag(STUDY_PASS_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STUDY_REVEAL_TEST_TAG).performScrollTo().performClick()
        onNodeWithTag(STUDY_PASS_TEST_TAG).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Study.Reveal, KaniAction.Study.Grade(rating = "good")),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertPassMapsToGoodAndFailToAgain() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = {
            StudySessionScreen(
                // Already answered-visible so both grades are on screen without a reveal.
                session(StudySessionState.CARD, card = flashcard(), feedback = StudyFeedback()),
                studyCopy(),
                TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(STUDY_REVEAL_TEST_TAG).performScrollTo().performClick()
        onNodeWithTag(STUDY_FAIL_TEST_TAG).performScrollTo().performClick()
        assertTrue(KaniAction.Study.Grade(rating = "again") in recorded, "Fail must submit again: $recorded")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnAnsweredCardCannotBeGradedTwice() {
    // The double-commit guard: with a grade already applied, the card shows Continue,
    // and the reducer's `acceptsGrade` is false — so even a synthesized second grade
    // click is dropped. This is what protects against key-repeat and double-click.
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = {
            StudySessionScreen(
                session(
                    StudySessionState.CARD,
                    card = flashcard(),
                    feedback = StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.CORRECT),
                ),
                studyCopy(),
                TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        // The answer is shown (feedback visible), and Continue is the only forward move.
        onNodeWithTag(STUDY_CONTINUE_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Study.Continue), recorded)
        // Pass, even if tapped, submits nothing: acceptsGrade is false once applied.
        onNodeWithTag(STUDY_PASS_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Study.Continue), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertATypedCardSubmitsWhatTheHostWillGrade() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.CARD, card = typedCard()), studyCopy(), TestUiTextResolver, dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(STUDY_TYPING_INPUT_TEST_TAG).performScrollTo().performTextInput("take off")
        onNodeWithTag(STUDY_TYPING_SUBMIT_TEST_TAG).performScrollTo().performClick()
        // The card carries one submit; the host resolves it to good/again from the
        // text. The surface only dispatches what the card offered.
        assertEquals(listOf<KaniAction>(KaniAction.Study.Grade(rating = "good")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertPickingAChoiceIsGradingIt() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.CARD, card = choiceCard()), studyCopy(), TestUiTextResolver, dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(studyChoiceTestTag("説")).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Study.Grade(rating = "again")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAWritingCardOffersOnlyPassAndFail() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.CARD, card = writingCard()), studyCopy(), TestUiTextResolver, dispatch = { recorded += it }) },
    ) {
        // No Hard/Easy, and no Save hard unless the CLOSE exception applies.
        onNodeWithTag(STUDY_SAVE_HARD_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STUDY_PASS_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Study.Grade(rating = "good")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertACloseWritingAttemptOffersSaveHardInsteadOfPass() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.CARD, card = writingCard(close = true)), studyCopy(), TestUiTextResolver, dispatch = { recorded += it }) },
    ) {
        // The documented exception: a CLOSE attempt's primary action is Save hard,
        // submitting hard, which still counts as a pass.
        onNodeWithTag(STUDY_PASS_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STUDY_SAVE_HARD_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Study.Grade(rating = "hard")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertUndoIsOfferedOnlyWhenTheSessionReportsAReversibleCard() {
    val recorded = mutableListOf<KaniAction>()
    renderStudy(
        content = {
            StudySessionScreen(
                session(StudySessionState.CARD, card = flashcard()).copy(undoable = true),
                studyCopy(),
                TestUiTextResolver,
                dispatch = { recorded += it },
            )
        },
    ) {
        onNodeWithTag(STUDY_UNDO_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Study.Undo), recorded)
    }

    renderStudy(
        content = { StudySessionScreen(session(StudySessionState.CARD, card = flashcard()), studyCopy(), TestUiTextResolver, dispatch = {}) },
    ) {
        onNodeWithTag(STUDY_UNDO_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheWrongPickAndCorrectChoiceAreMarkedAfterAnAnswer() {
    renderStudy(
        content = {
            StudySessionScreen(
                session(
                    StudySessionState.CARD,
                    card = choiceCard(),
                    feedback = StudyFeedback(StudyFeedbackPhase.APPLIED, StudyOutcome.INCORRECT, selected = "説"),
                ),
                studyCopy(),
                TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        // Both the wrong pick and the correct choice are still on screen after the
        // answer — the frozen grid the Android surface kept — so the user can compare.
        onNodeWithTag(studyChoiceTestTag("説")).assertExists()
        onNodeWithTag(studyChoiceTestTag("脱")).assertExists()
        onNodeWithTag(STUDY_CONTINUE_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheStudyTestTagsAreDistinctSoAssertionsCannotCollide() {
    val tags = listOf(
        STUDY_SESSION_TEST_TAG,
        STUDY_PROGRESS_TEST_TAG,
        STUDY_LOADING_TEST_TAG,
        STUDY_DONE_TEST_TAG,
        STUDY_DONE_HOME_TEST_TAG,
        STUDY_EMPTY_TEST_TAG,
        STUDY_UNDO_TEST_TAG,
        STUDY_CARD_TEST_TAG,
        STUDY_REVEAL_TEST_TAG,
        STUDY_PASS_TEST_TAG,
        STUDY_FAIL_TEST_TAG,
        STUDY_SAVE_HARD_TEST_TAG,
        STUDY_CONTINUE_TEST_TAG,
        STUDY_ANSWER_TEST_TAG,
        STUDY_ANSWER_DETAILS_TEST_TAG,
        STUDY_TYPING_INPUT_TEST_TAG,
        STUDY_TYPING_SUBMIT_TEST_TAG,
    ) + listOf("脱", "説").map(::studyChoiceTestTag)
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-study-choice-脱", studyChoiceTestTag("脱"))
}

private fun session(
    state: StudySessionState,
    card: dev.bee.kanjianki.presentation.StudyCard? = null,
    feedback: StudyFeedback = StudyFeedback(),
    progress: StudyProgress = StudyProgress(),
) = StudySession(state = state, progress = progress, card = card, feedback = feedback)
