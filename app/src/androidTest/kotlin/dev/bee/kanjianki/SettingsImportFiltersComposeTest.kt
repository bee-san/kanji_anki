package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsImportFiltersComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freshSettingsRenderBrowserQueryUncheckedAndEmpty() {
        val state = freshState()

        render(state = state)

        composeRule.onNodeWithText(SettingsTextCopy.importFiltersTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.presetsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ankiBrowserQueryLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ankiBrowserQueryHint()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ankiBrowserQueryHelperText()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.minimumMatchingCardsLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.activeCardsLabel()).assertIsOff()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.suspendedCardsLabel()).assertIsOn()
        composeRule.onNodeWithContentDescription(SettingsTextCopy.browserQueryLabel()).assertIsOff()
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT).assertTextEquals("")
    }

    @Test
    fun blankBrowserQueryValidationBlocksSaveWhenChecked() {
        var saved = false
        var validationMessage = ""
        val state = freshState(suspendedCards = false)

        render(
            state = state,
            onSave = {
                if (state.browserQueryCards && state.browserQuery.trim().isEmpty()) {
                    validationMessage = SettingsTextCopy.browserQueryRequiredToast()
                } else {
                    saved = true
                }
            }
        )

        composeRule.onNodeWithContentDescription(SettingsTextCopy.browserQueryLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.saveImportFiltersLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(SettingsTextCopy.browserQueryRequiredToast(), validationMessage)
            assertFalse(saved)
        }
    }

    @Test
    fun nonblankBrowserQueryCanBeSaved() {
        var saved = false
        var savedBrowserQuery = ""
        val state = freshState(suspendedCards = false)

        render(
            state = state,
            onSave = {
                savedBrowserQuery = state.browserQuery
                saved = true
            }
        )

        composeRule.onNodeWithContentDescription(SettingsTextCopy.browserQueryLabel()).performClick()
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT).performTextReplacement("deck:Kiku tag:kani")
        composeRule.onNodeWithText(SettingsTextCopy.saveImportFiltersLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
            assertTrue(state.browserQueryCards)
            assertEquals("deck:Kiku tag:kani", savedBrowserQuery)
        }
    }

    @Test
    fun turningOffBrowserQueryPreservesTypedText() {
        val state = freshState(browserQueryCards = true, browserQuery = "deck:Kiku tag:kani")

        render(state = state)

        composeRule.onNodeWithContentDescription(SettingsTextCopy.browserQueryLabel()).performClick()

        composeRule.runOnIdle {
            assertFalse(state.browserQueryCards)
            assertEquals("deck:Kiku tag:kani", state.browserQuery)
        }
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT).assertTextEquals("deck:Kiku tag:kani")
    }

    @Test
    fun summaryUsesBrowserQueryPillWithoutPrivateBrowserQuery() {
        render(
            state = freshState(browserQueryCards = true, browserQuery = "deck:Private tag:secret"),
            summary = "browser query; 2 cards per kanji"
        )

        composeRule.onNodeWithText("browser query; 2 cards per kanji").assertTextEquals("browser query; 2 cards per kanji")
    }

    @Test
    fun longBrowserQueryFitsNarrowPanel() {
        val longQuery = "deck:Japanese tag:kani (rated:7:1 OR prop:cdn:d>20) -tag:private source:very-long-custom-import-filter"

        render(
            state = freshState(browserQueryCards = true, browserQuery = longQuery),
            widthDp = 320
        )

        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.BROWSER_QUERY_INPUT).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveImportFiltersLabel()).assertIsDisplayed()
    }

    @Test
    fun presetAndOtherFieldsStillWireActions() {
        var presetApplied = false
        var saved = false
        val state = freshState(difficulty = "7.5", lapses = "3", minMatching = "2")

        render(
            state = state,
            presets = listOf(SettingsImportPresetButtonModel("Leech tag", SettingsImportFilterAction { presetApplied = true })),
            onSave = { saved = true }
        )

        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.TAGS_INPUT).performTextReplacement("leeches, custom")
        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.DIFFICULTY_INPUT).assertTextEquals("7.5")
        composeRule.onNode(hasText("Leech tag") and hasClickAction()).performClick()
        composeRule.onNode(hasText(SettingsTextCopy.saveImportFiltersLabel()) and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertTrue(presetApplied)
            assertTrue(saved)
            assertEquals("leeches, custom", state.tags)
        }
    }

    @Test
    fun importFilterDraftSurvivesStateRestorationAndSavesThroughFreshModel() {
        var state = freshState(tags = "initial")
        var savedTags = ""
        var model = importModel(state) { restored -> savedTags = restored.tags }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { SettingsImportFiltersPanel(model) }

        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.TAGS_INPUT)
            .performTextReplacement("draft tags")

        state = freshState(tags = "initial")
        model = importModel(state) { restored -> savedTags = restored.tags }
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(SettingsImportFiltersTestTags.TAGS_INPUT).assertTextEquals("draft tags")
        composeRule.onNode(hasText(SettingsTextCopy.saveImportFiltersLabel()) and hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals("draft tags", savedTags)
        }
    }

    private fun render(
        state: SettingsImportFiltersState,
        summary: String = "Suspended cards",
        widthDp: Int? = null,
        presets: List<SettingsImportPresetButtonModel> = emptyList(),
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            val content = @androidx.compose.runtime.Composable {
                SettingsImportFiltersPanel(
                    model = importModel(
                        state = state,
                        summary = summary,
                        presets = presets,
                        onSave = { onSave() },
                    ),
                )
            }
            if (widthDp == null) {
                content()
            } else {
                Box(modifier = Modifier.width(widthDp.dp)) { content() }
            }
        }
    }

    private fun importModel(
        state: SettingsImportFiltersState,
        summary: String = "Suspended cards",
        presets: List<SettingsImportPresetButtonModel> = emptyList(),
        onSave: (SettingsImportFiltersState) -> Unit = {},
    ) = SettingsImportFiltersPanelModel(
        title = SettingsTextCopy.importFiltersTitle(),
        summary = summary,
        body = SettingsTextCopy.importFiltersBody(),
        presetsTitle = SettingsTextCopy.presetsTitle(),
        presets = presets,
        state = state,
        activeCardsLabel = SettingsTextCopy.activeCardsLabel(),
        suspendedCardsLabel = SettingsTextCopy.suspendedCardsLabel(),
        taggedCardsLabel = SettingsTextCopy.taggedCardsLabel(),
        weakCardsLabel = SettingsTextCopy.weakCardsLabel(),
        browserQueryCardsLabel = SettingsTextCopy.browserQueryLabel(),
        browserQueryLabel = SettingsTextCopy.ankiBrowserQueryLabel(),
        browserQueryHint = SettingsTextCopy.ankiBrowserQueryHint(),
        browserQueryHelperText = SettingsTextCopy.ankiBrowserQueryHelperText(),
        tagsLabel = SettingsTextCopy.ankiNoteTagsLabel(),
        tagsHint = SettingsTextCopy.ankiNoteTagsHint(),
        difficultyLabel = SettingsTextCopy.fsrsDifficultyLabel(),
        lapsesLabel = SettingsTextCopy.lapsesLabel(),
        minMatchingLabel = SettingsTextCopy.minimumMatchingCardsLabel(),
        saveLabel = SettingsTextCopy.saveImportFiltersLabel(),
        onSave = SettingsImportFilterSaveAction(onSave),
        tagRepairedCardsLabel = SettingsTextCopy.tagRepairedCardsLabel(),
    )

    private fun freshState(
        activeCards: Boolean = false,
        suspendedCards: Boolean = true,
        taggedCards: Boolean = false,
        weakCards: Boolean = false,
        browserQueryCards: Boolean = false,
        browserQuery: String = "",
        tags: String = "",
        difficulty: String = "7.5",
        lapses: String = "3",
        minMatching: String = "2",
    ): SettingsImportFiltersState {
        return SettingsImportFiltersState(
            activeCards = activeCards,
            suspendedCards = suspendedCards,
            taggedCards = taggedCards,
            weakCards = weakCards,
            browserQueryCards = browserQueryCards,
            browserQuery = browserQuery,
            tags = tags,
            difficulty = difficulty,
            lapses = lapses,
            minMatching = minMatching
        )
    }
}
