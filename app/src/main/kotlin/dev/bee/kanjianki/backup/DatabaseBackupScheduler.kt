package dev.bee.kanjianki.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DatabaseBackupScheduler {
    private const val UNIQUE_WORK_NAME = "kani_daily_db_backup"

    @JvmStatic
    fun schedule(context: Context?) {
        val appContext = context!!.applicationContext
        val request = PeriodicWorkRequest.Builder(
            DatabaseBackupWorker::class.java,
            1,
            TimeUnit.DAYS,
            6,
            TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    @JvmStatic
    fun cancel(context: Context?) {
        cancel(context) { appContext ->
            WorkCanceller { workName ->
                WorkManager.getInstance(appContext).cancelUniqueWork(workName)
            }
        }
    }

    internal fun cancel(context: Context?, factory: WorkCancellerFactory) {
        factory.create(context!!.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    internal fun interface WorkCancellerFactory {
        fun create(appContext: Context): WorkCanceller
    }

    internal fun interface WorkCanceller {
        fun cancelUniqueWork(workName: String)
    }
}
