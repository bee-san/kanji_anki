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

    @Test
    fun normalizeActiveQueueCapClampsToBounds() {
        assertEquals(DeckLimitsSettingsPolicy.MIN_ACTIVE_QUEUE_CAP, DeckLimitsSettingsPolicy.normalizeActiveQueueCap(1))
        assertEquals(24, DeckLimitsSettingsPolicy.normalizeActiveQueueCap(24))
        assertEquals(DeckLimitsSettingsPolicy.MAX_ACTIVE_QUEUE_CAP, DeckLimitsSettingsPolicy.normalizeActiveQueueCap(10000))
    }

    @Test
    fun saveActiveQueueCapNormalizesAndReportsSavedValue() {
        val request = DeckLimitsSettingsPolicy.saveActiveQueueCap(" 40 ")

        assertEquals(40, request.newPerDay)
        assertEquals("Active queue cap saved: 40", request.message)
    }
}
