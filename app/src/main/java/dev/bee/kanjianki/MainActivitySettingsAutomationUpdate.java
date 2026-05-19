package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsAutomationUpdate {
    private final MainActivitySettings activity;

    MainActivitySettingsAutomationUpdate(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout updateSettingsPanel() {
        LinearLayout box = activity.autoUpdatePanel(SettingsTextCopy.appUpdatesTitle());
        Button update = activity.primaryButton(SettingsTextCopy.openUpdaterLabel(), activity.STUDY_PINK_DARK);
        update.setOnClickListener(v -> activity.renderUpdate());
        box.addView(update);
        return box;
    }
}
