package dev.bee.kanjianki.backup

import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import dev.bee.kanjianki.automation.AndroidWorkManagerGateway
import dev.bee.kanjianki.automation.WorkManagerGateway
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import java.util.concurrent.TimeUnit

object DatabaseBackupScheduler {
    private const val UNIQUE_WORK_NAME = "kani_daily_db_backup"

    @JvmStatic
    fun schedule(context: Context?) {
        val appContext = context!!.applicationContext
        schedule(Build.VERSION.SDK_INT, AndroidWorkManagerGateway(appContext))
    }

    internal fun schedule(apiLevel: Int, backend: WorkManagerGateway) {
        if (!DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel).operationsAllowed) {
            backend.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequest.Builder(
            DatabaseBackupWorker::class.java,
            1,
            TimeUnit.DAYS,
            6,
            TimeUnit.HOURS,
        ).build()
        backend.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    @JvmStatic
    fun cancel(context: Context?) {
        AndroidWorkManagerGateway(context!!.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
