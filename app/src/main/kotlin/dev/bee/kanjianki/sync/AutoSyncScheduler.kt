package dev.bee.kanjianki.sync

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
    private const val JOB_ID = 3801

    @JvmStatic
    fun schedule(context: Context) {
        LocalStore(context).use { store ->
            schedule(context, store, store.autoSyncSettings())
        }
    }

    @JvmStatic
    fun schedule(context: Context, store: LocalStore, settings: LocalStoreBase.AutoSyncSettings) {
        schedule(context, store, settings, AppClock.systemClock())
    }

    @JvmStatic
    fun schedule(context: Context, store: LocalStore, settings: LocalStoreBase.AutoSyncSettings, clock: AppClock?) {
        schedule(store, settings, AndroidSchedulerBackend(context), clock)
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

    @JvmStatic
    fun scheduleWithState(
        settings: LocalStoreBase.AutoSyncSettings?,
        now: Long,
        alreadySyncedToday: Boolean,
        recorder: ScheduleRecorder,
        backend: SchedulerBackend,
    ) {
        if (settings == null || !settings.enabled) {
            backend.cancel()
            recorder.markAutoSyncScheduled(0L)
            return
        }
        schedulePlan(
            recorder,
            backend,
            AutoSyncSchedulePolicy.plan(settings.enabled, settings.hour, settings.minute, now, alreadySyncedToday),
        )
    }

    @JvmStatic
    fun cancel(context: Context) {
        val backend: SchedulerBackend = AndroidSchedulerBackend(context)
        backend.cancel()
        LocalStore(context).use { store ->
            store.markAutoSyncScheduled(0L)
        }
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

    @JvmStatic
    fun scheduleAt(recorder: ScheduleRecorder, backend: SchedulerBackend, triggerAt: Long, now: Long) {
        schedulePlan(recorder, backend, AutoSyncSchedulePolicy.planAt(triggerAt, now))
    }

    private fun schedulePlan(
        recorder: ScheduleRecorder,
        backend: SchedulerBackend,
        plan: AutoSyncSchedulePolicy.SchedulePlan,
    ) {
        try {
            val scheduled = backend.schedule(plan.minimumLatencyMillis(), plan.overrideDeadlineMillis())
            recorder.markAutoSyncScheduled(if (scheduled) plan.triggerAtMillis() else 0L)
        } catch (error: RuntimeException) {
            warn("Failed to schedule automatic sync job.", error)
            recorder.markAutoSyncScheduled(0L)
        }
    }

    fun interface ScheduleRecorder {
        fun markAutoSyncScheduled(nextRunAt: Long)
    }

    interface SchedulerBackend {
        fun schedule(minimumLatencyMillis: Long, overrideDeadlineMillis: Long): Boolean

        fun cancel()
    }

    private class AndroidSchedulerBackend(context: Context) : SchedulerBackend {
        private val context: Context = context.applicationContext

        override fun schedule(minimumLatencyMillis: Long, overrideDeadlineMillis: Long): Boolean {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return false
            val job = JobInfo.Builder(
                JOB_ID,
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
            scheduler?.cancel(JOB_ID)
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
