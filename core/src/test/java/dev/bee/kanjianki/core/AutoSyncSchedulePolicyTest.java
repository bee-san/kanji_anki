package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoSyncSchedulePolicyTest {
    @Test
    public void disabledPlanClearsSchedulingFields() {
        AutoSyncSchedulePolicy.SchedulePlan plan = AutoSyncSchedulePolicy.plan(false, 8, 30, utc(2026, Calendar.MAY, 15, 7, 0), false);

        assertFalse(plan.enabled());
        assertEquals(0L, plan.triggerAtMillis());
        assertEquals(0L, plan.minimumLatencyMillis());
        assertEquals(0L, plan.overrideDeadlineMillis());
        assertTrue(AutoSyncSchedulePolicy.SchedulePlan.class.isRecord());
        assertEquals(
                "SchedulePlan[enabled=false, triggerAtMillis=0, minimumLatencyMillis=0, overrideDeadlineMillis=0]",
                plan.toString()
        );
    }

    @Test
    public void enabledPlanUsesTodayWhenTimeIsAhead() {
        withUtcZone(() -> {
            long now = utc(2026, Calendar.MAY, 15, 7, 45);
            AutoSyncSchedulePolicy.SchedulePlan plan = AutoSyncSchedulePolicy.plan(true, 8, 30, now, false);

            assertTrue(plan.enabled());
            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), plan.triggerAtMillis());
            assertEquals(45L * 60L * 1000L, plan.minimumLatencyMillis());
            assertEquals(plan.minimumLatencyMillis() + AutoSyncSchedulePolicy.DEADLINE_WINDOW_MILLIS, plan.overrideDeadlineMillis());
        });
    }

    @Test
    public void enabledPlanUsesTomorrowWhenTimePassedOrAlreadySyncedToday() {
        withUtcZone(() -> {
            long now = utc(2026, Calendar.MAY, 15, 8, 30);

            assertEquals(
                    utc(2026, Calendar.MAY, 16, 8, 30),
                    AutoSyncSchedulePolicy.nextTriggerMillis(8, 30, now)
            );
            assertEquals(
                    utc(2026, Calendar.MAY, 16, 8, 30),
                    AutoSyncSchedulePolicy.nextTriggerMillis(8, 30, utc(2026, Calendar.MAY, 15, 7, 45), true)
            );
        });
    }

    @Test
    public void planAppliesMinimumDelayForNearFutureTriggers() {
        withUtcZone(() -> {
            long trigger = utc(2026, Calendar.MAY, 15, 8, 29);
            AutoSyncSchedulePolicy.SchedulePlan plan = AutoSyncSchedulePolicy.planAt(trigger, trigger - 1_000L);

            assertEquals(trigger, plan.triggerAtMillis());
            assertEquals(AutoSyncSchedulePolicy.MIN_DELAY_MILLIS, plan.minimumLatencyMillis());
            assertEquals(
                    AutoSyncSchedulePolicy.MIN_DELAY_MILLIS + AutoSyncSchedulePolicy.DEADLINE_WINDOW_MILLIS,
                    plan.overrideDeadlineMillis()
            );
        });
    }

    @Test
    public void localDayStartUsesCurrentTimeZoneMidnight() {
        withUtcZone(() -> assertEquals(
                utc(2026, Calendar.MAY, 15, 0, 0),
                AutoSyncSchedulePolicy.localDayStart(utc(2026, Calendar.MAY, 15, 23, 59))
        ));
    }

    private static void withUtcZone(Runnable body) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            body.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(year, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
