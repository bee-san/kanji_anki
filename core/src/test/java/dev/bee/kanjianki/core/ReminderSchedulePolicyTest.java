package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public final class ReminderSchedulePolicyTest {
    @Test
    public void nextTriggerUsesTodayWhenReminderTimeIsStillAhead() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = utc(2026, Calendar.MAY, 15, 7, 15);

            long trigger = ReminderSchedulePolicy.nextTriggerMillis(8, 30, now);

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), trigger);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void nextTriggerMovesToTomorrowWhenReminderTimeHasPassedOrMatchesNow() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = utc(2026, Calendar.MAY, 15, 8, 30);

            assertEquals(
                    utc(2026, Calendar.MAY, 16, 8, 30),
                    ReminderSchedulePolicy.nextTriggerMillis(8, 30, now)
            );
            assertEquals(
                    utc(2026, Calendar.MAY, 16, 7, 0),
                    ReminderSchedulePolicy.nextTriggerMillis(7, 0, now)
            );
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(year, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
