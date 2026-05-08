package dev.bee.kanjianki.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import dev.bee.kanjianki.data.LocalStore;

import java.util.Calendar;

public final class AutoSyncScheduler {
    private static final int JOB_ID = 3801;
    private static final long MIN_DELAY_MILLIS = 10_000L;
    private static final long DEADLINE_WINDOW_MILLIS = 6L * 60L * 60L * 1000L;

    private AutoSyncScheduler() {
    }

    public static void schedule(Context context) {
        try (LocalStore store = new LocalStore(context)) {
            schedule(context, store, store.autoSyncSettings());
        }
    }

    public static void schedule(Context context, LocalStore store, LocalStore.AutoSyncSettings settings) {
        if (settings == null || !settings.enabled) {
            cancelJob(context);
            store.markAutoSyncScheduled(0L);
            return;
        }
        long now = System.currentTimeMillis();
        long triggerAt = nextTriggerMillis(settings, now, store.hasSuccessfulSyncSince(localDayStart(now)));
        scheduleAt(context, store, triggerAt);
    }

    public static void cancel(Context context) {
        cancelJob(context);
        try (LocalStore store = new LocalStore(context)) {
            store.markAutoSyncScheduled(0L);
        }
    }

    static long nextTriggerMillis(LocalStore.AutoSyncSettings settings, long now) {
        return nextTriggerMillis(settings, now, false);
    }

    static long nextTriggerMillis(LocalStore.AutoSyncSettings settings, long now, boolean alreadySyncedToday) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, settings.hour);
        calendar.set(Calendar.MINUTE, settings.minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long trigger = calendar.getTimeInMillis();
        if (trigger <= now || alreadySyncedToday) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            trigger = calendar.getTimeInMillis();
        }
        return trigger;
    }

    private static long localDayStart(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static void scheduleAt(Context context, LocalStore store, long triggerAt) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            store.markAutoSyncScheduled(0L);
            return;
        }
        long delay = Math.max(MIN_DELAY_MILLIS, triggerAt - System.currentTimeMillis());
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context.getApplicationContext(), AutoSyncJobService.class)
        )
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + DEADLINE_WINDOW_MILLIS)
                .setPersisted(true)
                .build();
        try {
            int result = scheduler.schedule(job);
            store.markAutoSyncScheduled(result == JobScheduler.RESULT_SUCCESS ? triggerAt : 0L);
        } catch (RuntimeException ignored) {
            store.markAutoSyncScheduled(0L);
        }
    }

    private static void cancelJob(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }
}
