package dev.bee.kanjianki.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.AndroidContainerProvider
import dev.bee.kanjianki.requireKaniContainer

/** Runs only the bounded follow-up attempts created after a transient daily-sync result. */
class AutoSyncRetryWorker internal constructor(
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
        val appContext = applicationContext
        val container = containerProvider.get()
        val sync = createAutoSyncRunner(
            appContext,
            container.syncUseCases,
            container.deviceSettingsStore,
            container.newAnkiDroidGateway(SyncCancellation { isStopped }),
        ).run()
        return workerResult(sync, runAttemptCount)
    }

    companion object {
        @JvmStatic
        internal fun workerResult(
            sync: AutoSyncRunner.Result,
            runAttemptCount: Int,
        ): ListenableWorker.Result {
            return if (sync.retryable && runAttemptCount < AutoSyncRetryScheduler.MAX_EXECUTIONS - 1) {
                Result.retry()
            } else {
                // A terminal result, successful/irrelevant skip, or exhausted chain
                // completes the unique work. The normal daily job remains scheduled.
                Result.success()
            }
        }
    }
}
