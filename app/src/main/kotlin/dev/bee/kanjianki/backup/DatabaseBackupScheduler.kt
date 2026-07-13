package dev.bee.kanjianki.backup

import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import java.util.concurrent.TimeUnit

object DatabaseBackupScheduler {
    private const val UNIQUE_WORK_NAME = "kani_daily_db_backup"

    @JvmStatic
    fun schedule(context: Context?) {
        val appContext = context!!.applicationContext
        schedule(Build.VERSION.SDK_INT, WorkManagerBackend(appContext))
    }

    internal fun schedule(apiLevel: Int, backend: SchedulerBackend) {
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
        WorkManagerBackend(context!!.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    internal interface SchedulerBackend {
        fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        )

        fun cancelUniqueWork(workName: String)
    }

    private class WorkManagerBackend(context: Context) : SchedulerBackend {
        private val workManager = WorkManager.getInstance(context.applicationContext)

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            workManager.enqueueUniquePeriodicWork(uniqueWorkName, policy, request)
        }

        override fun cancelUniqueWork(workName: String) {
            workManager.cancelUniqueWork(workName)
        }
    }
}
