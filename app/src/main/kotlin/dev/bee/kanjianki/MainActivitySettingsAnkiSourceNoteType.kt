package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsAnkiSourceNoteType(private val activity: MainActivitySettings) {
    fun noteTypeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNoteTypePanelModel {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val fieldState = SettingsNoteTypeFieldState(
            current.modelName,
            current.expressionField,
            current.readingField,
            current.meaningField,
            current.sentenceField,
            current.frequencyField,
            current.frequencySortField
        )
        return SettingsNoteTypePanelModel(
            title = SettingsTextCopy.noteTypeFieldsTitle(),
            status = SettingsTextCopy.noteTypeUsingText(current.modelName),
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
                NoteTypeFieldMappings.choose(activity, activity.gateway, activity.io, activity.main, fieldState)
            },
            onUseKiku = SettingsNoteTypeAction { fieldState.applyDefaults(defaults) },
            onSave = SettingsNoteTypeAction { saveNoteTypeFields(fieldState) }
        )
    }

    private fun saveNoteTypeFields(fieldState: SettingsNoteTypeFieldState) {
        val selectedNoteType = fieldState.noteType.trim()
        if (selectedNoteType.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.noteTypeRequiredToast(), Toast.LENGTH_SHORT).show()
            return
        }
        val selectedExpressionField = fieldState.expression.trim()
        if (selectedExpressionField.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.expressionFieldRequiredToast(), Toast.LENGTH_SHORT).show()
            return
        }
        SettingsWriteActions.saveNoteTypeFields(
            SettingsWriteActions.NoteTypeFieldWriteRequest(
                selectedNoteType,
                selectedExpressionField,
                fieldState.reading.trim(),
                fieldState.meaning.trim(),
                fieldState.sentence.trim(),
                fieldState.frequency.trim(),
                fieldState.frequencySort.trim()
            ),
            activity.store::putStringSetting
        )
        Toast.makeText(activity, SettingsTextCopy.noteTypeSavedToast(), Toast.LENGTH_LONG).show()
        activity.renderSettings(true)
    }
}
