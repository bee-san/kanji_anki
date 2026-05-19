package dev.bee.kanjianki;

import android.widget.EditText;
import android.widget.LinearLayout;

final class MainActivitySettingsRetention {
    private final MainActivitySettings activity;
    private final MainActivitySettingsRetentionPanel retentionPanel;

    MainActivitySettingsRetention(MainActivitySettings activity) {
        this.activity = activity;
        this.retentionPanel = new MainActivitySettingsRetentionPanel(activity);
    }

    LinearLayout retentionSettingsPanel() {
        return retentionPanel.retentionSettingsPanel();
    }

    EditText rankRetentionRangesInput(String value) {
        return retentionPanel.rankRetentionRangesInput(value);
    }
}
