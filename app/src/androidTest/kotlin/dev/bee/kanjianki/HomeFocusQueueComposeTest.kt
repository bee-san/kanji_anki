package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeFocusQueueComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersQueuedCards() {
        composeRule.setContent {
            HomeFocusQueuePanel(
                model = HomeFocusQueuePanelModel(
                    planText = "Today's adaptive focus: 2 items left / 5",
                    emptyTitle = null,
                    emptyBody = null,
                    showSyncButton = false,
                    cards = listOf(
                        HomeFocusQueueCardModel(
                            kanji = "裂",
                            meaning = "split; tear",
                            sourceEvidence = "From phrase · missed card",
                            reasonLine = "Why: weakness 80 · support 0/2 · kanji -> meaning · due now",
                            body = "Needs focused kanji practice.",
                            tags = listOf("kanji -> meaning", "learning"),
                            accentColor = androidx.compose.ui.graphics.Color(0xFFFF4C76),
                            onClick = {}
                        )
                    )
                ),
                onSync = {}
            )
        }

        composeRule.onNodeWithText("Today's adaptive focus: 2 items left / 5").assertIsDisplayed()
        composeRule.onNodeWithText("裂").assertIsDisplayed()
        composeRule.onNodeWithText("split; tear").assertIsDisplayed()
        composeRule.onNodeWithText("kanji -> meaning").assertIsDisplayed()
        composeRule.onNodeWithText("learning").assertIsDisplayed()
        composeRule.onNodeWithText(">").assertIsDisplayed()
    }

    @Test
    fun rendersEmptyStateAndSyncButtonWhenNothingIsQueued() {
        composeRule.setContent {
            HomeFocusQueuePanel(
                model = HomeFocusQueuePanelModel(
                    planText = "Adaptive focus is waiting for sync",
                    emptyTitle = "No active practice yet",
                    emptyBody = "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.",
                    showSyncButton = true,
                    cards = emptyList()
                ),
                onSync = {}
            )
        }

        composeRule.onNodeWithText("No active practice yet").assertIsDisplayed()
        composeRule.onNodeWithText("Study now will admit the next problem kanji through your adaptive focus.").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid").assertIsDisplayed()
    }
}
