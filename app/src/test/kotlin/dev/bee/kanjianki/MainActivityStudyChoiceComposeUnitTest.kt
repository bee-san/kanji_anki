package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
    fun similarChoiceRouteKeepsChoicesVisibleInBottomActionBarOnPhoneViewport() {
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
                MainActivityComposeRouteWithActionBar(
                    model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY),
                    content = {
                        Column {
                            SimilarChoiceSessionCard(
                                model = model,
                                showInlineChoices = false,
                            )
                            Spacer(modifier = Modifier.height(1000.dp))
                        }
                    },
                    actionBar = { SimilarChoiceActionBar(model.gridModel) },
                )
            }
        }

        val choiceTag = similarChoiceTestTag("列")
        composeRule.onNodeWithText("Which kanji means split?").assertIsDisplayed()
        composeRule.onNodeWithTag(choiceTag).assertIsDisplayed()

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
                onExploreDifferences = Runnable { explored = true },
            )
        }

        composeRule.onNodeWithText("Explore the differences").assertIsDisplayed().performClick()

        assertTrue(explored)
        assertEquals("", selected)
    }

    @Test
    fun similarKanjiDifferenceScreenShowsSafeComparisonAndBrowseBackActions() {
        var browsed = false
        var back = false
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
                    onOpenBrowse = Runnable { browsed = true },
                ),
            ),
            explanationLines = listOf(
                SimilarKanjiExplanationLineModel("Compare shapes", "裂 vs 列", true),
                SimilarKanjiExplanationLineModel("Shape hint", "Use the lower component as the safe fallback.", true),
            ),
            onBack = Runnable { back = true },
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
        composeRule.onNodeWithText("Similar choices").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kanji 裂").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open in Browse").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Back to study").performScrollTo().assertIsDisplayed().performClick()

        assertTrue(browsed)
        assertTrue(back)
    }

}
