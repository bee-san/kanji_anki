package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
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

        composeRule.onNodeWithText("Study more new cards").assertIsEnabled().performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_CONTINUE_ALL_KANJI).assertIsEnabled().performClick()
        composeRule.onNodeWithText(MainActivityBase.LABEL_BACK_HOME).assertIsEnabled().performClick()

        assertTrue(studyMoreClicked)
        assertTrue(continueClicked)
        assertTrue(backClicked)
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
        composeRule.onNodeWithText("Continue all kanji").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.onNodeWithText("Back home").assertIsDisplayed().assertIsEnabled().performClick()

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
                    summaryLines = listOf("Today's focus: 0 of 3 left", "Done"),
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
        composeRule.onNodeWithText("Today's focus: 0 of 3 left").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("Continue all kanji").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Back home").assertIsEnabled().performClick()

        assertTrue(continueClicked)
        assertTrue(backClicked)
    }

    @Test
    fun rendersStudyMoreDialogAndSubmitsTypedCount() {
        var confirmedCount = ""
        var dismissed = false

        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = MainActivityBase.LABEL_PRACTICE,
                    title = "Today's focus done",
                    headline = null,
                    body = "Your Pareto focus is complete.",
                    summaryLines = emptyList(),
                    showDoneActions = true,
                    availableStudyMoreNewCards = 4,
                    showBackHome = false,
                    backHomePrimary = false,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable {},
                    studyMoreDialog = StudyMoreNewCardsDialogModel(
                        title = "Study more new cards",
                        message = "How many extra new cards?",
                        inputLabel = MainActivityBase.LABEL_NEW_CARDS,
                        initialCount = 2,
                        confirmLabel = MainActivityBase.LABEL_STUDY,
                        cancelLabel = "Cancel",
                        onConfirm = { value ->
                            confirmedCount = value
                            false
                        },
                        onDismiss = Runnable { dismissed = true }
                    )
                )
            )
        }

        composeRule.onAllNodesWithText("Study more new cards").assertCountEquals(2)
        composeRule.onNodeWithText("How many extra new cards?").assertIsDisplayed()
        composeRule.onNodeWithText("2").performTextReplacement("3")
        composeRule.onNodeWithText("Study").assertIsEnabled().performClick()

        assertEquals("3", confirmedCount)
        composeRule.onNodeWithText("Cancel").assertIsEnabled().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun studyMoreCountDraftSurvivesStateRestorationWithFreshModel() {
        var confirmed = ""
        var model = studyMoreDialog { confirmed = it }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { StudyMoreNewCardsDialog(model) }

        composeRule.onNodeWithText("2").performTextReplacement("4")
        model = studyMoreDialog { confirmed = it }
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText(MainActivityBase.LABEL_STUDY).performClick()
        assertEquals("4", confirmed)
    }

    private fun studyMoreDialog(onConfirm: (String) -> Unit) = StudyMoreNewCardsDialogModel(
        title = "Study more new cards",
        message = "How many extra new cards?",
        inputLabel = MainActivityBase.LABEL_NEW_CARDS,
        initialCount = 2,
        confirmLabel = MainActivityBase.LABEL_STUDY,
        cancelLabel = "Cancel",
        onConfirm = {
            onConfirm(it)
            true
        },
        onDismiss = Runnable {},
    )

    @Test
    fun rendersFullEmptyScreenWithoutActions() {
        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = MainActivityBase.LABEL_PRACTICE,
                    title = "Study practice",
                    headline = "Nothing to study yet",
                    body = "Sync AnkiDroid first.",
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
        composeRule.onNodeWithText("Sync AnkiDroid first.").assertIsDisplayed()
        composeRule.onNodeWithTag(homeEmptyStateTestTag("Nothing to study yet")).assertIsDisplayed()
        composeRule.onAllNodesWithText(MainActivityBase.LABEL_BACK_HOME).assertCountEquals(0)
    }

    @Test
    fun rendersNoSessionBackHomeAction() {
        var backClicked = false

        composeRule.setContent {
            StudyDoneScreen(
                model = StudyDoneScreenModel(
                    modeLabel = MainActivityBase.LABEL_PRACTICE,
                    title = "Nothing due now",
                    headline = "All caught up",
                    body = "Your active kanji are resting. Sync for new cards, or come back when reviews are due.",
                    summaryLines = emptyList(),
                    showDoneActions = false,
                    availableStudyMoreNewCards = 0,
                    showBackHome = true,
                    backHomePrimary = true,
                    onStudyMore = Runnable {},
                    onContinueAll = Runnable {},
                    onBackHome = Runnable { backClicked = true }
                )
            )
        }

        composeRule.onNodeWithText("Nothing due now").assertIsDisplayed()
        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
        composeRule.onNodeWithTag(homeEmptyStateTestTag("All caught up")).assertIsDisplayed()
        composeRule.onNodeWithText("Back home").assertIsEnabled().performClick()

        assertTrue(backClicked)
    }
}
