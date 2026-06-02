package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
        var dataClicked = false

        composeRule.setContent {
            SettingsScreen(
                model = SettingsScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable { homeClicked = true },
                    hero = SettingsAutomationHeroModel(
                        cockpitLabel = "Settings overview",
                        title = "Settings",
                        body = "Configure Kani behavior.",
                        rows = listOf(
                            listOf(SettingsAutomationHeroPillModel("Anki note type", "Kiku", 0xFF7A245D.toInt())),
                            listOf(SettingsAutomationHeroPillModel("Daily Anki sync", "Enabled", 0xFF00AEB5.toInt()))
                        )
                    ),
                    categories = listOf(
                        SettingsCategorySectionModel(
                            title = "Import from Anki",
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            expanded = false,
                            panelCount = "3 panels",
                            contentDescription = "Expand Import from Anki",
                            onToggle = Runnable { categoryToggled = true },
                            panels = emptyList()
                        ),
                        SettingsCategorySectionModel(
                            title = "Data sources",
                            summary = "Offline data and licenses.",
                            iconRes = R.drawable.ic_sparkle_24,
                            expanded = true,
                            panelCount = "1 panel",
                            contentDescription = "Collapse Data sources",
                            onToggle = Runnable {},
                            panels = listOf(
                                SettingsReferenceDataLinkModel(
                                    title = "Offline data licenses",
                                    body = "Dictionary, stroke, and font attributions.",
                                    actionLabel = "Open licenses",
                                    onAction = Runnable { dataClicked = true }
                                )
                            )
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Anki note type").assertIsDisplayed()
        composeRule.onNodeWithText("Import from Anki").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("Import from Anki")).assertIsDisplayed()
        composeRule.onNodeWithText("3 panels").assertIsDisplayed()
        composeRule.onNodeWithText("Data sources").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("Data sources")).assertIsDisplayed()
        composeRule.onNodeWithText("Offline data licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Open licenses").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithContentDescription("Expand Import from Anki").performClick()
        composeRule.onNodeWithText("Open licenses").performClick()

        assertTrue(homeClicked)
        assertTrue(categoryToggled)
        assertTrue(dataClicked)
    }
}
