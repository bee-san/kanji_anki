package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.sync.AutoSyncScheduler;

final class MainActivitySettingsAutomationAutoSync {
    private final MainActivitySettings activity;

    MainActivitySettingsAutomationAutoSync(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout autoSyncSettingsPanel() {
        dev.bee.kanjianki.data.LocalStore.AutoSyncSettings auto = activity.store.autoSyncSettings();
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
                off.setOnClickListener(new RunnableClickListener(this::disableAutoSync));
                box.addView(off);
            } else {
                Button on = activity.primaryButton(SettingsTextCopy.turnOnDailySyncLabel(), activity.STUDY_PINK_DARK);
                on.setOnClickListener(new RunnableClickListener(this::enableAutoSync));
                box.addView(on);
            }
        }
        return box;
    }

    private void enableAutoSync() {
        AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.enable();
        activity.store.setAutoSyncEnabled(result.enabled());
        AutoSyncScheduler.schedule(activity);
        Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
    }

    private void disableAutoSync() {
        AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.disable();
        activity.store.setAutoSyncEnabled(result.enabled());
        AutoSyncScheduler.cancel(activity);
        Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show();
        activity.renderSettings();
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
}
