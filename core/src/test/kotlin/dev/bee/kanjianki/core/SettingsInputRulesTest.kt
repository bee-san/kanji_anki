package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInputRulesTest {
    @Test
    fun importThresholdValidationPreservesSettingsBounds() {
        assertTrue(SettingsInputRules.validImportThresholds(1.0, 1, 1))
        assertTrue(SettingsInputRules.validImportThresholds(10.0, 100, 1000))
        assertTrue(SettingsInputRules.validImportThresholds(7.5, 3, 2))

        assertFalse(SettingsInputRules.validImportThresholds(0.999, 3, 2))
        assertFalse(SettingsInputRules.validImportThresholds(10.001, 3, 2))
        assertFalse(SettingsInputRules.validImportThresholds(Double.NaN, 3, 2))
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 0, 2))
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 101, 2))
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 3, 0))
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 3, 1001))
    }

    @Test
    fun rankSliderConversionPreservesClampedOneBasedRanks() {
        assertEquals(0, SettingsInputRules.rankSliderProgress(-20))
        assertEquals(0, SettingsInputRules.rankSliderProgress(1))
        assertEquals(9, SettingsInputRules.rankSliderProgress(10))
        assertEquals(19999, SettingsInputRules.rankSliderProgress(20000))
        assertEquals(19999, SettingsInputRules.rankSliderProgress(50_000))

        assertEquals(1, SettingsInputRules.rankFromSliderProgress(-4))
        assertEquals(1, SettingsInputRules.rankFromSliderProgress(0))
        assertEquals(10, SettingsInputRules.rankFromSliderProgress(9))
        assertEquals(20000, SettingsInputRules.rankFromSliderProgress(19999))
        assertEquals(20000, SettingsInputRules.rankFromSliderProgress(50_000))
    }

    @Test
    fun rankValidationAndRangeNormalizationPreserveFrequencyBounds() {
        assertFalse(SettingsInputRules.validRank(0))
        assertTrue(SettingsInputRules.validRank(1))
        assertTrue(SettingsInputRules.validRank(20000))
        assertFalse(SettingsInputRules.validRank(20001))

        assertEquals(SettingsInputRules.RankRange(20, 300), SettingsInputRules.normalizedRankRange(300, 20))
        assertEquals(SettingsInputRules.RankRange(1, 20000), SettingsInputRules.normalizedRankRange(-50, 50_000))
        assertEquals(SettingsInputRules.RankRange(1, 1), SettingsInputRules.normalizedRankRange(Int.MIN_VALUE, 0))
        assertEquals(SettingsInputRules.RankRange(20000, 20000), SettingsInputRules.normalizedRankRange(20_001, Int.MAX_VALUE))
        assertTrue(SettingsInputRules.RankRange::class.java.isRecord())
        assertEquals("RankRange[minRank=20, maxRank=300]", SettingsInputRules.normalizedRankRange(300, 20).toString())
    }

    @Test
    fun importSourceSelectionPreservesEnabledSourceRules() {
        val emptyList = emptyList<String>()
        val leeches = listOf("leeches")

        assertFalse(SettingsInputRules.hasSelectedImportSource(false, false, false, false, false, emptyList, ""))
        assertTrue(SettingsInputRules.hasSelectedImportSource(true, false, false, false, false, null, null))
        assertTrue(SettingsInputRules.hasSelectedImportSource(false, true, false, false, false, null, null))
        assertTrue(SettingsInputRules.hasSelectedImportSource(false, false, false, true, false, null, null))
        assertFalse(SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, emptyList, null))
        assertTrue(SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, leeches, null))
        assertFalse(SettingsInputRules.hasSelectedImportSource(false, false, false, false, true, emptyList, ""))
        assertTrue(SettingsInputRules.hasSelectedImportSource(false, false, false, false, true, emptyList, "deck:Kiku"))
        assertThrows(NullPointerException::class.java) { SettingsInputRules.hasSelectedImportSource(false, false, true, false, false, null, "") }
        assertThrows(NullPointerException::class.java) { SettingsInputRules.hasSelectedImportSource(false, false, false, false, true, emptyList, null) }
    }

    @Test
    fun retentionPercentPreservesSettingsClamp() {
        assertEquals(80, SettingsInputRules.retentionPercent(0.1))
        assertEquals(80, SettingsInputRules.retentionPercent(0.799))
        assertEquals(90, SettingsInputRules.retentionPercent(0.9))
        assertEquals(97, SettingsInputRules.retentionPercent(0.974))
        assertEquals(97, SettingsInputRules.retentionPercent(1.0))
        assertEquals(80, SettingsInputRules.retentionPercent(Double.NaN))
    }

    @Test
    fun studyAheadMinutesClampPreservesStoredBounds() {
        assertEquals(0, SettingsInputRules.normalizeStudyAheadMinutes(Int.MIN_VALUE))
        assertEquals(0, SettingsInputRules.normalizeStudyAheadMinutes(-5))
        assertEquals(0, SettingsInputRules.normalizeStudyAheadMinutes(0))
        assertEquals(15, SettingsInputRules.normalizeStudyAheadMinutes(15))
        assertEquals(1440, SettingsInputRules.normalizeStudyAheadMinutes(1440))
        assertEquals(1440, SettingsInputRules.normalizeStudyAheadMinutes(99_999))
    }
}
