package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderSchedulePolicyTest {
    @Test
    fun nextTriggerUsesTodayWhenReminderTimeIsStillAhead() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 7, 15)

            val trigger = ReminderSchedulePolicy.nextTriggerMillis(8, 30, now)

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), trigger)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun nextTriggerMovesToTomorrowWhenReminderTimeHasPassedOrMatchesNow() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 8, 30)

            assertEquals(
                utc(2026, Calendar.MAY, 16, 8, 30),
                ReminderSchedulePolicy.nextTriggerMillis(8, 30, now)
            )
            assertEquals(
                utc(2026, Calendar.MAY, 16, 7, 0),
                ReminderSchedulePolicy.nextTriggerMillis(7, 0, now)
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun nextTriggerCanSkipTodayWhenNeededForDailyReminderReschedule() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = utc(2026, Calendar.MAY, 15, 7, 15)

            val trigger = ReminderSchedulePolicy.nextTriggerMillis(8, 30, now, false)

            assertEquals(utc(2026, Calendar.MAY, 16, 8, 30), trigger)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun adaptiveTriggerUsesStudyHistoryBeforeCutoffAndFallsBackAfterCutoff() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            assertEquals(
                utc(2026, Calendar.MAY, 15, 8, 30),
                ReminderSchedulePolicy.nextTriggerMillis(
                    8,
                    30,
                    utc(2026, Calendar.MAY, 15, 7, 15),
                    false,
                    listOf(
                        utc(2026, Calendar.MAY, 15, 12, 0),
                        utc(2026, Calendar.MAY, 15, 14, 0),
                    ),
                ),
            )
            assertEquals(
                utc(2026, Calendar.MAY, 15, 14, 0),
                ReminderSchedulePolicy.nextTriggerMillis(
                    8,
                    30,
                    utc(2026, Calendar.MAY, 15, 9, 0),
                    true,
                    listOf(
                        utc(2026, Calendar.MAY, 15, 12, 0),
                        utc(2026, Calendar.MAY, 15, 14, 0),
                    ),
                ),
            )
            assertEquals(
                utc(2026, Calendar.MAY, 16, 8, 30),
                ReminderSchedulePolicy.nextTriggerMillis(
                    8,
                    30,
                    utc(2026, Calendar.MAY, 15, 21, 0),
                    true,
                    listOf(utc(2026, Calendar.MAY, 15, 23, 0)),
                ),
            )
            assertEquals(
                utc(2026, Calendar.MAY, 16, 8, 30),
                ReminderSchedulePolicy.nextTriggerMillis(
                    8,
                    30,
                    utc(2026, Calendar.MAY, 15, 22, 15),
                    true,
                    listOf(utc(2026, Calendar.MAY, 15, 23, 0)),
                ),
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
