package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoUpdateSchedulePolicyTest {
    @Test
    fun disabledPlanCancelsDailyAutoUpdateWork() {
        val plan = AutoUpdateSchedulePolicy.plan(false)

        assertFalse(plan.enabled())
        assertEquals("kani_daily_auto_updates", plan.uniqueWorkName())
        assertEquals(TimeUnit.DAYS.toMillis(1), plan.intervalMillis())
        assertEquals(TimeUnit.HOURS.toMillis(6), plan.flexMillis())
    }

    @Test
    fun enabledPlanSchedulesDailyNetworkConstrainedWork() {
        val plan = AutoUpdateSchedulePolicy.plan(true)

        assertTrue(plan.enabled())
        assertEquals("kani_daily_auto_updates", plan.uniqueWorkName())
        assertTrue(plan.requiresConnectedNetwork())
        assertEquals(TimeUnit.DAYS.toMillis(1), plan.intervalMillis())
        assertEquals(TimeUnit.HOURS.toMillis(6), plan.flexMillis())
    }
}
