package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingToolActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersToolActionsAndInvokesVisibleCallbacks() {
        var erased = false
        var undone = false
        var hinted = false

        composeRule.setContent {
            WritingToolActions(
                WritingToolActionsModel(
                    undoEnabled = true,
                    hintText = "More help",
                    hintVisible = true,
                    onErase = Runnable { erased = true },
                    onUndo = Runnable { undone = true },
                    onHint = Runnable { hinted = true }
                )
            )
        }

        composeRule.onNodeWithText("Erase").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
        composeRule.onNodeWithText("More help").assertIsDisplayed()
        composeRule.onNodeWithText("Erase").performClick()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.onNodeWithText("More help").performClick()

        assertTrue(erased)
        assertTrue(undone)
        assertTrue(hinted)
    }

    @Test
    fun disablesUndoAndOmitsHiddenHint() {
        composeRule.setContent {
            WritingToolActions(
                WritingToolActionsModel(
                    undoEnabled = false,
                    hintText = "Hint",
                    hintVisible = false,
                    onErase = Runnable {},
                    onUndo = Runnable {},
                    onHint = Runnable {}
                )
            )
        }

        composeRule.onNodeWithText("Undo").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Hint").assertCountEquals(0)
    }
}
