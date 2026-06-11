package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.StudyWritingCopy
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

        composeRule.onNodeWithText(StudyWritingCopy.eraseLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyWritingCopy.undoLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("More help").assertIsDisplayed()
        composeRule.onNodeWithText(StudyWritingCopy.eraseLabel()).performClick()
        composeRule.onNodeWithText(StudyWritingCopy.undoLabel()).performClick()
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

        composeRule.onNodeWithText(StudyWritingCopy.undoLabel()).assertIsNotEnabled()
        composeRule.onAllNodesWithText(StudyWritingCopy.eraseLabel()).assertCountEquals(1)
        composeRule.onAllNodesWithText("Hint").assertCountEquals(0)
    }
}
