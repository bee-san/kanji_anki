package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncSettingsTogglePolicyTest {
    @Test
    fun enablePreservesEnabledFlagAndCopy() {
        val result = AutoSyncSettingsTogglePolicy.enable()

        assertTrue(result.enabled)
        assertEquals("Daily sync turned on.", result.message)
        assertTrue(AutoSyncSettingsTogglePolicy.ToggleResult::class.java.isRecord)
        assertEquals("ToggleResult[enabled=true, message=Daily sync turned on.]", result.toString())
    }

    @Test
    fun disablePreservesDisabledFlagAndCopy() {
        val result = AutoSyncSettingsTogglePolicy.disable()

        assertFalse(result.enabled)
        assertEquals("Daily sync turned off.", result.message)
    }

    @Test
    fun toggleCopyLocalizesInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val enabled = AutoSyncSettingsTogglePolicy.enable()
            val disabled = AutoSyncSettingsTogglePolicy.disable()

            assertTrue(enabled.enabled)
            assertEquals("毎日の同期をオンにしました。", enabled.message)
            assertFalse(disabled.enabled)
            assertEquals("毎日の同期をオフにしました。", disabled.message)
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
