package dev.bee.kanjianki.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.time.AppClock;

import java.util.Calendar;

public final class AutoSyncScheduler {
    private static final String TAG = "AutoSyncScheduler";
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
        schedule(context, store, settings, AppClock.system());
    }

    public static void schedule(Context context, LocalStore store, LocalStore.AutoSyncSettings settings, AppClock clock) {
        schedule(context, store, settings, new AndroidSchedulerBackend(context), clock);
    }

    static void schedule(Context context, LocalStore store, LocalStore.AutoSyncSettings settings, SchedulerBackend backend) {
        schedule(context, store, settings, backend, AppClock.system());
    }

    static void schedule(Context context, LocalStore store, LocalStore.AutoSyncSettings settings, SchedulerBackend backend, AppClock clock) {
        long now = AppClock.orSystem(clock).nowMillis();
        scheduleWithState(
                settings,
                now,
                store.hasSuccessfulSyncSince(localDayStart(now)),
                store::markAutoSyncScheduled,
                backend);
    }

    static void scheduleWithState(
            LocalStore.AutoSyncSettings settings,
            long now,
            boolean alreadySyncedToday,
            ScheduleRecorder recorder,
            SchedulerBackend backend) {
        if (settings == null || !settings.enabled) {
            backend.cancel();
            recorder.markAutoSyncScheduled(0L);
            return;
        }
        long triggerAt = nextTriggerMillis(settings, now, alreadySyncedToday);
        scheduleAt(recorder, backend, triggerAt, now);
    }

    public static void cancel(Context context) {
        SchedulerBackend backend = new AndroidSchedulerBackend(context);
        backend.cancel();
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

    static void scheduleAt(ScheduleRecorder recorder, SchedulerBackend backend, long triggerAt, long now) {
        long delay = Math.max(MIN_DELAY_MILLIS, triggerAt - now);
        try {
            boolean scheduled = backend.schedule(delay, delay + DEADLINE_WINDOW_MILLIS);
            recorder.markAutoSyncScheduled(scheduled ? triggerAt : 0L);
        } catch (RuntimeException error) {
            warn("Failed to schedule automatic sync job.", error);
            recorder.markAutoSyncScheduled(0L);
        }
    }

    interface ScheduleRecorder {
        void markAutoSyncScheduled(long nextRunAt);
    }

    interface SchedulerBackend {
        boolean schedule(long minimumLatencyMillis, long overrideDeadlineMillis);

        void cancel();
    }

    private static final class AndroidSchedulerBackend implements SchedulerBackend {
        private final Context context;

        AndroidSchedulerBackend(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public boolean schedule(long minimumLatencyMillis, long overrideDeadlineMillis) {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return false;
            }
            JobInfo job = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(context, AutoSyncJobService.class)
            )
                    .setMinimumLatency(minimumLatencyMillis)
                    .setOverrideDeadline(overrideDeadlineMillis)
                    .setPersisted(true)
                    .build();
            return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
        }

        @Override
        public void cancel() {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.cancel(JOB_ID);
            }
        }
    }

    private static void warn(String message, Throwable error) {
        try {
            Log.w(TAG, message, error);
        } catch (RuntimeException ignored) {
            // Android Log is unavailable in local JVM tests.
        }
    }
}
