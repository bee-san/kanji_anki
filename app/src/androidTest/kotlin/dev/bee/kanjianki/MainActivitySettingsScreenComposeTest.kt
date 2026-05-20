package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySettingsScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSettingsRouteAndInvokesShellActions() {
        var homeClicked = false
        var categoryToggled = false

        composeRule.setContent {
            SettingsScreen(
                model = SettingsScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable { homeClicked = true },
                    hero = SettingsAutomationHeroModel(
                        cockpitLabel = "Cockpit",
                        title = "Settings",
                        body = "Configure Kani behavior.",
                        rows = listOf(
                            listOf(SettingsAutomationHeroPillModel("Note type", "Kiku", 0xFF7A245D.toInt())),
                            listOf(SettingsAutomationHeroPillModel("Daily sync", "Enabled", 0xFF00AEB5.toInt()))
                        )
                    ),
                    categories = listOf(
                        SettingsCategorySectionModel(
                            title = "Anki source",
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            expanded = false,
                            panelCount = "3 panels",
                            contentDescription = "Expand Anki source",
                            onToggle = Runnable { categoryToggled = true },
                            panels = emptyList()
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Note type").assertIsDisplayed()
        composeRule.onNodeWithText("Anki source").assertIsDisplayed()
        composeRule.onNodeWithText("3 panels").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithContentDescription("Expand Anki source").performClick()

        assertTrue(homeClicked)
        assertTrue(categoryToggled)
    }
}
