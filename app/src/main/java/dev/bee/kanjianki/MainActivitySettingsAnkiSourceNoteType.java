package dev.bee.kanjianki;

import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.Arrays;

final class MainActivitySettingsAnkiSourceNoteType {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceInputs inputs;

    MainActivitySettingsAnkiSourceNoteType(MainActivitySettings activity, MainActivitySettingsAnkiSourceInputs inputs) {
        this.activity = activity;
        this.inputs = inputs;
    }

    android.view.View noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        EditText noteType = inputs.noteTypeInput(current.modelName);
        EditText expressionField = inputs.fieldInput(current.expressionField);
        EditText readingField = inputs.fieldInput(current.readingField);
        EditText meaningField = inputs.fieldInput(current.meaningField);
        EditText sentenceField = inputs.fieldInput(current.sentenceField);
        EditText frequencyField = inputs.fieldInput(current.frequencyField);
        EditText frequencySortField = inputs.fieldInput(current.frequencySortField);
        setInputDescription(noteType, SettingsTextCopy.noteTypeStatusLabel());
        setInputDescription(expressionField, SettingsTextCopy.expressionFieldLabel());
        setInputDescription(readingField, SettingsTextCopy.readingFieldLabel());
        setInputDescription(meaningField, SettingsTextCopy.meaningFieldLabel());
        setInputDescription(sentenceField, SettingsTextCopy.sentenceFieldLabel());
        setInputDescription(frequencyField, SettingsTextCopy.frequencyFieldLabel());
        setInputDescription(frequencySortField, SettingsTextCopy.frequencySortFieldLabel());

        NoteTypeFieldMappings.Inputs fieldMappings = new NoteTypeFieldMappings.Inputs(
                noteType,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField
        );
        return MainActivitySettingsAnkiSourceNoteTypeCompose.noteTypeSettingsPanelView(
                activity,
                new SettingsNoteTypePanelModel(
                        SettingsTextCopy.noteTypeFieldsTitle(),
                        SettingsTextCopy.noteTypeUsingText(current.modelName),
                        SettingsTextCopy.noteTypeFieldsBody(),
                        noteType,
                        SettingsTextCopy.requiredFieldsTitle(),
                        SettingsTextCopy.requiredFieldsBody(),
                        Arrays.asList(
                                new SettingsNoteTypeFieldModel(SettingsTextCopy.expressionFieldLabel(), expressionField),
                                new SettingsNoteTypeFieldModel(SettingsTextCopy.readingFieldLabel(), readingField),
                                new SettingsNoteTypeFieldModel(SettingsTextCopy.meaningFieldLabel(), meaningField),
                                new SettingsNoteTypeFieldModel(SettingsTextCopy.sentenceFieldLabel(), sentenceField),
                                new SettingsNoteTypeFieldModel(SettingsTextCopy.frequencyFieldLabel(), frequencyField),
                                new SettingsNoteTypeFieldModel(SettingsTextCopy.frequencySortFieldLabel(), frequencySortField)
                        ),
                        SettingsTextCopy.chooseFromAnkiDroidLabel(),
                        SettingsTextCopy.useKikuLabel(),
                        SettingsTextCopy.saveNoteTypeLabel(),
                        () -> NoteTypeFieldMappings.choose(activity, activity.gateway, activity.io, activity.main, fieldMappings),
                        () -> applyKikuDefaults(
                                noteType,
                                expressionField,
                                readingField,
                                meaningField,
                                sentenceField,
                                frequencyField,
                                frequencySortField,
                                defaults
                        ),
                        () -> saveNoteTypeFields(
                                noteType,
                                expressionField,
                                readingField,
                                meaningField,
                                sentenceField,
                                frequencyField,
                                frequencySortField
                        )
                )
        );
    }

    private void setInputDescription(EditText input, String description) {
        input.setContentDescription(description);
    }

    private void applyKikuDefaults(
            EditText noteType,
            EditText expressionField,
            EditText readingField,
            EditText meaningField,
            EditText sentenceField,
            EditText frequencyField,
            EditText frequencySortField,
            RecordsSyncModels.Settings defaults
    ) {
        noteType.setText(defaults.modelName);
        expressionField.setText(defaults.expressionField);
        readingField.setText(defaults.readingField);
        meaningField.setText(defaults.meaningField);
        sentenceField.setText(defaults.sentenceField);
        frequencyField.setText(defaults.frequencyField);
        frequencySortField.setText(defaults.frequencySortField);
    }

    private void saveNoteTypeFields(
            EditText noteType,
            EditText expressionField,
            EditText readingField,
            EditText meaningField,
            EditText sentenceField,
            EditText frequencyField,
            EditText frequencySortField
    ) {
        String selectedNoteType = noteType.getText().toString().trim();
        if (selectedNoteType.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.noteTypeRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        String selectedExpressionField = expressionField.getText().toString().trim();
        if (selectedExpressionField.isEmpty()) {
            Toast.makeText(activity, SettingsTextCopy.expressionFieldRequiredToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        SettingsWriteActions.saveNoteTypeFields(
                new SettingsWriteActions.NoteTypeFieldWriteRequest(
                        selectedNoteType,
                        selectedExpressionField,
                        readingField.getText().toString().trim(),
                        meaningField.getText().toString().trim(),
                        sentenceField.getText().toString().trim(),
                        frequencyField.getText().toString().trim(),
                        frequencySortField.getText().toString().trim()
                ),
                activity.store::putStringSetting
        );
        Toast.makeText(activity, SettingsTextCopy.noteTypeSavedToast(), Toast.LENGTH_LONG).show();
        activity.renderSettings();
    }
}
