package dev.bee.kanjianki.sync

import dev.bee.kanjianki.AppLocalStoreFactory

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.bee.kanjianki.requireKaniContainer
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import java.util.concurrent.ConcurrentHashMap

class AutoSyncJobService : JobService {
    private val executor: JobExecutor
    private val shutdown: Shutdown
    private val autoSyncTask: AutoSyncTask
    private val activeRuns = ConcurrentHashMap<Int, JobRun>()

    constructor() {
        executor = JobExecutor { job ->
            requireKaniContainer().maintenanceExecutor.execute(job)
        }
        shutdown = Shutdown { }
        autoSyncTask = AutoSyncTask { run -> runAutoSync(run) }
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
        val run = JobRun(params)
        return startJob(
            RunningMarker {
                activeRuns.put(run.key, run)?.markStopped()
            },
            executor,
            Runnable {
                var taskReturned = false
                try {
                    autoSyncTask.run(run)
                    taskReturned = true
                } finally {
                    // Keep a throwing task registered until JobScheduler calls
                    // onStopJob (or the process dies), so the platform can retry it.
                    if (taskReturned && !run.hasPendingCompletion()) {
                        activeRuns.remove(run.key, run)
                    }
                }
            },
        )
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        val run = activeRuns[jobKey(params?.jobId)]?.takeIf { it.matches(params) }
            ?: return stopJob(false)
        // If durable replacement/retry work already finished scheduling, that work
        // owns continuation. Otherwise JobScheduler must retry the interrupted job.
        return stopJob(run.markStoppedAndShouldReschedule())
    }

    override fun onDestroy() {
        activeRuns.values.forEach(JobRun::markStopped)
        destroyJob(shutdown)
        super.onDestroy()
    }

    private fun runAutoSync(run: JobRun) {
        val platformFinisher = jobFinisherFactory.create(this)
        val lifecycleSafeFinisher = JobFinisher { params, needsReschedule ->
            run.markCompletionPending(needsReschedule)
            val posted = Handler(Looper.getMainLooper()).post {
                try {
                    // onStopJob and this callback both run on the service main
                    // thread, so this is the final race-free lifecycle decision.
                    if (!run.isStopped()) {
                        platformFinisher.jobFinished(params, needsReschedule)
                    }
                } finally {
                    run.markCompletionDelivered()
                    activeRuns.remove(run.key, run)
                }
            }
            if (!posted) {
                run.markCompletionDelivered()
                activeRuns.remove(run.key, run)
            }
        }
        val container = requireKaniContainer()
        runAutoSync(
            this,
            run.params,
            SyncCancellation { run.isStopped() },
            run,
            lifecycleSafeFinisher,
            storeFactory = container::openLocalStore,
            gatewayFactory = container::newAnkiDroidGateway,
        )
    }

    fun interface SettingsReader {
        fun autoSyncSettings(): LocalStoreBase.AutoSyncSettings
    }

    fun interface StoreCloser {
        fun close()
    }

    fun interface Scheduler {
        fun schedule(
            context: Context?,
            settings: LocalStoreBase.AutoSyncSettings,
            currentJobId: Int?,
        ): Boolean
    }

    interface RetryScheduler {
        fun schedule(context: Context?)

        fun cancel(context: Context?)
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
        fun run(run: JobRun)
    }

    fun interface JobFinisherFactory {
        fun create(service: AutoSyncJobService): JobFinisher
    }

    fun interface CompletionGate {
        /** Starts [completion] only if JobScheduler has not already stopped this run. */
        fun complete(completion: Runnable): Boolean
    }

    class CompletionActions(
        internal val settingsReader: SettingsReader,
        internal val storeCloser: StoreCloser,
        internal val scheduler: Scheduler,
        internal val retryScheduler: RetryScheduler,
    )

    class JobRun(@JvmField val params: JobParameters?) : CompletionGate {
        @JvmField
        val jobId: Int? = params?.jobId

        internal val key: Int = jobKey(jobId)

        private val lifecycleLock = Any()
        private var stopped = false
        private var completionStarted = false
        private var completionPrepared = false
        private var completionPending = false
        private var completionRequiresReschedule = false

        fun markStopped() {
            markStoppedAndShouldReschedule()
        }

        fun markStoppedAndShouldReschedule(): Boolean {
            synchronized(lifecycleLock) {
                stopped = true
                return !completionPrepared || completionRequiresReschedule
            }
        }

        fun isStopped(): Boolean = synchronized(lifecycleLock) { stopped }

        fun matches(other: JobParameters?): Boolean = jobIdsMatch(jobId, other?.jobId)

        fun markCompletionPending(needsReschedule: Boolean) {
            synchronized(lifecycleLock) {
                completionPending = true
                completionRequiresReschedule = needsReschedule
            }
        }

        fun markCompletionDelivered() {
            synchronized(lifecycleLock) {
                completionPending = false
                completionRequiresReschedule = false
            }
        }

        fun hasPendingCompletion(): Boolean = synchronized(lifecycleLock) { completionPending }

        override fun complete(completion: Runnable): Boolean {
            synchronized(lifecycleLock) {
                if (stopped || completionStarted) {
                    return false
                }
                completionStarted = true
            }
            completion.run()
            synchronized(lifecycleLock) {
                completionPrepared = true
            }
            return true
        }
    }

    companion object {
        @JvmField
        var jobFinisherFactory: JobFinisherFactory = JobFinisherFactory { service ->
            JobFinisher { params, needsReschedule -> service.jobFinished(params, needsReschedule) }
        }

        @JvmStatic
        internal fun jobIdsMatch(expected: Int?, actual: Int?): Boolean = expected == actual

        @JvmStatic
        internal fun jobKey(jobId: Int?): Int = jobId ?: Int.MIN_VALUE

        @JvmStatic
        fun runAutoSync(
            context: Context,
            params: JobParameters?,
            cancellation: SyncCancellation,
            finisher: JobFinisher,
        ) {
            runAutoSync(
                context,
                params,
                cancellation,
                CompletionGate { completion ->
                    if (cancellation.isStopped()) {
                        false
                    } else {
                        completion.run()
                        true
                    }
                },
                finisher,
                storeFactory = { AppLocalStoreFactory.create(context) },
                gatewayFactory = { cancellation -> AnkiDroidGateway(context, cancellation) },
            )
        }

        private fun runAutoSync(
            context: Context,
            params: JobParameters?,
            cancellation: SyncCancellation,
            completionGate: CompletionGate,
            finisher: JobFinisher,
            storeFactory: () -> LocalStore,
            gatewayFactory: (SyncCancellation) -> AnkiDroidGateway,
        ) {
            val store = storeFactory()
            var result: AutoSyncRunner.Result? = null
            try {
                result = createAutoSyncRunner(
                    context,
                    store,
                    gatewayFactory(cancellation),
                    sourceBindingGate = AndroidSourceBindingGate(
                        dev.bee.kanjianki.data.SqliteSourceBindingStore(store),
                    ),
                ).run()
            } finally {
                finishJob(
                    context,
                    params,
                    result?.retryable == true,
                    completionGate,
                    CompletionActions(
                        SettingsReader { store.autoSyncSettings() },
                        StoreCloser { store.close() },
                        Scheduler { appContext, settings, currentJobId ->
                            appContext ?: return@Scheduler false
                            AutoSyncScheduler.scheduleNext(appContext, store, settings, currentJobId)
                        },
                        object : RetryScheduler {
                            override fun schedule(context: Context?) {
                                context?.let(AutoSyncRetryScheduler::scheduleAndAwait)
                            }

                            override fun cancel(context: Context?) {
                                context?.let(AutoSyncRetryScheduler::cancelAndAwait)
                            }
                        },
                    ),
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
        fun stopJob(shouldReschedule: Boolean): Boolean {
            return shouldReschedule
        }

        @JvmStatic
        fun destroyJob(shutdown: Shutdown) {
            shutdown.shutdownNow()
        }

        @JvmStatic
        fun finishJob(
            context: Context?,
            params: JobParameters?,
            retryable: Boolean,
            completionGate: CompletionGate,
            actions: CompletionActions,
            finisher: JobFinisher,
        ) {
            val completed = completionGate.complete {
                var needsReschedule = false
                try {
                    val settings = actions.settingsReader.autoSyncSettings()
                    if (settings.enabled) {
                        // Use the alternate ID so persisting tomorrow's job does not
                        // stop the currently executing JobScheduler entry.
                        needsReschedule = !actions.scheduler.schedule(context, settings, params?.jobId)
                    }
                    if (settings.enabled && retryable) {
                        actions.retryScheduler.schedule(context)
                    } else {
                        actions.retryScheduler.cancel(context)
                    }
                } catch (error: Exception) {
                    needsReschedule = true
                    warn("Could not persist automatic sync continuation.", error)
                }
                try {
                    actions.storeCloser.close()
                } catch (error: Exception) {
                    needsReschedule = true
                    warn("Could not close the automatic sync store.", error)
                }
                // WorkManager persistence is confirmed before releasing the
                // current job's component lifetime. If continuation setup failed
                // or timed out, ask JobScheduler for prompt recovery instead.
                finisher.jobFinished(params, needsReschedule)
            }
            if (!completed) {
                // onStopJob returning true owns the interrupted-run reschedule and
                // JobService explicitly forbids a subsequent jobFinished call.
                actions.storeCloser.close()
            }
        }

        private fun warn(message: String, error: Throwable) {
            try {
                Log.w("AutoSyncJobService", message, error)
            } catch (_: RuntimeException) {
                // Android Log is unavailable in local JVM tests.
            }
        }
    }
}
