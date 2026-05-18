package dev.bee.kanjianki;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UiDateTextTest {
    @Test
    public void wrapperDelegatesDateTextFormatting() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = 1_000_000L;
            long later = 3L * 24L * 3_600_000L;

            assertEquals("date unknown", UiDateText.humanSyncTime(0L));
            assertEquals("due now", UiDateText.dueText(now, now));
            assertEquals("due in 1 min", UiDateText.dueText(now + 59_000L, now));
            assertEquals("due Jan 4, 1970", UiDateText.dueText(later, 0L));
            assertEquals("Unknown time", UiDateText.timelineDate(0L));
            assertTrue(UiDateText.timelineDate(later).contains("Jan 4, 1970"));
            assertTrue(UiDateText.shortDateTime(later).contains("Jan 4, 1970"));
            assertEquals("not yet", UiDateText.autoUpdateLastCheckText(0L));
            assertTrue(UiDateText.autoUpdateLastCheckText(later).contains("Jan 4, 1970"));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void wrapperDelegatesLocalDayHelpers() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        calendar.clear();
        calendar.set(2026, Calendar.MAY, 15, 13, 45, 30);
        calendar.set(Calendar.MILLISECOND, 987);
        long now = calendar.getTimeInMillis();
        long nextDayStart = UiDateText.nextLocalDayStart(now);

        assertTrue(UiDateText.sameLocalDay(now, now + 1L));
        assertFalse(UiDateText.sameLocalDay(now, nextDayStart));
    }
}
