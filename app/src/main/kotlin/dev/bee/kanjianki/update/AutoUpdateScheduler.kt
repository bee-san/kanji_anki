package dev.bee.kanjianki.update

import dev.bee.kanjianki.AppLocalStoreFactory

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.AutoUpdateSchedulePolicy
import java.util.concurrent.TimeUnit

object AutoUpdateScheduler {
    private const val TAG = "KaniUpdate"

    @JvmStatic
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        AppLocalStoreFactory.create(appContext).use { store ->
            schedule(store.autoUpdateStatus().enabled, WorkManagerSchedulerBackend(appContext))
        }
    }

    internal fun schedule(enabled: Boolean, backend: SchedulerBackend) {
        val plan = AutoUpdateSchedulePolicy.plan(enabled)
        if (!plan.enabled()) {
            cancel(backend, plan.uniqueWorkName())
            return
        }
        try {
            backend.enqueueUniquePeriodicWork(
                plan.uniqueWorkName(),
                ExistingPeriodicWorkPolicy.KEEP,
                dailyUpdateRequest(plan),
            )
        } catch (error: RuntimeException) {
            warn("Could not schedule automatic update checks.", error)
        }
    }

    private fun dailyUpdateRequest(plan: AutoUpdateSchedulePolicy.SchedulePlan): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (plan.requiresConnectedNetwork()) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(plan.requiresBatteryNotLow())
            .build()
        return PeriodicWorkRequest.Builder(
            AutoUpdateWorker::class.java,
            plan.intervalMillis(),
            TimeUnit.MILLISECONDS,
            plan.flexMillis(),
            TimeUnit.MILLISECONDS,
        )
            .setConstraints(constraints)
            .build()
    }

    @JvmStatic
    fun cancel(context: Context) {
        cancel(
            WorkManagerSchedulerBackend(context.applicationContext),
            AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME,
        )
    }

    private fun cancel(backend: SchedulerBackend, uniqueWorkName: String) {
        try {
            backend.cancelUniqueWork(uniqueWorkName)
        } catch (error: RuntimeException) {
            warn("Could not cancel automatic update checks.", error)
        }
    }

    private fun warn(message: String, error: Throwable) {
        try {
            Log.w(TAG, message, error)
        } catch (_: RuntimeException) {
            // Android Log is unavailable in local JVM tests.
        }
    }

    internal interface SchedulerBackend {
        fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        )

        fun cancelUniqueWork(uniqueWorkName: String)
    }

    private class WorkManagerSchedulerBackend(context: Context) : SchedulerBackend {
        private val context = context.applicationContext

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(uniqueWorkName, policy, request)
        }

        override fun cancelUniqueWork(uniqueWorkName: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        }
    }
}
