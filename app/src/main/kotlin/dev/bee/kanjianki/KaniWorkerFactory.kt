package dev.bee.kanjianki

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.bee.kanjianki.backup.DatabaseBackupWorker
import dev.bee.kanjianki.fsrs.FsrsFitWorker
import dev.bee.kanjianki.sync.AutoSyncRetryWorker
import dev.bee.kanjianki.update.AutoUpdateWorker

/**
 * Constructs known workers without resolving process dependencies. Each worker
 * invokes [containerProvider] from doWork, after application startup has passed
 * the staged-restore gate.
 */
internal class KaniWorkerFactory(
    private val containerProvider: AndroidContainerProvider,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        DatabaseBackupWorker::class.java.name ->
            DatabaseBackupWorker(appContext, workerParameters, containerProvider)
        FsrsFitWorker::class.java.name ->
            FsrsFitWorker(appContext, workerParameters, containerProvider)
        AutoSyncRetryWorker::class.java.name ->
            AutoSyncRetryWorker(appContext, workerParameters, containerProvider)
        AutoUpdateWorker::class.java.name ->
            AutoUpdateWorker(appContext, workerParameters, containerProvider)
        else -> null
    }
}
