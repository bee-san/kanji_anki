package dev.bee.kanjianki

import android.content.Context
import android.widget.CheckBox
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selected = intArrayOf(90)
        val ranges = EditText(context).apply { setText("1-500=95%") }

        composeRule.setContent {
            SettingsRetentionPanel(
                model = SettingsRetentionPanelModel(
                    title = SettingsTextCopy.fsrsRetentionTitle(),
                    body = SettingsTextCopy.fsrsRetentionBody(),
                    selectedRetentionPercent = selected,
                    presetValues = intArrayOf(85, 90, 95),
                    rankRetentionEnabled = CheckBox(context).apply {
                        text = SettingsTextCopy.useJitenRankRetentionRangesLabel()
                    },
                    rankRangesBody = SettingsTextCopy.jitenRankRetentionRangesBody(),
                    rankRangesInput = ranges,
                    exampleRangesLabel = SettingsTextCopy.useExampleRangesLabel(),
                    saveLabel = SettingsTextCopy.saveRetentionLabel(),
                    onUseExampleRanges = SettingsRetentionAction {
                        ranges.setText(FrequencyRetentionRanges.exampleText())
                    },
                    onSave = SettingsRetentionAction { saved = true }
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

        composeRule.onNodeWithText(SettingsTextCopy.useExampleRangesLabel()).performClick()
        composeRule.runOnIdle {
            assertEquals(FrequencyRetentionRanges.exampleText(), ranges.text.toString())
        }
        composeRule.onNodeWithText(SettingsTextCopy.saveRetentionLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(saved)
        }
    }
}
