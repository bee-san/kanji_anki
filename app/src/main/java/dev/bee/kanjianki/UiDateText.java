package dev.bee.kanjianki;

import java.text.DateFormat;
import java.util.Date;

import dev.bee.kanjianki.core.LocalDayPolicy;

final class UiDateText {
    private UiDateText() {
    }

    static String humanSyncTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return "date unknown";
        }
        Date date = new Date(timestampMillis);
        long now = System.currentTimeMillis();
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        if (sameLocalDay(timestampMillis, now)) {
            return "today at " + timeFormat.format(date);
        }
        long yesterday = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(now), -1);
        if (sameLocalDay(timestampMillis, yesterday)) {
            return "yesterday at " + timeFormat.format(date);
        }
        return shortDateTime(timestampMillis);
    }

    static String dueText(long dueAt, long now) {
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

    static String timelineDate(long occurredAt) {
        if (occurredAt <= 0L) {
            return "Unknown time";
        }
        return shortDateTime(occurredAt);
    }

    static boolean sameLocalDay(long leftMillis, long rightMillis) {
        return LocalDayPolicy.sameLocalDay(leftMillis, rightMillis);
    }

    static long nextLocalDayStart(long nowMillis) {
        return LocalDayPolicy.nextLocalDayStart(nowMillis);
    }

    static String shortDateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    static String autoUpdateLastCheckText(long lastCheckAtMillis) {
        return lastCheckAtMillis <= 0L ? "not yet" : shortDateTime(lastCheckAtMillis);
    }
}
