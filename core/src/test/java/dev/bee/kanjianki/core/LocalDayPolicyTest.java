package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void nextLocalDayStartReturnsNextLocalMidnight() {
        withUtcZone(() -> assertEquals(
                utc(2026, Calendar.MAY, 16, 0, 0),
                LocalDayPolicy.nextLocalDayStart(utc(2026, Calendar.MAY, 15, 18, 30))
        ));
    }

    @Test
    public void sameLocalDayComparesCalendarDay() {
        withUtcZone(() -> {
            assertTrue(LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 0, 0),
                    utc(2026, Calendar.MAY, 15, 23, 59)
            ));
            assertFalse(LocalDayPolicy.sameLocalDay(
                    utc(2026, Calendar.MAY, 15, 23, 59),
                    utc(2026, Calendar.MAY, 16, 0, 0)
            ));
        });
    }

    @Test
    public void sameLocalDayRejectsDifferentEraAndYear() {
        withUtcZone(() -> {
            Calendar ad = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            ad.clear();
            ad.set(Calendar.ERA, GregorianCalendar.AD);
            ad.set(Calendar.YEAR, 2026);
            ad.set(Calendar.DAY_OF_YEAR, 1);
            Calendar nextYear = (Calendar) ad.clone();
            nextYear.add(Calendar.YEAR, 1);
            Calendar bc = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            bc.clear();
            bc.set(Calendar.ERA, GregorianCalendar.BC);
            bc.set(Calendar.YEAR, 1);
            bc.set(Calendar.DAY_OF_YEAR, 1);

            assertFalse(LocalDayPolicy.sameLocalDay(ad.getTimeInMillis(), nextYear.getTimeInMillis()));
            assertFalse(LocalDayPolicy.sameLocalDay(ad.getTimeInMillis(), bc.getTimeInMillis()));
        });
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
