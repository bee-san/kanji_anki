package dev.bee.kanjianki;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import dev.bee.kanjianki.core.SettingsTextCopy;
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
        activity.content.addView(activity.fullWidthHomeButton());
        Button back = activity.secondaryButton(SettingsTextCopy.backToSettingsLabel());
        back.setOnClickListener(new RunnableClickListener(() -> activity.renderSettings(false)));
        activity.content.addView(back);
        activity.content.addView(activity.text(copy.title(), 32, activity.INK, true));
        activity.content.addView(activity.text(copy.body(), 16, activity.MUTED, false));
        activity.content.addView(indeterminateProgressRow(copy.progressLabel()));
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

    LinearLayout indeterminateProgressRow(String label) {
        LinearLayout row = activity.panelBox(Color.WHITE, activity.STUDY_BORDER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        progress.setContentDescription(label);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36));
        progressLp.setMargins(0, 0, activity.dp(12), 0);
        row.addView(progress, progressLp);
        row.addView(activity.text(label, 16, activity.STUDY_PLUM, true), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
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
