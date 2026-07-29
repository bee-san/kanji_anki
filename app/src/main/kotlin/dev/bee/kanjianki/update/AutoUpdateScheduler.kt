package dev.bee.kanjianki.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import dev.bee.kanjianki.automation.AndroidWorkManagerGateway
import dev.bee.kanjianki.automation.WorkManagerGateway
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.AutoUpdateSchedulePolicy
import java.util.concurrent.TimeUnit

object AutoUpdateScheduler {
    private const val TAG = "KaniUpdate"

    @JvmStatic
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        appContext.requireKaniContainer().openLocalStore().use { store ->
            schedule(appContext, store)
        }
    }

    internal fun schedule(context: Context, store: LocalStore) {
        schedule(
            store.autoUpdateStatus().enabled,
            AndroidWorkManagerGateway(context.applicationContext),
        )
    }

    internal fun schedule(enabled: Boolean, backend: WorkManagerGateway) {
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
            AndroidWorkManagerGateway(context.applicationContext),
            AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME,
        )
    }

    private fun cancel(backend: WorkManagerGateway, uniqueWorkName: String) {
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

}
