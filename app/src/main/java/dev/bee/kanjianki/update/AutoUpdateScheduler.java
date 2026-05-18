package dev.bee.kanjianki.update;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.updatecore.AutoUpdateSchedulePolicy;

import java.util.concurrent.TimeUnit;

public final class AutoUpdateScheduler {
    private AutoUpdateScheduler() {
    }

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        try (LocalStore store = new LocalStore(appContext)) {
            schedule(store.autoUpdateStatus().enabled, new WorkManagerSchedulerBackend(appContext));
        }
    }

    static void schedule(boolean enabled, SchedulerBackend backend) {
        AutoUpdateSchedulePolicy.SchedulePlan plan = AutoUpdateSchedulePolicy.plan(enabled);
        if (!plan.enabled()) {
            backend.cancelUniqueWork(plan.uniqueWorkName());
            return;
        }
        backend.enqueueUniquePeriodicWork(
                plan.uniqueWorkName(),
                ExistingPeriodicWorkPolicy.KEEP,
                dailyUpdateRequest(plan)
        );
    }

    private static PeriodicWorkRequest dailyUpdateRequest(AutoUpdateSchedulePolicy.SchedulePlan plan) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(plan.requiresConnectedNetwork() ? NetworkType.CONNECTED : NetworkType.NOT_REQUIRED)
                .build();
        return new PeriodicWorkRequest.Builder(
                AutoUpdateWorker.class,
                plan.intervalMillis(),
                TimeUnit.MILLISECONDS,
                plan.flexMillis(),
                TimeUnit.MILLISECONDS
        )
                .setConstraints(constraints)
                .build();
    }

    public static void cancel(Context context) {
        new WorkManagerSchedulerBackend(context.getApplicationContext()).cancelUniqueWork(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME);
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
