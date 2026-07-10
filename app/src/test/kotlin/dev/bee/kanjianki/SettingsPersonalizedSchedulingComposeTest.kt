package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPersonalizedSchedulingComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun panelRendersOptInStatusAndWiresToggleFitAndReset() {
        var toggled = false
        var fitRuns = 0
        var resets = 0
        val state = SettingsPersonalizedSchedulingState(false)
        val panel = SettingsPersonalizedSchedulingPanelModel(
            title = "Personalized scheduling",
            body = "Fit FSRS to your review history.",
            status = "Off — using defaults",
            state = state,
            toggleLabel = "Use my review history",
            fitNowLabel = "Fit now",
            resetLabel = "Reset to defaults",
            onToggle = SettingsPersonalizedSchedulingToggleAction { toggled = it },
            onFitNow = Runnable { fitRuns++ },
            onReset = Runnable { resets++ },
        )
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsSubmenuScreen(
                    SettingsSubmenuScreenModel(
                        "Home", Runnable {}, "Back", Runnable {},
                        "Study settings", "Scheduling", listOf(panel),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("settings-panel-personalized-scheduling").assertIsDisplayed()
        composeRule.onNodeWithText("Off — using defaults").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsPersonalizedSchedulingControlDescriptions.TOGGLE)
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithText("Fit now").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset to defaults").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(state.enabled)
            assertTrue(toggled)
            assertEquals(1, fitRuns)
            assertEquals(1, resets)
        }
    }
}
