package dev.bee.kanjianki

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeFocusQueueComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersQueuedCards() {
        var clicked = false

        composeRule.setContent {
            HomeFocusQueuePanel(
                model = HomeFocusQueuePanelModel(
                    planText = "Today's adaptive focus: 2 of 5 left",
                    emptyTitle = null,
                    emptyBody = null,
                    showSyncButton = false,
                    cards = listOf(
                        HomeFocusQueueCardModel(
                            kanji = "裂",
                            meaning = "split; tear",
                            sourceEvidence = "From phrase · missed card",
                            reasonLine = "weakness 80 · support 0/2 · kanji -> meaning · due now",
                            body = "Needs focused kanji practice.",
                            tags = listOf(
                                HomeFocusQueueTagModel("kanji -> meaning", androidx.compose.ui.graphics.Color(0xFF6E5CE6)),
                                HomeFocusQueueTagModel("learning", androidx.compose.ui.graphics.Color(0xFF00AEB5))
                            ),
                            accentColor = androidx.compose.ui.graphics.Color(0xFFFF4C76),
                            onClick = { clicked = true }
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
        composeRule.onNodeWithContentDescription("Focus queue card 裂, split; tear")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithTag(homeFocusQueueCardTestTag("裂"))
            .performClick()
        assertTrue(clicked)
    }

    @Test
    fun rendersEmptyStateAndSyncButtonWhenNothingIsQueued() {
        var synced = false

        composeRule.setContent {
            HomeFocusQueuePanel(
                model = HomeFocusQueuePanelModel(
                    planText = "Adaptive focus is waiting for sync",
                    emptyTitle = "No active practice yet",
                    emptyBody = "Kani found candidates from AnkiDroid. Study now will admit the next problem kanji through your adaptive focus.",
                    showSyncButton = true,
                    cards = emptyList()
                ),
                onSync = { synced = true }
            )
        }

        composeRule.onNodeWithText("No active practice yet").assertIsDisplayed()
        composeRule.onNodeWithText("Study now will admit the next problem kanji through your adaptive focus.").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertTrue(synced)
    }
}
