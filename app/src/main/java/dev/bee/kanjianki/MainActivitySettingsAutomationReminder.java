package dev.bee.kanjianki;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;

import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.data.LocalStore;

import java.util.ArrayList;
import java.util.List;

final class MainActivitySettingsAutomationReminder {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAutomationReminderActions actions;

    MainActivitySettingsAutomationReminder(MainActivitySettings activity) {
        this.activity = activity;
        this.actions = new MainActivitySettingsAutomationReminderActions(activity);
    }

    View reminderSettingsPanel() {
        LocalStore.ReminderSettings reminder = activity.store.reminderSettings();
        boolean notificationsAllowed = activity.notificationsAllowedForReminders();
        boolean blocked = reminder.enabled && !notificationsAllowed;
        int[] selectedHour = new int[]{reminder.hour};
        int[] selectedMinute = new int[]{reminder.minute};

        String warning = null;
        String notificationSettingsLabel = null;
        SettingsReminderAction notificationSettingsAction = null;
        SettingsReminderAction disableAction = null;
        if (reminder.enabled) {
            disableAction = () -> actions.disableReminder(reminder);
        }
        if (blocked) {
            warning = SettingsTextCopy.notificationsBlockedBody();
            notificationSettingsLabel = SettingsTextCopy.openNotificationSettingsLabel();
            notificationSettingsAction = this::openNotificationSettings;
        } else if (!activity.hasRuntimeNotificationPermissionForReminder()) {
            warning = SettingsTextCopy.notificationPermissionBody();
        }
        return MainActivitySettingsAutomationReminderCompose.reminderSettingsPanelView(
                activity,
                new SettingsReminderPanelModel(
                        SettingsTextCopy.dailyReminderTitle(),
                        SettingsTextCopy.reminderStatus(reminder.enabled, blocked, reminder.displayTime()),
                        blocked ? activity.CORAL : (reminder.enabled ? activity.TEAL : activity.MUTED),
                        SettingsTextCopy.dailyReminderBody(),
                        selectedHour,
                        selectedMinute,
                        reminderPresets(),
                        reminder.enabled ? SettingsTextCopy.saveReminderLabel() : SettingsTextCopy.enableReminderLabel(),
                        reminder.enabled ? SettingsTextCopy.turnOffReminderLabel() : null,
                        warning,
                        notificationSettingsLabel,
                        this::showReminderTimePicker,
                        () -> saveReminderFromSelection(selectedHour[0], selectedMinute[0], true),
                        disableAction,
                        notificationSettingsAction
                )
        );
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        actions.saveReminderFromSelection(hour, minute, enabled);
    }

    private List<SettingsReminderPresetModel> reminderPresets() {
        List<SettingsReminderPresetModel> presets = new ArrayList<>();
        presets.add(new SettingsReminderPresetModel(SettingsTextCopy.morningReminderPresetLabel(), 8, 0));
        presets.add(new SettingsReminderPresetModel(SettingsTextCopy.lunchReminderPresetLabel(), 12, 30));
        presets.add(new SettingsReminderPresetModel(SettingsTextCopy.eveningReminderPresetLabel(), 19, 0));
        presets.add(new SettingsReminderPresetModel(SettingsTextCopy.nightReminderPresetLabel(), 21, 0));
        return presets;
    }

    private void showReminderTimePicker(int selectedHour, int selectedMinute, SettingsReminderSelectedTimeAction onSelected) {
        new TimePickerDialog(
                activity,
                (view, hour, minute) -> {
                    onSelected.select(hour, minute);
                },
                selectedHour,
                selectedMinute,
                true
        ).show();
    }

    private void openNotificationSettings() {
        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }

}
