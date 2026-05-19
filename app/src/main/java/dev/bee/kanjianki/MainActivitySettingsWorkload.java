package dev.bee.kanjianki;

import android.widget.LinearLayout;

final class MainActivitySettingsWorkload {
    private final MainActivitySettings activity;
    private final MainActivitySettingsWorkloadPanel workloadPanel;

    MainActivitySettingsWorkload(MainActivitySettings activity) {
        this.activity = activity;
        this.workloadPanel = new MainActivitySettingsWorkloadPanel(activity);
    }

    LinearLayout workloadSettingsPanel() {
        return workloadPanel.workloadSettingsPanel();
    }
}
