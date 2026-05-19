package dev.bee.kanjianki;

import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsStudyLadder {
    private final MainActivitySettings activity;
    private final MainActivitySettingsStudyLadderPanel studyLadderPanel;

    MainActivitySettingsStudyLadder(MainActivitySettings activity) {
        this.activity = activity;
        this.studyLadderPanel = new MainActivitySettingsStudyLadderPanel(activity, this);
    }

    LinearLayout studyLadderSettingsPanel() {
        return studyLadderPanel.studyLadderSettingsPanel();
    }

    void toggleLadderRung(RecordsBase.LadderRung rung) {
        RecordsBase.StudyLadderSettings current = activity.studyLadderSettings();
        boolean wasEnabled = current.isEnabled(rung);
        RecordsBase.StudyLadderSettings next = current.withRungEnabled(rung, !wasEnabled);
        if (wasEnabled && next.enabledText().equals(current.enabledText())) {
            Toast.makeText(activity, SettingsTextCopy.keepAlwaysAvailableRungToast(), Toast.LENGTH_SHORT).show();
            return;
        }
        activity.store.saveStudyLadderSettings(next);
        Toast.makeText(activity, SettingsTextCopy.ladderRungToggleToast(rung, wasEnabled), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }
}
