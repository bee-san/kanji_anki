package dev.bee.kanjianki;

import android.view.View;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.RecordsSyncModels;

final class MainActivitySettingsAnkiSource {
    private final MainActivitySettingsAnkiSourceInputs inputs;
    private final MainActivitySettingsAnkiSourceFrequencyRange frequencyRange;
    private final MainActivitySettingsAnkiSourceNoteType noteType;
    private final MainActivitySettingsAnkiSourceImportFilters importFilters;

    MainActivitySettingsAnkiSource(MainActivitySettings activity) {
        this.inputs = new MainActivitySettingsAnkiSourceInputs(activity);
        MainActivitySettingsAnkiSourceValidation validation = new MainActivitySettingsAnkiSourceValidation(activity);
        this.frequencyRange = new MainActivitySettingsAnkiSourceFrequencyRange(activity, this.inputs, validation);
        this.noteType = new MainActivitySettingsAnkiSourceNoteType(activity);
        this.importFilters = new MainActivitySettingsAnkiSourceImportFilters(activity, this.inputs, validation);
    }

    View noteTypeSettingsPanel(RecordsSyncModels.Settings current) {
        return noteType.noteTypeSettingsPanel(current);
    }

    View importFilterSettingsPanel(RecordsSyncModels.Settings current) {
        return importFilters.importFilterSettingsPanel(current);
    }

    View frequencyRangeSettingsPanel(RecordsSyncModels.Settings current) {
        return frequencyRange.frequencyRangeSettingsPanel(current);
    }
}
