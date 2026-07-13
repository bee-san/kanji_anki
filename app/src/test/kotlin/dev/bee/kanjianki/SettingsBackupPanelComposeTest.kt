package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsBackupPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backupPanelRendersWithStableTagAndBothActions() {
        var exports = 0
        var restores = 0
        val panel = SettingsBackupPanelModel(
            title = "Backup & restore",
            body = "Keep a copy outside Kani.",
            lastBackupLine = "Last automatic backup: 2026-07-10 09:30",
            archiveCountLine = "7 automatic backups kept on this device",
            exportLabel = "Export now",
            onExport = Runnable { exports += 1 },
            restoreLabel = "Restore from backup…",
            onRestore = Runnable { restores += 1 },
        )
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsSubmenuScreen(
                    SettingsSubmenuScreenModel(
                        homeLabel = "Home",
                        onHome = Runnable {},
                        backLabel = "Back",
                        onBack = Runnable {},
                        title = "Automation",
                        body = "Manage background tools.",
                        panels = listOf(panel),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("settings-panel-backup").assertIsDisplayed()
        composeRule.onNodeWithText("Backup & restore").assertIsDisplayed()
        composeRule.onNodeWithText("Last automatic backup: 2026-07-10 09:30").assertIsDisplayed()
        composeRule.onNodeWithText("Export now").performClick()
        composeRule.onNodeWithText("Restore from backup…").performScrollTo().performClick()

        assertEquals(1, exports)
        assertEquals(1, restores)
    }

    @Test
    fun unsupportedPlatformExplainsPreservationAndDisablesBothActions() {
        var exports = 0
        var restores = 0
        val warning = "Backup & restore requires Android 11 or later. Existing files are unchanged."
        val panel = SettingsBackupPanelModel(
            title = "Backup & restore",
            body = "Keep a copy outside Kani.",
            lastBackupLine = "Last automatic backup: 2026-07-10 09:30",
            archiveCountLine = "7 automatic backups kept on this device",
            exportLabel = "Export now",
            onExport = Runnable { exports += 1 },
            restoreLabel = "Restore from backup…",
            onRestore = Runnable { restores += 1 },
            availabilityMessage = warning,
            actionsEnabled = false,
        )
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsSubmenuScreen(
                    SettingsSubmenuScreenModel(
                        homeLabel = "Home",
                        onHome = Runnable {},
                        backLabel = "Back",
                        onBack = Runnable {},
                        title = "Automation",
                        body = "Manage background tools.",
                        panels = listOf(panel),
                    ),
                )
            }
        }

        composeRule.onNodeWithText(warning).assertIsDisplayed()
        composeRule.onNodeWithText("Export now").assertIsNotEnabled()
        composeRule.onNodeWithText("Restore from backup…")
            .performScrollTo()
            .assertIsNotEnabled()

        assertEquals(0, exports)
        assertEquals(0, restores)
    }
}
