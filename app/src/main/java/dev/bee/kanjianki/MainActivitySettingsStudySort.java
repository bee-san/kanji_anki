package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.LinearLayout;
import dev.bee.kanjianki.core.RecordsSyncModels;

final class MainActivitySettingsStudySort {
    private final MainActivitySettingsStudySortPanel studySortPanel;

    MainActivitySettingsStudySort(MainActivitySettings activity) {
        this.studySortPanel = new MainActivitySettingsStudySortPanel(activity);
    }

    LinearLayout newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        return studySortPanel.newCardSortSettingsPanel(current);
    }
}
