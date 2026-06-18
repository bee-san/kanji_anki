package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.StudyWritingCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingFallbackActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersVisibleFallbackActionsAndInvokesCallbacks() {
        var replayed = false
        var continuedAnyway = false
        var retried = false

        composeRule.setContent {
            WritingFallbackActions(
                WritingFallbackActionsModel(
                    replayVisible = true,
                    manualOverrideVisible = true,
                    practiceWithGuideVisible = true,
                    onReplay = Runnable { replayed = true },
                    onManualOverride = Runnable { continuedAnyway = true },
                    onPracticeWithGuide = Runnable { retried = true },
                    manualOverrideLabel = StudyWritingCopy.continueAnywayLabel(),
                )
            )
        }

        composeRule.onNodeWithText(StudyWritingCopy.replayLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyWritingCopy.continueAnywayLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyWritingCopy.practiceWithGuideLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(StudyWritingCopy.replayLabel()).performClick()
        composeRule.onNodeWithText(StudyWritingCopy.continueAnywayLabel()).performClick()
        composeRule.onNodeWithText(StudyWritingCopy.practiceWithGuideLabel()).performClick()

        assertTrue(replayed)
        assertTrue(continuedAnyway)
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

        composeRule.onAllNodesWithText(StudyWritingCopy.replayLabel()).assertCountEquals(0)
        composeRule.onNodeWithText(StudyWritingCopy.manualOverrideLabel()).assertIsDisplayed()
        composeRule.onAllNodesWithText(StudyWritingCopy.practiceWithGuideLabel()).assertCountEquals(0)
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

        composeRule.onAllNodesWithText(StudyWritingCopy.replayLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyWritingCopy.manualOverrideLabel()).assertCountEquals(0)
        composeRule.onAllNodesWithText(StudyWritingCopy.practiceWithGuideLabel()).assertCountEquals(0)
    }
}
