package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.CheckBox;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.ArrayList;
import java.util.List;

final class MainActivitySettingsAnkiSource {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceInputs inputs;
    private final MainActivitySettingsAnkiSourceValidation validation;
    private final MainActivitySettingsAnkiSourceFrequencyRange frequencyRange;
    private final MainActivitySettingsAnkiSourceNoteType noteType;
    private final MainActivitySettingsAnkiSourceImportFilters importFilters;

    MainActivitySettingsAnkiSource(MainActivitySettings activity) {
        this.activity = activity;
        this.inputs = new MainActivitySettingsAnkiSourceInputs(activity);
        this.validation = new MainActivitySettingsAnkiSourceValidation(activity);
        this.frequencyRange = new MainActivitySettingsAnkiSourceFrequencyRange(activity, this);
        this.noteType = new MainActivitySettingsAnkiSourceNoteType(activity, this);
        this.importFilters = new MainActivitySettingsAnkiSourceImportFilters(activity, this);
    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        return noteType.noteTypeSettingsPanel(current);
    }

    EditText noteTypeInput(String value) {
        return inputs.noteTypeInput(value);
    }

    EditText fieldInput(String value) {
        return inputs.fieldInput(value);
    }

    void addFieldMappingInput(LinearLayout box, String label, EditText input) {
        inputs.addFieldMappingInput(box, label, input);
    }

    LinearLayout importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        return importFilters.importFilterSettingsPanel(current);
    }

    void addImportPresetButtons(LinearLayout box) {
        box.addView(activity.text(SettingsTextCopy.presetsTitle(), 17, activity.INK, true));
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (SettingsImportPreset preset : SettingsImportPreset.defaults()) {
            Button button = activity.secondaryButton(preset.label());
            button.setOnClickListener(v -> {
                SettingsWriteActions.saveImportFilters(
                        new SettingsWriteActions.ImportFilterWriteRequest(
                                preset.activeCards(),
                                preset.suspendedCards(),
                                preset.taggedCards(),
                                preset.tags(),
                                preset.weakCards(),
                                preset.weakDifficulty(),
                                preset.weakLapses(),
                                preset.minMatchingCards(),
                                preset.browserQueryCards(),
                                preset.browserQuery()
                        ),
                        new SettingsWriteActions.SettingWriter() {
                            @Override
                            public void putIntSetting(String key, int value) {
                                activity.store.putIntSetting(key, value);
                            }

                            @Override
                            public void putStringSetting(String key, String value) {
                                activity.store.putStringSetting(key, value);
                            }

                            @Override
                            public void putDoubleSetting(String key, double value) {
                                activity.store.putDoubleSetting(key, value);
                            }
                        }
                );
                Toast.makeText(activity, SettingsTextCopy.importPresetSavedToast(), Toast.LENGTH_LONG).show();
                activity.renderSettings();
            });
            grid.addView(button);
        }
        box.addView(grid);
    }

    MainActivityBase.ImportThresholds readImportThresholds(EditText difficultyInput, EditText lapses, EditText minMatching) {
        return validation.readImportThresholds(difficultyInput, lapses, minMatching);
    }

    boolean hasSelectedImportSource(
            CheckBox activeCards,
            CheckBox suspendedCards,
            CheckBox taggedCards,
            CheckBox weakCards,
            CheckBox browserQueryCards,
            List<String> parsedTags,
            String queryText
    ) {
        return validation.hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText);
    }

    LinearLayout inputColumn(String label, EditText input, int leftPadding) {
        return inputs.inputColumn(label, input, leftPadding);
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return frequencyRange.frequencyRangeSettingsPanel(current);
    }

    EditText rankInput(int value) {
        return inputs.rankInput(value);
    }

    EditText decimalInput(double value) {
        return inputs.decimalInput(value);
    }

    void bindRankSliders(
            int[] selected,
            android.widget.TextView status,
            EditText minInput,
            EditText maxInput,
            android.widget.SeekBar minSlider,
            android.widget.SeekBar maxSlider
    ) {
        inputs.bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);
    }

    int parseRankInput(EditText input) {
        return validation.parseRankInput(input);
    }

    double parseDecimalInput(EditText input) {
        return validation.parseDecimalInput(input);
    }
}
