package dev.bee.kanjianki.sync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.data.LocalStore;

import java.util.concurrent.Executors;

public final class AutoSyncJobService extends JobService {
    private static JobFinisherFactory jobFinisherFactory = service -> service::jobFinished;

    private final JobExecutor executor;
    private final Shutdown shutdown;
    private final AutoSyncTask autoSyncTask;
    private volatile boolean stopped;

    public AutoSyncJobService() {
        java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();
        executor = io::execute;
        shutdown = io::shutdownNow;
        autoSyncTask = this::runAutoSync;
    }

    AutoSyncJobService(JobExecutor executor, Shutdown shutdown, AutoSyncTask autoSyncTask) {
        this.executor = executor;
        this.shutdown = shutdown;
        this.autoSyncTask = autoSyncTask;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        return startJob(() -> stopped = false, executor, () -> autoSyncTask.run(params));
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return stopJob(() -> stopped = true);
    }

    @Override
    public void onDestroy() {
        destroyJob(shutdown);
        super.onDestroy();
    }

    private void runAutoSync(JobParameters params) {
        runAutoSync(this, params, stopped, jobFinisherFactory.create(this));
    }

    static void runAutoSync(Context context, JobParameters params, boolean stopped, JobFinisher finisher) {
        LocalStore store = new LocalStore(context);
        try {
            new AutoSyncRunner(context, store, new AnkiDroidGateway(context)).run();
        } finally {
            finishJob(
                    context,
                    params,
                    stopped,
                    store::autoSyncSettings,
                    store::close,
                    (appContext, settings) -> AutoSyncScheduler.schedule(appContext, store, settings),
                    finisher);
        }
    }

    static boolean startJob(RunningMarker runningMarker, JobExecutor executor, Runnable job) {
        runningMarker.markRunning();
        executor.execute(job);
        return true;
    }

    static boolean stopJob(StopMarker stopMarker) {
        stopMarker.markStopped();
        return true;
    }

    static void destroyJob(Shutdown shutdown) {
        shutdown.shutdownNow();
    }

    static void finishJob(
            Context context,
            JobParameters params,
            boolean stopped,
            SettingsReader settingsReader,
            StoreCloser storeCloser,
            Scheduler scheduler,
            JobFinisher finisher) {
        try {
            LocalStore.AutoSyncSettings settings = settingsReader.autoSyncSettings();
            if (settings.enabled) {
                scheduler.schedule(context, settings);
            }
        } finally {
            storeCloser.close();
            finisher.jobFinished(params, stopped);
        }
    }

    interface SettingsReader {
        LocalStore.AutoSyncSettings autoSyncSettings();
    }

    interface StoreCloser {
        void close();
    }

    interface Scheduler {
        void schedule(Context context, LocalStore.AutoSyncSettings settings);
    }

    interface JobFinisher {
        void jobFinished(JobParameters params, boolean needsReschedule);
    }

    interface RunningMarker {
        void markRunning();
    }

    interface StopMarker {
        void markStopped();
    }

    interface JobExecutor {
        void execute(Runnable job);
    }

    interface Shutdown {
        void shutdownNow();
    }

    interface AutoSyncTask {
        void run(JobParameters params);
    }

    interface JobFinisherFactory {
        JobFinisher create(AutoSyncJobService service);
    }
}
