package dev.bee.kanjianki;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

final class UiDateText {
    private UiDateText() {
    }

    static String humanSyncTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return "date unknown";
        }
        Date date = new Date(timestampMillis);
        Calendar then = Calendar.getInstance();
        then.setTime(date);
        Calendar now = Calendar.getInstance();
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        if (sameLocalDay(then, now)) {
            return "today at " + timeFormat.format(date);
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameLocalDay(then, now)) {
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
        Calendar left = Calendar.getInstance();
        left.setTimeInMillis(leftMillis);
        Calendar right = Calendar.getInstance();
        right.setTimeInMillis(rightMillis);
        return sameLocalDay(left, right);
    }

    static long nextLocalDayStart(long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    static String shortDateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    static String autoUpdateLastCheckText(long lastCheckAtMillis) {
        return lastCheckAtMillis <= 0L ? "not yet" : shortDateTime(lastCheckAtMillis);
    }

    private static boolean sameLocalDay(Calendar left, Calendar right) {
        return left.get(Calendar.ERA) == right.get(Calendar.ERA)
                && left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }
}
