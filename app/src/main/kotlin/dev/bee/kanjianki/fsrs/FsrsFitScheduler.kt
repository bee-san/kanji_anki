package dev.bee.kanjianki.fsrs

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import dev.bee.kanjianki.automation.AndroidWorkManagerGateway
import dev.bee.kanjianki.automation.WorkManagerGateway
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.data.LocalStore
import java.util.concurrent.TimeUnit

object FsrsFitScheduler {
    const val UNIQUE_PERIODIC_WORK_NAME: String = "kani_fsrs_fit"
    const val UNIQUE_NOW_WORK_NAME: String = "kani_fsrs_fit_now"
    private const val INTERVAL_DAYS = 7L

    @JvmStatic
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        appContext.requireKaniContainer().openLocalStore().use { store ->
            schedule(appContext, store)
        }
    }

    internal fun schedule(context: Context, store: LocalStore) {
        schedule(
            store.fsrsPersonalizationEnabled(),
            AndroidWorkManagerGateway(context.applicationContext),
        )
    }

    internal fun schedule(enabled: Boolean, backend: WorkManagerGateway) {
        if (!enabled) {
            backend.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            backend.cancelUniqueWork(UNIQUE_NOW_WORK_NAME)
            return
        }
        backend.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest(),
        )
    }

    @JvmStatic
    fun fitNow(context: Context) {
        fitNow(AndroidWorkManagerGateway(context.applicationContext))
    }

    internal fun fitNow(backend: WorkManagerGateway) {
        backend.enqueueUniqueWork(
            UNIQUE_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneShotRequest(),
        )
    }

    @JvmStatic
    fun cancel(context: Context) {
        schedule(false, AndroidWorkManagerGateway(context.applicationContext))
    }

    private fun periodicRequest(): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
        return PeriodicWorkRequest.Builder(FsrsFitWorker::class.java, INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
    }

    private fun oneShotRequest(): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(FsrsFitWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

}
