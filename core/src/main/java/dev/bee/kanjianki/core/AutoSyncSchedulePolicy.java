package dev.bee.kanjianki.core;

import java.util.Calendar;

public final class AutoSyncSchedulePolicy {
    public static final long MIN_DELAY_MILLIS = 10_000L;
    public static final long DEADLINE_WINDOW_MILLIS = 6L * 60L * 60L * 1000L;

    private AutoSyncSchedulePolicy() {
    }

    public static SchedulePlan plan(
            boolean enabled,
            int hour,
            int minute,
            long nowMillis,
            boolean alreadySyncedToday
    ) {
        if (!enabled) {
            return SchedulePlan.disabled();
        }
        long triggerAt = nextTriggerMillis(hour, minute, nowMillis, alreadySyncedToday);
        long minimumLatency = Math.max(MIN_DELAY_MILLIS, triggerAt - nowMillis);
        return planWithLatency(triggerAt, minimumLatency);
    }

    public static SchedulePlan planAt(long triggerAtMillis, long nowMillis) {
        return planWithLatency(triggerAtMillis, Math.max(MIN_DELAY_MILLIS, triggerAtMillis - nowMillis));
    }

    public static long nextTriggerMillis(int hour, int minute, long nowMillis) {
        return nextTriggerMillis(hour, minute, nowMillis, false);
    }

    public static long nextTriggerMillis(int hour, int minute, long nowMillis, boolean alreadySyncedToday) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long trigger = calendar.getTimeInMillis();
        if (trigger <= nowMillis || alreadySyncedToday) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            trigger = calendar.getTimeInMillis();
        }
        return trigger;
    }

    public static long localDayStart(long nowMillis) {
        return LocalDayPolicy.localDayStart(nowMillis);
    }

    private static SchedulePlan planWithLatency(long triggerAtMillis, long minimumLatencyMillis) {
        return new SchedulePlan(
                true,
                triggerAtMillis,
                minimumLatencyMillis,
                minimumLatencyMillis + DEADLINE_WINDOW_MILLIS
        );
    }

    public record SchedulePlan(
            boolean enabled,
            long triggerAtMillis,
            long minimumLatencyMillis,
            long overrideDeadlineMillis
    ) {
        static SchedulePlan disabled() {
            return new SchedulePlan(false, 0L, 0L, 0L);
        }
    }
}
