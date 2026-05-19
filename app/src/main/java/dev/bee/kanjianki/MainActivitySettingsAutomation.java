package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.data.LocalStore;

final class MainActivitySettingsAutomation {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAutomationReminder reminder;
    private final MainActivitySettingsAutomationAutoSync autoSync;
    private final MainActivitySettingsAutomationUpdate update;
    private final MainActivitySettingsAutomationHero hero;

    MainActivitySettingsAutomation(MainActivitySettings activity) {
        this.activity = activity;
        this.reminder = new MainActivitySettingsAutomationReminder(activity);
        this.autoSync = new MainActivitySettingsAutomationAutoSync(activity);
        this.update = new MainActivitySettingsAutomationUpdate(activity);
        this.hero = new MainActivitySettingsAutomationHero(activity);
    }

    View settingsHero(
            RecordsSyncModels.Settings current,
            LocalStore.ReminderSettings reminder,
            LocalStore.AutoSyncSettings autoSync,
            LocalStore.AutoUpdateStatus autoUpdate
    ) {
        return hero.settingsHero(current, reminder, autoSync, autoUpdate);
    }

    LinearLayout reminderSettingsPanel() {
        return reminder.reminderSettingsPanel();
    }

    LinearLayout autoSyncSettingsPanel() {
        return autoSync.autoSyncSettingsPanel();
    }

    LinearLayout updateSettingsPanel() {
        return update.updateSettingsPanel();
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        reminder.saveReminderFromSelection(hour, minute, enabled);
    }

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        return reminder.reminderPresetButton(label, hour, minute, selectedHour, selectedMinute, timeButton);
    }
}
