package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeSecondaryScreensComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusQueueScreenRendersHeaderAndDelegatesActions() {
        var homeClicked = false
        var syncClicked = false

        composeRule.setContent {
            HomeFocusQueueScreen(
                HomeFocusQueueScreenModel(
                    title = "Focus queue",
                    homeLabel = "Home",
                    onHome = { homeClicked = true },
                    queue = HomeFocusQueuePanelModel(
                        planText = "Adaptive focus is waiting for sync",
                        emptyTitle = "No kanji queued",
                        emptyBody = "Sync AnkiDroid to find problem cards.",
                        showSyncButton = true,
                        cards = emptyList()
                    ),
                    onSync = { syncClicked = true }
                )
            )
        }

        composeRule.onNodeWithText("Focus queue").assertIsDisplayed()
        composeRule.onNodeWithText("Home >").performClick()
        composeRule.onNodeWithText("Sync AnkiDroid").performClick()
        assertTrue(homeClicked)
        assertTrue(syncClicked)
    }

    @Test
    fun recentMistakesScreenRendersHeaderAndCards() {
        var homeClicked = false
        var cardClicked = false

        composeRule.setContent {
            HomeRecentMistakesScreen(
                HomeRecentMistakesScreenModel(
                    title = "Recent mistakes",
                    homeLabel = "Home",
                    onHome = { homeClicked = true },
                    mistakes = HomeRecentMistakesPanelModel(
                        emptyTitle = "No recent mistakes yet",
                        emptyBody = "Missed reviews will appear here.",
                        cards = listOf(
                            HomeRecentMistakesCardModel(
                                kanji = "裂",
                                title = "split",
                                subtitle = "Rated again",
                                sourceEvidence = "From 裂語",
                                accentColor = Color(0xFFFF4C76),
                                onClick = { cardClicked = true }
                            )
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Recent mistakes").assertIsDisplayed()
        composeRule.onNodeWithText("split").assertIsDisplayed()
        composeRule.onNodeWithText("From 裂語").assertIsDisplayed()
        composeRule.onNodeWithTag(homeRecentMistakesCardTestTag("裂"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Home >").performClick()
        assertTrue(homeClicked)
        assertTrue(cardClicked)
    }
}
