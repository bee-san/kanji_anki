package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDebugLogPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersStatusAndFiresToggleAndShareActions() {
        var toggled = false
        var shared = false
        composeRule.setContent {
            SettingsDebugLogPanel(
                SettingsDebugLogPanelModel(
                    title = "Debug log",
                    status = "Off",
                    statusColor = 0xFF6C5674.toInt(),
                    detail = "Records timestamped app activity to a local file.",
                    toggleLabel = "Turn on debug log",
                    togglePrimary = true,
                    onToggle = Runnable { toggled = true },
                    shareLabel = "Share debug log",
                    onShare = Runnable { shared = true },
                ),
            )
        }

        composeRule.onNodeWithText("Debug log").assertIsDisplayed()
        composeRule.onNodeWithText("Off").assertIsDisplayed()
        composeRule.onNodeWithText("Records timestamped app activity to a local file.").assertIsDisplayed()

        composeRule.onNodeWithText("Turn on debug log").assertIsDisplayed().performClick()
        assertTrue(toggled)
        assertFalse(shared)

        composeRule.onNodeWithText("Share debug log").assertIsDisplayed().performClick()
        assertTrue(shared)
    }

    @Test
    fun enabledStateRendersOutlinedToggle() {
        var toggled = false
        composeRule.setContent {
            SettingsDebugLogPanel(
                SettingsDebugLogPanelModel(
                    title = "Debug log",
                    status = "Recording",
                    statusColor = 0xFF00AEB5.toInt(),
                    detail = "Recording timestamped app activity.",
                    toggleLabel = "Turn off debug log",
                    togglePrimary = false,
                    onToggle = Runnable { toggled = true },
                    shareLabel = "Share debug log",
                    onShare = Runnable {},
                ),
            )
        }

        composeRule.onNodeWithText("Recording").assertIsDisplayed()
        composeRule.onNodeWithText("Turn off debug log").assertIsDisplayed().performClick()
        assertTrue(toggled)
    }
}
