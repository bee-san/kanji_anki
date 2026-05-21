package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingPrimaryActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersVisiblePrimaryActionsAndInvokesCallbacks() {
        var checked = false
        var downloaded = false

        composeRule.setContent {
            WritingPrimaryActions(
                WritingPrimaryActionsModel(
                    checkText = "Check",
                    checkVisible = true,
                    checkEnabled = true,
                    downloadText = "Download checker",
                    downloadVisible = true,
                    nextText = MainActivityBase.LABEL_PASS,
                    nextVisible = false,
                    onCheck = Runnable { checked = true },
                    onDownload = Runnable { downloaded = true },
                    onNext = Runnable {}
                )
            )
        }

        composeRule.onNodeWithText("Check").assertIsDisplayed()
        composeRule.onNodeWithText("Download checker").assertIsDisplayed()
        composeRule.onAllNodesWithText(MainActivityBase.LABEL_PASS).assertCountEquals(0)
        composeRule.onNodeWithText("Check").performClick()
        composeRule.onNodeWithText("Download checker").performClick()

        assertTrue(checked)
        assertTrue(downloaded)
    }

    @Test
    fun rendersNextActionWhenAnalysisCanBeSubmitted() {
        var submitted = false

        composeRule.setContent {
            WritingPrimaryActions(
                WritingPrimaryActionsModel(
                    checkText = "Try cleaner",
                    checkVisible = true,
                    checkEnabled = true,
                    downloadText = "Download checker",
                    downloadVisible = false,
                    nextText = "Save hard",
                    nextVisible = true,
                    onCheck = Runnable {},
                    onDownload = Runnable {},
                    onNext = Runnable { submitted = true }
                )
            )
        }

        composeRule.onNodeWithText("Try cleaner").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download checker").assertCountEquals(0)
        composeRule.onNodeWithText("Save hard").assertIsDisplayed()
        composeRule.onNodeWithText("Save hard").performClick()

        assertTrue(submitted)
    }
}
