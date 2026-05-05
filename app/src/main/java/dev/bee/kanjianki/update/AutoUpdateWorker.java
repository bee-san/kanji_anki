package dev.bee.kanjianki.update;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import dev.bee.kanjianki.data.LocalStore;

public final class AutoUpdateWorker extends Worker {
    public AutoUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            if (!status.enabled || status.hasPendingUpdate()) {
                return Result.success();
            }
        }
        GitHubUpdater.UpdateResult result = new GitHubUpdater(context).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC);
        return result.retryable ? Result.retry() : Result.success();
    }
}
