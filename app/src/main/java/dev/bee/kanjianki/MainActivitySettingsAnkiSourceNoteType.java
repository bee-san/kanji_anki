package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAnkiSourceNoteType {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSource source;

    MainActivitySettingsAnkiSourceNoteType(MainActivitySettings activity, MainActivitySettingsAnkiSource source) {
        this.activity = activity;
        this.source = source;
    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.noteTypeFieldsTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.noteTypeUsingText(current.modelName), 17, activity.TEAL, true));
        box.addView(activity.text(SettingsTextCopy.noteTypeFieldsBody(), 15, activity.MUTED, false));

        EditText noteType = source.noteTypeInput(current.modelName);
        box.addView(noteType, new LinearLayout.LayoutParams(-1, activity.dp(58)));
        EditText expressionField = source.fieldInput(current.expressionField);
        EditText readingField = source.fieldInput(current.readingField);
        EditText meaningField = source.fieldInput(current.meaningField);
        EditText sentenceField = source.fieldInput(current.sentenceField);
        EditText frequencyField = source.fieldInput(current.frequencyField);
        EditText frequencySortField = source.fieldInput(current.frequencySortField);
        box.addView(activity.text(SettingsTextCopy.requiredFieldsTitle(), 15, activity.STUDY_PLUM, true));
        box.addView(activity.text(SettingsTextCopy.requiredFieldsBody(), 14, activity.MUTED, false));
        source.addFieldMappingInput(box, SettingsTextCopy.expressionFieldLabel(), expressionField);
        source.addFieldMappingInput(box, SettingsTextCopy.readingFieldLabel(), readingField);
        source.addFieldMappingInput(box, SettingsTextCopy.meaningFieldLabel(), meaningField);
        source.addFieldMappingInput(box, SettingsTextCopy.sentenceFieldLabel(), sentenceField);
        source.addFieldMappingInput(box, SettingsTextCopy.frequencyFieldLabel(), frequencyField);
        source.addFieldMappingInput(box, SettingsTextCopy.frequencySortFieldLabel(), frequencySortField);

        NoteTypeFieldMappings.Inputs fieldMappings = new NoteTypeFieldMappings.Inputs(
                noteType,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField
        );
        Button choose = activity.secondaryButton(SettingsTextCopy.chooseFromAnkiDroidLabel());
        choose.setOnClickListener(v -> NoteTypeFieldMappings.choose(activity, activity.gateway, activity.io, activity.main, fieldMappings));
        box.addView(choose);
        Button kiku = activity.secondaryButton(SettingsTextCopy.useKikuLabel());
        kiku.setOnClickListener(v -> {
            noteType.setText(defaults.modelName);
            expressionField.setText(defaults.expressionField);
            readingField.setText(defaults.readingField);
            meaningField.setText(defaults.meaningField);
            sentenceField.setText(defaults.sentenceField);
            frequencyField.setText(defaults.frequencyField);
            frequencySortField.setText(defaults.frequencySortField);
        });
        box.addView(kiku);

        Button save = activity.primaryButton(SettingsTextCopy.saveNoteTypeLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> {
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
        });
        box.addView(save);
        return box;
    }
}
