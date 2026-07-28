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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsFlashcardGestureComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun panelRendersStatusAndTogglesSwipeOff() {
        var newValue: Boolean? = null
        val state = SettingsFlashcardGestureState(true)
        val panel = SettingsFlashcardGesturePanelModel(
            title = "Swipe to grade",
            body = "Swipe left to fail or right to pass after revealing the answer.",
            status = "On — swipe left or right to grade",
            state = state,
            toggleLabel = "Enable swipe to grade",
            onToggle = SettingsFlashcardGestureToggleAction { newValue = it },
        )
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsSubmenuScreen(
                    SettingsSubmenuScreenModel(
                        "Home", Runnable {}, "Back", Runnable {},
                        "Study settings", "Gestures", listOf(panel),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("settings-panel-flashcard-gesture").assertIsDisplayed()
        composeRule.onNodeWithText("On — swipe left or right to grade").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsFlashcardGestureControlDescriptions.TOGGLE)
            .assertIsOn()
            .performClick()
            .assertIsOff()

        composeRule.runOnIdle {
            assertFalse(state.enabled)
            assertFalse(newValue ?: true)
        }
    }
}
