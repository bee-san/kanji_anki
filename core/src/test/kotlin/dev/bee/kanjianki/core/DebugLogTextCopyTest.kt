package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTextCopyTest {
    @Test
    fun debugLogCopyPreservesEnglishText() {
        assertEquals("Debug log", DebugLogTextCopy.debugLogTitle())
        assertEquals("Recording", DebugLogTextCopy.debugLogStatus(true))
        assertEquals("Off", DebugLogTextCopy.debugLogStatus(false))
        assertEquals("Turn off debug log", DebugLogTextCopy.debugLogToggleLabel(true))
        assertEquals("Turn on debug log", DebugLogTextCopy.debugLogToggleLabel(false))
        assertEquals("Share debug log", DebugLogTextCopy.shareDebugLogLabel())
        assertEquals("Share debug log", DebugLogTextCopy.shareDebugLogChooserTitle())
        assertEquals(
            "No debug log captured yet. Turn it on, reproduce the problem, then share.",
            DebugLogTextCopy.debugLogEmptyToast(),
        )
    }

    @Test
    fun debugLogDetailDependsOnState() {
        val enabledDetail = DebugLogTextCopy.debugLogDetail(true)
        val disabledDetail = DebugLogTextCopy.debugLogDetail(false)

        assertTrue(enabledDetail.startsWith("Recording timestamped app activity"))
        assertTrue(disabledDetail.startsWith("Records timestamped app activity"))
        assertNotEquals(enabledDetail, disabledDetail)
    }

    @Test
    fun settingsTextCopyFacadeDelegatesDebugLogCopy() {
        assertEquals(DebugLogTextCopy.debugLogTitle(), SettingsTextCopy.debugLogTitle())
        assertEquals(DebugLogTextCopy.debugLogStatus(true), SettingsTextCopy.debugLogStatus(true))
        assertEquals(DebugLogTextCopy.debugLogDetail(false), SettingsTextCopy.debugLogDetail(false))
        assertEquals(DebugLogTextCopy.debugLogToggleLabel(true), SettingsTextCopy.debugLogToggleLabel(true))
        assertEquals(DebugLogTextCopy.shareDebugLogLabel(), SettingsTextCopy.shareDebugLogLabel())
        assertEquals(DebugLogTextCopy.debugLogEmptyToast(), SettingsTextCopy.debugLogEmptyToast())
        assertEquals(DebugLogTextCopy.shareDebugLogChooserTitle(), SettingsTextCopy.shareDebugLogChooserTitle())
    }

    @Test
    fun debugLogCopyLocalizesInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            assertEquals("デバッグログ", DebugLogTextCopy.debugLogTitle())
            assertEquals("記録中", DebugLogTextCopy.debugLogStatus(true))
            assertEquals("オフ", DebugLogTextCopy.debugLogStatus(false))
            assertEquals("デバッグログをオフにする", DebugLogTextCopy.debugLogToggleLabel(true))
            assertEquals("デバッグログをオンにする", DebugLogTextCopy.debugLogToggleLabel(false))
            assertEquals("デバッグログを共有", DebugLogTextCopy.shareDebugLogLabel())
            assertTrue(DebugLogTextCopy.debugLogDetail(true).contains("記録中"))
            assertTrue(DebugLogTextCopy.debugLogEmptyToast().contains("デバッグログ"))
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
