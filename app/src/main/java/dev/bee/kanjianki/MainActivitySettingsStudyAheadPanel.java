package dev.bee.kanjianki;

import android.view.View;
import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy;

import java.util.Locale;

final class MainActivitySettingsStudyAheadPanel {
    private final MainActivitySettings activity;

    MainActivitySettingsStudyAheadPanel(MainActivitySettings activity) {
        this.activity = activity;
    }

    View studyAheadSettingsPanel() {
        int currentMinutes = activity.store.studyAheadMinutes();
        return MainActivitySettingsStudyAheadCompose.studyAheadSettingsPanelView(
                activity,
                new SettingsStudyAheadPanelModel(
                        SettingsTextCopy.studyAheadTitle(),
                        SettingsTextCopy.studyAheadBody(),
                        SettingsTextCopy.studyAheadMinutesLabel(),
                        String.format(Locale.ROOT, "%d", currentMinutes),
                        SettingsTextCopy.saveStudyAheadLabel(),
                        this::saveStudyAhead
                )
        );
    }

    private void saveStudyAhead(String minutesText) {
        StudyAheadSettingsPolicy.SaveResult request = StudyAheadSettingsPolicy.saveRequest(minutesText);
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.store.saveStudyAheadMinutes(request.minutes);
        Toast.makeText(activity, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }
}
