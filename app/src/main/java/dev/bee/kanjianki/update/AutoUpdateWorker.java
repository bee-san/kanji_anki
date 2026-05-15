package dev.bee.kanjianki.update;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import dev.bee.kanjianki.data.LocalStore;

public final class AutoUpdateWorker extends Worker {
    static UpdateClientFactory updateClientFactory = GitHubUpdater::androidClient;

    public AutoUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        return runFromStore(getApplicationContext(), automaticUpdateCheckerFactory(AutoUpdateWorker::checkAutomaticUpdate));
    }

    static UpdateCheckerFactory automaticUpdateCheckerFactory(AutomaticUpdateRunner runner) {
        return context -> () -> runner.check(context);
    }

    static GitHubUpdater.UpdateResult checkAutomaticUpdate(Context context) {
        return new GitHubUpdater(context, updateClientFactory.create(context)).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC);
    }

    static Result runFromStore(Context context, UpdateCheckerFactory checkerFactory) {
        Context appContext = context.getApplicationContext();
        try (LocalStore store = new LocalStore(appContext)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            if (!status.enabled || status.hasPendingUpdate()) {
                return Result.success();
            }
            return runAutoUpdate(true, false, checkerFactory.create(appContext));
        }
    }

    static Result runAutoUpdate(boolean enabled, boolean hasPendingUpdate, UpdateChecker checker) {
        if (!enabled || hasPendingUpdate) {
            return Result.success();
        }
        GitHubUpdater.UpdateResult result = checker.check();
        return result.retryable ? Result.retry() : Result.success();
    }

    interface UpdateChecker {
        GitHubUpdater.UpdateResult check();
    }

    interface UpdateCheckerFactory {
        UpdateChecker create(Context context);
    }

    interface AutomaticUpdateRunner {
        GitHubUpdater.UpdateResult check(Context context);
    }

    interface UpdateClientFactory {
        GitHubUpdater.UpdateClient create(Context context);
    }
}
