package dev.bee.kanjianki.sync

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import java.util.concurrent.Executors

class AutoSyncJobService : JobService {
    private lateinit var executor: JobExecutor
    private lateinit var shutdown: Shutdown
    private lateinit var autoSyncTask: AutoSyncTask

    @Volatile
    private var stopped = false

    constructor() {
        val io = Executors.newSingleThreadExecutor()
        executor = JobExecutor { job -> io.execute(job) }
        shutdown = Shutdown { io.shutdownNow() }
        autoSyncTask = AutoSyncTask { params -> runAutoSync(params) }
    }

    constructor(
        executor: JobExecutor,
        shutdown: Shutdown,
        autoSyncTask: AutoSyncTask,
    ) {
        this.executor = executor
        this.shutdown = shutdown
        this.autoSyncTask = autoSyncTask
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        return startJob(
            RunningMarker { stopped = false },
            executor,
            Runnable { autoSyncTask.run(params) },
        )
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Signal cooperative cancellation; the in-flight run observes `stopped`, aborts
        // at the next safe point, and calls jobFinished(needsReschedule=stopped) itself.
        // Returning true asks the system to reschedule the stopped job.
        stopped = true
        return stopJob()
    }

    override fun onDestroy() {
        destroyJob(shutdown)
        super.onDestroy()
    }

    private fun runAutoSync(params: JobParameters?) {
        // Pass a live supplier (not the captured value) so the sync sees a mid-run stop
        // and the reschedule flag is evaluated against the stopped state at completion.
        runAutoSync(this, params, SyncCancellation { stopped }, jobFinisherFactory.create(this))
    }

    fun interface SettingsReader {
        fun autoSyncSettings(): LocalStoreBase.AutoSyncSettings
    }

    fun interface StoreCloser {
        fun close()
    }

    fun interface Scheduler {
        fun schedule(context: Context?, settings: LocalStoreBase.AutoSyncSettings)
    }

    fun interface JobFinisher {
        fun jobFinished(params: JobParameters?, needsReschedule: Boolean)
    }

    fun interface RunningMarker {
        fun markRunning()
    }

    fun interface JobExecutor {
        fun execute(job: Runnable)
    }

    fun interface Shutdown {
        fun shutdownNow()
    }

    fun interface AutoSyncTask {
        fun run(params: JobParameters?)
    }

    fun interface JobFinisherFactory {
        fun create(service: AutoSyncJobService): JobFinisher
    }

    companion object {
        @JvmField
        var jobFinisherFactory: JobFinisherFactory = JobFinisherFactory { service ->
            JobFinisher { params, needsReschedule -> service.jobFinished(params, needsReschedule) }
        }

        @JvmStatic
        fun runAutoSync(
            context: Context,
            params: JobParameters?,
            cancellation: SyncCancellation,
            finisher: JobFinisher,
        ) {
            val store = LocalStore(context)
            try {
                AutoSyncRunner(context, store, AnkiDroidGateway(context, cancellation)).run()
            } finally {
                // Evaluate stopped at completion time so a job stopped mid-sync requests
                // a reschedule with the fresh state, not the pre-sync value.
                finishJob(
                    context,
                    params,
                    cancellation.isStopped(),
                    SettingsReader { store.autoSyncSettings() },
                    StoreCloser { store.close() },
                    Scheduler { appContext, settings ->
                        appContext ?: return@Scheduler
                        AutoSyncScheduler.schedule(appContext, store, settings)
                    },
                    finisher,
                )
            }
        }

        @JvmStatic
        fun startJob(runningMarker: RunningMarker, executor: JobExecutor, job: Runnable): Boolean {
            runningMarker.markRunning()
            executor.execute(job)
            return true
        }

        @JvmStatic
        fun stopJob(): Boolean {
            return true
        }

        @JvmStatic
        fun destroyJob(shutdown: Shutdown) {
            shutdown.shutdownNow()
        }

        @JvmStatic
        fun finishJob(
            context: Context?,
            params: JobParameters?,
            stopped: Boolean,
            settingsReader: SettingsReader,
            storeCloser: StoreCloser,
            scheduler: Scheduler,
            finisher: JobFinisher,
        ) {
            try {
                val settings = settingsReader.autoSyncSettings()
                if (settings.enabled) {
                    scheduler.schedule(context, settings)
                }
            } finally {
                storeCloser.close()
                finisher.jobFinished(params, stopped)
            }
        }
    }
}
