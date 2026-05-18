package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public final class LocalDayPolicyTest {
    @Test
    public void localDayStartUsesCurrentTimeZoneMidnight() {
        withUtcZone(() -> assertEquals(
                utc(2026, Calendar.MAY, 15, 0, 0),
                LocalDayPolicy.localDayStart(utc(2026, Calendar.MAY, 15, 23, 59))
        ));
    }

    @Test
    public void moveLocalDaysKeepsResultAtLocalMidnight() {
        withUtcZone(() -> assertEquals(
                utc(2026, Calendar.MAY, 17, 0, 0),
                LocalDayPolicy.moveLocalDays(utc(2026, Calendar.MAY, 15, 18, 30), 2)
        ));
    }

    private static void withUtcZone(Runnable body) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            body.run();
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
