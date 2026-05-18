package dev.bee.kanjianki.core;

import java.util.Locale;

public final class TimeOfDaySettingsPolicy {
    public static final int DEFAULT_REMINDER_HOUR = 19;
    public static final int DEFAULT_REMINDER_MINUTE = 0;
    public static final int DEFAULT_AUTO_SYNC_HOUR = DEFAULT_REMINDER_HOUR;
    public static final int DEFAULT_AUTO_SYNC_MINUTE = DEFAULT_REMINDER_MINUTE;

    private static final int MIN_HOUR = 0;
    private static final int MAX_HOUR = 23;
    private static final int MIN_MINUTE = 0;
    private static final int MAX_MINUTE = 59;

    private TimeOfDaySettingsPolicy() {
    }

    public static ReminderFields normalizeReminder(boolean enabled, int hour, int minute) {
        return new ReminderFields(enabled, normalizeHour(hour), normalizeMinute(minute));
    }

    public static AutoSyncFields normalizeAutoSync(
            boolean configured,
            boolean enabled,
            int hour,
            int minute,
            long lastAttemptAtMillis,
            long lastSuccessAtMillis,
            long nextRunAtMillis
    ) {
        return new AutoSyncFields(
                configured,
                configured && enabled,
                normalizeHour(hour),
                normalizeMinute(minute),
                normalizeTimestampMillis(lastAttemptAtMillis),
                normalizeTimestampMillis(lastSuccessAtMillis),
                normalizeTimestampMillis(nextRunAtMillis)
        );
    }

    public static String displayTime(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private static int normalizeHour(int hour) {
        return Math.max(MIN_HOUR, Math.min(MAX_HOUR, hour));
    }

    private static int normalizeMinute(int minute) {
        return Math.max(MIN_MINUTE, Math.min(MAX_MINUTE, minute));
    }

    private static long normalizeTimestampMillis(long timestampMillis) {
        return Math.max(0L, timestampMillis);
    }

    public record ReminderFields(boolean enabled, int hour, int minute) {
    }

    public record AutoSyncFields(
            boolean configured,
            boolean enabled,
            int hour,
            int minute,
            long lastAttemptAtMillis,
            long lastSuccessAtMillis,
            long nextRunAtMillis
    ) {
    }
}
