package dev.bee.kanjianki;

import android.view.View;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.ArrayList;
import java.util.List;

final class MainActivitySettingsStudyLadderPanel {
    private final MainActivitySettings activity;
    private final MainActivitySettingsStudyLadder source;

    MainActivitySettingsStudyLadderPanel(MainActivitySettings activity, MainActivitySettingsStudyLadder source) {
        this.activity = activity;
        this.source = source;
    }

    View studyLadderSettingsPanel() {
        RecordsBase.StudyLadderSettings ladder = activity.studyLadderSettings();
        List<RecordsBase.LadderRung> rungs = ladder.orderedRungs;
        List<SettingsStudyLadderRungModel> rungModels = new ArrayList<>();
        for (int i = 0; i < rungs.size(); i++) {
            RecordsBase.LadderRung rung = rungs.get(i);
            String label = SettingsTextCopy.settingsLadderRungLabel(rung);
            rungModels.add(new SettingsStudyLadderRungModel(
                    label,
                    SettingsTextCopy.ladderRungSubtitle(ladder, rung),
                    SettingsTextCopy.ladderToggleLabel(ladder.isEnabled(rung)),
                    SettingsTextCopy.moveUpLabel(),
                    SettingsTextCopy.moveDownLabel(),
                    i > 0,
                    i < rungs.size() - 1,
                    toggleDescription(label, ladder.isEnabled(rung)),
                    ladderActionDescription(SettingsTextCopy.moveUpLabel(), label),
                    ladderActionDescription(SettingsTextCopy.moveDownLabel(), label),
                    () -> source.toggleLadderRung(rung),
                    () -> moveRung(rung, -1),
                    () -> moveRung(rung, 1)
            ));
        }
        return MainActivitySettingsStudyLadderCompose.studyLadderSettingsPanelView(
                activity,
                new SettingsStudyLadderPanelModel(
                        SettingsTextCopy.studyLadderTitle(),
                        SettingsTextCopy.studyLadderBody(),
                        rungModels,
                        SettingsTextCopy.restoreDefaultLadderLabel(),
                        SettingsTextCopy.restoreDefaultLadderLabel(),
                        this::restoreDefaultLadderSettings
                )
        );
    }

    private String toggleDescription(String rungLabel, boolean enabled) {
        return (enabled ? "Turn off " : "Turn on ") + rungLabel;
    }

    private String ladderActionDescription(String action, String rungLabel) {
        return action + " " + rungLabel;
    }

    private void moveRung(RecordsBase.LadderRung rung, int direction) {
        activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(rung, direction));
        activity.renderSettings();
    }

    private void restoreDefaultLadderSettings() {
        activity.store.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults());
        Toast.makeText(activity, SettingsTextCopy.studyLadderRestoredToast(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }
}
