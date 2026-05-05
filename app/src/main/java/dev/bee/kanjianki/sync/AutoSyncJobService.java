package dev.bee.kanjianki.sync;

import android.app.job.JobParameters;
import android.app.job.JobService;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.data.LocalStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AutoSyncJobService extends JobService {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(JobParameters params) {
        stopped = false;
        io.execute(() -> {
            LocalStore store = new LocalStore(this);
            try {
                new AutoSyncRunner(this, store, new AnkiDroidGateway(this)).run();
            } finally {
                LocalStore.AutoSyncSettings settings = store.autoSyncSettings();
                if (settings.enabled) {
                    AutoSyncScheduler.schedule(this, store, settings);
                }
                store.close();
                jobFinished(params, stopped);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }

    @Override
    public void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
