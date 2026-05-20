package dev.bee.kanjianki;

import android.widget.Toast;

import dev.bee.kanjianki.update.GitHubUpdater;
import dev.bee.kanjianki.updatecore.UpdateRunScreenCopy;

final class MainActivitySettingsUpdateFlow {
    private final MainActivitySettings activity;

    MainActivitySettingsUpdateFlow(MainActivitySettings activity) {
        this.activity = activity;
    }

    void runUpdate(boolean cachedPending) {
        activity.base(activity.NAV_SETTINGS_ROUTE);
        int updateUiRun = ++activity.updateUiRunCounter;
        activity.activeUpdateUiRunToken = updateUiRun;
        UpdateRunScreenCopy.Copy copy = UpdateRunScreenCopy.forRun(cachedPending);
        activity.content.addView(MainActivitySettingsUpdateRunCompose.settingsUpdateRunView(
                activity,
                copy.title(),
                copy.body(),
                copy.progressLabel()
        ));
        activity.io.execute(() -> {
            GitHubUpdater updater = new GitHubUpdater(activity);
            GitHubUpdater.UpdateResult result = cachedPending
                    ? updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
                    : updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);
            activity.main.post(() -> {
                if (activity.activeUpdateUiRunToken != updateUiRun) {
                    return;
                }
                Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show();
                if (result.intent != null) {
                    activity.startActivity(result.intent);
                }
                activity.renderUpdate();
            });
        });
    }
}
