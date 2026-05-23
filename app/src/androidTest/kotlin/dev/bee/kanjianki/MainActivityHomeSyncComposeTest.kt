package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityHomeSyncComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSuccessResultAndActions() {
        var studyClicked = false
        var homeClicked = false

        composeRule.setContent {
            SyncResultScreen(
                model = SyncResultScreenModel(
                    title = "Sync complete",
                    headline = "3 kanji ready to study",
                    lines = listOf("Found 3 candidates.", "Cleanup finished."),
                    accentColor = 0xFF00AEB5.toInt(),
                    primaryLabel = "Study now",
                    primaryColor = 0xFFFF4C76.toInt(),
                    onPrimary = Runnable { studyClicked = true },
                    secondaryLabel = "Back home",
                    onSecondary = Runnable { homeClicked = true }
                )
            )
        }

        composeRule.onNodeWithText("Sync complete").assertIsDisplayed()
        composeRule.onNodeWithText("3 kanji ready to study").assertIsDisplayed()
        composeRule.onNodeWithText("Found 3 candidates.").assertIsDisplayed()
        composeRule.onNodeWithText("Cleanup finished.").assertIsDisplayed()

        composeRule.onNodeWithText("Study now").performClick()
        composeRule.onNodeWithText("Back home").performClick()

        composeRule.runOnIdle {
            assertTrue(studyClicked)
            assertTrue(homeClicked)
        }
    }

    @Test
    fun rendersSkippedAndFailureStates() {
        var retryClicked = false

        composeRule.setContent {
            androidx.compose.foundation.layout.Column {
                SyncResultScreen(
                    model = SyncResultScreenModel(
                        title = "Sync already running",
                        headline = null,
                        lines = listOf("Already syncing."),
                        accentColor = 0xFF6E5CE6.toInt(),
                        primaryLabel = null,
                        primaryColor = 0xFF00AEB5.toInt(),
                        onPrimary = null,
                        secondaryLabel = "Back home",
                        onSecondary = Runnable {}
                    )
                )
                SyncResultScreen(
                    model = SyncResultScreenModel(
                        title = "Sync needs attention",
                        headline = "Could not read AnkiDroid",
                        lines = listOf("Provider unavailable."),
                        accentColor = 0xFFFF4C76.toInt(),
                        primaryLabel = "Try sync again",
                        primaryColor = 0xFF00AEB5.toInt(),
                        onPrimary = Runnable { retryClicked = true },
                        secondaryLabel = "Back home",
                        onSecondary = Runnable {}
                    )
                )
            }
        }

        composeRule.onNodeWithText("Sync already running").assertIsDisplayed()
        composeRule.onNodeWithText("Already syncing.").assertIsDisplayed()
        composeRule.onNodeWithText("Sync needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("Could not read AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithText("Provider unavailable.").assertIsDisplayed()

        composeRule.onNodeWithText("Try sync again").performClick()

        composeRule.runOnIdle {
            assertTrue(retryClicked)
        }
    }
}
