package dev.bee.kanjianki

import android.content.Context
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

class SettingsNoteTypeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFieldsAndWiresActions() {
        var chose = false
        var kiku = false
        var saved = false
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeRule.setContent {
            SettingsNoteTypePanel(
                model = SettingsNoteTypePanelModel(
                    title = SettingsTextCopy.noteTypeFieldsTitle(),
                    status = SettingsTextCopy.noteTypeUsingText("Kiku"),
                    body = SettingsTextCopy.noteTypeFieldsBody(),
                    noteTypeInput = EditText(context).apply { setText("Kiku") },
                    requiredTitle = SettingsTextCopy.requiredFieldsTitle(),
                    requiredBody = SettingsTextCopy.requiredFieldsBody(),
                    fields = listOf(
                        SettingsNoteTypeFieldModel(SettingsTextCopy.expressionFieldLabel(), EditText(context)),
                        SettingsNoteTypeFieldModel(SettingsTextCopy.readingFieldLabel(), EditText(context)),
                        SettingsNoteTypeFieldModel(SettingsTextCopy.meaningFieldLabel(), EditText(context))
                    ),
                    chooseLabel = SettingsTextCopy.chooseFromAnkiDroidLabel(),
                    kikuLabel = SettingsTextCopy.useKikuLabel(),
                    saveLabel = SettingsTextCopy.saveNoteTypeLabel(),
                    onChoose = SettingsNoteTypeAction { chose = true },
                    onUseKiku = SettingsNoteTypeAction { kiku = true },
                    onSave = SettingsNoteTypeAction { saved = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.noteTypeFieldsTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.noteTypeUsingText("Kiku")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.expressionFieldLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.chooseFromAnkiDroidLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.useKikuLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.saveNoteTypeLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(chose)
            assertTrue(kiku)
            assertTrue(saved)
        }
    }
}
