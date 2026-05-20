package dev.bee.kanjianki

import android.view.View
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun writingStatusViewKeepsLatestBridgeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = WritingStatusView(context)

        view.setStatus("Checking handwriting...", MainActivityUiSupport.STUDY_MUTED)
        assertEquals("Checking handwriting...", view.getText().toString())

        view.setText("Existing analysis message")
        assertEquals("Existing analysis message", view.getText().toString())
    }

    @Test
    fun writingResultStatusHandleShowsAndHidesComposeStatus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handle = WritingResultStatusHandle(context)

        assertTrue(handle.view() is WritingStatusView)

        composeRule.setContent {
            AndroidView(factory = { handle.view() })
        }

        handle.hide()
        assertEquals(View.GONE, handle.getVisibility())
        composeRule.onAllNodesWithText("Model unavailable").assertCountEquals(0)

        handle.show("Model unavailable", MainActivityUiSupport.CORAL)
        assertEquals("Model unavailable", handle.getText().toString())
        assertEquals(View.VISIBLE, handle.getVisibility())
        composeRule.onNodeWithText("Model unavailable").assertIsDisplayed()

        handle.hide()
        composeRule.onAllNodesWithText("Model unavailable").assertCountEquals(0)
    }
}
