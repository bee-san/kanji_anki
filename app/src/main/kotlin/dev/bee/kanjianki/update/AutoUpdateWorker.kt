package dev.bee.kanjianki.update

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.AutoUpdateRunPolicy

class AutoUpdateWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        return runFromStore(applicationContext, automaticUpdateCheckerFactory(::checkAutomaticUpdate))
    }

    fun interface UpdateChecker {
        fun check(): GitHubUpdater.UpdateResult
    }

    fun interface UpdateCheckerFactory {
        fun create(context: Context?): UpdateChecker
    }

    fun interface AutomaticUpdateRunner {
        fun check(context: Context?): GitHubUpdater.UpdateResult
    }

    fun interface UpdateClientFactory {
        fun create(context: Context): GitHubUpdater.UpdateClient
    }

    companion object {
        @JvmField
        var updateClientFactory: UpdateClientFactory =
            UpdateClientFactory { context -> GitHubUpdater.androidClient(context) }

        @JvmStatic
        fun automaticUpdateCheckerFactory(runner: AutomaticUpdateRunner): UpdateCheckerFactory {
            return UpdateCheckerFactory { context -> UpdateChecker { runner.check(context) } }
        }

        @JvmStatic
        fun checkAutomaticUpdate(context: Context?): GitHubUpdater.UpdateResult {
            val updateContext = context!!
            return GitHubUpdater(updateContext, updateClientFactory.create(updateContext))
                .checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)
        }

        @JvmStatic
        fun runFromStore(context: Context, checkerFactory: UpdateCheckerFactory): Result {
            val appContext = context.applicationContext
            LocalStore(appContext).use { store ->
                val status = store.autoUpdateStatus()
                if (!AutoUpdateRunPolicy.shouldRun(status.enabled, status.hasPendingUpdate())) {
                    return Result.success()
                }
                return runAutoUpdate(true, false, checkerFactory.create(appContext))
            }
        }

        @JvmStatic
        fun runAutoUpdate(enabled: Boolean, hasPendingUpdate: Boolean, checker: UpdateChecker): Result {
            if (!AutoUpdateRunPolicy.shouldRun(enabled, hasPendingUpdate)) {
                return Result.success()
            }
            val result = checker.check()
            return workerResult(AutoUpdateRunPolicy.workerOutcome(result.retryable))
        }

        @JvmStatic
        fun workerResult(outcome: AutoUpdateRunPolicy.WorkerOutcome): Result {
            return if (outcome == AutoUpdateRunPolicy.WorkerOutcome.RETRY) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }
}
