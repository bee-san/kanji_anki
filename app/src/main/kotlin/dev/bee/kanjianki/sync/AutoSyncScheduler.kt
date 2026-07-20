package dev.bee.kanjianki.sync

import dev.bee.kanjianki.AppLocalStoreFactory

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.bee.kanjianki.core.AutoSyncSchedulePolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.time.AppClock

internal object AutoSyncScheduler {
    private const val TAG = "AutoSyncScheduler"
    internal const val PRIMARY_JOB_ID = 3801
    internal const val SECONDARY_JOB_ID = 3802

    @JvmStatic
    fun schedule(context: Context) {
        AppLocalStoreFactory.create(context).use { store ->
            schedule(context, store, store.autoSyncSettings())
        }
    }

    @JvmStatic
    fun schedule(context: Context, store: LocalStore, settings: LocalStoreBase.AutoSyncSettings) {
        schedule(context, store, settings, AppClock.systemClock())
    }

    @JvmStatic
    fun schedule(context: Context, store: LocalStore, settings: LocalStoreBase.AutoSyncSettings, clock: AppClock?) {
        val now = AppClock.orSystem(clock).nowMillis()
        val alreadySyncedToday = store.hasSuccessfulSyncSince(localDayStart(now))
        val existingJobId = existingAutoSyncJobId(context)
        val plan = AutoSyncSchedulePolicy.plan(
            settings.enabled,
            settings.hour,
            settings.minute,
            now,
            alreadySyncedToday,
        )
        if (
            settings.enabled &&
            existingJobId != null &&
            shouldKeepExistingJob(settings.nextRunAt, plan.triggerAtMillis, now, alreadySyncedToday)
        ) {
            if (shouldCancelRetry(settings, alreadySyncedToday)) {
                AutoSyncRetryScheduler.cancel(context)
            }
            return
        }
        scheduleWithState(
            settings,
            now,
            alreadySyncedToday,
            ScheduleRecorder { nextRunAt -> store.markAutoSyncScheduled(nextRunAt) },
            AndroidSchedulerBackend(context, existingJobId ?: PRIMARY_JOB_ID),
        )
        if (shouldCancelRetry(settings, alreadySyncedToday)) {
            AutoSyncRetryScheduler.cancel(context)
        }
    }

    @JvmStatic
    fun schedule(store: LocalStore, settings: LocalStoreBase.AutoSyncSettings, backend: SchedulerBackend) {
        schedule(store, settings, backend, AppClock.systemClock())
    }

    @JvmStatic
    fun schedule(store: LocalStore, settings: LocalStoreBase.AutoSyncSettings, backend: SchedulerBackend, clock: AppClock?) {
        val now = AppClock.orSystem(clock).nowMillis()
        scheduleWithState(
            settings,
            now,
            store.hasSuccessfulSyncSince(localDayStart(now)),
            ScheduleRecorder { nextRunAt -> store.markAutoSyncScheduled(nextRunAt) },
            backend,
        )
    }

    /**
     * Schedules tomorrow with an ID different from the currently executing job.
     * JobScheduler stops a running job when [android.app.job.JobScheduler.schedule]
     * replaces the same ID, so alternating IDs keeps completion atomic.
     */
    @JvmStatic
    fun scheduleNext(
        context: Context,
        store: LocalStore,
        settings: LocalStoreBase.AutoSyncSettings,
        currentJobId: Int?,
    ): Boolean {
        val now = AppClock.systemClock().nowMillis()
        val alreadySyncedToday = store.hasSuccessfulSyncSince(localDayStart(now))
        val scheduled = scheduleWithState(
            settings,
            now,
            alreadySyncedToday,
            ScheduleRecorder { nextRunAt -> store.markAutoSyncScheduled(nextRunAt) },
            AndroidSchedulerBackend(context, nextJobId(currentJobId)),
        )
        if (shouldCancelRetry(settings, alreadySyncedToday)) {
            AutoSyncRetryScheduler.cancel(context)
        }
        return scheduled
    }

    @JvmStatic
    fun scheduleWithState(
        settings: LocalStoreBase.AutoSyncSettings?,
        now: Long,
        alreadySyncedToday: Boolean,
        recorder: ScheduleRecorder,
        backend: SchedulerBackend,
    ): Boolean {
        if (settings == null || !settings.enabled) {
            backend.cancel()
            recorder.markAutoSyncScheduled(0L)
            return true
        }
        return schedulePlan(
            recorder,
            backend,
            AutoSyncSchedulePolicy.plan(settings.enabled, settings.hour, settings.minute, now, alreadySyncedToday),
        )
    }

    @JvmStatic
    fun cancel(context: Context) {
        val backend: SchedulerBackend = AndroidSchedulerBackend(context, PRIMARY_JOB_ID)
        backend.cancel()
        AutoSyncRetryScheduler.cancel(context)
        AppLocalStoreFactory.create(context).use { store ->
            store.markAutoSyncScheduled(0L)
        }
    }

    @JvmStatic
    internal fun shouldCancelRetry(
        settings: LocalStoreBase.AutoSyncSettings?,
        alreadySyncedToday: Boolean,
    ): Boolean = settings == null || !settings.enabled || alreadySyncedToday

    @JvmStatic
    internal fun nextJobId(currentJobId: Int?): Int {
        return if (currentJobId == PRIMARY_JOB_ID) SECONDARY_JOB_ID else PRIMARY_JOB_ID
    }

    @JvmStatic
    internal fun shouldKeepExistingJob(
        recordedTriggerAt: Long,
        plannedTriggerAt: Long,
        now: Long,
        alreadySyncedToday: Boolean,
    ): Boolean {
        if (recordedTriggerAt <= 0L) {
            return false
        }
        if (recordedTriggerAt == plannedTriggerAt) {
            return true
        }
        // Do not replace an overdue/running job before it gets its chance to sync.
        return !alreadySyncedToday && recordedTriggerAt <= now
    }

    @JvmStatic
    fun nextTriggerMillis(settings: LocalStoreBase.AutoSyncSettings, now: Long): Long {
        return nextTriggerMillis(settings, now, false)
    }

    @JvmStatic
    fun nextTriggerMillis(settings: LocalStoreBase.AutoSyncSettings, now: Long, alreadySyncedToday: Boolean): Long {
        return AutoSyncSchedulePolicy.nextTriggerMillis(settings.hour, settings.minute, now, alreadySyncedToday)
    }

    private fun localDayStart(now: Long): Long {
        return AutoSyncSchedulePolicy.localDayStart(now)
    }

    private fun existingAutoSyncJobId(context: Context): Int? {
        return try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return null
            scheduler.allPendingJobs
                .firstOrNull { it.id == PRIMARY_JOB_ID || it.id == SECONDARY_JOB_ID }
                ?.id
        } catch (error: RuntimeException) {
            warn("Could not inspect existing automatic sync jobs.", error)
            null
        }
    }

    @JvmStatic
    fun scheduleAt(
        recorder: ScheduleRecorder,
        backend: SchedulerBackend,
        triggerAt: Long,
        now: Long,
    ): Boolean = schedulePlan(recorder, backend, AutoSyncSchedulePolicy.planAt(triggerAt, now))

    private fun schedulePlan(
        recorder: ScheduleRecorder,
        backend: SchedulerBackend,
        plan: AutoSyncSchedulePolicy.SchedulePlan,
    ): Boolean {
        return try {
            val scheduled = backend.schedule(plan.minimumLatencyMillis, plan.overrideDeadlineMillis)
            recorder.markAutoSyncScheduled(if (scheduled) plan.triggerAtMillis else 0L)
            scheduled
        } catch (error: RuntimeException) {
            warn("Failed to schedule automatic sync job.", error)
            recorder.markAutoSyncScheduled(0L)
            false
        }
    }

    fun interface ScheduleRecorder {
        fun markAutoSyncScheduled(nextRunAt: Long)
    }

    interface SchedulerBackend {
        fun schedule(minimumLatencyMillis: Long, overrideDeadlineMillis: Long): Boolean

        fun cancel()
    }

    private class AndroidSchedulerBackend(context: Context, private val jobId: Int) : SchedulerBackend {
        private val context: Context = context.applicationContext

        override fun schedule(minimumLatencyMillis: Long, overrideDeadlineMillis: Long): Boolean {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return false
            val job = JobInfo.Builder(
                jobId,
                ComponentName(context, AutoSyncJobService::class.java),
            )
                .setMinimumLatency(minimumLatencyMillis)
                .setOverrideDeadline(overrideDeadlineMillis)
                .setPersisted(true)
                .build()
            return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        }

        override fun cancel() {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(PRIMARY_JOB_ID)
            scheduler?.cancel(SECONDARY_JOB_ID)
        }
    }

    private fun warn(message: String, error: Throwable) {
        try {
            Log.w(TAG, message, error)
        } catch (_: RuntimeException) {
            // Android Log is unavailable in local JVM tests.
        }
    }
}
