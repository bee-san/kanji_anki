package dev.bee.kanjianki.updatecore

import java.util.Locale
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

    @Test
    fun toggleCopyLocalizesInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val enabled = AutoUpdateSettingsTogglePolicy.toggle(false)
            val disabled = AutoUpdateSettingsTogglePolicy.toggle(true)

            assertTrue(enabled.enabled())
            assertEquals("自動アップデートをオンにしました。", enabled.message())
            assertFalse(disabled.enabled())
            assertEquals("自動アップデートをオフにしました。", disabled.message())
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
