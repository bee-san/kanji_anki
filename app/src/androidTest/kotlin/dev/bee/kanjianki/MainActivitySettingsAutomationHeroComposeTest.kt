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
                    cockpitLabel = "Settings overview",
                    title = "Settings",
                    body = "Sync, retention, and import controls live here.",
                    rows = listOf(
                        listOf(
                            SettingsAutomationHeroPillModel("Anki note type", "Kiku", 0xFF7A245D.toInt()),
                            SettingsAutomationHeroPillModel("Import filters", "1-20 / 4", 0xFF00AEB5.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Suspended card range", "1-100", 0xFF00AEB5.toInt()),
                            SettingsAutomationHeroPillModel("Daily reminder", "Daily around 21:05", 0xFF6E6E78.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Daily sync", "Enabled", 0xFF00AEB5.toInt()),
                            SettingsAutomationHeroPillModel("App updates", "Pending verified APK", 0xFFFF4C76.toInt())
                        ),
                        listOf(
                            SettingsAutomationHeroPillModel("Cards per kanji", "Kiku only", 0xFF7A245D.toInt())
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Settings overview").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Sync, retention, and import controls live here.").assertIsDisplayed()
        composeRule.onNodeWithText("Anki note type").assertIsDisplayed()
        composeRule.onNodeWithText("Kiku").assertIsDisplayed()
        composeRule.onNodeWithText("Import filters").assertIsDisplayed()
        composeRule.onNodeWithText("1-20 / 4").assertIsDisplayed()
        composeRule.onNodeWithText("Suspended card range").assertIsDisplayed()
        composeRule.onNodeWithText("Daily reminder").assertIsDisplayed()
        composeRule.onNodeWithText("Daily sync").assertIsDisplayed()
        composeRule.onNodeWithText("App updates").assertIsDisplayed()
        composeRule.onNodeWithText("Cards per kanji").assertIsDisplayed()
        composeRule.onNodeWithText("Kiku only").assertIsDisplayed()
    }
}
