package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.List;

final class MainActivitySettingsStudyLadder {
    private final MainActivitySettings activity;

    MainActivitySettingsStudyLadder(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout studyLadderSettingsPanel() {
        RecordsBase.StudyLadderSettings ladder = activity.studyLadderSettings();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.studyLadderTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.studyLadderBody(), 15, activity.MUTED, false));

        List<RecordsBase.LadderRung> rungs = ladder.orderedRungs;
        for (int i = 0; i < rungs.size(); i++) {
            RecordsBase.LadderRung rung = rungs.get(i);
            LinearLayout row = activity.softInsetPanel();
            row.addView(activity.text(SettingsTextCopy.settingsLadderRungLabel(rung), 19, activity.STUDY_PLUM, true));
            row.addView(activity.text(SettingsTextCopy.ladderRungSubtitle(ladder, rung), 13, activity.MUTED, false));

            LinearLayout controls = new LinearLayout(activity);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            Button toggle = activity.secondaryButton(SettingsTextCopy.ladderToggleLabel(ladder.isEnabled(rung)));
            toggle.setOnClickListener(v -> activity.toggleLadderRung(rung));
            controls.addView(toggle, new LinearLayout.LayoutParams(0, activity.dp(48), 1));

            Button up = activity.secondaryButton(SettingsTextCopy.moveUpLabel());
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> {
                activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(rung, -1));
                activity.renderSettings();
            });
            LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0, activity.dp(48), 1);
            upLp.setMargins(activity.dp(8), 0, 0, 0);
            controls.addView(up, upLp);

            Button down = activity.secondaryButton(SettingsTextCopy.moveDownLabel());
            down.setEnabled(i < rungs.size() - 1);
            down.setOnClickListener(v -> {
                activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(rung, 1));
                activity.renderSettings();
            });
            LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(0, activity.dp(48), 1);
            downLp.setMargins(activity.dp(8), 0, 0, 0);
            controls.addView(down, downLp);
            row.addView(controls);
            box.addView(row);
        }

        Button reset = activity.secondaryButton(SettingsTextCopy.restoreDefaultLadderLabel());
        reset.setOnClickListener(v -> {
            activity.store.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults());
            Toast.makeText(activity, SettingsTextCopy.studyLadderRestoredToast(), Toast.LENGTH_SHORT).show();
            activity.renderSettings();
        });
        box.addView(reset);
        return box;
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
