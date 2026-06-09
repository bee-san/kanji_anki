package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReferenceDataTextCopyTest {
    @Test
    fun referenceDataHelpersPreserveFormatting() {
        assertEquals("Suspended card range", SettingsReferenceDataTextCopy.frequencyRangeTitle())
        assertEquals(
            "Set suspended-card ranks, then sync.",
            SettingsReferenceDataTextCopy.frequencyRangeBody(),
        )
        assertEquals("Min rank", SettingsReferenceDataTextCopy.minRankLabel())
        assertEquals("Max rank", SettingsReferenceDataTextCopy.maxRankLabel())
        assertEquals("Minimum rank", SettingsReferenceDataTextCopy.minimumRankLabel())
        assertEquals("Maximum rank", SettingsReferenceDataTextCopy.maximumRankLabel())
        assertEquals("Save rank range", SettingsReferenceDataTextCopy.saveFrequencyRangeLabel())
        assertEquals("Use numbers for ranks.", SettingsReferenceDataTextCopy.numericRanksToast())
        assertEquals("Use ranks 1-20000.", SettingsReferenceDataTextCopy.rankRangeToast())
        assertEquals(
            "Saved. Sync to refresh.",
            SettingsReferenceDataTextCopy.frequencyRangeSavedToast(),
        )
        assertEquals("Offline data licenses", SettingsReferenceDataTextCopy.offlineDataLicensesTitle())
        assertEquals(
            "Dictionary, stroke, and font credits.",
            SettingsReferenceDataTextCopy.offlineDataLicensesBody(),
        )
        assertEquals("Open data licenses", SettingsReferenceDataTextCopy.openDataLicensesLabel())
        assertEquals("Data licenses", SettingsReferenceDataTextCopy.dataLicensesTitle())
        assertEquals(
            "Dictionary, stroke, and font credits.",
            SettingsReferenceDataTextCopy.dataLicensesBody(),
        )
        assertEquals("Dictionary data", SettingsReferenceDataTextCopy.dictionaryDataTitle())
        assertEquals("Stroke data", SettingsReferenceDataTextCopy.strokeDataTitle())
        assertEquals("Fonts", SettingsReferenceDataTextCopy.fontsTitle())
    }
}
