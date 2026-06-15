package dev.bee.kanjianki

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.StudyReviewButtonCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyUndoBannerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersMessageAndInvokesUndoAction() {
        var undoClicks = 0

        composeRule.setContent {
            MaterialTheme {
                StudyUndoBanner(
                    undoMessage = "Good saved",
                    onUndo = { undoClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(StudyUndoBannerTestTags.BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Good saved").assertIsDisplayed()
        composeRule.onNodeWithTag(studyActionButtonTestTag(StudyReviewButtonCopy.undoLabel()))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, undoClicks)
        }
    }
}
