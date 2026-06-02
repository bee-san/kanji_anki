package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReferenceDataTextCopyTest {
    @Test
    fun referenceDataHelpersPreserveFormatting() {
        assertEquals("Suspended card range", SettingsReferenceDataTextCopy.frequencyRangeTitle())
        assertEquals(
            "Import suspended cards only inside this Jiten rank range. Default 100-3000.",
            SettingsReferenceDataTextCopy.frequencyRangeBody(),
        )
        assertEquals("Min rank", SettingsReferenceDataTextCopy.minRankLabel())
        assertEquals("Max rank", SettingsReferenceDataTextCopy.maxRankLabel())
        assertEquals("Minimum rank", SettingsReferenceDataTextCopy.minimumRankLabel())
        assertEquals("Maximum rank", SettingsReferenceDataTextCopy.maximumRankLabel())
        assertEquals("Save frequency range", SettingsReferenceDataTextCopy.saveFrequencyRangeLabel())
        assertEquals("Enter numeric ranks.", SettingsReferenceDataTextCopy.numericRanksToast())
        assertEquals("Use ranks from 1 to 20000.", SettingsReferenceDataTextCopy.rankRangeToast())
        assertEquals(
            "Suspended card range saved. Sync again to rebuild practice.",
            SettingsReferenceDataTextCopy.frequencyRangeSavedToast(),
        )
        assertEquals("Offline data licenses", SettingsReferenceDataTextCopy.offlineDataLicensesTitle())
        assertEquals(
            "View KANJIDIC2, Jiten, KanjiVG, and font credits.",
            SettingsReferenceDataTextCopy.offlineDataLicensesBody(),
        )
        assertEquals("Open data licenses", SettingsReferenceDataTextCopy.openDataLicensesLabel())
        assertEquals("Data licenses", SettingsReferenceDataTextCopy.dataLicensesTitle())
        assertEquals(
            "Dictionary and stroke data bundled for offline use.",
            SettingsReferenceDataTextCopy.dataLicensesBody(),
        )
        assertEquals("Dictionary data", SettingsReferenceDataTextCopy.dictionaryDataTitle())
        assertEquals("Stroke data", SettingsReferenceDataTextCopy.strokeDataTitle())
        assertEquals("Fonts", SettingsReferenceDataTextCopy.fontsTitle())
    }
}
