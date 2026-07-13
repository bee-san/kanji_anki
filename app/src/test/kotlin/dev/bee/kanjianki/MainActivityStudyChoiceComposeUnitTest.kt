package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import dev.bee.kanjianki.core.StudyTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SIMILAR_CHOICE_PHONE_VIEWPORT_TAG = "similar-choice-phone-viewport"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyChoiceComposeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun similarChoiceRouteKeepsChoicesInlineInMainCardOnPhoneViewport() {
        var selected = ""
        val model = SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler {
                    selected = it
                    true
                },
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Compare shapes", "裂 vs 列", true),
                SimilarKanjiExplanationLineModel("Seen in", "source one • source two"),
                SimilarKanjiExplanationLineModel("Meaning hint", "split • tear • rend"),
                SimilarKanjiExplanationLineModel("Reading hint", "れつ"),
                SimilarKanjiExplanationLineModel("Shared part", "刀"),
                SimilarKanjiExplanationLineModel("Different part", "衣 vs 歹"),
                SimilarKanjiExplanationLineModel("Shape hint", "Look closely at the lower component before choosing.", true),
            ),
        )

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(640.dp)
                    .testTag(SIMILAR_CHOICE_PHONE_VIEWPORT_TAG)
            ) {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                    navActions = KaniNavActions({}, {}, {}, {}),
                    content = {
                        SimilarChoiceSessionCard(
                            model = model,
                            showInlineChoices = true,
                            detailsExpandedByDefault = false,
                        )
                    },
                )
            }
        }

        val choiceTag = similarChoiceTestTag("列")
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()
        composeRule.onNodeWithText("Compare shapes: 裂 vs 列").assertIsDisplayed()
        composeRule.onNodeWithTag(choiceTag).assertIsDisplayed()
        composeRule.onNodeWithTag(SIMILAR_KANJI_DETAILS_TOGGLE_TAG).assertIsDisplayed()

        val viewportBounds = composeRule.onNodeWithTag(SIMILAR_CHOICE_PHONE_VIEWPORT_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val choiceBounds = composeRule.onNodeWithTag(choiceTag)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(choiceBounds.bottom <= viewportBounds.bottom)

        composeRule.onNodeWithTag(choiceTag).performClick()
        assertEquals("列", selected)
    }

    @Test
    fun similarChoiceRouteHidesExplanationDetailsBehindAToggleByDefault() {
        val model = SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler { true },
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Compare shapes", "裂 vs 列", true),
                SimilarKanjiExplanationLineModel("Seen in", "source one • source two"),
                SimilarKanjiExplanationLineModel("Meaning hint", "split • tear • rend"),
                SimilarKanjiExplanationLineModel("Reading hint", "れつ"),
                SimilarKanjiExplanationLineModel("Shared part", "刀"),
                SimilarKanjiExplanationLineModel("Different part", "衣 vs 歹"),
                SimilarKanjiExplanationLineModel("Shape hint", "Look closely at the lower component before choosing.", true),
            ),
        )

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(640.dp)
                    .testTag(SIMILAR_CHOICE_PHONE_VIEWPORT_TAG)
            ) {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                    navActions = KaniNavActions({}, {}, {}, {}),
                    content = {
                        SimilarChoiceSessionCard(
                            model = model,
                            showInlineChoices = true,
                            detailsExpandedByDefault = false,
                        )
                    },
                )
            }
        }

        val choiceTag = similarChoiceTestTag("裂")
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()
        composeRule.onNodeWithText("Compare shapes: 裂 vs 列").assertIsDisplayed()
        composeRule.onNodeWithTag(choiceTag).assertIsDisplayed()
        composeRule.onNodeWithTag(SIMILAR_KANJI_DETAILS_TOGGLE_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("Seen in").assertCountEquals(0)
        composeRule.onAllNodesWithText("Meaning hint").assertCountEquals(0)
        composeRule.onAllNodesWithText("Reading hint").assertCountEquals(0)
        composeRule.onAllNodesWithText("Shape hint").assertCountEquals(0)

        composeRule.onNodeWithTag(SIMILAR_KANJI_DETAILS_TOGGLE_TAG).performClick()
        composeRule.onNodeWithText(StudyTextCopy.similarKanjiHideDetailsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("Seen in: source one • source two").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun similarChoiceCardOffersExploreDifferencesWithoutSubmittingChoice() {
        var explored = false
        var selected = ""
        val model = SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler {
                    selected = it
                    true
                },
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Shape hint", "Look at the lower component.", true),
            ),
        )

        composeRule.setContent {
            SimilarChoiceSessionCard(
                model = model,
                showInlineChoices = false,
                onExploreDifferences = Runnable { explored = true },
            )
        }

        composeRule.onNodeWithText("Explore the differences").assertIsDisplayed().performClick()

        assertTrue(explored)
        assertEquals("", selected)
    }

    @Test
    fun meaningChoiceGradesOnTapAndWaitsForContinue() {
        var graded = ""
        var continued = 0
        val feedback = StudyAnswerFeedbackState("token-森")
        val model = MeaningChoiceSessionModel(
            modeLabel = "Choose",
            question = "Which kanji means forest?",
            choices = listOf("森", "林"),
            answerPanel = StudyAnswerPanelModel("Answer", "森", 76, emptyList(), null),
            onChoice = KanjiChoiceHandler { glyph ->
                feedback.begin(if (glyph == "森") StudyAnswerOutcome.CORRECT else StudyAnswerOutcome.INCORRECT)
                graded = glyph
                true
            },
            resultResolver = MeaningChoiceResultResolver { glyph ->
                val correct = glyph == "森"
                MeaningChoiceResultModel(
                    status = if (correct) StudyTextCopy.answerCorrectFeedback() else StudyTextCopy.answerIncorrectFeedback(),
                    statusColor = if (correct) MainActivityBase.TEAL else MainActivityBase.CORAL,
                    actionTone = if (correct) StudyActionTone.PASS else StudyActionTone.FAIL,
                    correctChoice = "森",
                    selectedChoiceCorrect = correct,
                )
            },
            feedbackState = feedback,
            onContinue = Runnable { continued += 1 },
        )

        composeRule.setContent {
            MeaningChoiceSessionCard(model = model)
        }

        composeRule.onNodeWithTag(similarChoiceTestTag("森")).performClick()
        assertEquals("森", graded)
        composeRule.onNodeWithText(StudyTextCopy.answerCorrectFeedback()).assertExists()
        composeRule.mainClock.advanceTimeBy(5_000L)
        assertEquals(0, continued)

        assertTrue(feedback.markApplied("token-森"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyTextCopy.continueLabel()))
            .assertIsEnabled()
        model.onContinue.run()
        assertEquals(1, continued)
    }

    @Test
    fun rejectedMeaningChoiceSubmissionDoesNotFreezeTheCard() {
        var submissions = 0
        val feedback = StudyAnswerFeedbackState("token-rejected-meaning")
        val model = MeaningChoiceSessionModel(
            modeLabel = "Choose",
            question = "Which kanji means forest?",
            choices = listOf("森", "林"),
            answerPanel = StudyAnswerPanelModel("Answer", "森", 76, emptyList(), null),
            onChoice = KanjiChoiceHandler {
                submissions += 1
                false
            },
            resultResolver = MeaningChoiceResultResolver {
                MeaningChoiceResultModel(
                    StudyTextCopy.answerCorrectFeedback(),
                    MainActivityBase.TEAL,
                    StudyActionTone.PASS,
                    correctChoice = "森",
                    selectedChoiceCorrect = true,
                )
            },
            feedbackState = feedback,
        )

        composeRule.setContent { MeaningChoiceSessionCard(model = model) }

        composeRule.onNodeWithTag(similarChoiceTestTag("森")).performClick()
        composeRule.onNodeWithTag(similarChoiceTestTag("林")).performClick()

        assertEquals(2, submissions)
        composeRule.onAllNodesWithText(StudyTextCopy.answerCorrectFeedback()).assertCountEquals(0)
    }

    @Test
    fun rejectedSimilarChoiceSubmissionDoesNotFreezeTheGrid() {
        var submissions = 0
        val feedback = StudyAnswerFeedbackState("token-rejected-similar")
        val model = similarChoiceModelWithCorrectChoice(
            correctChoice = "裂",
            feedback = feedback,
            onChoice = {
                submissions += 1
                false
            },
            onContinue = {},
        )

        composeRule.setContent {
            SimilarChoiceSessionCard(
                model = model,
                showInlineChoices = true,
                detailsExpandedByDefault = false,
            )
        }

        composeRule.onNodeWithTag(similarChoiceTestTag("列")).performClick()
        composeRule.onNodeWithTag(similarChoiceTestTag("烈")).performClick()

        assertEquals(2, submissions)
        composeRule.onAllNodesWithText(StudyTextCopy.similarKanjiWrongChoiceResult("裂"))
            .assertCountEquals(0)
    }

    @Test
    fun restoredAppliedChoiceSeedsSelectedChoiceForContinue() {
        val feedback = StudyAnswerFeedbackState("restored-choice-token")
        assertTrue(feedback.begin(StudyAnswerOutcome.INCORRECT, selectedAnswer = "列"))
        assertTrue(feedback.markApplied("restored-choice-token"))

        val state = meaningChoiceSessionStateForFeedback(feedback)

        assertEquals("列", state.selectedChoice)
        assertTrue(state.answered)
    }

    @Test
    fun wrongSimilarChoiceGradesOnceAndWaitsForExplicitContinue() {
        var selected = ""
        var continued = 0
        val feedback = StudyAnswerFeedbackState("token-裂")
        val mnemonic = StudyAnswerMnemonicModel(
            label = "My mnemonic",
            note = "A shell\nsplits open.",
        )
        val model = similarChoiceModelWithCorrectChoice(
            correctChoice = "裂",
            feedback = feedback,
            onChoice = {
                feedback.begin(StudyAnswerOutcome.INCORRECT)
                selected = it
                true
            },
            onContinue = {
                if (feedback.tryContinue()) continued += 1
            },
        ).copy(mnemonic = mnemonic)

        composeRule.setContent {
            // Tall viewport so the post-answer Continue bar is within tappable bounds.
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(1600.dp)
            ) {
                SimilarChoiceSessionCard(
                    model = model,
                    showInlineChoices = true,
                    detailsExpandedByDefault = false,
                )
            }
        }

        composeRule.onAllNodesWithTag(STUDY_ANSWER_MNEMONIC_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithText(mnemonic.label).assertCountEquals(0)
        composeRule.onAllNodesWithText(mnemonic.note).assertCountEquals(0)

        composeRule.onNodeWithTag(similarChoiceTestTag("列")).performClick()
        assertEquals("列", selected)
        composeRule.onNodeWithTag(STUDY_ANSWER_MNEMONIC_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(mnemonic.label).assertExists()
        composeRule.onNodeWithText(mnemonic.note).assertExists()
        composeRule.onNodeWithText(choiceButtonText("列", KanjiChoiceFeedback.INCORRECT)).assertExists()
        composeRule.onNodeWithText(choiceButtonText("裂", KanjiChoiceFeedback.CORRECT)).assertExists()
        composeRule.onNodeWithText(StudyTextCopy.similarKanjiWrongChoiceResult("裂"))
            .assertExists()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )

        // Inputs stay frozen, and even a long wait never advances this card.
        composeRule.onNodeWithTag(similarChoiceTestTag("烈")).performClick()
        composeRule.mainClock.advanceTimeBy(5_000L)
        assertEquals("列", selected)
        assertEquals(0, continued)

        feedback.markApplied("token-裂")
        composeRule.onNodeWithText(StudyTextCopy.continueLabel())
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, continued)
        model.onContinue.run()
        assertEquals(1, continued)
    }

    @Test
    fun correctSimilarChoiceAlsoWaitsForExplicitContinue() {
        var selected = ""
        var continued = 0
        val feedback = StudyAnswerFeedbackState("token-裂")
        val model = similarChoiceModelWithCorrectChoice(
            correctChoice = "裂",
            feedback = feedback,
            onChoice = {
                feedback.begin(StudyAnswerOutcome.CORRECT)
                selected = it
                true
            },
            onContinue = {
                if (feedback.tryContinue()) continued += 1
            },
        )

        composeRule.setContent {
            SimilarChoiceSessionCard(
                model = model,
                showInlineChoices = true,
                detailsExpandedByDefault = false,
            )
        }

        composeRule.onNodeWithTag(similarChoiceTestTag("裂")).performClick()

        assertEquals("裂", selected)
        composeRule.onNodeWithText(choiceButtonText("裂", KanjiChoiceFeedback.CORRECT)).assertExists()
        composeRule.onNodeWithText(StudyTextCopy.answerCorrectFeedback()).assertExists()
        composeRule.mainClock.advanceTimeBy(5_000L)
        assertEquals(0, continued)

        feedback.markApplied("token-裂")
        composeRule.onNodeWithText(StudyTextCopy.continueLabel())
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, continued)
    }

    @Test
    fun feedbackForSimilarChoiceMarksSelectionAndCorrectAnswerOnly() {
        // No selection or no known correct answer: no feedback.
        assertEquals(null, feedbackForSimilarChoice("列", null, "裂"))
        assertEquals(null, feedbackForSimilarChoice("列", "列", null))

        // Wrong selection: the pressed glyph is red, the correct one green, others none.
        assertEquals(KanjiChoiceFeedback.INCORRECT, feedbackForSimilarChoice("列", "列", "裂"))
        assertEquals(KanjiChoiceFeedback.CORRECT, feedbackForSimilarChoice("裂", "列", "裂"))
        assertEquals(null, feedbackForSimilarChoice("烈", "列", "裂"))
    }

    private fun similarChoiceModelWithCorrectChoice(
        correctChoice: String,
        feedback: StudyAnswerFeedbackState,
        onChoice: (String) -> Boolean,
        onContinue: () -> Unit,
    ): SimilarChoiceSessionModel {
        return SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler { onChoice(it) },
                correctChoice = correctChoice,
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Shape hint", "Look at the lower component.", true),
            ),
            feedbackState = feedback,
            onContinue = Runnable { onContinue() },
        )
    }

    @Test
    fun similarKanjiDifferenceScreenShowsSafeComparisonAndBrowseBackActions() {
        val browsed = AtomicBoolean(false)
        val back = AtomicBoolean(false)
        val model = SimilarKanjiDifferenceModel(
            modeLabel = "Recognise",
            title = "Explore the differences",
            body = "Compare the target kanji with safe local hints.",
            correctLabel = "Correct kanji",
            correctKanji = "裂",
            choicesTitle = "Similar choices",
            choices = listOf(
                SimilarKanjiDifferenceChoiceModel(
                    kanji = "裂",
                    label = "Kanji 裂",
                    onOpenBrowse = Runnable { browsed.set(true) },
                ),
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Compare shapes", "裂 vs 列", true),
                SimilarKanjiExplanationLineModel("Shape hint", "Use the lower component as the safe fallback.", true),
            ),
            onBack = Runnable { back.set(true) },
        )

        composeRule.setContent {
            MainActivityComposeRouteWithActionBar(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                content = { SimilarKanjiDifferenceScreen(model) },
                actionBar = {},
            )
        }

        composeRule.onNodeWithText("Explore the differences").assertIsDisplayed()
        composeRule.onNodeWithText("Correct kanji").assertIsDisplayed()
        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("Similar choices").assertExists()
        composeRule.onNodeWithText("Kanji 裂").assertExists()
        composeRule.onNodeWithTag(studyActionButtonTestTag("Open in Browse")).assertExists()
        composeRule.onNodeWithTag(studyActionButtonTestTag("Back to study")).assertExists()
    }
}
