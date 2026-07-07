package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogSettingsTogglePolicyTest {
    @Test
    fun enablePreservesEnabledFlagAndCopy() {
        val result = DebugLogSettingsTogglePolicy.enable()

        assertTrue(result.enabled)
        assertEquals("Debug log turned on.", result.message)
        assertTrue(DebugLogSettingsTogglePolicy.ToggleResult::class.java.isRecord)
        assertEquals("ToggleResult[enabled=true, message=Debug log turned on.]", result.toString())
    }

    @Test
    fun disablePreservesDisabledFlagAndCopy() {
        val result = DebugLogSettingsTogglePolicy.disable()

        assertFalse(result.enabled)
        assertEquals("Debug log turned off.", result.message)
    }

    @Test
    fun toggleCopyLocalizesInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val enabled = DebugLogSettingsTogglePolicy.enable()
            val disabled = DebugLogSettingsTogglePolicy.disable()

            assertTrue(enabled.enabled)
            assertEquals("デバッグログをオンにしました。", enabled.message)
            assertFalse(disabled.enabled)
            assertEquals("デバッグログをオフにしました。", disabled.message)
        }
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
