package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingFallbackActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersVisibleFallbackActionsAndInvokesCallbacks() {
        var replayed = false
        var manuallyAccepted = false
        var retried = false

        composeRule.setContent {
            WritingFallbackActions(
                WritingFallbackActionsModel(
                    replayVisible = true,
                    manualOverrideVisible = true,
                    practiceWithGuideVisible = true,
                    onReplay = Runnable { replayed = true },
                    onManualOverride = Runnable { manuallyAccepted = true },
                    onPracticeWithGuide = Runnable { retried = true }
                )
            )
        }

        composeRule.onNodeWithText("Replay").assertIsDisplayed()
        composeRule.onNodeWithText("Mark right anyway").assertIsDisplayed()
        composeRule.onNodeWithText("Try again with full guide").assertIsDisplayed()
        composeRule.onNodeWithText("Replay").performClick()
        composeRule.onNodeWithText("Mark right anyway").performClick()
        composeRule.onNodeWithText("Try again with full guide").performClick()

        assertTrue(replayed)
        assertTrue(manuallyAccepted)
        assertTrue(retried)
    }

    @Test
    fun omitsHiddenFallbackActions() {
        composeRule.setContent {
            WritingFallbackActions(
                WritingFallbackActionsModel(
                    replayVisible = false,
                    manualOverrideVisible = true,
                    practiceWithGuideVisible = false,
                    onReplay = Runnable {},
                    onManualOverride = Runnable {},
                    onPracticeWithGuide = Runnable {}
                )
            )
        }

        composeRule.onAllNodesWithText("Replay").assertCountEquals(0)
        composeRule.onNodeWithText("Mark right anyway").assertIsDisplayed()
        composeRule.onAllNodesWithText("Try again with full guide").assertCountEquals(0)
    }

    @Test
    fun rendersNothingWhenEveryFallbackActionIsHidden() {
        composeRule.setContent {
            WritingFallbackActions(
                WritingFallbackActionsModel(
                    replayVisible = false,
                    manualOverrideVisible = false,
                    practiceWithGuideVisible = false,
                    onReplay = Runnable {},
                    onManualOverride = Runnable {},
                    onPracticeWithGuide = Runnable {}
                )
            )
        }

        composeRule.onAllNodesWithText("Replay").assertCountEquals(0)
        composeRule.onAllNodesWithText("Mark right anyway").assertCountEquals(0)
        composeRule.onAllNodesWithText("Try again with full guide").assertCountEquals(0)
    }
}
