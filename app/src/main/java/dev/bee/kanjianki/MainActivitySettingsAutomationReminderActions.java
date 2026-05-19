package dev.bee.kanjianki;

import android.widget.Toast;

import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;

final class MainActivitySettingsAutomationReminderActions {
    private final MainActivitySettings activity;

    MainActivitySettingsAutomationReminderActions(MainActivitySettings activity) {
        this.activity = activity;
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(enabled, hour, minute);
        LocalStore.ReminderSettings reminder = new LocalStore.ReminderSettings(fields.enabled(), fields.hour(), fields.minute());
        if (!enabled) {
            disableReminder(reminder);
            return;
        }
        ReminderScheduler.ensureNotificationChannel(activity);
        if (!activity.hasRuntimeNotificationPermissionForReminder()) {
            activity.pendingReminderSettings = reminder;
            activity.requestPermissions(new String[]{MainActivityBase.PERMISSION_POST_NOTIFICATIONS}, MainActivityBase.REQUEST_POST_NOTIFICATIONS);
            return;
        }
        activity.store.saveReminderSettings(reminder);
        ReminderScheduler.schedule(activity, reminder);
        boolean allowed = activity.notificationsAllowedForReminders();
        Toast.makeText(activity, ReminderSettingsSavePolicy.savedMessage(reminder.hour, reminder.minute, allowed), allowed ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        activity.renderSettings();
    }

    void disableReminder(LocalStore.ReminderSettings reminder) {
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(false, reminder.hour, reminder.minute);
        activity.store.saveReminderSettings(new LocalStore.ReminderSettings(fields.enabled(), fields.hour(), fields.minute()));
        ReminderScheduler.cancel(activity);
        Toast.makeText(activity, ReminderSettingsSavePolicy.DISABLED_MESSAGE, Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }
}
