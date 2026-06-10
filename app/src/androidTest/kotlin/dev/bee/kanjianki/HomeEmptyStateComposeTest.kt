package dev.bee.kanjianki

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
        composeRule.onNodeWithText("No kanji queued")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        composeRule.onNodeWithText("Sync AnkiDroid to find problem cards.").assertIsDisplayed()
    }

    @Test
    fun rendersPanelEmptyState() {
        composeRule.setContent {
            HomeEmptyState(
                title = "No mistakes yet",
                body = "Missed or hard reviews."
            )
        }

        composeRule.onNodeWithTag(homeEmptyStateTestTag("No mistakes yet")).assertIsDisplayed()
        composeRule.onNodeWithText("No mistakes yet")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        composeRule.onNodeWithText("Missed or hard reviews.").assertIsDisplayed()
    }
}
