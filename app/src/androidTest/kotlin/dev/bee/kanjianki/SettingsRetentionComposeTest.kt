package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsActions
import dev.bee.kanjianki.core.FrequencyRetentionRanges
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsRetentionComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRetentionControlsAndWiresActions() {
        var saved = false
        var savedRetention = 0
        var savedRankRetention = false
        var savedRanges = ""
        val selected = intArrayOf(90)
        val state = SettingsRetentionState(
            frequencyRetentionEnabled = false,
            frequencyRetentionRanges = "1-500=95%"
        )

        composeRule.setContent {
            SettingsRetentionPanel(
                model = SettingsRetentionPanelModel(
                    title = SettingsTextCopy.fsrsRetentionTitle(),
                    body = SettingsTextCopy.fsrsRetentionBody(),
                    selectedRetentionPercent = selected,
                    presetValues = intArrayOf(85, 90, 95),
                    state = state,
                    rankRetentionLabel = SettingsTextCopy.useJitenRankRetentionRangesLabel(),
                    rankRangesBody = SettingsTextCopy.jitenRankRetentionRangesBody(),
                    exampleRangesText = FrequencyRetentionRanges.exampleText(),
                    exampleRangesLabel = SettingsTextCopy.useExampleRangesLabel(),
                    saveLabel = SettingsTextCopy.saveRetentionLabel(),
                    onSave = SettingsRetentionSaveAction { retentionPercent, rankRetentionEnabled, rankRanges ->
                        savedRetention = retentionPercent
                        savedRankRetention = rankRetentionEnabled
                        savedRanges = rankRanges
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.fsrsRetentionTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.retentionStatusText(90)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RETENTION_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(92f)
            }
        composeRule.onNodeWithText(SettingsTextCopy.retentionStatusText(92)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(92, selected[0])
        }
        composeRule.onNodeWithText(SettingsTextCopy.retentionPresetLabel(95)).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.retentionStatusText(95)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(95, selected[0])
        }

        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RANK_RETENTION_CHECKBOX).assertIsOff()
        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RANK_RETENTION_CHECKBOX).performClick()
        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RANK_RETENTION_CHECKBOX).assertIsOn()
        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RANK_RANGES_INPUT)
            .assertTextEquals("1-500=95%")
        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RANK_RANGES_INPUT)
            .performTextReplacement("1-200=96%")
        composeRule.onNodeWithText(SettingsTextCopy.useExampleRangesLabel()).performClick()
        composeRule.onNodeWithContentDescription(SettingsRetentionControlDescriptions.RANK_RANGES_INPUT)
            .assertTextEquals(FrequencyRetentionRanges.exampleText())
        composeRule.onNodeWithText(SettingsTextCopy.saveRetentionLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
            assertEquals(95, savedRetention)
            assertTrue(savedRankRetention)
            assertEquals(FrequencyRetentionRanges.exampleText(), savedRanges)
        }
    }
}
