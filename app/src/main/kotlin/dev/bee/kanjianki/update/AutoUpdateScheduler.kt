package dev.bee.kanjianki.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.AutoUpdateSchedulePolicy
import java.util.concurrent.TimeUnit

object AutoUpdateScheduler {
    @JvmStatic
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        LocalStore(appContext).use { store ->
            schedule(store.autoUpdateStatus().enabled, WorkManagerSchedulerBackend(appContext))
        }
    }

    internal fun schedule(enabled: Boolean, backend: SchedulerBackend) {
        val plan = AutoUpdateSchedulePolicy.plan(enabled)
        if (!plan.enabled()) {
            backend.cancelUniqueWork(plan.uniqueWorkName())
            return
        }
        backend.enqueueUniquePeriodicWork(
            plan.uniqueWorkName(),
            ExistingPeriodicWorkPolicy.KEEP,
            dailyUpdateRequest(plan),
        )
    }

    private fun dailyUpdateRequest(plan: AutoUpdateSchedulePolicy.SchedulePlan): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (plan.requiresConnectedNetwork()) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
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
        WorkManagerSchedulerBackend(context.applicationContext).cancelUniqueWork(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME)
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
