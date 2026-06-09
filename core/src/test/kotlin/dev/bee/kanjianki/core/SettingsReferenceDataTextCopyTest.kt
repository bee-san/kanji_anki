package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReferenceDataTextCopyTest {
    @Test
    fun referenceDataHelpersPreserveFormatting() {
        assertEquals("Jiten rank range", SettingsReferenceDataTextCopy.frequencyRangeTitle())
        assertEquals(
            "Import ranks 100-3000 by default.",
            SettingsReferenceDataTextCopy.frequencyRangeBody(),
        )
        assertEquals("Most frequent rank", SettingsReferenceDataTextCopy.minRankLabel())
        assertEquals("Least frequent rank", SettingsReferenceDataTextCopy.maxRankLabel())
        assertEquals("Most frequent", SettingsReferenceDataTextCopy.minimumRankLabel())
        assertEquals("Least frequent", SettingsReferenceDataTextCopy.maximumRankLabel())
        assertEquals("Save rank range", SettingsReferenceDataTextCopy.saveFrequencyRangeLabel())
        assertEquals("Enter rank numbers.", SettingsReferenceDataTextCopy.numericRanksToast())
        assertEquals("Use ranks 1-20000.", SettingsReferenceDataTextCopy.rankRangeToast())
        assertEquals(
            "Ranks saved. Sync to refresh practice.",
            SettingsReferenceDataTextCopy.frequencyRangeSavedToast(),
        )
        assertEquals("Offline data licenses", SettingsReferenceDataTextCopy.offlineDataLicensesTitle())
        assertEquals(
            "View dictionary, stroke, and font credits.",
            SettingsReferenceDataTextCopy.offlineDataLicensesBody(),
        )
        assertEquals("View licenses", SettingsReferenceDataTextCopy.openDataLicensesLabel())
        assertEquals("Data licenses", SettingsReferenceDataTextCopy.dataLicensesTitle())
        assertEquals(
            "Bundled dictionary, stroke, and font credits.",
            SettingsReferenceDataTextCopy.dataLicensesBody(),
        )
        assertEquals("Dictionary data", SettingsReferenceDataTextCopy.dictionaryDataTitle())
        assertEquals("Stroke data", SettingsReferenceDataTextCopy.strokeDataTitle())
        assertEquals("Fonts", SettingsReferenceDataTextCopy.fontsTitle())
    }
}
