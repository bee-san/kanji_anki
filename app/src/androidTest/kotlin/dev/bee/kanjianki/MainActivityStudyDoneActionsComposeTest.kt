package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyDoneActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersStudyMoreContinueAndBackActions() {
        var studyMoreClicked = false
        var continueClicked = false
        var backClicked = false

        composeRule.setContent {
            StudyDoneActions(
                availableStudyMoreNewCards = 2,
                onStudyMore = { studyMoreClicked = true },
                onContinueAll = { continueClicked = true },
                onBackHome = { backClicked = true }
            )
        }

        composeRule.onNodeWithText("Study more new cards").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_CONTINUE_ALL_KANJI).assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).assertIsDisplayed()

        composeRule.onNodeWithText("Study more new cards").performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_CONTINUE_ALL_KANJI).performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).performClick()

        assertTrue(studyMoreClicked)
        assertTrue(continueClicked)
        assertTrue(backClicked)
    }

    @Test
    fun rendersPrimaryBackHomeButtonAndInvokesAction() {
        var clicked = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            studyBackHomeButtonView(
                context = context,
                primary = true,
                onBackHome = Runnable { clicked = true }
            )
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).performClick()

        assertTrue(clicked)
    }

    @Test
    fun rendersContinueAndBackActionsWithoutStudyMoreWhenNothingIsAvailable() {
        var continueClicked = false
        var backClicked = false

        composeRule.setContent {
            StudyDoneActions(
                availableStudyMoreNewCards = 0,
                onStudyMore = {},
                onContinueAll = { continueClicked = true },
                onBackHome = { backClicked = true }
            )
        }

        composeRule.onAllNodesWithText("Study more new cards").assertCountEquals(0)
        composeRule.onNodeWithText(MainActivityBase.LABEL_CONTINUE_ALL_KANJI).assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).assertIsDisplayed()

        composeRule.onNodeWithText(MainActivityBase.LABEL_CONTINUE_ALL_KANJI).performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).performClick()

        assertTrue(continueClicked)
        assertTrue(backClicked)
    }

    @Test
    fun rendersFullDoneScreenWithSummaryAndActions() {
        var continueClicked = false
        var backClicked = false

        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = MainActivityBase.LABEL_PRACTICE,
                    title = "Today's focus done",
                    headline = null,
                    body = "Your Pareto focus is complete.",
                    summaryLines = listOf("Today's focus: 0 items left / 3", "Done"),
                    showDoneActions = true,
                    availableStudyMoreNewCards = 0,
                    showBackHome = false,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable { continueClicked = true },
                    onBackHome = Runnable { backClicked = true }
                )
            )
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_PRACTICE).assertIsDisplayed()
        composeRule.onNodeWithText("Today's focus done").assertIsDisplayed()
        composeRule.onNodeWithText("Your Pareto focus is complete.").assertIsDisplayed()
        composeRule.onNodeWithText("Today's focus: 0 items left / 3").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_CONTINUE_ALL_KANJI).performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).performClick()

        assertTrue(continueClicked)
        assertTrue(backClicked)
    }

    @Test
    fun rendersFullEmptyScreenWithoutActions() {
        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = MainActivityBase.LABEL_PRACTICE,
                    title = "Study practice",
                    headline = "Nothing to study yet",
                    body = "Sync from AnkiDroid first.",
                    summaryLines = emptyList(),
                    showDoneActions = false,
                    availableStudyMoreNewCards = 0,
                    showBackHome = false,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable {}
                )
            )
        }

        composeRule.onNodeWithText("Study practice").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing to study yet").assertIsDisplayed()
        composeRule.onNodeWithText("Sync from AnkiDroid first.").assertIsDisplayed()
        composeRule.onAllNodesWithText(MainActivityBase.LABEL_BACK_HOME).assertCountEquals(0)
    }
}
