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
            if (!store.autoUpdateStatus().enabled) {
                cancel(appContext);
                return;
            }
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoUpdateWorker.class,
                1,
                TimeUnit.DAYS,
                6,
                TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK_NAME);
    }
}
