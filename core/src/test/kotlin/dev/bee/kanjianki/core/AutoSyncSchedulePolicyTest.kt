package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AutoSyncSchedulePolicyTest {
    @Test
    fun disabledPlanClearsSchedulingFields() {
        val plan = AutoSyncSchedulePolicy.plan(false, 8, 30, utc(2026, Calendar.MAY, 15, 7, 0), false)

        assertFalse(plan.enabled)
        assertEquals(0L, plan.triggerAtMillis)
        assertEquals(0L, plan.minimumLatencyMillis)
        assertEquals(0L, plan.overrideDeadlineMillis)
        assertTrue(AutoSyncSchedulePolicy.SchedulePlan::class.java.isRecord())
        assertEquals(
            "SchedulePlan[enabled=false, triggerAtMillis=0, minimumLatencyMillis=0, overrideDeadlineMillis=0]",
            plan.toString(),
        )
        assertEquals(plan, AutoSyncSchedulePolicy.SchedulePlan.disabled())
    }

    @Test
    fun enabledPlanUsesTodayWhenTimeIsAhead() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 7, 45)
            val plan = AutoSyncSchedulePolicy.plan(true, 8, 30, now, false)

            assertTrue(plan.enabled)
            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), plan.triggerAtMillis)
            assertEquals(45L * 60L * 1000L, plan.minimumLatencyMillis)
            assertEquals(
                plan.minimumLatencyMillis + AutoSyncSchedulePolicy.DEADLINE_WINDOW_MILLIS,
                plan.overrideDeadlineMillis,
            )
        }
    }

    @Test
    fun enabledPlanUsesTomorrowWhenTimePassedOrAlreadySyncedToday() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 30)

            assertEquals(
                utc(2026, Calendar.MAY, 16, 8, 30),
                AutoSyncSchedulePolicy.nextTriggerMillis(8, 30, now),
            )
            assertEquals(
                utc(2026, Calendar.MAY, 16, 8, 30),
                AutoSyncSchedulePolicy.nextTriggerMillis(8, 30, utc(2026, Calendar.MAY, 15, 7, 45), true),
            )
        }
    }

    @Test
    fun planAppliesMinimumDelayForNearFutureTriggers() {
        withUtcZone {
            val trigger = utc(2026, Calendar.MAY, 15, 8, 29)
            val plan = AutoSyncSchedulePolicy.planAt(trigger, trigger - 1_000L)

            assertEquals(trigger, plan.triggerAtMillis)
            assertEquals(AutoSyncSchedulePolicy.MIN_DELAY_MILLIS, plan.minimumLatencyMillis)
            assertEquals(
                AutoSyncSchedulePolicy.MIN_DELAY_MILLIS + AutoSyncSchedulePolicy.DEADLINE_WINDOW_MILLIS,
                plan.overrideDeadlineMillis,
            )
        }
    }

    @Test
    fun planAtSaturatesLatencyAndDeadlineAtLongMaximum() {
        val plan = AutoSyncSchedulePolicy.planAt(Long.MAX_VALUE, Long.MIN_VALUE)

        assertEquals(Long.MAX_VALUE, plan.minimumLatencyMillis)
        assertEquals(Long.MAX_VALUE, plan.overrideDeadlineMillis)
    }

    @Test
    fun localDayStartUsesCurrentTimeZoneMidnight() {
        withUtcZone {
            assertEquals(
                utc(2026, Calendar.MAY, 15, 0, 0),
                AutoSyncSchedulePolicy.localDayStart(utc(2026, Calendar.MAY, 15, 23, 59)),
            )
        }
    }

    private fun withUtcZone(body: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            body()
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
