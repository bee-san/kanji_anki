package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SyncProgressPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSyncProgressTitle() {
        composeRule.setContent {
            SyncProgressTitle("Syncing cards")
        }

        composeRule.onNodeWithText("Syncing cards").assertIsDisplayed()
    }
}
