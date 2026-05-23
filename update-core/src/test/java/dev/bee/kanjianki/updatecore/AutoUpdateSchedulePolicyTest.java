package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoUpdateSchedulePolicyTest {
    @Test
    public void disabledPlanCancelsDailyAutoUpdateWork() {
        AutoUpdateSchedulePolicy.SchedulePlan plan = AutoUpdateSchedulePolicy.plan(false);

        assertFalse(plan.enabled());
        assertEquals("kani_daily_auto_updates", plan.uniqueWorkName());
        assertEquals(TimeUnit.DAYS.toMillis(1), plan.intervalMillis());
        assertEquals(TimeUnit.HOURS.toMillis(6), plan.flexMillis());
    }

    @Test
    public void enabledPlanSchedulesDailyNetworkConstrainedWork() {
        AutoUpdateSchedulePolicy.SchedulePlan plan = AutoUpdateSchedulePolicy.plan(true);

        assertTrue(plan.enabled());
        assertEquals("kani_daily_auto_updates", plan.uniqueWorkName());
        assertTrue(plan.requiresConnectedNetwork());
        assertEquals(TimeUnit.DAYS.toMillis(1), plan.intervalMillis());
        assertEquals(TimeUnit.HOURS.toMillis(6), plan.flexMillis());
    }
}
