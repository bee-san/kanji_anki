package dev.bee.kanjianki.domain.model.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncSettingsTest {
    @Test
    fun fromStoredClampsInvalidLegacyValues() {
        val settings = AutoSyncSettings.fromStored(
            configured = false,
            enabled = true,
            hour = 99,
            minute = -4,
            lastAttemptAtMillis = -1L,
            lastSuccessAtMillis = -2L,
            nextRunAtMillis = -3L,
        )

        assertFalse(settings.configured)
        assertFalse(settings.enabled)
        assertEquals(23, settings.hour)
        assertEquals(0, settings.minute)
        assertEquals(0L, settings.lastAttemptAtMillis)
        assertEquals(0L, settings.lastSuccessAtMillis)
        assertEquals(0L, settings.nextRunAtMillis)
    }

    @Test
    fun configuredEnabledSchedulePreservesValidValues() {
        val settings = AutoSyncSettings.fromStored(
            configured = true,
            enabled = true,
            hour = 7,
            minute = 5,
            lastAttemptAtMillis = 10L,
            lastSuccessAtMillis = 20L,
            nextRunAtMillis = 30L,
        )

        assertTrue(settings.configured)
        assertTrue(settings.enabled)
        assertEquals(7, settings.hour)
        assertEquals(5, settings.minute)
        assertEquals(10L, settings.lastAttemptAtMillis)
        assertEquals(20L, settings.lastSuccessAtMillis)
        assertEquals(30L, settings.nextRunAtMillis)
        assertEquals("07:05", settings.displayTime())
    }
}
