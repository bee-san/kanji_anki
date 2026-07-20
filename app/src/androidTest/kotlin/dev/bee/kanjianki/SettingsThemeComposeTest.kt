package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsThemeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAllThemeChoicesWithPreviewStripsAndSelectedBadge() {
        val currentChoice = mutableStateOf(KaniThemeChoice.GIRLYPOP)

        composeRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                KaniTheme(choice = currentChoice.value, isSystemInDarkTheme = false) {
                    SettingsThemePanel(
                        model = SettingsThemePanelModels.themeSettingsPanelModel(
                            currentChoice = currentChoice.value,
                            onSelectChoice = { currentChoice.value = it },
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText(SettingsThemeCopy.appearanceTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsThemeCopy.appearanceBody()).assertIsDisplayed()

        KaniThemeChoice.entries.forEach { choice ->
            composeRule.onNodeWithTag("settings-theme-choice-${choice.storageKey}", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithTag("settings-theme-choice-preview-${choice.storageKey}", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeRule.onNodeWithTag("settings-theme-choice-selected-girlypop", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(SettingsThemeCopy.selectedLabel()).assertIsDisplayed()
    }

    @Test
    fun selectingAChoiceReRendersTheSelectedBadgeAndCallsBack() {
        val currentChoice = mutableStateOf(KaniThemeChoice.GIRLYPOP)
        var persistedChoice: KaniThemeChoice? = null

        composeRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                KaniTheme(choice = currentChoice.value, isSystemInDarkTheme = false) {
                    SettingsThemePanel(
                        model = SettingsThemePanelModels.themeSettingsPanelModel(
                            currentChoice = currentChoice.value,
                            onSelectChoice = {
                                persistedChoice = it
                                currentChoice.value = it
                            },
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-theme-choice-dark", useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(KaniThemeChoice.DARK, persistedChoice)
        }
        composeRule.onNodeWithTag("settings-theme-choice-selected-dark", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("settings-theme-choice-selected-girlypop"), useUnmergedTree = true).assertCountEquals(0)
    }
}
