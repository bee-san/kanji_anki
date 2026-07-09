package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAntiSpamPolicyTest {
    @Test
    fun normalizesInvalidMinuteOfDayToFallback() {
        assertEquals(
            ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE,
            ReminderAntiSpamPolicy.normalizeMinuteOfDay(-1, ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE),
        )
        assertEquals(
            ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE,
            ReminderAntiSpamPolicy.normalizeMinuteOfDay(24 * 60, ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE),
        )
        assertEquals(9 * 60, ReminderAntiSpamPolicy.normalizeMinuteOfDay(9 * 60, 0))
    }

    @Test
    fun clampsMaxPerDayIntoRange() {
        assertEquals(ReminderAntiSpamPolicy.MIN_MAX_PER_DAY, ReminderAntiSpamPolicy.normalizeMaxPerDay(0))
        assertEquals(ReminderAntiSpamPolicy.MAX_MAX_PER_DAY, ReminderAntiSpamPolicy.normalizeMaxPerDay(9))
        assertEquals(2, ReminderAntiSpamPolicy.normalizeMaxPerDay(2))
    }

    @Test
    fun quietHoursActiveOnlyForRealSpans() {
        assertTrue(ReminderAntiSpamPolicy.quietHoursActive(22 * 60, 8 * 60))
        assertFalse(ReminderAntiSpamPolicy.quietHoursActive(22 * 60, 22 * 60))
        assertFalse(ReminderAntiSpamPolicy.quietHoursActive(-1, 8 * 60))
    }

    @Test
    fun quietLeadMinutesMeasuresDistanceToStart() {
        // now 21:00, quiet starts 22:00 -> 60 minutes of lead.
        assertEquals(60, ReminderAntiSpamPolicy.quietLeadMinutesUntilStart(21 * 60, 22 * 60, 8 * 60))
        // now 23:00, quiet starts 22:00 -> already inside; wraps to next day (23h).
        assertEquals(23 * 60, ReminderAntiSpamPolicy.quietLeadMinutesUntilStart(23 * 60, 22 * 60, 8 * 60))
    }

    @Test
    fun quietLeadIsNullForInactiveWindow() {
        assertNull(ReminderAntiSpamPolicy.quietLeadMinutesUntilStart(12 * 60, 22 * 60, 22 * 60))
    }

    @Test
    fun quietLeadAtExactStartRollsToFullDay() {
        assertEquals(24 * 60, ReminderAntiSpamPolicy.quietLeadMinutesUntilStart(22 * 60, 22 * 60, 8 * 60))
    }
}
