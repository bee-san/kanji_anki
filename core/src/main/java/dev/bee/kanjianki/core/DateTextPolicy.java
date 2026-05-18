package dev.bee.kanjianki.core;

import java.text.DateFormat;
import java.util.Date;

public final class DateTextPolicy {
    private DateTextPolicy() {
    }

    public static String humanSyncTime(long timestampMillis) {
        return humanSyncTime(timestampMillis, System.currentTimeMillis());
    }

    public static String humanSyncTime(long timestampMillis, long nowMillis) {
        if (timestampMillis <= 0L) {
            return "date unknown";
        }
        Date date = new Date(timestampMillis);
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        if (LocalDayPolicy.sameLocalDay(timestampMillis, nowMillis)) {
            return "today at " + timeFormat.format(date);
        }
        long yesterday = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -1);
        if (LocalDayPolicy.sameLocalDay(timestampMillis, yesterday)) {
            return "yesterday at " + timeFormat.format(date);
        }
        return shortDateTime(timestampMillis);
    }

    public static String dueText(long dueAt, long now) {
        if (dueAt <= now) {
            return "due now";
        }
        long delta = dueAt - now;
        long minutes = Math.max(1L, delta / 60_000L);
        if (minutes < 60L) {
            return "due in " + minutes + " min";
        }
        long hours = Math.max(1L, delta / 3_600_000L);
        if (hours < 24L) {
            return "due in " + hours + " hr";
        }
        return "due " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(dueAt));
    }

    public static String timelineDate(long occurredAt) {
        if (occurredAt <= 0L) {
            return "Unknown time";
        }
        return shortDateTime(occurredAt);
    }

    public static boolean sameLocalDay(long leftMillis, long rightMillis) {
        return LocalDayPolicy.sameLocalDay(leftMillis, rightMillis);
    }

    public static long nextLocalDayStart(long nowMillis) {
        return LocalDayPolicy.nextLocalDayStart(nowMillis);
    }

    public static String shortDateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    public static String autoUpdateLastCheckText(long lastCheckAtMillis) {
        return lastCheckAtMillis <= 0L ? "not yet" : shortDateTime(lastCheckAtMillis);
    }
}
