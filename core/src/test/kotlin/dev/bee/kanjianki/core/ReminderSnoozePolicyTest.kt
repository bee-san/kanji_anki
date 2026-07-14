package dev.bee.kanjianki.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSnoozePolicyTest {
    @Test
    fun rearmTimeIsSixtyMinutesLater() {
        val now = calendarMillis(2026, 7, 14, 14, 0)
        val rearm = ReminderSnoozePolicy.rearmTime(now, 22 * 60, 8 * 60)
        val expected = calendarMillis(2026, 7, 14, 15, 0)
        assertEquals(expected, rearm)
    }

    @Test
    fun rearmShiftedPastQuietHours() {
        val now = calendarMillis(2026, 7, 14, 21, 30)
        val rearm = ReminderSnoozePolicy.rearmTime(now, 22 * 60, 8 * 60)
        val expected = calendarMillis(2026, 7, 15, 8, 0)
        assertTrue(rearm >= expected)
    }

    @Test
    fun noQuietHoursNoShift() {
        val now = calendarMillis(2026, 7, 14, 21, 30)
        val rearm = ReminderSnoozePolicy.rearmTime(now, 8 * 60, 8 * 60)
        val expected = calendarMillis(2026, 7, 14, 22, 30)
        assertEquals(expected, rearm)
    }

    @Test
    fun repostAllowedUnderDailyLimit() {
        assertTrue(ReminderSnoozePolicy.isRepostAllowed(0, 2))
        assertTrue(ReminderSnoozePolicy.isRepostAllowed(1, 2))
    }

    @Test
    fun repostDeniedAtDailyLimit() {
        assertFalse(ReminderSnoozePolicy.isRepostAllowed(2, 2))
        assertFalse(ReminderSnoozePolicy.isRepostAllowed(3, 2))
    }

    @Test
    fun repostNeverMintsExtraBudget() {
        assertFalse(ReminderSnoozePolicy.isRepostAllowed(2, ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY))
    }

    private fun calendarMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
