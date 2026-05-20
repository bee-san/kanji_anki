package dev.bee.kanjianki;

import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAnkiSourceNoteType {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceNoteType(MainActivitySettings activity) {
        this.activity = activity;
    }

    android.view.View noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        SettingsNoteTypeFieldState fieldState = new SettingsNoteTypeFieldState(
                current.modelName,
                current.expressionField,
                current.readingField,
                current.meaningField,
                current.sentenceField,
                current.frequencyField,
                current.frequencySortField
        );
        return MainActivitySettingsAnkiSourceNoteTypeCompose.noteTypeSettingsPanelView(
                activity,
                new SettingsNoteTypePanelModel(
                        SettingsTextCopy.noteTypeFieldsTitle(),
                        SettingsTextCopy.noteTypeUsingText(current.modelName),
                        SettingsTextCopy.noteTypeFieldsBody(),
                        fieldState,
                        SettingsTextCopy.requiredFieldsTitle(),
                        SettingsTextCopy.requiredFieldsBody(),
                        SettingsTextCopy.noteTypeStatusLabel(),
                        SettingsTextCopy.expressionFieldLabel(),
                        SettingsTextCopy.readingFieldLabel(),
                        SettingsTextCopy.meaningFieldLabel(),
                        SettingsTextCopy.sentenceFieldLabel(),
                        SettingsTextCopy.frequencyFieldLabel(),
                        SettingsTextCopy.frequencySortFieldLabel(),
                        SettingsTextCopy.chooseFromAnkiDroidLabel(),
                        SettingsTextCopy.useKikuLabel(),
                        SettingsTextCopy.saveNoteTypeLabel(),
                        () -> NoteTypeFieldMappings.choose(activity, activity.gateway, activity.io, activity.main, fieldState),
                        () -> fieldState.applyDefaults(defaults),
                        () -> saveNoteTypeFields(fieldState)
                )
        );
    }

    private void saveNoteTypeFields(SettingsNoteTypeFieldState fieldState) {
        String selectedNoteType = fieldState.getNoteType().trim();
        if (selectedNoteType.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.noteTypeRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        String selectedExpressionField = fieldState.getExpression().trim();
        if (selectedExpressionField.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.expressionFieldRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsWriteActions.saveNoteTypeFields(
                new SettingsWriteActions.NoteTypeFieldWriteRequest(
                        selectedNoteType,
                        selectedExpressionField,
                        fieldState.getReading().trim(),
                        fieldState.getMeaning().trim(),
                        fieldState.getSentence().trim(),
                        fieldState.getFrequency().trim(),
                        fieldState.getFrequencySort().trim()
                ),
                activity.store::putStringSetting
        );
        Toast.makeText(activity, SettingsTextCopy.noteTypeSavedToast(), Toast.LENGTH_LONG).show();
        activity.renderSettings();
    }
}
