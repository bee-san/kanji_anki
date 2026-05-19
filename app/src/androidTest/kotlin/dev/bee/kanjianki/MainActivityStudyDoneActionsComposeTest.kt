package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
}
