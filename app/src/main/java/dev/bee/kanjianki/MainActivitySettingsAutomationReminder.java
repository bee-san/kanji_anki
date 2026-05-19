package dev.bee.kanjianki;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

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
        time.setOnClickListener(new RunnableClickListener(() -> showReminderTimePicker(selectedHour, selectedMinute, time)));
        box.addView(time);

        List<View> presets = new ArrayList<>();
        presets.add(reminderPresetButton(SettingsTextCopy.morningReminderPresetLabel(), 8, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.lunchReminderPresetLabel(), 12, 30, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.eveningReminderPresetLabel(), 19, 0, selectedHour, selectedMinute, time));
        presets.add(reminderPresetButton(SettingsTextCopy.nightReminderPresetLabel(), 21, 0, selectedHour, selectedMinute, time));
        box.addView(activity.twoColumnGrid(presets));

        Button save = activity.primaryButton(reminder.enabled ? SettingsTextCopy.saveReminderLabel() : SettingsTextCopy.enableReminderLabel(), activity.STUDY_PINK_DARK);
        save.setOnClickListener(new RunnableClickListener(() -> saveReminderFromSelection(selectedHour[0], selectedMinute[0], true)));
        box.addView(save);
        if (reminder.enabled) {
            Button off = activity.secondaryButton(SettingsTextCopy.turnOffReminderLabel());
            off.setOnClickListener(new RunnableClickListener(() -> actions.disableReminder(reminder)));
            box.addView(off);
        }
        if (blocked) {
            box.addView(activity.text(SettingsTextCopy.notificationsBlockedBody(), 14, activity.CORAL, false));
            Button notificationSettings = activity.secondaryButton(SettingsTextCopy.openNotificationSettingsLabel());
            notificationSettings.setOnClickListener(new RunnableClickListener(this::openNotificationSettings));
            box.addView(notificationSettings);
        } else if (!activity.hasRuntimeNotificationPermissionForReminder()) {
            box.addView(activity.text(SettingsTextCopy.notificationPermissionBody(), 14, activity.CORAL, false));
        }
        return box;
    }

    void saveReminderFromSelection(int hour, int minute, boolean enabled) {
        actions.saveReminderFromSelection(hour, minute, enabled);
    }

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        Button preset = activity.secondaryButton(SettingsTextCopy.reminderPresetButtonLabel(label, hour, minute));
        preset.setTextSize(13);
        preset.setMinHeight(activity.dp(54));
        preset.setOnClickListener(new ReminderPresetClickListener(hour, minute, selectedHour, selectedMinute, timeButton));
        return preset;
    }

    private void showReminderTimePicker(int[] selectedHour, int[] selectedMinute, Button timeButton) {
        new TimePickerDialog(
                activity,
                (view, hour, minute) -> {
                    selectedHour[0] = hour;
                    selectedMinute[0] = minute;
                    timeButton.setText(SettingsTextCopy.reminderTimeButtonLabel(hour, minute));
                },
                selectedHour[0],
                selectedMinute[0],
                true
        ).show();
    }

    private void openNotificationSettings() {
        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
    }

    private static final class RunnableClickListener implements View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View v) {
            action.run();
        }
    }

    private static final class ReminderPresetClickListener implements View.OnClickListener {
        private final int hour;
        private final int minute;
        private final int[] selectedHour;
        private final int[] selectedMinute;
        private final Button timeButton;

        ReminderPresetClickListener(int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
            this.hour = hour;
            this.minute = minute;
            this.selectedHour = selectedHour;
            this.selectedMinute = selectedMinute;
            this.timeButton = timeButton;
        }

        @Override
        public void onClick(View v) {
            selectedHour[0] = hour;
            selectedMinute[0] = minute;
            timeButton.setText(SettingsTextCopy.reminderTimeButtonLabel(hour, minute));
        }
    }
}
