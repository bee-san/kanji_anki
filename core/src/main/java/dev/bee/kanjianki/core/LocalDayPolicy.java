package dev.bee.kanjianki.core;

import java.util.Calendar;

public final class LocalDayPolicy {
    private LocalDayPolicy() {
    }

    public static long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        clearTimeOfDay(calendar);
        return calendar.getTimeInMillis();
    }

    public static long moveLocalDays(long localDayStart, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localDayStart);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        clearTimeOfDay(calendar);
        return calendar.getTimeInMillis();
    }

    public static long nextLocalDayStart(long millis) {
        return moveLocalDays(localDayStart(millis), 1);
    }

    public static boolean sameLocalDay(long leftMillis, long rightMillis) {
        Calendar left = Calendar.getInstance();
        left.setTimeInMillis(leftMillis);
        Calendar right = Calendar.getInstance();
        right.setTimeInMillis(rightMillis);
        return left.get(Calendar.ERA) == right.get(Calendar.ERA)
                && left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private static void clearTimeOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
