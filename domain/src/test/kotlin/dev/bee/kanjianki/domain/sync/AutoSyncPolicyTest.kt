package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.model.sync.AutoSyncSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AutoSyncPolicyTest {
    private val timeZone = TimeZone.getTimeZone("Europe/London")
    private val policy = AutoSyncPolicy { timeZone }

    @Test
    fun localDayStartUsesLocalCalendarDay() {
        val now = millis(2026, Calendar.MAY, 15, 9, 45)

        assertEquals(millis(2026, Calendar.MAY, 15, 0, 0), policy.localDayStartMillis(now))
    }

    @Test
    fun nextTriggerUsesTodayWhenConfiguredTimeIsStillAhead() {
        val now = millis(2026, Calendar.MAY, 15, 7, 45)
        val settings = AutoSyncSettings(configured = true, enabled = true, hour = 8, minute = 30)

        assertEquals(millis(2026, Calendar.MAY, 15, 8, 30), policy.nextTriggerMillis(settings, now))
    }

    @Test
    fun nextTriggerUsesTomorrowWhenConfiguredTimePassedOrAlreadySyncedToday() {
        val settings = AutoSyncSettings(configured = true, enabled = true, hour = 8, minute = 30)

        assertEquals(
            millis(2026, Calendar.MAY, 16, 8, 30),
            policy.nextTriggerMillis(settings, millis(2026, Calendar.MAY, 15, 9, 0)),
        )
        assertEquals(
            millis(2026, Calendar.MAY, 16, 8, 30),
            policy.nextTriggerMillis(
                settings,
                millis(2026, Calendar.MAY, 15, 7, 45),
                alreadySyncedToday = true,
            ),
        )
    }

    @Test
    fun defaultTimeZoneIsResolvedAtCallTime() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val policy = AutoSyncPolicy()
            val now = millis(TimeZone.getTimeZone("UTC"), 2026, Calendar.MAY, 15, 23, 30)

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            assertEquals(
                millis(TimeZone.getTimeZone("Asia/Tokyo"), 2026, Calendar.MAY, 16, 0, 0),
                policy.localDayStartMillis(now),
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun nextTriggerAdvancesByLocalDayAcrossDstBoundaries() {
        val settings = AutoSyncSettings(configured = true, enabled = true, hour = 3, minute = 30)
        val springForwardNow = millis(2026, Calendar.MARCH, 28, 4, 0)
        val fallBackNow = millis(2026, Calendar.OCTOBER, 24, 4, 0)

        assertEquals(
            millis(2026, Calendar.MARCH, 29, 3, 30),
            policy.nextTriggerMillis(settings, springForwardNow),
        )
        assertEquals(
            millis(2026, Calendar.OCTOBER, 25, 3, 30),
            policy.nextTriggerMillis(settings, fallBackNow),
        )
    }

    @Test
    fun skippedSpringForwardTimeDoesNotPoisonNextDayTriggerHour() {
        val settings = AutoSyncSettings(configured = true, enabled = true, hour = 1, minute = 30)
        val nowAfterSkippedTrigger = millis(2026, Calendar.MARCH, 29, 3, 0)

        assertEquals(
            millis(2026, Calendar.MARCH, 30, 1, 30),
            policy.nextTriggerMillis(settings, nowAfterSkippedTrigger),
        )
    }

    @Test
    fun scheduleWindowPreservesLegacyMinimumLatencyAndDeadline() {
        val nearFutureDelay = policy.minimumLatencyMillis(
            triggerAtMillis = millis(2026, Calendar.MAY, 15, 8, 0) + 1_000L,
            nowMillis = millis(2026, Calendar.MAY, 15, 8, 0),
        )
        val normalDelay = policy.minimumLatencyMillis(
            triggerAtMillis = millis(2026, Calendar.MAY, 15, 8, 30),
            nowMillis = millis(2026, Calendar.MAY, 15, 7, 45),
        )

        assertEquals(AutoSyncPolicy.MINIMUM_DELAY_MILLIS, nearFutureDelay)
        assertEquals(45L * 60L * 1000L, normalDelay)
        assertEquals(
            normalDelay + AutoSyncPolicy.DEADLINE_WINDOW_MILLIS,
            policy.overrideDeadlineMillis(normalDelay),
        )
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = millis(timeZone, year, month, day, hour, minute)

    private fun millis(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
