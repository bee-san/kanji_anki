package dev.bee.kanjianki

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import dev.bee.kanjianki.core.StudyTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyFlashcardComposeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstFrameStudySurfacesAvoidShadowElevation() {
        assertEquals(0.dp, StudyCardShadowElevation)
        assertEquals(0.dp, StudyTopBarButtonElevation)
    }

    @Test
    fun nextCardEnterMotionStaysWithinTheFastInteractionBudget() {
        assertEquals(60, STUDY_CARD_ENTER_FADE_MILLIS)
        assertEquals(90, STUDY_CARD_ENTER_SLIDE_MILLIS)
        assertEquals(72, STUDY_CARD_SWIPE_COMMIT_MILLIS)
    }

    @Test
    fun acceptedSwipeKeepsItsOffsetAndCommitsAwayFromCentre() {
        val state = StudySwipeFeedbackState().apply {
            thresholdPx = 72f
            update(-128f)
        }

        state.commit(StudyRatings.AGAIN)

        assertEquals(-128f, state.dragOffsetX)
        assertTrue(state.committed)
        assertEquals(StudySwipeReleaseKind.COMMIT_FAIL, state.releaseRequest.kind)
    }

    @Test
    fun rejectedSwipeRequestsSpringBackWithoutSnappingImmediately() {
        val state = StudySwipeFeedbackState().apply { update(38f) }

        state.settleBack()

        assertEquals(38f, state.dragOffsetX)
        assertFalse(state.committed)
        assertEquals(StudySwipeReleaseKind.SETTLE_BACK, state.releaseRequest.kind)
    }

    @Test
    fun rapidSecondDragCancelsTheOldSettleAnimation() {
        val state = StudySwipeFeedbackState().apply { update(38f) }
        state.settleBack()

        assertTrue(state.beginDrag())
        state.update(-91f)

        assertEquals(-91f, state.dragOffsetX)
        assertEquals(StudySwipeReleaseKind.IDLE, state.releaseRequest.kind)
        assertFalse(state.committed)
    }

    @Test
    fun rejectedEnqueueReturnsOnlyTheCommitStartedByThatAttempt() {
        val state = StudySwipeFeedbackState().apply { update(96f) }

        val accepted = submitReviewWithSwipeFeedback(state) { false }

        assertFalse(accepted)
        assertFalse(state.committed)
        assertEquals(StudySwipeReleaseKind.SETTLE_BACK, state.releaseRequest.kind)
    }

    @Test
    fun acceptedReviewSettlesCardBackForPersistentFeedback() {
        val state = StudySwipeFeedbackState().apply { update(96f) }

        assertTrue(submitReviewWithSwipeFeedback(state) { true })

        assertFalse(state.committed)
        assertEquals(StudySwipeReleaseKind.SETTLE_BACK, state.releaseRequest.kind)
        assertFalse(submitReviewWithSwipeFeedback(state) { false })
        assertFalse(state.committed)
        assertEquals(StudySwipeReleaseKind.SETTLE_BACK, state.releaseRequest.kind)
    }

    @Test
    fun typingMeaningAnswerSubmitsOnImeAction() {
        var submitCount = 0
        val state = TypingAnswerState()

        composeRule.setContent {
            TypingMeaningAnswer(
                label = MainActivityBase.LABEL_MEANING,
                state = state,
                onDone = Runnable { submitCount++ },
            )
        }

        val textField = composeRule.onNode(hasSetTextAction())
        textField.performTextReplacement("prison")
        textField.performImeAction()

        composeRule.runOnIdle {
            assertEquals(1, submitCount)
        }
    }

    @Test
    fun typingAnswerNotifiesPersistenceAfterUpdatingVisibleState() {
        val observed = mutableListOf<String>()
        val state = TypingAnswerState("before").also {
            it.onTextChanged = { value ->
                observed.add("$value:${it.text}")
            }
        }

        state.updateText("after")

        assertEquals(listOf("after:after"), observed)
        assertEquals("after", state.text)
    }

    @Test
    fun typingAnswerBoundsPastedTextBeforePersistence() {
        var persisted = ""
        val state = TypingAnswerState().also { answer ->
            answer.onTextChanged = { persisted = it }
        }

        state.updateText("x".repeat(MAX_STUDY_TYPED_DRAFT_CHARS + 1))

        assertEquals(MAX_STUDY_TYPED_DRAFT_CHARS, state.text.length)
        assertEquals(state.text, persisted)
    }

    @Test
    fun typingMeaningSubmitKeyHelperMatchesEnterKeysOnKeyUpOnly() {
        assertTrue(isTypingMeaningSubmitKey(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_ENTER))
        assertTrue(isTypingMeaningSubmitKey(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER))
        assertFalse(isTypingMeaningSubmitKey(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun revealedFlashcardCardShowsAnswerOnlyAfterReveal() {
        val revealState = FlashcardRevealState(false)
        val mnemonic = StudyAnswerMnemonicModel(
            label = "My mnemonic",
            note = "A prison gate\nlocks the meaning in.",
        )

        composeRule.setContent {
            FlashcardCard(
                model = FlashcardCardModel(
                    promptHeader = FlashcardPromptHeaderModel(
                        modeLabel = "Type",
                        question = "What does it mean?",
                    ),
                    heroPanel = FlashcardHeroPanelModel(
                        glyph = "獄",
                        glyphSizeSp = 64,
                        typeface = null,
                    ),
                    typingAnswer = null,
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "獄",
                        glyphSizeSp = 76,
                        lines = listOf(
                            StudyAnswerLineModel(
                                text = "split",
                                color = 0xFF2E1035.toInt(),
                                sizeSp = 17,
                                bold = true,
                            )
                        ),
                        helperText = null,
                        mnemonic = mnemonic,
                    ),
                    revealState = revealState,
                )
            )
        }

        composeRule.onAllNodesWithText("split").assertCountEquals(0)
        composeRule.onAllNodesWithText(mnemonic.label).assertCountEquals(0)
        composeRule.onAllNodesWithText(mnemonic.note).assertCountEquals(0)
        composeRule.onAllNodesWithTag(STUDY_ANSWER_MNEMONIC_TEST_TAG).assertCountEquals(0)
        val heroCenterBeforeReveal = composeRule.onNodeWithText("獄").fetchSemanticsNode().boundsInRoot.center

        composeRule.runOnIdle {
            revealState.reveal()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("split").assertExists()
        composeRule.onNodeWithTag(STUDY_ANSWER_MNEMONIC_TEST_TAG).assertExists()
        composeRule.onNodeWithText(mnemonic.label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        composeRule.onNodeWithText(mnemonic.note).assertExists()
        composeRule.onAllNodesWithText("What does it mean?").assertCountEquals(0)
        composeRule.onAllNodesWithText("獄").assertCountEquals(1)
        composeRule.onAllNodesWithText("Answer").assertCountEquals(0)
        val heroCenterAfterReveal = composeRule.onNodeWithText("獄").fetchSemanticsNode().boundsInRoot.center
        assertEquals(heroCenterBeforeReveal.x, heroCenterAfterReveal.x, 1f)
        assertEquals(heroCenterBeforeReveal.y, heroCenterAfterReveal.y, 1f)
    }

    @Test
    fun revealedBlankTypingFlashcardHidesPromptCopyAndTypingInput() {
        val revealState = FlashcardRevealState(false)
        val typingAnswer = TypingAnswerState()

        composeRule.setContent {
            FlashcardCard(
                model = FlashcardCardModel(
                    promptHeader = FlashcardPromptHeaderModel(
                        modeLabel = "Type",
                        question = "What does it mean?",
                    ),
                    heroPanel = FlashcardHeroPanelModel(
                        glyph = "獄",
                        glyphSizeSp = 64,
                        typeface = null,
                    ),
                    typingAnswer = typingAnswer,
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "獄",
                        glyphSizeSp = 76,
                        lines = listOf(
                            StudyAnswerLineModel(
                                text = "split",
                                color = 0xFF2E1035.toInt(),
                                sizeSp = 17,
                                bold = true,
                            )
                        ),
                        helperText = null,
                    ),
                    revealState = revealState,
                )
            )
        }

        // KB1: an unrevealed typing card is compact, so the hidden-answer hint
        // is dropped from the first frame; the typing field is present.
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        composeRule.onAllNodesWithText("split").assertCountEquals(0)
        typingAnswer.updateBounds(Rect(10f, 10f, 200f, 80f))
        assertTrue(typingAnswer.containsWindowPoint(20f, 20f))

        composeRule.runOnIdle {
            revealState.reveal()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onAllNodesWithText(MainActivityBase.LABEL_MEANING).assertCountEquals(0)
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
        assertFalse(typingAnswer.containsWindowPoint(20f, 20f))
        composeRule.onNodeWithText("split").assertExists()
    }

    @Test
    fun revealedTypedTypingFlashcardHidesPromptCopyAndTypingInput() {
        val revealState = FlashcardRevealState(false)
        val typingAnswer = TypingAnswerState("wrong")

        composeRule.setContent {
            FlashcardCard(
                model = FlashcardCardModel(
                    promptHeader = FlashcardPromptHeaderModel(
                        modeLabel = "Type",
                        question = "What does it mean?",
                    ),
                    heroPanel = FlashcardHeroPanelModel(
                        glyph = "獄",
                        glyphSizeSp = 64,
                        typeface = null,
                    ),
                    typingAnswer = typingAnswer,
                    answerPanel = StudyAnswerPanelModel(
                        title = "Answer",
                        glyph = "獄",
                        glyphSizeSp = 76,
                        lines = listOf(
                            StudyAnswerLineModel(
                                text = "split",
                                color = 0xFF2E1035.toInt(),
                                sizeSp = 17,
                                bold = true,
                            )
                        ),
                        helperText = null,
                    ),
                    revealState = revealState,
                )
            )
        }

        // KB1: an unrevealed typing card is compact, so the hidden-answer hint
        // is dropped from the first frame; the typing field is present.
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        composeRule.onAllNodesWithText("split").assertCountEquals(0)
        typingAnswer.updateBounds(Rect(10f, 10f, 200f, 80f))
        assertTrue(typingAnswer.containsWindowPoint(20f, 20f))

        composeRule.runOnIdle {
            revealState.reveal()
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onAllNodesWithText(MainActivityBase.LABEL_MEANING).assertCountEquals(0)
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
        assertFalse(typingAnswer.containsWindowPoint(20f, 20f))
        composeRule.onNodeWithText("split").assertExists()
    }

    @Test
    fun typingCardCompactsWhileKeyboardIsOpenSoTheKanjiStaysVisible() {
        val revealState = FlashcardRevealState(false)
        val typingAnswer = TypingAnswerState()

        composeRule.setContent {
            FlashcardCard(
                model = typingCardModel(revealState, typingAnswer),
                imeVisible = true,
            )
        }

        // The compact shared header, kanji, and answer field all fit above the keyboard.
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onAllNodesWithText("Prompt").assertCountEquals(0)
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
        // The essentials stay: question, kanji hero, and the typing field.
        composeRule.onNodeWithText("What does it mean?").assertIsDisplayed()
        composeRule.onNodeWithText("獄").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test
    fun typingCardRendersCompactFromTheFirstFrameEvenBeforeKeyboardOpens() {
        // KB1: an unrevealed typing card is compact from the first frame (before
        // the auto-focus opens the IME), so nothing reshapes when the keyboard
        // animates in. The shared chip, question line, and kanji hero stay.
        val revealState = FlashcardRevealState(false)
        val typingAnswer = TypingAnswerState()

        composeRule.setContent {
            FlashcardCard(
                model = typingCardModel(revealState, typingAnswer),
                imeVisible = false,
            )
        }

        // Question line and kanji hero stay in compact.
        composeRule.onNodeWithText("What does it mean?").assertIsDisplayed()
        composeRule.onNodeWithText("獄").assertIsDisplayed()
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onAllNodesWithText("Prompt").assertCountEquals(0)
        composeRule.onAllNodesWithText("Answer hidden until reveal").assertCountEquals(0)
    }

    @Test
    fun nonTypingCardIgnoresKeyboardVisibility() {
        val revealState = FlashcardRevealState(false)

        composeRule.setContent {
            FlashcardCard(
                model = typingCardModel(revealState, typingAnswer = null),
                imeVisible = true,
            )
        }

        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onNodeWithText("What does it mean?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Prompt").assertCountEquals(0)
        composeRule.onNodeWithText("獄").assertIsDisplayed()
    }

    private fun typingCardModel(
        revealState: FlashcardRevealState,
        typingAnswer: TypingAnswerState?,
    ): FlashcardCardModel {
        return FlashcardCardModel(
            promptHeader = FlashcardPromptHeaderModel(
                modeLabel = "Type",
                question = "What does it mean?",
            ),
            heroPanel = FlashcardHeroPanelModel(
                glyph = "獄",
                glyphSizeSp = 116,
                typeface = null,
            ),
            typingAnswer = typingAnswer,
            answerPanel = StudyAnswerPanelModel(
                title = "Answer",
                glyph = "獄",
                glyphSizeSp = 76,
                lines = listOf(
                    StudyAnswerLineModel(
                        text = "split",
                        color = 0xFF2E1035.toInt(),
                        sizeSp = 17,
                        bold = true,
                    )
                ),
                helperText = null,
            ),
            revealState = revealState,
        )
    }

    @Test
    fun rendersUndoBannerAndInvokesAction() {
        var undoTriggered = false
        val undoMessage = StudyTextCopy.reviewUndoMessage(StudyRatings.GOOD)

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = {},
                undoMessage = undoMessage,
                onUndo = { undoTriggered = true },
            )
        }

        composeRule.onNodeWithText(undoMessage).assertIsDisplayed()
        composeRule.onNodeWithText(StudyReviewButtonCopy.undoLabel())
            .assertIsDisplayed()
            .performClick()

        assertTrue(undoTriggered)
    }

    @Test
    fun gradedFlashcardShowsPersistentFeedbackAndContinueInsteadOfRatingActions() {
        var continued = 0
        val feedback = StudyAnswerFeedbackState("token-獄").apply {
            begin(StudyAnswerOutcome.CORRECT)
        }

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = {},
                feedbackState = feedback,
                onContinue = {
                    if (feedback.tryContinue()) continued += 1
                },
            )
        }

        composeRule.onAllNodesWithText(StudyReviewButtonCopy.againLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyReviewButtonCopy.goodLabel()).assertCountEquals(0)
        composeRule.onNodeWithText(StudyTextCopy.answerCorrectFeedback()).assertIsDisplayed()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyTextCopy.continueLabel()))
            .assertIsNotEnabled()
        composeRule.mainClock.advanceTimeBy(5_000L)
        assertEquals(0, continued)

        feedback.markApplied("token-獄")
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyTextCopy.continueLabel()))
            .assertIsEnabled()
            .performClick()
        assertEquals(1, continued)
    }

    @Test
    fun ratingButtonKeepsCardCenteredWhileSubmittingFeedback() {
        val swipeFeedback = StudySwipeFeedbackState()
        var passCount = 0

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = { passCount += 1 },
                swipeFeedback = swipeFeedback,
            )
        }

        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.goodLabel()))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, passCount)
            assertFalse(swipeFeedback.committed)
            assertEquals(StudySwipeReleaseKind.IDLE, swipeFeedback.releaseRequest.kind)
        }
    }

    @Test
    fun ratingButtonPreservesSourceAndRatingAndResetsWhenGateRejects() {
        val swipeFeedback = StudySwipeFeedbackState()
        var submittedSource = ""
        var submittedRating = ""
        var fallbackPassCount = 0

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = { fallbackPassCount += 1 },
                swipeFeedback = swipeFeedback,
                onReview = { source, rating ->
                    submittedSource = source
                    submittedRating = rating
                    false
                },
            )
        }

        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.goodLabel()))
            .performClick()

        composeRule.runOnIdle {
            assertEquals("button", submittedSource)
            assertEquals(StudyRatings.GOOD, submittedRating)
            assertEquals(0, fallbackPassCount)
            assertFalse(swipeFeedback.committed)
            assertEquals(StudySwipeReleaseKind.IDLE, swipeFeedback.releaseRequest.kind)
        }
    }

    @Test
    fun undoBannerDoesNotMoveRevealedRatingActions() {
        val undoMessage = mutableStateOf<String?>(null)

        composeRule.setContent {
            StudyFlashcardActionBar(
                revealed = true,
                onReveal = {},
                onFail = {},
                onPass = {},
                undoMessage = undoMessage.value,
                onUndo = {},
            )
        }

        val failTag = studyActionButtonTestTag(StudyReviewButtonCopy.againLabel())
        val passTag = studyActionButtonTestTag(StudyReviewButtonCopy.goodLabel())
        val failBefore = composeRule.onNodeWithTag(failTag).fetchSemanticsNode().boundsInRoot
        val passBefore = composeRule.onNodeWithTag(passTag).fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            undoMessage.value = StudyTextCopy.reviewUndoMessage(StudyRatings.GOOD)
        }
        composeRule.waitForIdle()

        assertEquals(failBefore, composeRule.onNodeWithTag(failTag).fetchSemanticsNode().boundsInRoot)
        assertEquals(passBefore, composeRule.onNodeWithTag(passTag).fetchSemanticsNode().boundsInRoot)
    }
}
