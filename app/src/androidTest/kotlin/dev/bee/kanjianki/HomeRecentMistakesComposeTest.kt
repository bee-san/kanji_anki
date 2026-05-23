package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                    emptyTitle = "No recent mistakes yet",
                    emptyBody = "Missed and hard reviews will show here after you study.",
                    cards = listOf(
                        HomeRecentMistakesCardModel(
                            kanji = "裂",
                            title = "split; tear",
                            subtitle = "Rated AGAIN on May 19, 2026",
                            sourceEvidence = "From phrase · missed card",
                            accentColor = androidx.compose.ui.graphics.Color(0xFFFF4C76),
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
    fun rendersEmptyStateWhenNoMistakesExist() {
        composeRule.setContent {
            HomeRecentMistakesPanel(
                model = HomeRecentMistakesPanelModel(
                    emptyTitle = "No recent mistakes yet",
                    emptyBody = "Missed and hard reviews will show here after you study.",
                    cards = emptyList()
                )
            )
        }

        composeRule.onNodeWithText("No recent mistakes yet").assertIsDisplayed()
        composeRule.onNodeWithText("Missed and hard reviews will show here after you study.").assertIsDisplayed()
    }
}
