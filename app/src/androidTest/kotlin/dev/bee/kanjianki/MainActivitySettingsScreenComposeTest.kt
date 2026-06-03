package dev.bee.kanjianki

import androidx.compose.runtime.mutableStateOf
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
    fun preservesExpandedCategoryWhenTitleChanges() {
        val titleState = mutableStateOf("Anki import")

        composeRule.setContent {
            SettingsScreen(
                model = SettingsScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable {},
                    hero = SettingsAutomationHeroModel(
                        cockpitLabel = "Settings overview",
                        title = "Settings",
                        body = "Configure Kani behavior.",
                        rows = listOf(
                            listOf(SettingsAutomationHeroPillModel("Anki note type", "Kiku", 0xFF7A245D.toInt())),
                        ),
                    ),
                    categories = listOf(
                        SettingsCategorySectionModel(
                            sectionKey = "settings-anki-source",
                            title = titleState.value,
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            expanded = false,
                            panelCount = "1 panel",
                            contentDescription = "Expand ${titleState.value}",
                            onToggle = Runnable {},
                            panels = listOf(
                                SettingsReferenceDataLinkModel(
                                    title = "Import details",
                                    body = "Review the import mapping before enabling it.",
                                    actionLabel = "Open import details",
                                    onAction = Runnable {},
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("settings-anki-source")).performClick()
        composeRule.onNodeWithText("Open import details").assertIsDisplayed()

        composeRule.runOnIdle {
            titleState.value = "Import sources"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open import details").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collapse Import sources").assertIsDisplayed()
    }

    @Test
    fun rendersSettingsRouteAndInvokesShellActions() {
        var homeClicked = false
        var categoryToggled = false
        var importClicked = false

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
                            sectionKey = "settings-anki-source",
                            title = "Anki import",
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            expanded = false,
                            panelCount = "3 panels",
                            contentDescription = "Expand Anki import",
                            onToggle = Runnable { categoryToggled = true },
                            panels = listOf(
                                SettingsReferenceDataLinkModel(
                                    title = "Import details",
                                    body = "Review the import mapping before enabling it.",
                                    actionLabel = "Open import details",
                                    onAction = Runnable { importClicked = true }
                                )
                            )
                        ),
                        SettingsCategorySectionModel(
                            sectionKey = "settings-reference-data",
                            title = "Reference data",
                            summary = "Reference data and licenses.",
                            iconRes = R.drawable.ic_sparkle_24,
                            expanded = true,
                            panelCount = "1 panel",
                            contentDescription = "Collapse Reference data",
                            onToggle = Runnable {},
                            panels = listOf(
                                SettingsReferenceDataLinkModel(
                                    title = "Data licenses",
                                    body = "Dictionary, stroke, and font attributions.",
                                    actionLabel = "Open licenses",
                                    onAction = Runnable {}
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
        composeRule.onNodeWithText("Anki import").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("settings-anki-source")).assertIsDisplayed()
        composeRule.onNodeWithText("3 panels").assertIsDisplayed()
        composeRule.onNodeWithText("Reference data").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("settings-reference-data")).assertIsDisplayed()
        composeRule.onNodeWithText("Open import details").assertDoesNotExist()
        composeRule.onNodeWithText("Data licenses").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithContentDescription("Expand Anki import").performClick()
        composeRule.onNodeWithContentDescription("Collapse Anki import").assertIsDisplayed()
        composeRule.onNodeWithText("Open import details").assertExists()
        composeRule.onNodeWithText("Open import details").performClick()
        composeRule.onNodeWithText("Open licenses").assertExists()

        assertTrue(homeClicked)
        assertTrue(categoryToggled)
        assertTrue(importClicked)
    }
}
