package dev.bee.kanjianki;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.update.GitHubUpdater;
import dev.bee.kanjianki.update.AutoUpdateScheduler;
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy;

final class MainActivitySettingsUpdatePage {
    private final MainActivitySettings activity;

    MainActivitySettingsUpdatePage(MainActivitySettings activity) {
        this.activity = activity;
    }

    void renderUpdate() {
        activity.base(activity.NAV_SETTINGS_ROUTE);
        activity.content.addView(activity.fullWidthHomeButton());
        Button backButton = activity.secondaryButton(SettingsTextCopy.backToSettingsLabel());
        backButton.setOnClickListener(v -> activity.renderSettings(false));
        activity.content.addView(backButton);
        activity.content.addView(activity.text(SettingsTextCopy.updatePageTitle(), 34, activity.INK, true));
        activity.content.addView(activity.text(SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME), 16, activity.MUTED, false));
        activity.content.addView(autoUpdatePanel(SettingsTextCopy.automaticUpdatesTitle()));

        Button button = activity.primaryButton(SettingsTextCopy.checkForUpdateLabel(), activity.STUDY_PINK_DARK);
        button.setOnClickListener(v -> activity.runUpdate(false));
        activity.content.addView(button);
    }

    LinearLayout autoUpdatePanel(String title) {
        LocalStore.AutoUpdateStatus status = activity.store.autoUpdateStatus();
        boolean canInstall = canInstallUpdates();
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(title, 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.autoUpdatePanelStatus(status.enabled), 18, status.enabled ? activity.TEAL : activity.MUTED, true));
        box.addView(activity.text(
                SettingsTextCopy.autoUpdateLastCheckLine(DateTextPolicy.autoUpdateLastCheckText(status.lastCheckAtMillis)),
                15,
                activity.MUTED,
                false
        ));
        box.addView(activity.text(SettingsTextCopy.autoUpdateLastResultLine(status.lastResult), 15, activity.MUTED, false));
        box.addView(activity.text(SettingsTextCopy.installPermissionLine(canInstall), 15, canInstall ? activity.TEAL : activity.CORAL, true));

        if (status.hasPendingUpdate()) {
            box.addView(activity.text(SettingsTextCopy.verifiedApkReadyLine(status.lastVersion), 18, activity.CORAL, true));
            String pending = status.pendingMessage.isEmpty() ? SettingsTextCopy.pendingUpdateFallback() : status.pendingMessage;
            box.addView(activity.text(pending, 15, activity.MUTED, false));
            if (canInstall) {
                Button install = activity.primaryButton(SettingsTextCopy.installVerifiedUpdateLabel(), activity.CORAL);
                install.setOnClickListener(v -> activity.runUpdate(true));
                box.addView(install);
            }
        }

        if (!canInstall) {
            Button permission = activity.secondaryButton(SettingsTextCopy.setupAppInstallsLabel());
            permission.setOnClickListener(v -> activity.startActivity(GitHubUpdater.installPermissionIntent(activity)));
            box.addView(permission);
        }

        Button toggle = activity.secondaryButton(SettingsTextCopy.automaticUpdatesToggleLabel(status.enabled));
        toggle.setOnClickListener(v -> {
            AutoUpdateSettingsTogglePolicy.ToggleResult result = AutoUpdateSettingsTogglePolicy.toggle(status.enabled);
            activity.store.saveAutoUpdateEnabled(result.enabled());
            if (result.enabled()) {
                AutoUpdateScheduler.schedule(activity);
            } else {
                AutoUpdateScheduler.cancel(activity);
            }
            Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show();
            activity.renderUpdate();
        });
        box.addView(toggle);
        return box;
    }

    private boolean canInstallUpdates() {
        if (MainActivityBase.installPermissionForTests != null) {
            return MainActivityBase.installPermissionForTests;
        }
        return activity.getPackageManager().canRequestPackageInstalls();
    }
}
