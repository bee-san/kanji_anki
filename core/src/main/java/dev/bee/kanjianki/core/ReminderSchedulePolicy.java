package dev.bee.kanjianki.core;

import java.util.Calendar;

public final class ReminderSchedulePolicy {
    private ReminderSchedulePolicy() {
    }

    public static long nextTriggerMillis(int hour, int minute, long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long trigger = calendar.getTimeInMillis();
        if (trigger <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            trigger = calendar.getTimeInMillis();
        }
        return trigger;
    }
}
