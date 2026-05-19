package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsStudySort {
    private final MainActivitySettings activity;
    private final MainActivitySettingsStudySortPanel studySortPanel;

    MainActivitySettingsStudySort(MainActivitySettings activity) {
        this.activity = activity;
        this.studySortPanel = new MainActivitySettingsStudySortPanel(activity, this);
    }

    LinearLayout newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        return studySortPanel.newCardSortSettingsPanel(current);
    }

    void addSortModeButton(LinearLayout box, String label, String mode, String[] selected, TextView status) {
        Button button = activity.secondaryButton(label);
        button.setOnClickListener(v -> {
            selected[0] = mode;
            status.setText(SettingsTextCopy.newCardSortStatusText(mode));
        });
        box.addView(button);
    }
}
