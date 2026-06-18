package dev.bee.kanjianki

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySettingsScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersHubCardsAndUpdatesCardDescription() {
        val titleState = mutableStateOf("Import & sync")

        composeRule.setContent {
            SettingsScreen(
                model = SettingsScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable {},
                    hero = SettingsAutomationHeroModel(
                        cockpitLabel = "Overview",
                        title = "Settings",
                        body = "Configure Kani behavior.",
                        rows = listOf(
                            listOf(SettingsAutomationHeroPillModel("Note type", "Kiku", 0xFF7A245D.toInt())),
                        ),
                    ),
                    cards = listOf(
                        SettingsHubCardModel(
                            routeKey = MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE,
                            title = titleState.value,
                            summary = "Choose what gets imported.",
                            iconRes = R.drawable.ic_book_24,
                            panelCount = "4 cards",
                            contentDescription = SettingsTextCopy.sectionOpenDescription(titleState.value),
                            onOpen = Runnable {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Import & sync").assertIsDisplayed()
        composeRule.onNodeWithText("4 cards").assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SCREEN_BOTTOM_SPACER_TAG).assertExists()

        composeRule.runOnIdle { titleState.value = "Import sources" }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Import sources").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.sectionOpenDescription("Import sources")).assertIsDisplayed()
    }

    @Test
    fun rendersSubmenuScreenWithBackButtonAndPanels() {
        var homeClicked = false
        var backClicked = false
        val panel = SettingsReferenceDataLinkModel(
            title = "Data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = "Open licenses",
            onAction = Runnable {},
        )

        composeRule.setContent {
            SettingsSubmenuScreen(
                model = SettingsSubmenuScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable { homeClicked = true },
                    backLabel = SettingsTextCopy.backToSettingsLabel(),
                    onBack = Runnable { backClicked = true },
                    title = "Display & data",
                    body = "Manage dictionaries and credits.",
                    panels = listOf(panel),
                ),
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Display & data").assertIsDisplayed()
        composeRule.onNodeWithText("Manage dictionaries and credits.").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsPanelTestTag(panel)).assertIsDisplayed()

        assertTrue(homeClicked)
        assertTrue(backClicked)
    }
}
