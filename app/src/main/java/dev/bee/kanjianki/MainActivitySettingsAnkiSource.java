package dev.bee.kanjianki;

import android.view.View;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.RecordsSyncModels;

final class MainActivitySettingsAnkiSource {
    private final MainActivitySettingsAnkiSourceFrequencyRange frequencyRange;
    private final MainActivitySettingsAnkiSourceNoteType noteType;
    private final MainActivitySettingsAnkiSourceImportFilters importFilters;

    MainActivitySettingsAnkiSource(MainActivitySettings activity) {
        MainActivitySettingsAnkiSourceValidation validation = new MainActivitySettingsAnkiSourceValidation(activity);
        this.frequencyRange = new MainActivitySettingsAnkiSourceFrequencyRange(activity, validation);
        this.noteType = new MainActivitySettingsAnkiSourceNoteType(activity);
        this.importFilters = new MainActivitySettingsAnkiSourceImportFilters(activity, validation);
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
