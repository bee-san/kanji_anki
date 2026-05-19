package dev.bee.kanjianki;

import android.widget.EditText;
import android.widget.LinearLayout;

final class MainActivitySettingsLearning {
    private final MainActivitySettings activity;
    private final MainActivitySettingsLearningPanel learningPanel;

    MainActivitySettingsLearning(MainActivitySettings activity) {
        this.activity = activity;
        this.learningPanel = new MainActivitySettingsLearningPanel(activity);
    }

    LinearLayout learningStepsSettingsPanel() {
        return learningPanel.learningStepsSettingsPanel();
    }

    EditText stepInput(String value) {
        return learningPanel.stepInput(value);
    }
}
