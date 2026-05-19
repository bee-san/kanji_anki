package dev.bee.kanjianki;

import android.widget.LinearLayout;

import dev.bee.kanjianki.core.RecordsSyncModels;

final class MainActivitySettingsAnkiSource {
    private final MainActivitySettingsAnkiSourceInputs inputs;
    private final MainActivitySettingsAnkiSourceValidation validation;
    private final MainActivitySettingsAnkiSourceFrequencyRange frequencyRange;
    private final MainActivitySettingsAnkiSourceNoteType noteType;
    private final MainActivitySettingsAnkiSourceImportFilters importFilters;

    MainActivitySettingsAnkiSource(MainActivitySettings activity) {
        this.inputs = new MainActivitySettingsAnkiSourceInputs(activity);
        this.validation = new MainActivitySettingsAnkiSourceValidation(activity);
        MainActivitySettingsAnkiSourcePresets presets = new MainActivitySettingsAnkiSourcePresets(activity);
        this.frequencyRange = new MainActivitySettingsAnkiSourceFrequencyRange(activity, this.inputs, validation);
        this.noteType = new MainActivitySettingsAnkiSourceNoteType(activity, this.inputs);
        this.importFilters = new MainActivitySettingsAnkiSourceImportFilters(activity, this.inputs, validation, presets);
    }

    LinearLayout noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        return noteType.noteTypeSettingsPanel(current);
    }

    LinearLayout importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        return importFilters.importFilterSettingsPanel(current);
    }

    LinearLayout frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return frequencyRange.frequencyRangeSettingsPanel(current);
    }

    void bindRankSliders(
            int[] selected,
            android.widget.TextView status,
            android.widget.EditText minInput,
            android.widget.EditText maxInput,
            android.widget.SeekBar minSlider,
            android.widget.SeekBar maxSlider
    ) {
        inputs.bindRankSliders(selected, status, minInput, maxInput, minSlider, maxSlider);
    }

    MainActivityBase.ImportThresholds readImportThresholds(android.widget.EditText difficultyInput, android.widget.EditText lapses, android.widget.EditText minMatching) {
        return validation.readImportThresholds(difficultyInput, lapses, minMatching);
    }

    boolean hasSelectedImportSource(
            android.widget.CheckBox activeCards,
            android.widget.CheckBox suspendedCards,
            android.widget.CheckBox taggedCards,
            android.widget.CheckBox weakCards,
            android.widget.CheckBox browserQueryCards,
            java.util.List<String> parsedTags,
            String queryText
    ) {
        return validation.hasSelectedImportSource(activeCards, suspendedCards, taggedCards, weakCards, browserQueryCards, parsedTags, queryText);
    }
}
