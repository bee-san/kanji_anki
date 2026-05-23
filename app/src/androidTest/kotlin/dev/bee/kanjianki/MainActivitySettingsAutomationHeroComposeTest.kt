package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivitySettingsAutomationHeroComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersHeroBadgeTitleBodyAndStatusPills() {
        composeRule.setContent {
            SettingsAutomationHero(
                model = SettingsAutomationHeroModel(
                    cockpitLabel = "Settings cockpit",
                    title = "Settings",
                    body = "Sync, retention, and import controls live here.",
                    rows = listOf(
                        listOf(
                            SettingsAutomationHeroPillModel("Note type", "Kiku", 0xFF7A245D.toInt()),
                            SettingsAutomationHeroPillModel("Import filters", "1-20 / 4", 0xFF00AEB5.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Import ranks", "1-100", 0xFF00AEB5.toInt()),
                            SettingsAutomationHeroPillModel("Reminder", "Daily around 21:05", 0xFF6E6E78.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Daily sync", "Enabled", 0xFF00AEB5.toInt()),
                            SettingsAutomationHeroPillModel("Updates", "Pending verified APK", 0xFFFF4C76.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Matching cards", "Kiku only", 0xFF7A245D.toInt())
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Settings cockpit").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Sync, retention, and import controls live here.").assertIsDisplayed()
        composeRule.onNodeWithText("Note type").assertIsDisplayed()
        composeRule.onNodeWithText("Kiku").assertIsDisplayed()
        composeRule.onNodeWithText("Import filters").assertIsDisplayed()
        composeRule.onNodeWithText("1-20 / 4").assertIsDisplayed()
        composeRule.onNodeWithText("Matching cards").assertIsDisplayed()
        composeRule.onNodeWithText("Kiku only").assertIsDisplayed()
    }
}
