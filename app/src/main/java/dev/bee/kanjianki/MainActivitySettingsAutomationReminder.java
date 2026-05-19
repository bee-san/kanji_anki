package dev.bee.kanjianki;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;

import java.util.ArrayList;
import java.util.List;

final class MainActivitySettingsAutomationReminder {
    private final MainActivitySettings activity;

    MainActivitySettingsAutomationReminder(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout reminderSettingsPanel() {
        LocalStore.ReminderSettings reminder = activity.store.reminderSettings();
        boolean notificationsAllowed = activity.notificationsAllowedForReminders();
        boolean blocked = reminder.enabled && !notificationsAllowed;
        int[] selectedHour = new int[]{reminder.hour};
        int[] selectedMinute = new int[]{reminder.minute};

        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.dailyReminderTitle(), 23, activity.INK, true));
        box.addView(activity.text(
                SettingsTextCopy.reminderStatus(reminder.enabled, blocked, reminder.displayTime()),
                17,
                blocked ? activity.CORAL : (reminder.enabled ? activity.TEAL : activity.MUTED),
                true
        ));
        box.addView(activity.text(SettingsTextCopy.dailyReminderBody(), 15, activity.MUTED, false));

        Button time = activity.secondaryButton(SettingsTextCopy.reminderTimeButtonLabel(selectedHour[0], selectedMinute[0]));
        time.setOnClickListener(v -> new TimePickerDialog(
                activity,
                (view, hour, minute) -> {
                    selectedHour[0] = hour;
                    selectedMinute[0] = minute;
                    time.setText(SettingsTextCopy.reminderTimeButtonLabel(hour, minute));
                },
                selectedHour[0],
                selectedMinute[0],
                true
        ).show());
        box.addView(time);

        List<View> presets = new ArrayList<>();
        presets.add(reminderPresetButton(SettingsTextCopy.morningReminderPresetLabel(), 8, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.lunchReminderPresetLabel(), 12, 30, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.eveningReminderPresetLabel(), 19, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.nightReminderPresetLabel(), 21, 0, selectedHour, selectedMinute, time));
        box.addView(activity.twoColumnGrid(presets));

        Button save = activity.primaryButton(reminder.enabled ? SettingsTextCopy.saveReminderLabel() : SettingsTextCopy.enableReminderLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(v -> saveReminderFromSelection(selectedHour[0], selectedMinute[0], true));
        box.addView(save);
        if (reminder.enabled) {
            Button off = activity.secondaryButton(SettingsTextCopy.turnOffReminderLabel());
            off.setOnClickListener(v -> {
                ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(false, reminder.hour, reminder.minute);
                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(fields.enabled(), fields.hour(), fields.minute()));
                ReminderScheduler.cancel(activity);
                Toast.makeText(activity, ReminderSettingsSavePolicy.DISABLED_MESSAGE, Toast.LENGTH_SHORT).show();
                activity.renderSettings();
            });
            box.addView(off);
        }
        if (blocked) {
            box.addView(activity.text(SettingsTextCopy.notificationsBlockedBody(), 14, activity.CORAL, false));
            Button notificationSettings = activity.secondaryButton(SettingsTextCopy.openNotificationSettingsLabel());
            notificationSettings.setOnClickListener(v -> activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName())));
            box.addView(notificationSettings);
        } else if (!activity.hasRuntimeNotificationPermissionForReminder()) {
            box.addView(activity.text(SettingsTextCopy.notificationPermissionBody(), 14, activity.CORAL, false));
        }
        return box;
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        ReminderSettingsSavePolicy.ReminderFields fields = ReminderSettingsSavePolicy.fields(enabled, hour, minute);
        LocalStore.ReminderSettings reminder = new LocalStore.ReminderSettings(fields.enabled(), fields.hour(), fields.minute());
        if (!enabled) {
            activity.store.saveReminderSettings(reminder);
            ReminderScheduler.cancel(activity);
            Toast.makeText(activity, ReminderSettingsSavePolicy.DISABLED_MESSAGE, Toast.LENGTH_SHORT).show();
            activity.renderSettings();
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

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        Button preset = activity.secondaryButton(SettingsTextCopy.reminderPresetButtonLabel(label, hour, minute));
        preset.setTextSize(13);
        preset.setMinHeight(activity.dp(54));
        preset.setOnClickListener(v -> {
            selectedHour[0] = hour;
            selectedMinute[0] = minute;
            timeButton.setText(SettingsTextCopy.reminderTimeButtonLabel(hour, minute));
        });
        return preset;
    }
}
