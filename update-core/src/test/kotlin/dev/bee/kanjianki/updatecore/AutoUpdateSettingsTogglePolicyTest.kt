package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateSettingsTogglePolicyTest {
    @Test
    fun toggleTurnsEnabledStatusOffWithExistingCopy() {
        val result = AutoUpdateSettingsTogglePolicy.toggle(true)

        assertFalse(result.enabled())
        assertEquals("Automatic updates turned off.", result.message())
    }

    @Test
    fun toggleTurnsDisabledStatusOnWithExistingCopy() {
        val result = AutoUpdateSettingsTogglePolicy.toggle(false)

        assertTrue(result.enabled())
        assertEquals("Automatic updates turned on.", result.message())
    }
}
