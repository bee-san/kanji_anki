package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsReferenceDataTextCopyTest {
    @Test
    fun referenceDataHelpersPreserveFormatting() {
        assertEquals("Suspended card range", SettingsReferenceDataTextCopy.frequencyRangeTitle())
        assertEquals(
            "Set the rank range, then sync.",
            SettingsReferenceDataTextCopy.frequencyRangeBody(),
        )
        assertEquals("Min rank", SettingsReferenceDataTextCopy.minRankLabel())
        assertEquals("Max rank", SettingsReferenceDataTextCopy.maxRankLabel())
        assertEquals("Minimum rank", SettingsReferenceDataTextCopy.minimumRankLabel())
        assertEquals("Maximum rank", SettingsReferenceDataTextCopy.maximumRankLabel())
        assertEquals("Save rank range", SettingsReferenceDataTextCopy.saveFrequencyRangeLabel())
        assertEquals("Enter numbers for ranks.", SettingsReferenceDataTextCopy.numericRanksToast())
        assertEquals("Enter ranks 1-20000.", SettingsReferenceDataTextCopy.rankRangeToast())
        assertEquals(
            "Range saved. Sync to refresh.",
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

    @Test
    fun referenceDataHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("停止カード範囲", SettingsReferenceDataTextCopy.frequencyRangeTitle())
            assertEquals(
                "順位範囲を設定してから同期する。",
                SettingsReferenceDataTextCopy.frequencyRangeBody(),
            )
            assertEquals("最小順位", SettingsReferenceDataTextCopy.minRankLabel())
            assertEquals("最大順位", SettingsReferenceDataTextCopy.maxRankLabel())
            assertEquals("最小順位", SettingsReferenceDataTextCopy.minimumRankLabel())
            assertEquals("最大順位", SettingsReferenceDataTextCopy.maximumRankLabel())
            assertEquals("順位範囲を保存", SettingsReferenceDataTextCopy.saveFrequencyRangeLabel())
            assertEquals("順位には数字を入力してください。", SettingsReferenceDataTextCopy.numericRanksToast())
            assertEquals("順位は1-20000で入力してください。", SettingsReferenceDataTextCopy.rankRangeToast())
            assertEquals(
                "範囲を保存しました。同期すると更新されます。",
                SettingsReferenceDataTextCopy.frequencyRangeSavedToast(),
            )
            assertEquals("オフラインデータライセンス", SettingsReferenceDataTextCopy.offlineDataLicensesTitle())
            assertEquals(
                "辞書、筆順、フォントのクレジット。",
                SettingsReferenceDataTextCopy.offlineDataLicensesBody(),
            )
            assertEquals("データライセンスを開く", SettingsReferenceDataTextCopy.openDataLicensesLabel())
            assertEquals("データライセンス", SettingsReferenceDataTextCopy.dataLicensesTitle())
            assertEquals(
                "辞書、筆順、フォントのクレジット。",
                SettingsReferenceDataTextCopy.dataLicensesBody(),
            )
            assertEquals("辞書データ", SettingsReferenceDataTextCopy.dictionaryDataTitle())
            assertEquals("筆順データ", SettingsReferenceDataTextCopy.strokeDataTitle())
            assertEquals("フォント", SettingsReferenceDataTextCopy.fontsTitle())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
