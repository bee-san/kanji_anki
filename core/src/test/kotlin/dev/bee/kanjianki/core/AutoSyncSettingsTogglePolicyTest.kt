package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncSettingsTogglePolicyTest {
    @Test
    fun enablePreservesEnabledFlagAndCopy() {
        val result = AutoSyncSettingsTogglePolicy.enable()

        assertTrue(result.enabled)
        assertEquals("Daily Anki sync turned on.", result.message)
        assertTrue(AutoSyncSettingsTogglePolicy.ToggleResult::class.java.isRecord)
        assertEquals("ToggleResult[enabled=true, message=Daily Anki sync turned on.]", result.toString())
    }

    @Test
    fun disablePreservesDisabledFlagAndCopy() {
        val result = AutoSyncSettingsTogglePolicy.disable()

        assertFalse(result.enabled)
        assertEquals("Daily Anki sync turned off.", result.message)
    }
}
