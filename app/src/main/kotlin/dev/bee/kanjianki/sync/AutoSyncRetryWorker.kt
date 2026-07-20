package dev.bee.kanjianki.sync

import dev.bee.kanjianki.AppLocalStoreFactory

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.data.LocalStore

/** Runs only the bounded follow-up attempts created after a transient daily-sync result. */
class AutoSyncRetryWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val appContext = applicationContext
        AppLocalStoreFactory.create(appContext).use { store ->
            val sync = AutoSyncRunner(
                appContext,
                store,
                AnkiDroidGateway(appContext, SyncCancellation { isStopped }),
            ).run()
            return workerResult(sync, runAttemptCount)
        }
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
