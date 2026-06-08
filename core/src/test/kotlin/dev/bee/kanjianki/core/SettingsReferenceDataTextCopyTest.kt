package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReferenceDataTextCopyTest {
    @Test
    fun referenceDataHelpersPreserveFormatting() {
        assertEquals("Kanji frequency range", SettingsReferenceDataTextCopy.frequencyRangeTitle())
        assertEquals(
            "Suspend cards by Jiten rank. Default 100-3000.",
            SettingsReferenceDataTextCopy.frequencyRangeBody(),
        )
        assertEquals("Min rank", SettingsReferenceDataTextCopy.minRankLabel())
        assertEquals("Max rank", SettingsReferenceDataTextCopy.maxRankLabel())
        assertEquals("Minimum rank", SettingsReferenceDataTextCopy.minimumRankLabel())
        assertEquals("Maximum rank", SettingsReferenceDataTextCopy.maximumRankLabel())
        assertEquals("Save frequency range", SettingsReferenceDataTextCopy.saveFrequencyRangeLabel())
        assertEquals("Enter numeric ranks.", SettingsReferenceDataTextCopy.numericRanksToast())
        assertEquals("Ranks must be 1-20000.", SettingsReferenceDataTextCopy.rankRangeToast())
        assertEquals(
            "Saved. Sync to update practice.",
            SettingsReferenceDataTextCopy.frequencyRangeSavedToast(),
        )
        assertEquals("Offline data licenses", SettingsReferenceDataTextCopy.offlineDataLicensesTitle())
        assertEquals(
            "KANJIDIC2, Jiten, KanjiVG, font credits.",
            SettingsReferenceDataTextCopy.offlineDataLicensesBody(),
        )
        assertEquals("Open data licenses", SettingsReferenceDataTextCopy.openDataLicensesLabel())
        assertEquals("Data licenses", SettingsReferenceDataTextCopy.dataLicensesTitle())
        assertEquals(
            "Bundled dictionary and stroke data.",
            SettingsReferenceDataTextCopy.dataLicensesBody(),
        )
        assertEquals("Dictionary data", SettingsReferenceDataTextCopy.dictionaryDataTitle())
        assertEquals("Stroke data", SettingsReferenceDataTextCopy.strokeDataTitle())
        assertEquals("Fonts", SettingsReferenceDataTextCopy.fontsTitle())
    }
}
