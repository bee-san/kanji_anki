package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersWritingSectionTitle() {
        composeRule.setContent {
            WritingSectionTitle(title = "Writing", color = MainActivityUiSupport.STUDY_PLUM)
        }

        composeRule.onNodeWithText("Writing").assertIsDisplayed()
    }

    @Test
    fun rendersWritingStatusText() {
        composeRule.setContent {
            WritingStatusText(
                text = "Trace the first strokes, then try from memory.",
                color = MainActivityUiSupport.STUDY_MUTED
            )
        }

        composeRule.onNodeWithText("Trace the first strokes, then try from memory.").assertIsDisplayed()
    }
}
