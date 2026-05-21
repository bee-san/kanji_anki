package dev.bee.kanjianki

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsAutoSyncComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersConfiguredSyncAndWiresAction() {
        var toggled = false

        composeRule.setContent {
            SettingsAutoSyncPanel(
                SettingsAutoSyncPanelModel(
                    title = SettingsTextCopy.dailyAnkiSyncTitle(),
                    status = SettingsTextCopy.autoSyncStatus(true, true, "06:45"),
                    statusColor = MainActivityUiSupport.TEAL,
                    detail = "Last auto success yesterday. Next scheduled tomorrow.",
                    actionLabel = SettingsTextCopy.turnOffDailySyncLabel(),
                    primaryAction = false,
                    onAction = SettingsAutoSyncAction { toggled = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.dailyAnkiSyncTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoSyncStatus(true, true, "06:45")).assertIsDisplayed()
        composeRule.onNodeWithText("Last auto success yesterday. Next scheduled tomorrow.").assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.turnOffDailySyncLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(toggled)
        }
    }

    @Test
    fun hidesActionWhenSyncIsNotConfigured() {
        composeRule.setContent {
            SettingsAutoSyncPanel(
                SettingsAutoSyncPanelModel(
                    title = SettingsTextCopy.dailyAnkiSyncTitle(),
                    status = SettingsTextCopy.autoSyncStatus(false, false, "00:00"),
                    statusColor = MainActivityUiSupport.MUTED,
                    detail = "Manual sync once, then Kani will keep itself refreshed once per day.",
                    actionLabel = null,
                    primaryAction = false,
                    onAction = null
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.dailyAnkiSyncTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoSyncStatus(false, false, "00:00")).assertIsDisplayed()
        composeRule.onAllNodesWithText(SettingsTextCopy.turnOnDailySyncLabel()).assertCountEquals(0)
    }
}
