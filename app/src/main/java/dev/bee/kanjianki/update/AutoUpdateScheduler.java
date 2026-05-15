package dev.bee.kanjianki.update;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import dev.bee.kanjianki.data.LocalStore;

import java.util.concurrent.TimeUnit;

public final class AutoUpdateScheduler {
    private static final String UNIQUE_WORK_NAME = "kani_daily_auto_updates";

    private AutoUpdateScheduler() {
    }

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        try (LocalStore store = new LocalStore(appContext)) {
            schedule(store.autoUpdateStatus().enabled, new WorkManagerSchedulerBackend(appContext));
        }
    }

    static void schedule(boolean enabled, SchedulerBackend backend) {
        if (!enabled) {
            backend.cancelUniqueWork(UNIQUE_WORK_NAME);
            return;
        }
        backend.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyUpdateRequest()
        );
    }

    private static PeriodicWorkRequest dailyUpdateRequest() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        return new PeriodicWorkRequest.Builder(
                AutoUpdateWorker.class,
                1,
                TimeUnit.DAYS,
                6,
                TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();
    }

    public static void cancel(Context context) {
        new WorkManagerSchedulerBackend(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    interface SchedulerBackend {
        void enqueueUniquePeriodicWork(String uniqueWorkName, ExistingPeriodicWorkPolicy policy, PeriodicWorkRequest request);

        void cancelUniqueWork(String uniqueWorkName);
    }

    private static final class WorkManagerSchedulerBackend implements SchedulerBackend {
        private final Context context;

        private WorkManagerSchedulerBackend(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void enqueueUniquePeriodicWork(String uniqueWorkName, ExistingPeriodicWorkPolicy policy, PeriodicWorkRequest request) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(uniqueWorkName, policy, request);
        }

        @Override
        public void cancelUniqueWork(String uniqueWorkName) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName);
        }
    }
}
