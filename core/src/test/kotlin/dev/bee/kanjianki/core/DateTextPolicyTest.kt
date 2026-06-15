package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class DateTextPolicyTest {
    @Test
    fun formatsDeterministicRelativeDurations() {
        val now = 1_000_000L

        assertEquals("date unknown", DateTextPolicy.humanSyncTime(0L))
        assertEquals("date unknown", DateTextPolicy.humanSyncTime(0L, now))
        assertEquals("due now", DateTextPolicy.dueText(now, now))
        assertEquals("due in 1 min", DateTextPolicy.dueText(now + 59_000L, now))
        assertEquals("due in 2 min", DateTextPolicy.dueText(now + 120_000L, now))
        assertEquals("due in 3 hr", DateTextPolicy.dueText(now + 3_600_000L * 3L, now))
        assertEquals("Unknown time", DateTextPolicy.timelineDate(0L))
        assertEquals("not yet", DateTextPolicy.autoUpdateLastCheckText(0L))
    }

    @Test
    fun formatsOlderDueDatesAndNonZeroAutoUpdateChecks() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = 0L
            val later = 3L * 24L * 3_600_000L

            assertEquals("due Jan 4, 1970", DateTextPolicy.dueText(later, now))
            assertTrue(DateTextPolicy.timelineDate(later).contains("Jan 4, 1970"))
            assertTrue(DateTextPolicy.autoUpdateLastCheckText(later).contains("Jan 4, 1970"))
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun formatsTodayYesterdayAndOlderSyncTimes() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utcCalendar(2026, Calendar.MAY, 15, 12, 0)
            val today = utcCalendar(2026, Calendar.MAY, 15, 9, 30)
            val yesterday = utcCalendar(2026, Calendar.MAY, 14, 9, 30)
            val older = utcCalendar(2026, Calendar.MAY, 12, 9, 30)

            assertTrue(DateTextPolicy.humanSyncTime(today.timeInMillis, now.timeInMillis).startsWith("today at "))
            assertTrue(DateTextPolicy.humanSyncTime(yesterday.timeInMillis, now.timeInMillis).startsWith("yesterday at "))
            assertFalse(DateTextPolicy.humanSyncTime(older.timeInMillis, now.timeInMillis).startsWith("today at "))
            assertFalse(DateTextPolicy.humanSyncTime(older.timeInMillis, now.timeInMillis).startsWith("yesterday at "))
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun japaneseLocaleLocalizesRelativeStatusCopy() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            val now = 1_000_000L

            assertEquals("日付不明", DateTextPolicy.humanSyncTime(0L, now))
            assertEquals("今すぐ復習", DateTextPolicy.dueText(now, now))
            assertEquals("1分後に復習", DateTextPolicy.dueText(now + 59_000L, now))
            assertEquals("2分後に復習", DateTextPolicy.dueText(now + 120_000L, now))
            assertEquals("3時間後に復習", DateTextPolicy.dueText(now + 3_600_000L * 3L, now))
            assertEquals("時刻不明", DateTextPolicy.timelineDate(0L))
            assertEquals("未確認", DateTextPolicy.autoUpdateLastCheckText(0L))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun japaneseLocaleLocalizesDueAndSyncDatePrefixes() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utcCalendar(2026, Calendar.MAY, 15, 12, 0)
            val today = utcCalendar(2026, Calendar.MAY, 15, 9, 30)
            val yesterday = utcCalendar(2026, Calendar.MAY, 14, 9, 30)
            val later = 3L * 24L * 3_600_000L

            assertTrue(DateTextPolicy.humanSyncTime(today.timeInMillis, now.timeInMillis).startsWith("今日 "))
            assertTrue(DateTextPolicy.humanSyncTime(yesterday.timeInMillis, now.timeInMillis).startsWith("昨日 "))
            assertTrue(DateTextPolicy.dueText(later, 0L).startsWith("期限 "))
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun computesLocalDayBoundaries() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.MAY, 15, 13, 45, 30)
        calendar.set(Calendar.MILLISECOND, 987)
        val now = calendar.timeInMillis

        val nextDayStart = DateTextPolicy.nextLocalDayStart(now)
        val next = Calendar.getInstance()
        next.timeInMillis = nextDayStart

        assertTrue(DateTextPolicy.sameLocalDay(now, now + 1L))
        assertFalse(DateTextPolicy.sameLocalDay(now, nextDayStart))
        assertEquals(0, next.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, next.get(Calendar.MINUTE))
        assertEquals(0, next.get(Calendar.SECOND))
        assertEquals(0, next.get(Calendar.MILLISECOND))
    }

    @Test
    fun sameLocalDayRejectsDifferentEraAndYear() {
        val ad = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        ad.clear()
        ad[Calendar.ERA] = GregorianCalendar.AD
        ad[Calendar.YEAR] = 2026
        ad[Calendar.DAY_OF_YEAR] = 1
        val nextYear = ad.clone() as Calendar
        nextYear.add(Calendar.YEAR, 1)
        val bc = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        bc.clear()
        bc[Calendar.ERA] = GregorianCalendar.BC
        bc[Calendar.YEAR] = 1
        bc[Calendar.DAY_OF_YEAR] = 1

        assertFalse(DateTextPolicy.sameLocalDay(ad.timeInMillis, nextYear.timeInMillis))
        assertFalse(DateTextPolicy.sameLocalDay(ad.timeInMillis, bc.timeInMillis))
    }
}

private fun utcCalendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
    calendar.clear()
    calendar.set(year, month, day, hour, minute, 0)
    return calendar
}
