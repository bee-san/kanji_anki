package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySettingsReferenceDataComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersReferenceDataLinkPanelAndInvokesAction() {
        var clicked = false

        composeRule.setContent {
            ReferenceDataLinkPanel(
                model = SettingsReferenceDataLinkModel(
                    title = "Data licenses",
                    body = "One reference page covers reference data.",
                    actionLabel = "Open data licenses",
                    onAction = Runnable { clicked = true }
                )
            )
        }

        composeRule.onNodeWithText("Data licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Open data licenses").performClick()

        assertTrue(clicked)
    }

    @Test
    fun rendersDataSourcesIntroAndInvokesBack() {
        var clicked = false

        composeRule.setContent {
            DataSourcesIntro(
                model = SettingsReferenceDataIntroModel(
                    backLabel = "Back to settings",
                    title = "Data licenses",
                    body = "Bundled source attribution.",
                    onBack = Runnable { clicked = true }
                )
            )
        }

        composeRule.onNodeWithText("Data licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Back to settings").performClick()

        assertTrue(clicked)
    }

    @Test
    fun rendersReferenceDataRouteAndInvokesNavigation() {
        var homeClicked = false
        var backClicked = false

        composeRule.setContent {
            ReferenceDataScreen(
                SettingsReferenceDataScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable { homeClicked = true },
                    intro = SettingsReferenceDataIntroModel(
                        backLabel = "Back to settings",
                        title = "Data licenses",
                        body = "Bundled source attribution.",
                        onBack = Runnable { backClicked = true }
                    ),
                    dataSources = SettingsReferenceDataModel(
                        dictionaryTitle = "Dictionary data",
                        dictionaryBody = "KANJIDIC2 and Jiten sources",
                        strokeTitle = "Stroke data",
                        strokeBody = "KanjiVG attribution and source path",
                        fontsTitle = "Fonts",
                        fontsBody = "Bundled font attribution"
                    )
                )
            )
        }

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Back to settings").performClick()
        composeRule.onNodeWithText("Dictionary data").assertIsDisplayed()
        composeRule.onNodeWithText("Stroke data").assertIsDisplayed()
        composeRule.onNodeWithText("Fonts").assertIsDisplayed()

        assertTrue(homeClicked)
        assertTrue(backClicked)
    }

    @Test
    fun rendersAttributionPanels() {
        composeRule.setContent {
            DataSourcesPanels(
                model = SettingsReferenceDataModel(
                    dictionaryTitle = "Dictionary data",
                    dictionaryBody = "KANJIDIC2 and Jiten sources",
                    strokeTitle = "Stroke data",
                    strokeBody = "KanjiVG attribution and source path",
                    fontsTitle = "Fonts",
                    fontsBody = "Bundled font attribution"
                )
            )
        }

        composeRule.onNodeWithText("Dictionary data").assertIsDisplayed()
        composeRule.onNodeWithText("KANJIDIC2 and Jiten sources").assertIsDisplayed()
        composeRule.onNodeWithText("Stroke data").assertIsDisplayed()
        composeRule.onNodeWithText("KanjiVG attribution and source path").assertIsDisplayed()
        composeRule.onNodeWithText("Fonts").assertIsDisplayed()
        composeRule.onNodeWithText("Bundled font attribution").assertIsDisplayed()
    }
}
