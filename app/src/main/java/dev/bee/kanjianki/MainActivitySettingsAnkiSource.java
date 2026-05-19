package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MainActivitySettingsAnkiSource {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAnkiSourceInputs inputs;
    private final MainActivitySettingsAnkiSourceFrequencyRange frequencyRange;
    private final MainActivitySettingsAnkiSourceNoteType noteType;
    private final MainActivitySettingsAnkiSourceImportFilters importFilters;

    MainActivitySettingsAnkiSource(MainActivitySettings activity) {
        this.activity = activity;
        this.inputs = new MainActivitySettingsAnkiSourceInputs(activity);
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
        double difficulty;
        int lapseThreshold;
        int minCards;
        try {
            difficulty = parseDecimalInput(difficultyInput);
            lapseThreshold = activity.parseThresholdInput(lapses);
            minCards = activity.parseThresholdInput(minMatching);
        } catch (NumberFormatException error) {
            Toast.makeText(activity, SettingsTextCopy.numericImportThresholdsToast(), Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!SettingsInputRules.validImportThresholds(difficulty, lapseThreshold, minCards)) {
            Toast.makeText(activity, SettingsTextCopy.importThresholdRangeToast(), Toast.LENGTH_SHORT).show();
            return null;
        }
        return new MainActivityBase.ImportThresholds(difficulty, lapseThreshold, minCards);
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
        if (activeCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(true, false, false, false, false, null, null);
        }
        if (suspendedCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(false, true, false, false, false, null, null);
        }
        if (weakCards.isChecked()) {
            return SettingsInputRules.hasSelectedImportSource(false, false, false, true, false, null, null);
        }
        if (taggedCards.isChecked() && SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, parsedTags, "")) {
            return true;
        }
        return SettingsInputRules.hasSelectedImportSource(
                false,
                false,
                false,
                false,
                browserQueryCards.isChecked(),
                Collections.emptyList(),
                queryText
        );
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
            TextView status,
            EditText minInput,
            EditText maxInput,
            SeekBar minSlider,
            SeekBar maxSlider
    ) {
        inputs.bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);
    }

    int parseRankInput(EditText input) {
        return Integer.parseInt(input.getText().toString().trim());
    }

    double parseDecimalInput(EditText input) {
        return Double.parseDouble(input.getText().toString().trim());
    }
}
