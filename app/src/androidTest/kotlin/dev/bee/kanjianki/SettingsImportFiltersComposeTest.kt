package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsImportFiltersComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFieldsPresetsAndWiresActions() {
        var presetApplied = false
        var saved = false
        var savedBrowserQuery = ""
        val state = SettingsImportFiltersState(
            activeCards = false,
            suspendedCards = true,
            taggedCards = false,
            weakCards = false,
            browserQueryCards = false,
            browserQuery = "",
            tags = "",
            difficulty = "7.5",
            lapses = "3",
            minMatching = "2"
        )

        composeRule.setContent {
            SettingsImportFiltersPanel(
                model = SettingsImportFiltersPanelModel(
                    title = SettingsTextCopy.importFiltersTitle(),
                    summary = "Suspended cards",
                    body = SettingsTextCopy.importFiltersBody(),
                    presetsTitle = SettingsTextCopy.presetsTitle(),
                    presets = listOf(
                        SettingsImportPresetButtonModel("Leech tag", SettingsImportFilterAction { presetApplied = true })
                    ),
                    state = state,
                    activeCardsLabel = SettingsTextCopy.activeCardsLabel(),
                    suspendedCardsLabel = SettingsTextCopy.suspendedCardsLabel(),
                    taggedCardsLabel = SettingsTextCopy.taggedCardsLabel(),
                    weakCardsLabel = SettingsTextCopy.weakCardsLabel(),
                    browserQueryCardsLabel = SettingsTextCopy.browserQueryLabel(),
                    browserQueryLabel = SettingsTextCopy.ankiBrowserQueryLabel(),
                    browserQueryHint = SettingsTextCopy.ankiBrowserQueryHint(),
                    tagsLabel = SettingsTextCopy.ankiNoteTagsLabel(),
                    tagsHint = SettingsTextCopy.ankiNoteTagsHint(),
                    difficultyLabel = SettingsTextCopy.fsrsDifficultyLabel(),
                    lapsesLabel = SettingsTextCopy.lapsesLabel(),
                    minMatchingLabel = SettingsTextCopy.minimumMatchingCardsLabel(),
                    saveLabel = SettingsTextCopy.saveImportFiltersLabel(),
                    onSave = SettingsImportFilterAction {
                        savedBrowserQuery = state.browserQuery
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.importFiltersTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.presetsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ankiBrowserQueryLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.minimumMatchingCardsLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.activeCardsLabel()).assertIsOff()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.suspendedCardsLabel()).assertIsOn()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.browserQueryLabel()).performClick()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.browserQueryLabel()).assertIsOn()
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT).performTextReplacement("deck:Kiku")
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.TAGS_INPUT).performTextReplacement("leeches, custom")
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.DIFFICULTY_INPUT).assertTextEquals("7.5")
        composeRule.onNodeWithText("Leech tag").performClick()
        composeRule.onNodeWithText(SettingsTextCopy.saveImportFiltersLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(presetApplied)
            assertTrue(saved)
            assertTrue(state.browserQueryCards)
            assertEquals("deck:Kiku", savedBrowserQuery)
            assertEquals("leeches, custom", state.tags)
        }
    }
}
