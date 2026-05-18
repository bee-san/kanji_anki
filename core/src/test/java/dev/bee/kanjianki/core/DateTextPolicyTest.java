package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DateTextPolicyTest {
    @Test
    public void formatsDeterministicRelativeDurations() {
        long now = 1_000_000L;

        assertEquals("date unknown", DateTextPolicy.humanSyncTime(0L, now));
        assertEquals("due now", DateTextPolicy.dueText(now, now));
        assertEquals("due in 1 min", DateTextPolicy.dueText(now + 59_000L, now));
        assertEquals("due in 2 min", DateTextPolicy.dueText(now + 120_000L, now));
        assertEquals("due in 3 hr", DateTextPolicy.dueText(now + 3_600_000L * 3L, now));
        assertEquals("Unknown time", DateTextPolicy.timelineDate(0L));
        assertEquals("not yet", DateTextPolicy.autoUpdateLastCheckText(0L));
    }

    @Test
    public void formatsOlderDueDatesAndNonZeroAutoUpdateChecks() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = 0L;
            long later = 3L * 24L * 3_600_000L;

            assertEquals("due Jan 4, 1970", DateTextPolicy.dueText(later, now));
            assertTrue(DateTextPolicy.timelineDate(later).contains("Jan 4, 1970"));
            assertTrue(DateTextPolicy.autoUpdateLastCheckText(later).contains("Jan 4, 1970"));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void formatsTodayYesterdayAndOlderSyncTimes() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar now = utcCalendar(2026, Calendar.MAY, 15, 12, 0);
            Calendar today = utcCalendar(2026, Calendar.MAY, 15, 9, 30);
            Calendar yesterday = utcCalendar(2026, Calendar.MAY, 14, 9, 30);
            Calendar older = utcCalendar(2026, Calendar.MAY, 12, 9, 30);

            assertTrue(DateTextPolicy.humanSyncTime(today.getTimeInMillis(), now.getTimeInMillis()).startsWith("today at "));
            assertTrue(DateTextPolicy.humanSyncTime(yesterday.getTimeInMillis(), now.getTimeInMillis()).startsWith("yesterday at "));
            assertFalse(DateTextPolicy.humanSyncTime(older.getTimeInMillis(), now.getTimeInMillis()).startsWith("today at "));
            assertFalse(DateTextPolicy.humanSyncTime(older.getTimeInMillis(), now.getTimeInMillis()).startsWith("yesterday at "));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void computesLocalDayBoundaries() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 15, 13, 45, 30);
        calendar.set(Calendar.MILLISECOND, 987);
        long now = calendar.getTimeInMillis();

        long nextDayStart = DateTextPolicy.nextLocalDayStart(now);
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(nextDayStart);

        assertTrue(DateTextPolicy.sameLocalDay(now, now + 1L));
        assertFalse(DateTextPolicy.sameLocalDay(now, nextDayStart));
        assertEquals(0, next.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, next.get(Calendar.MINUTE));
        assertEquals(0, next.get(Calendar.SECOND));
        assertEquals(0, next.get(Calendar.MILLISECOND));
    }

    @Test
    public void sameLocalDayRejectsDifferentEraAndYear() {
        Calendar ad = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        ad.clear();
        ad.set(Calendar.ERA, GregorianCalendar.AD);
        ad.set(Calendar.YEAR, 2026);
        ad.set(Calendar.DAY_OF_YEAR, 1);
        Calendar nextYear = (Calendar) ad.clone();
        nextYear.add(Calendar.YEAR, 1);
        Calendar bc = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        bc.clear();
        bc.set(Calendar.ERA, GregorianCalendar.BC);
        bc.set(Calendar.YEAR, 1);
        bc.set(Calendar.DAY_OF_YEAR, 1);

        assertFalse(DateTextPolicy.sameLocalDay(ad.getTimeInMillis(), nextYear.getTimeInMillis()));
        assertFalse(DateTextPolicy.sameLocalDay(ad.getTimeInMillis(), bc.getTimeInMillis()));
    }

    private static Calendar utcCalendar(int year, int month, int day, int hour, int minute) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar;
    }
}
