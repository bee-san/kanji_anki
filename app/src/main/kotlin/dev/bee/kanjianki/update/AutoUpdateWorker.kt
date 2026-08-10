package dev.bee.kanjianki.update

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.AndroidContainerProvider
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.AutoUpdateRunPolicy

class AutoUpdateWorker internal constructor(
    context: Context,
    workerParams: WorkerParameters,
    private val containerProvider: AndroidContainerProvider,
) : Worker(context, workerParams) {
    constructor(context: Context, workerParams: WorkerParameters) : this(
        context,
        workerParams,
        AndroidContainerProvider { context.requireKaniContainer() },
    )

    override fun doWork(): Result {
        return containerProvider.get().openLocalStore().use { store ->
            runFromStore(
                store,
                applicationContext,
                automaticUpdateCheckerFactory(::checkAutomaticUpdate),
            )
        }
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
            appContext.requireKaniContainer().openLocalStore().use { store ->
                return runFromStore(store, appContext, checkerFactory)
            }
        }

        private fun runFromStore(
            store: LocalStore,
            appContext: Context,
            checkerFactory: UpdateCheckerFactory,
        ): Result {
            val status = store.autoUpdateStatus()
            if (!AutoUpdateRunPolicy.shouldRun(status.enabled, status.hasPendingUpdate())) {
                return Result.success()
            }
            return runAutoUpdate(true, false, checkerFactory.create(appContext))
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
