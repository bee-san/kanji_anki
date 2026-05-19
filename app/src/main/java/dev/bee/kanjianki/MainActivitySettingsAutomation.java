package dev.bee.kanjianki;

import android.graphics.Color;
import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.update.AutoUpdateScheduler;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class MainActivitySettingsAutomation {
    private final MainActivitySettings activity;
    private final MainActivitySettingsAutomationReminder reminder;

    MainActivitySettingsAutomation(MainActivitySettings activity) {
        this.activity = activity;
        this.reminder = new MainActivitySettingsAutomationReminder(activity);
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
        return reminder.reminderSettingsPanel();
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
        reminder.saveReminderFromSelection(hour, minute, enabled);
    }

    Button reminderPresetButton(String label, int hour, int minute, int[] selectedHour, int[] selectedMinute, Button timeButton) {
        return reminder.reminderPresetButton(label, hour, minute, selectedHour, selectedMinute, timeButton);
    }
}
