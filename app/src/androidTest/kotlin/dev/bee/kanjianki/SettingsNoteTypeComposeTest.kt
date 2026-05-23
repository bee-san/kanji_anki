package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsNoteTypeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFieldsAndWiresActions() {
        var chose = false
        var kiku = false
        var saved = false
        var savedNoteType = ""
        var savedExpression = ""
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val fieldState = SettingsNoteTypeFieldState(
            noteType = "Kiku",
            expression = defaults.expressionField,
            reading = defaults.readingField,
            meaning = defaults.meaningField,
            sentence = defaults.sentenceField,
            frequency = defaults.frequencyField,
            frequencySort = defaults.frequencySortField
        )

        composeRule.setContent {
            SettingsNoteTypePanel(
                model = SettingsNoteTypePanelModel(
                    title = SettingsTextCopy.noteTypeFieldsTitle(),
                    status = SettingsTextCopy.noteTypeUsingText("Kiku"),
                    body = SettingsTextCopy.noteTypeFieldsBody(),
                    fields = fieldState,
                    requiredTitle = SettingsTextCopy.requiredFieldsTitle(),
                    requiredBody = SettingsTextCopy.requiredFieldsBody(),
                    noteTypeLabel = SettingsTextCopy.noteTypeStatusLabel(),
                    expressionLabel = SettingsTextCopy.expressionFieldLabel(),
                    readingLabel = SettingsTextCopy.readingFieldLabel(),
                    meaningLabel = SettingsTextCopy.meaningFieldLabel(),
                    sentenceLabel = SettingsTextCopy.sentenceFieldLabel(),
                    frequencyLabel = SettingsTextCopy.frequencyFieldLabel(),
                    frequencySortLabel = SettingsTextCopy.frequencySortFieldLabel(),
                    chooseLabel = SettingsTextCopy.chooseFromAnkiDroidLabel(),
                    kikuLabel = SettingsTextCopy.useKikuLabel(),
                    saveLabel = SettingsTextCopy.saveNoteTypeLabel(),
                    onChoose = SettingsNoteTypeAction {
                        fieldState.setNoteType("Chosen model")
                        fieldState.setExpression("Front")
                        chose = true
                    },
                    onUseKiku = SettingsNoteTypeAction {
                        fieldState.applyDefaults(defaults)
                        kiku = true
                    },
                    onSave = SettingsNoteTypeAction {
                        savedNoteType = fieldState.noteType
                        savedExpression = fieldState.expression
                        saved = true
                    }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.noteTypeFieldsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.noteTypeUsingText("Kiku")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.expressionFieldLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsNoteTypeTestTags.NOTE_TYPE_INPUT).performTextReplacement("Custom model")
        composeRule.onNodeWithTag(SettingsNoteTypeTestTags.EXPRESSION_FIELD_INPUT).performTextReplacement("Word")
        composeRule.onNodeWithText(SettingsTextCopy.saveNoteTypeLabel()).performClick()
        composeRule.runOnIdle {
            assertTrue(saved)
            assertEquals("Custom model", savedNoteType)
            assertEquals("Word", savedExpression)
        }

        composeRule.onNodeWithText(SettingsTextCopy.chooseFromAnkiDroidLabel()).performClick()
        composeRule.onNodeWithTag(SettingsNoteTypeTestTags.NOTE_TYPE_INPUT).assertTextEquals("Chosen model")
        composeRule.onNodeWithTag(SettingsNoteTypeTestTags.EXPRESSION_FIELD_INPUT).assertTextEquals("Front")
        composeRule.onNodeWithText(SettingsTextCopy.useKikuLabel()).performClick()
        composeRule.onNodeWithTag(SettingsNoteTypeTestTags.NOTE_TYPE_INPUT).assertTextEquals(defaults.modelName)
        composeRule.onNodeWithTag(SettingsNoteTypeTestTags.EXPRESSION_FIELD_INPUT).assertTextEquals(defaults.expressionField)

        composeRule.runOnIdle {
            assertTrue(chose)
            assertTrue(kiku)
        }
    }
}
