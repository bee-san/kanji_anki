package dev.bee.kanjianki

import android.view.View
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    fun writingStatusStateKeepsLatestText() {
        val state = WritingStatusState()

        state.setStatus("Checking handwriting...", MainActivityUiSupport.STUDY_MUTED)
        assertEquals("Checking handwriting...", state.getText().toString())

        state.setText("Existing analysis message")
        assertEquals("Existing analysis message", state.getText().toString())
    }

    @Test
    fun writingStatusTextRepaintsWhenStateChanges() {
        val state = WritingStatusState()
        state.setStatus("Trace the first strokes", MainActivityUiSupport.STUDY_MUTED)

        composeRule.setContent {
            WritingStatusText(state)
        }

        composeRule.onNodeWithText("Trace the first strokes").assertIsDisplayed()

        composeRule.runOnIdle {
            state.setStatus("Handwriting checker ready", MainActivityUiSupport.TEAL)
        }

        composeRule.onAllNodesWithText("Trace the first strokes").assertCountEquals(0)
        composeRule.onNodeWithText("Handwriting checker ready")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun writingResultStatusHandleShowsAndHidesComposeStatus() {
        val handle = WritingResultStatusHandle()

        composeRule.setContent {
            WritingResultStatus(handle)
        }

        composeRule.runOnIdle {
            handle.hide()
        }
        assertEquals(View.GONE, handle.getVisibility())
        composeRule.onAllNodesWithText("Model unavailable").assertCountEquals(0)

        composeRule.runOnIdle {
            handle.show("Model unavailable", MainActivityUiSupport.CORAL)
        }
        assertEquals("Model unavailable", handle.getText().toString())
        assertEquals(View.VISIBLE, handle.getVisibility())
        composeRule.onNodeWithText("Model unavailable")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )

        composeRule.runOnIdle {
            handle.hide()
        }
        composeRule.onAllNodesWithText("Model unavailable").assertCountEquals(0)
    }
}
