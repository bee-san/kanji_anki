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
        val titleState = mutableStateOf("Import from Anki")
        val expandedState = mutableStateOf(false)

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
                            title = titleState.value,
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            expanded = expandedState.value,
                            panelCount = "1 panel",
                            contentDescription = if (expandedState.value) {
                                "Collapse ${titleState.value}"
                            } else {
                                "Expand ${titleState.value}"
                            },
                            onToggle = Runnable {
                                expandedState.value = !expandedState.value
                            },
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

        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("Import from Anki")).performClick()
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
                            title = "Import from Anki",
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            expanded = false,
                            panelCount = "3 panels",
                            contentDescription = "Expand Import from Anki",
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
                            title = "Reference data",
                            summary = "Offline data and licenses.",
                            iconRes = R.drawable.ic_sparkle_24,
                            expanded = true,
                            panelCount = "1 panel",
                            contentDescription = "Collapse Reference data",
                            onToggle = Runnable {},
                            panels = listOf(
                                SettingsReferenceDataLinkModel(
                                    title = "Offline data licenses",
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
        composeRule.onNodeWithText("Import from Anki").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("Import from Anki")).assertIsDisplayed()
        composeRule.onNodeWithText("3 panels").assertIsDisplayed()
        composeRule.onNodeWithText("Reference data").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsCategoryHeaderTestTag("Reference data")).assertIsDisplayed()
        composeRule.onNodeWithText("Open import details").assertDoesNotExist()
        composeRule.onNodeWithText("Offline data licenses").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithContentDescription("Expand Import from Anki").performClick()
        composeRule.onNodeWithContentDescription("Collapse Import from Anki").assertIsDisplayed()
        composeRule.onNodeWithText("Open import details").assertExists()
        composeRule.onNodeWithText("Open import details").performClick()
        composeRule.onNodeWithText("Open licenses").assertExists()

        assertTrue(homeClicked)
        assertTrue(categoryToggled)
        assertTrue(importClicked)
    }
}
