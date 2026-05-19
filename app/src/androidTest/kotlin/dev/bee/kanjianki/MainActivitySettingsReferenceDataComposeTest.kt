package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivitySettingsReferenceDataComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

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
