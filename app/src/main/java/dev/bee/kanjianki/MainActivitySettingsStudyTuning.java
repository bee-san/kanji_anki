package dev.bee.kanjianki;

import android.widget.LinearLayout;

final class MainActivitySettingsStudyTuning {
    private final MainActivitySettingsStudyAheadPanel studyAhead;
    private final MainActivitySettingsLadderThresholdPanel ladderThreshold;

    MainActivitySettingsStudyTuning(MainActivitySettings activity) {
        this.studyAhead = new MainActivitySettingsStudyAheadPanel(activity);
        this.ladderThreshold = new MainActivitySettingsLadderThresholdPanel(activity);
    }

    LinearLayout studyAheadSettingsPanel() {
        return studyAhead.studyAheadSettingsPanel();
    }

    LinearLayout ladderThresholdSettingsPanel() {
        return ladderThreshold.ladderThresholdSettingsPanel();
    }
}
