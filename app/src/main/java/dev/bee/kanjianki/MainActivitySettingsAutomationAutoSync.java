package dev.bee.kanjianki;

import android.view.View;
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

    View autoSyncSettingsPanel() {
        return MainActivitySettingsAutomationAutoSyncCompose.autoSyncSettingsPanelView(
                activity,
                autoSyncSettingsPanelModel()
        );
    }

    SettingsAutoSyncPanelModel autoSyncSettingsPanelModel() {
        dev.bee.kanjianki.data.LocalStore.AutoSyncSettings auto = activity.store.autoSyncSettings();
        String lastSuccess = auto.lastSuccessAt > 0L ? DateTextPolicy.shortDateTime(auto.lastSuccessAt) : "";
        String lastAttempt = auto.lastAttemptAt > 0L && auto.lastAttemptAt != auto.lastSuccessAt
                ? DateTextPolicy.shortDateTime(auto.lastAttemptAt)
                : "";
        String nextRun = auto.nextRunAt > 0L ? DateTextPolicy.shortDateTime(auto.nextRunAt) : "";
        String actionLabel = null;
        boolean primaryAction = false;
        SettingsAutoSyncAction action = null;
        if (auto.configured) {
            if (auto.enabled) {
                actionLabel = SettingsTextCopy.turnOffDailySyncLabel();
                action = this::disableAutoSync;
            } else {
                actionLabel = SettingsTextCopy.turnOnDailySyncLabel();
                primaryAction = true;
                action = this::enableAutoSync;
            }
        }
        return new SettingsAutoSyncPanelModel(
                SettingsTextCopy.dailyAnkiSyncTitle(),
                SettingsTextCopy.autoSyncStatus(auto.configured, auto.enabled, auto.displayTime()),
                auto.enabled ? activity.TEAL : activity.MUTED,
                SettingsTextCopy.autoSyncDetail(auto.configured, auto.enabled, lastSuccess, lastAttempt, nextRun),
                actionLabel,
                primaryAction,
                action
        );
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

}
