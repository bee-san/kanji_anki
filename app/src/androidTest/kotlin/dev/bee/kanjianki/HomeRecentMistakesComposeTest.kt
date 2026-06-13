package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRecentMistakesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRecentMistakeCard() {
        composeRule.setContent {
            HomeRecentMistakesPanel(
                model = HomeRecentMistakesPanelModel(
                    emptyTitle = "No mistakes yet",
                    emptyBody = "Missed or hard reviews.",
                    cards = listOf(
                        HomeRecentMistakesCardModel(
                            kanji = "裂",
                            title = "split; tear",
                            subtitle = "Rated AGAIN on May 19, 2026",
                            sourceEvidence = "From phrase · missed card",
                            accentColor = MainActivityUiSupport.CORAL,
                            onClick = {}
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("split; tear").assertIsDisplayed()
        composeRule.onNodeWithText("Rated AGAIN on May 19, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("From phrase · missed card").assertIsDisplayed()
    }

    @Test
    fun recentMistakeCardInvokesItsClickCallback() {
        var clicked = false

        composeRule.setContent {
            HomeRecentMistakesPanel(
                model = HomeRecentMistakesPanelModel(
                    emptyTitle = "No mistakes yet",
                    emptyBody = "Missed or hard reviews.",
                    cards = listOf(
                        HomeRecentMistakesCardModel(
                            kanji = "裂",
                            title = "split; tear",
                            subtitle = "Rated AGAIN on May 19, 2026",
                            sourceEvidence = "From phrase · missed card",
                            accentColor = MainActivityUiSupport.CORAL,
                            onClick = { clicked = true }
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithTag("home-recent-mistakes-card-裂").performClick()
        assertTrue(clicked)
    }

    @Test
    fun rendersEmptyStateWhenNoMistakesExist() {
        composeRule.setContent {
            HomeRecentMistakesPanel(
                model = HomeRecentMistakesPanelModel(
                    emptyTitle = "No mistakes yet",
                    emptyBody = "Missed or hard reviews.",
                    cards = emptyList()
                )
            )
        }

        composeRule.onNodeWithText("No mistakes yet").assertIsDisplayed()
        composeRule.onNodeWithText("Missed or hard reviews.").assertIsDisplayed()
    }
}
