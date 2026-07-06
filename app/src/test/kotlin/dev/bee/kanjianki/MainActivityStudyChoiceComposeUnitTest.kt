package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
            title = "Choose the kanji",
            taskLabel = MainActivityBase.LABEL_SIMILAR_KANJI,
            body = "Pick the matching kanji.",
            reasonLine = "Weak Anki evidence",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler { selected = it },
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
            title = "Choose the kanji",
            taskLabel = MainActivityBase.LABEL_SIMILAR_KANJI,
            body = "Pick the matching kanji.",
            reasonLine = "Weak Anki evidence",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler { },
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
            title = "Choose the kanji",
            taskLabel = MainActivityBase.LABEL_SIMILAR_KANJI,
            body = "Pick the matching kanji.",
            reasonLine = "Weak Anki evidence",
            question = "Which kanji means split?",
            gridModel = SimilarChoiceGridModel(
                choices = listOf("裂", "列", "烈"),
                balanceLastRow = false,
                onChoice = KanjiChoiceHandler { selected = it },
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
    fun wrongSimilarChoicePausesWithFeedbackAndSubmitsOnContinue() {
        var selected = ""
        val model = similarChoiceModelWithCorrectChoice(correctChoice = "裂") { selected = it }

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

        // Tap a wrong choice: nothing is submitted yet, the pressed choice shows the
        // incorrect (red) mark, the correct choice shows the correct (green) mark,
        // and a Continue action appears.
        composeRule.onNodeWithTag(similarChoiceTestTag("列")).performClick()
        assertEquals("", selected)
        composeRule.onNodeWithText(choiceButtonText("列", KanjiChoiceFeedback.INCORRECT)).assertExists()
        composeRule.onNodeWithText(choiceButtonText("裂", KanjiChoiceFeedback.CORRECT)).assertExists()
        composeRule.onNodeWithText(StudyTextCopy.similarKanjiWrongChoiceResult("裂")).assertExists()

        // A second tap on another choice is ignored while the feedback is showing.
        composeRule.onNodeWithTag(similarChoiceTestTag("烈")).performClick()
        assertEquals("", selected)

        // Continue submits the originally selected (wrong) glyph. The bar renders
        // below the small Robolectric window, so invoke the click action directly
        // instead of injecting a touch that would land outside the window.
        composeRule.onNodeWithText(StudyTextCopy.continueLabel())
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("列", selected)
    }

    @Test
    fun correctSimilarChoiceSubmitsImmediatelyWithoutContinueStep() {
        var selected = ""
        val model = similarChoiceModelWithCorrectChoice(correctChoice = "裂") { selected = it }

        composeRule.setContent {
            SimilarChoiceSessionCard(
                model = model,
                showInlineChoices = true,
                detailsExpandedByDefault = false,
            )
        }

        composeRule.onNodeWithTag(similarChoiceTestTag("裂")).performClick()

        assertEquals("裂", selected)
        composeRule.onAllNodesWithText(StudyTextCopy.continueLabel()).assertCountEquals(0)
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
        onChoice: (String) -> Unit,
    ): SimilarChoiceSessionModel {
        return SimilarChoiceSessionModel(
            modeLabel = "Recognise",
            title = "Choose the kanji",
            taskLabel = MainActivityBase.LABEL_SIMILAR_KANJI,
            body = "Pick the matching kanji.",
            reasonLine = "Weak Anki evidence",
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
