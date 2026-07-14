package dev.bee.kanjianki.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
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
    internal const val PERSISTENCE_TIMEOUT_SECONDS = 15L

    @JvmStatic
    fun schedule(context: Context) {
        schedule(WorkManagerBackend(context.applicationContext))
    }

    /** Persists the retry before a JobService releases its component lifetime. */
    @JvmStatic
    fun scheduleAndAwait(context: Context) {
        schedule(WorkManagerBackend(context.applicationContext)).await()
    }

    internal fun schedule(backend: SchedulerBackend): PendingOperation =
        backend.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request(),
        )

    @JvmStatic
    fun cancel(context: Context) {
        cancel(WorkManagerBackend(context.applicationContext))
    }

    /** Persists cancellation before a JobService reports terminal completion. */
    @JvmStatic
    fun cancelAndAwait(context: Context) {
        cancel(WorkManagerBackend(context.applicationContext)).await()
    }

    internal fun cancel(backend: SchedulerBackend): PendingOperation =
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

    internal interface SchedulerBackend {
        fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): PendingOperation

        fun cancelUniqueWork(uniqueWorkName: String): PendingOperation
    }

    internal fun interface PendingOperation {
        fun await()
    }

    private class WorkManagerBackend(context: Context) : SchedulerBackend {
        private val workManager = WorkManager.getInstance(context)

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): PendingOperation =
            workManager.enqueueUniqueWork(uniqueWorkName, policy, request).pendingOperation()

        override fun cancelUniqueWork(uniqueWorkName: String): PendingOperation =
            workManager.cancelUniqueWork(uniqueWorkName).pendingOperation()

        private fun Operation.pendingOperation(): PendingOperation = PendingOperation {
            try {
                result.get(PERSISTENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            }
        }
    }
}
