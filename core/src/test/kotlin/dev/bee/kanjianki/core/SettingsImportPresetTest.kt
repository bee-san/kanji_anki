package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsImportPresetTest {
    @Test
    fun defaultsPreserveImportPresetOrderAndLabels() {
        val presets = SettingsImportPreset.defaults()
        val firstPreset = presets[0]

        assertEquals(5, presets.size)
        assertEquals("Suspended only", presets[0].label())
        assertEquals("Kani tag", presets[1].label())
        assertEquals("Leech tag", presets[2].label())
        assertEquals("Mining deck", presets[3].label())
        assertEquals("Recent fails", presets[4].label())

        @Suppress("UNCHECKED_CAST")
        val mutablePresets = presets as MutableList<SettingsImportPreset>
        assertThrows(UnsupportedOperationException::class.java) {
            mutablePresets.add(firstPreset)
        }
    }

    @Test
    fun defaultsPreservePresetValues() {
        val suspended = SettingsImportPreset.defaults()[0]
        assertFalse(suspended.activeCards())
        assertTrue(suspended.suspendedCards())
        assertFalse(suspended.taggedCards())
        assertEquals("", suspended.tags())
        assertFalse(suspended.weakCards())
        assertEquals(7.0, suspended.weakDifficulty(), 0.0)
        assertEquals(2, suspended.weakLapses())
        assertEquals(1, suspended.minMatchingCards())
        assertFalse(suspended.browserQueryCards())
        assertEquals("", suspended.browserQuery())

        val leech = SettingsImportPreset.defaults()[2]
        assertFalse(leech.suspendedCards())
        assertTrue(leech.taggedCards())
        assertEquals("leech", leech.tags())
        assertFalse(leech.browserQueryCards())

        val mining = SettingsImportPreset.defaults()[3]
        assertFalse(mining.taggedCards())
        assertTrue(mining.browserQueryCards())
        assertEquals("deck:Mining", mining.browserQuery())
    }

    @Test
    fun boolFlagPreservesStoredBooleanEncoding() {
        assertEquals(1, SettingsImportPreset.boolFlag(true))
        assertEquals(0, SettingsImportPreset.boolFlag(false))
    }
}
