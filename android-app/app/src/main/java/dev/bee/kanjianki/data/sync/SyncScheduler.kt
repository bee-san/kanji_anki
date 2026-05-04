package dev.bee.kanjianki.data.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.bee.kanjianki.domain.SettingsSnapshot
import java.util.concurrent.TimeUnit
import kotlin.math.max

class SyncScheduler(
    private val workManager: WorkManager,
) {
    fun enqueueManualSync() {
        workManager.enqueueUniqueWork(
            AnkiSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AnkiSyncWorker>().build(),
        )
    }

    fun configurePolling(settings: SettingsSnapshot) {
        if (!settings.pollingEnabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val repeatIntervalSeconds = max(
            settings.pollingIntervalSeconds,
            MINIMUM_PERIODIC_INTERVAL_SECONDS,
        ).toLong()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequestBuilder<AnkiSyncWorker>(
                repeatIntervalSeconds,
                TimeUnit.SECONDS,
            ).build(),
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "anki-sync-periodic"
        private const val MINIMUM_PERIODIC_INTERVAL_SECONDS = 15 * 60
    }
}
