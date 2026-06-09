package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReferenceDataTextCopyTest {
    @Test
    fun referenceDataHelpersPreserveFormatting() {
        assertEquals("Jiten rank range", SettingsReferenceDataTextCopy.frequencyRangeTitle())
        assertEquals(
            "Import kanji within these Jiten ranks. Default: 100-3000.",
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
            "Rank range saved. Sync to refresh practice.",
            SettingsReferenceDataTextCopy.frequencyRangeSavedToast(),
        )
        assertEquals("Offline data licenses", SettingsReferenceDataTextCopy.offlineDataLicensesTitle())
        assertEquals(
            "Review KANJIDIC2, Jiten, KanjiVG, and font credits.",
            SettingsReferenceDataTextCopy.offlineDataLicensesBody(),
        )
        assertEquals("Open data licenses", SettingsReferenceDataTextCopy.openDataLicensesLabel())
        assertEquals("Data licenses", SettingsReferenceDataTextCopy.dataLicensesTitle())
        assertEquals(
            "Bundled dictionaries, stroke data, and fonts.",
            SettingsReferenceDataTextCopy.dataLicensesBody(),
        )
        assertEquals("Dictionary data", SettingsReferenceDataTextCopy.dictionaryDataTitle())
        assertEquals("Stroke data", SettingsReferenceDataTextCopy.strokeDataTitle())
        assertEquals("Fonts", SettingsReferenceDataTextCopy.fontsTitle())
    }
}
