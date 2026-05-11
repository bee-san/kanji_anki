package dev.bee.kanjianki.backup;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class DatabaseBackupScheduler {
    private static final String UNIQUE_WORK_NAME = "kani_daily_db_backup";

    private DatabaseBackupScheduler() {
    }

    public static void schedule(Context context) {
        Context appContext = context.getApplicationContext();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DatabaseBackupWorker.class,
                1,
                TimeUnit.DAYS,
                6,
                TimeUnit.HOURS
        ).build();
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
