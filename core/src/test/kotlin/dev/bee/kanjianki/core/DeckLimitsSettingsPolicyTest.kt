package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckLimitsSettingsPolicyTest {
    @Test
    fun normalizeNewPerDayKeepsAnkiStyleBoundedWholeNumber() {
        assertEquals(0, DeckLimitsSettingsPolicy.normalizeNewPerDay(-5))
        assertEquals(12, DeckLimitsSettingsPolicy.normalizeNewPerDay(12))
        assertEquals(999, DeckLimitsSettingsPolicy.normalizeNewPerDay(2000))
    }

    @Test
    fun saveRequestNormalizesInputTextAndReportsSavedValue() {
        val request = DeckLimitsSettingsPolicy.saveNewPerDay(" 42 ")

        assertEquals(42, request.newPerDay)
        assertEquals("New cards/day saved: 42", request.message)
    }

    @Test
    fun saveRequestFallsBackToDefaultForMalformedInput() {
        val request = DeckLimitsSettingsPolicy.saveNewPerDay("not a number", 24)

        assertEquals(24, request.newPerDay)
        assertEquals("New cards/day saved: 24", request.message)
    }
}
