package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenCategoryNavigationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hubCardsExposeDescriptionsAndInvokeOpenCallbacks() {
        var openRuns = 0
        val screen = SettingsScreenModel(
            homeLabel = "Home",
            onHome = Runnable {},
            hero = SettingsAutomationHeroModel(
                cockpitLabel = "Overview",
                title = "Settings",
                body = "Configure Kani behavior.",
                rows = emptyList(),
            ),
            cards = listOf(
                SettingsHubCardModel(
                    routeKey = MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
                    title = "Study settings",
                    summary = "Review pace and learning controls.",
                    iconRes = R.drawable.ic_study_24,
                    panelCount = "8 cards",
                    contentDescription = "Open Study settings",
                    onOpen = Runnable { openRuns += 1 },
                ),
            ),
        )

        composeRule.setContent { SettingsScreen(screen) }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Study settings").assertIsDisplayed()
        composeRule.onNodeWithText("8 cards").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open Study settings").assertIsDisplayed()
        composeRule.onNodeWithTag(settingsHubCardTestTag(MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE))
            .assertIsDisplayed()
            .performClick()

        assertTrue(openRuns == 1)
    }

    @Test
    fun submenuBackButtonReturnsToSettingsAndKeepsContentVisible() {
        var homeClicked = false
        var backClicked = false
        val panel = SettingsReferenceDataLinkModel(
            title = "Data licenses",
            body = "Dictionary, stroke, and font attributions.",
            actionLabel = SettingsTextCopy.openDataLicensesLabel(),
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
