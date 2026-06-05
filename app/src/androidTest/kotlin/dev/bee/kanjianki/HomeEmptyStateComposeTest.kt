package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeEmptyStateComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersLegacyBandEmptyState() {
        composeRule.setContent {
            HomeEmptyState(
                title = "No kanji queued",
                body = "Sync AnkiDroid to find problem cards.",
                style = HomeEmptyStateStyle.LegacyBand
            )
        }

        composeRule.onNodeWithTag(homeEmptyStateTestTag("No kanji queued")).assertIsDisplayed()
        composeRule.onNodeWithText("No kanji queued").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid to find problem cards.").assertIsDisplayed()
    }

    @Test
    fun rendersPanelEmptyState() {
        composeRule.setContent {
            HomeEmptyState(
                title = "No recent mistakes yet",
                body = "Missed and hard reviews will show here after you study."
            )
        }

        composeRule.onNodeWithTag(homeEmptyStateTestTag("No recent mistakes yet")).assertIsDisplayed()
        composeRule.onNodeWithText("No recent mistakes yet").assertIsDisplayed()
        composeRule.onNodeWithText("Missed and hard reviews will show here after you study.").assertIsDisplayed()
    }
}
