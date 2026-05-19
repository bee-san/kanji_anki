package dev.bee.kanjianki;

import android.graphics.Color;
import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.update.AutoUpdateScheduler;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

final class MainActivitySettingsAutomation {
    private final MainActivitySettings activity;

    MainActivitySettingsAutomation(MainActivitySettings activity) {
        this.activity = activity;
    }

    View settingsHero(
            RecordsSyncModels.Settings current,
            LocalStore.ReminderSettings reminder,
            LocalStore.AutoSyncSettings autoSync,
            LocalStore.AutoUpdateStatus autoUpdate
    ) {
        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(18));
        hero.setBackground(activity.panel(Color.rgb(255, 248, 252), activity.STUDY_BORDER, activity.dp(30)));
        hero.setElevation(activity.dp(6));

        TextView pill = activity.text(SettingsTextCopy.settingsCockpitLabel(), 13, activity.STUDY_PINK_DARK, true);
        pill.setGravity(Gravity.CENTER);
        pill.setIncludeFontPadding(false);
        pill.setPadding(activity.dp(12), activity.dp(7), activity.dp(12), activity.dp(7));
        pill.setBackground(activity.panel(Color.WHITE, activity.STUDY_BORDER, activity.dp(18)));
        hero.addView(pill, new LinearLayout.LayoutParams(-2, -2));

        TextView title = activity.text(activity.NAV_SETTINGS, 34, activity.STUDY_PLUM, true);
        title.setPadding(0, activity.dp(12), 0, activity.dp(4));
        hero.addView(title);
        hero.addView(activity.text(SettingsTextCopy.settingsHeroBody(), 16, activity.STUDY_MUTED, false));

        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setPadding(0, activity.dp(12), 0, 0);
        LinearLayout.LayoutParams topFirstLp = new LinearLayout.LayoutParams(0, -2, 1);
        topFirstLp.setMargins(0, 0, activity.dp(6), 0);
        topRow.addView(activity.settingsStatusPill(SettingsTextCopy.noteTypeStatusLabel(), current.modelName, activity.STUDY_PLUM), topFirstLp);
        LinearLayout.LayoutParams topSecondLp = new LinearLayout.LayoutParams(0, -2, 1);
        topSecondLp.setMargins(activity.dp(6), 0, 0, 0);
        topRow.addView(activity.settingsStatusPill(SettingsTextCopy.importFiltersStatusLabel(), SettingsTextCopy.settingsImportSummary(current), activity.TEAL), topSecondLp);

        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, activity.dp(12), 0, 0);
        LinearLayout.LayoutParams bottomFirstLp = new LinearLayout.LayoutParams(0, -2, 1);
        bottomFirstLp.setMargins(0, 0, activity.dp(6), 0);
        bottomRow.addView(activity.settingsStatusPill(SettingsTextCopy.importRanksStatusLabel(), current.suspendedRankMin + "-" + current.suspendedRankMax, activity.TEAL), bottomFirstLp);
        LinearLayout.LayoutParams bottomSecondLp = new LinearLayout.LayoutParams(0, -2, 1);
        bottomSecondLp.setMargins(activity.dp(6), 0, 0, 0);
        bottomRow.addView(activity.settingsStatusPill(
                SettingsTextCopy.reminderStatusLabel(),
                SettingsTextCopy.settingsReminderSummary(
                        reminder.enabled,
                        reminder.enabled && !ReminderScheduler.notificationsAllowed(activity),
                        reminder.displayTime()
                ),
                reminder.enabled ? activity.TEAL : activity.MUTED
        ), bottomSecondLp);

        LinearLayout automationRow = new LinearLayout(activity);
        automationRow.setOrientation(LinearLayout.HORIZONTAL);
        automationRow.setPadding(0, activity.dp(12), 0, 0);
        LinearLayout.LayoutParams automationFirstLp = new LinearLayout.LayoutParams(0, -2, 1);
        automationFirstLp.setMargins(0, 0, activity.dp(6), 0);
        automationRow.addView(activity.settingsStatusPill(
                SettingsTextCopy.dailySyncStatusLabel(),
                SettingsTextCopy.settingsAutoSyncSummary(autoSync.configured, autoSync.enabled, autoSync.displayTime()),
                autoSync.enabled ? activity.TEAL : activity.MUTED
        ), automationFirstLp);
        LinearLayout.LayoutParams automationSecondLp = new LinearLayout.LayoutParams(0, -2, 1);
        automationSecondLp.setMargins(activity.dp(6), 0, 0, 0);
        automationRow.addView(activity.settingsStatusPill(
                SettingsTextCopy.updatesStatusLabel(),
                SettingsTextCopy.settingsUpdateSummary(autoUpdate.hasPendingUpdate(), autoUpdate.enabled),
                autoUpdate.hasPendingUpdate() ? activity.CORAL : activity.STUDY_PINK_DARK
        ), automationSecondLp);

        hero.addView(topRow);
        hero.addView(bottomRow);
        hero.addView(automationRow);
        hero.addView(activity.settingsStatusPill(
                SettingsTextCopy.matchingCardsStatusLabel(),
                SettingsTextCopy.matchingCardsSummary(current),
                activity.STUDY_PLUM
        ));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, activity.dp(8), 0, activity.dp(10));
        hero.setLayoutParams(lp);
        return hero;
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

    LinearLayout autoSyncSettingsPanel() {
        LocalStore.AutoSyncSettings auto = activity.store.autoSyncSettings();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.dailyAnkiSyncTitle(), 23, activity.INK, true));
        box.addView(activity.text(
                SettingsTextCopy.autoSyncStatus(auto.configured, auto.enabled, auto.displayTime()),
                17,
                auto.enabled ? activity.TEAL : activity.MUTED,
                true
        ));
        String lastSuccess = auto.lastSuccessAt > 0L ? DateTextPolicy.shortDateTime(auto.lastSuccessAt) : "";
        String lastAttempt = auto.lastAttemptAt > 0L && auto.lastAttemptAt != auto.lastSuccessAt
                ? DateTextPolicy.shortDateTime(auto.lastAttemptAt)
                : "";
        String nextRun = auto.nextRunAt > 0L ? DateTextPolicy.shortDateTime(auto.nextRunAt) : "";
        box.addView(activity.text(SettingsTextCopy.autoSyncDetail(auto.configured, auto.enabled, lastSuccess, lastAttempt, nextRun), 15, activity.MUTED, false));
        if (auto.configured) {
            if (auto.enabled) {
                Button off = activity.secondaryButton(SettingsTextCopy.turnOffDailySyncLabel());
                off.setOnClickListener(v -> {
                    AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.disable();
                    activity.store.setAutoSyncEnabled(result.enabled());
                    AutoSyncScheduler.cancel(activity);
                    Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show();
                    activity.renderSettings();
                });
                box.addView(off);
            } else {
                Button on = activity.primaryButton(SettingsTextCopy.turnOnDailySyncLabel(), activity.STUDY_PINK_DARK);
                on.setOnClickListener(v -> {
                    AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.enable();
                    activity.store.setAutoSyncEnabled(result.enabled());
                    AutoSyncScheduler.schedule(activity);
                    Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show();
                    activity.renderSettings();
                });
                box.addView(on);
            }
        }
        return box;
    }

    LinearLayout updateSettingsPanel() {
        LinearLayout box = activity.autoUpdatePanel(SettingsTextCopy.appUpdatesTitle());
        Button update = activity.primaryButton(SettingsTextCopy.openUpdaterLabel(), activity.STUDY_PINK_DARK);
        update.setOnClickListener(v -> activity.renderUpdate());
        box.addView(update);
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
