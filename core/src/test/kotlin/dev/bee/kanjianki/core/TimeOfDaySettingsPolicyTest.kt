package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOfDaySettingsPolicyTest {
    @Test
    fun reminderDefaultsMatchExistingUserSchedule() {
        assertEquals(19, TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR)
        assertEquals(0, TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE)
        assertEquals(TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR, TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_HOUR)
        assertEquals(TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE, TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_MINUTE)
    }

    @Test
    fun reminderNormalizationClampsInvalidTimeOnly() {
        val early = TimeOfDaySettingsPolicy.normalizeReminder(true, -3, 90)
        val late = TimeOfDaySettingsPolicy.normalizeReminder(false, 30, -4)

        assertTrue(early.enabled)
        assertEquals(0, early.hour)
        assertEquals(59, early.minute)
        assertTrue(TimeOfDaySettingsPolicy.ReminderFields::class.java.isRecord)
        assertEquals("ReminderFields[enabled=true, hour=0, minute=59]", early.toString())
        assertFalse(late.enabled)
        assertEquals(23, late.hour)
        assertEquals(0, late.minute)
    }

    @Test
    fun autoSyncNormalizationDisablesUnconfiguredAndClampsState() {
        val autoSync = TimeOfDaySettingsPolicy.normalizeAutoSync(
            false,
            true,
            -2,
            70,
            -1L,
            -2L,
            -3L,
        )

        assertFalse(autoSync.configured)
        assertFalse(autoSync.enabled)
        assertEquals(0, autoSync.hour)
        assertEquals(59, autoSync.minute)
        assertEquals(0L, autoSync.lastAttemptAtMillis)
        assertEquals(0L, autoSync.lastSuccessAtMillis)
        assertEquals(0L, autoSync.nextRunAtMillis)
        assertTrue(TimeOfDaySettingsPolicy.AutoSyncFields::class.java.isRecord)
        assertEquals(
            "AutoSyncFields[configured=false, enabled=false, hour=0, minute=59, lastAttemptAtMillis=0, lastSuccessAtMillis=0, nextRunAtMillis=0]",
            autoSync.toString(),
        )
    }

    @Test
    fun autoSyncNormalizationPreservesConfiguredScheduleAndHistory() {
        val autoSync = TimeOfDaySettingsPolicy.normalizeAutoSync(
            true,
            true,
            25,
            -3,
            1L,
            2L,
            3L,
        )

        assertTrue(autoSync.configured)
        assertTrue(autoSync.enabled)
        assertEquals(23, autoSync.hour)
        assertEquals(0, autoSync.minute)
        assertEquals(1L, autoSync.lastAttemptAtMillis)
        assertEquals(2L, autoSync.lastSuccessAtMillis)
        assertEquals(3L, autoSync.nextRunAtMillis)
    }

    @Test
    fun displayTimeKeepsZeroPadding() {
        assertEquals("07:05", TimeOfDaySettingsPolicy.displayTime(7, 5))
        assertEquals("23:59", TimeOfDaySettingsPolicy.displayTime(23, 59))
    }
}