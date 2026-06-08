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
    fun rendersHeroBadgeTitleAndStatusPills() {
        composeRule.setContent {
            SettingsAutomationHero(
                model = SettingsAutomationHeroModel(
                    cockpitLabel = "Overview",
                    title = "Settings",
                    body = "Sync, retention, and import controls live here.",
                    rows = listOf(
                        listOf(
                            SettingsAutomationHeroPillModel("Note type", "Kiku", 0xFF7A245D.toInt()),
                            SettingsAutomationHeroPillModel("Import filters", "1-20 / 4", 0xFF00AEB5.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Kanji frequency range", "1-100", 0xFF00AEB5.toInt()),
                            SettingsAutomationHeroPillModel("Daily reminder", "Daily around 21:05", 0xFF6E6E78.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Daily sync", "Enabled", 0xFF00AEB5.toInt()),
                            SettingsAutomationHeroPillModel("App updates", "Ready to install", 0xFFFF4C76.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Cards per kanji", "Kiku only", 0xFF7A245D.toInt())
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Overview").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Note type").assertIsDisplayed()
        composeRule.onNodeWithText("Kiku").assertIsDisplayed()
        composeRule.onNodeWithText("Import filters").assertIsDisplayed()
        composeRule.onNodeWithText("1-20 / 4").assertIsDisplayed()
        composeRule.onNodeWithText("Kanji frequency range").assertIsDisplayed()
        composeRule.onNodeWithText("Daily reminder").assertIsDisplayed()
        composeRule.onNodeWithText("Daily sync").assertIsDisplayed()
        composeRule.onNodeWithText("App updates").assertIsDisplayed()
        composeRule.onNodeWithText("Cards per kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Kiku only").assertIsDisplayed()
    }
}
