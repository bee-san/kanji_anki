package dev.bee.kanjianki

import android.content.Context
import android.widget.CheckBox
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.SettingsTextCopy
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
        val context = ApplicationProvider.getApplicationContext<Context>()

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
                    sourceChecks = importFilterChecks(context),
                    browserQueryField = importFilterField(context, SettingsTextCopy.ankiBrowserQueryLabel()),
                    tagsField = importFilterField(context, SettingsTextCopy.ankiNoteTagsLabel()),
                    difficultyField = importFilterField(context, SettingsTextCopy.fsrsDifficultyLabel()),
                    lapsesField = importFilterField(context, SettingsTextCopy.lapsesLabel()),
                    minMatchingField = importFilterField(context, SettingsTextCopy.minimumMatchingCardsLabel()),
                    saveLabel = SettingsTextCopy.saveImportFiltersLabel(),
                    onSave = SettingsImportFilterAction { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.importFiltersTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.presetsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.ankiBrowserQueryLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.minimumMatchingCardsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("Leech tag").performClick()
        composeRule.onNodeWithText(SettingsTextCopy.saveImportFiltersLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(presetApplied)
            assertTrue(saved)
        }
    }

    private fun importFilterChecks(context: Context): List<CheckBox> {
        return listOf(
            SettingsTextCopy.activeCardsLabel(),
            SettingsTextCopy.suspendedCardsLabel(),
            SettingsTextCopy.taggedCardsLabel(),
            SettingsTextCopy.weakCardsLabel(),
            SettingsTextCopy.browserQueryLabel()
        ).map { label ->
            CheckBox(context).apply { text = label }
        }
    }

    private fun importFilterField(context: Context, label: String): SettingsImportFilterFieldModel {
        return SettingsImportFilterFieldModel(
            label = label,
            input = EditText(context),
            heightDp = 52
        )
    }
}
