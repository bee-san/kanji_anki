package dev.bee.kanjianki.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import dev.bee.kanjianki.automation.AndroidWorkManagerGateway
import dev.bee.kanjianki.automation.PendingWorkOperation
import dev.bee.kanjianki.automation.WorkManagerGateway
import java.util.concurrent.TimeUnit

/**
 * Owns the one bounded retry chain for a failed daily AnkiDroid sync.
 *
 * The initial delay and exponential backoff place the three executions at
 * approximately 15, 30, and 60 minutes after the daily job. WorkManager timing
 * remains inexact by design. [ExistingWorkPolicy.KEEP] prevents concurrent daily
 * jobs or foreground sync contention from multiplying retry chains.
 */
internal object AutoSyncRetryScheduler {
    internal const val UNIQUE_WORK_NAME = "kani_auto_sync_retry"
    internal const val MAX_EXECUTIONS = 3
    internal const val BASE_DELAY_MINUTES = 15L

    @JvmStatic
    fun schedule(context: Context) {
        schedule(AndroidWorkManagerGateway(context.applicationContext))
    }

    /** Persists the retry before a JobService releases its component lifetime. */
    @JvmStatic
    fun scheduleAndAwait(context: Context) {
        schedule(AndroidWorkManagerGateway(context.applicationContext)).await()
    }

    internal fun schedule(backend: WorkManagerGateway): PendingWorkOperation =
        backend.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request(),
        )

    @JvmStatic
    fun cancel(context: Context) {
        cancel(AndroidWorkManagerGateway(context.applicationContext))
    }

    /** Persists cancellation before a JobService reports terminal completion. */
    @JvmStatic
    fun cancelAndAwait(context: Context) {
        cancel(AndroidWorkManagerGateway(context.applicationContext)).await()
    }

    internal fun cancel(backend: WorkManagerGateway): PendingWorkOperation =
        backend.cancelUniqueWork(UNIQUE_WORK_NAME)

    private fun request(): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(AutoSyncRetryWorker::class.java)
            .setInitialDelay(BASE_DELAY_MINUTES, TimeUnit.MINUTES)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BASE_DELAY_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()
}
