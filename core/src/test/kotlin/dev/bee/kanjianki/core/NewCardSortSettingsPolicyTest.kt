package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NewCardSortSettingsPolicyTest {
    @Test
    fun saveRequestPreservesSelectedKnownModes() {
        assertEquals(RecordsBase.NEW_CARD_SORT_FREQUENCY, request(RecordsBase.NEW_CARD_SORT_FREQUENCY).mode)
        assertEquals(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, request(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY).mode)
        assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, request(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK).mode)
        assertEquals(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS, request(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS).mode)
        assertEquals(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY, request(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY).mode)
    }

    @Test
    fun saveRequestNormalizesUnknownModesLikeSettingsModel() {
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, request("fastest").mode)
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, request(null).mode)
    }

    @Test
    fun saveRequestPreservesExistingToastCopy() {
        assertEquals("New card sort saved.", request(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK).message)
        assertEquals("New card sort saved.", NewCardSortSettingsPolicy.savedMessage())
    }

    @Test
    fun saveRequestLocalizesToastCopyInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val request = request(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)

            assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, request.mode)
            assertEquals("新規カードの並び順を保存しました。", request.message)
            assertEquals("新規カードの並び順を保存しました。", NewCardSortSettingsPolicy.savedMessage())
        }
    }

    private fun request(selectedMode: String?): NewCardSortSettingsPolicy.SaveRequest {
        return NewCardSortSettingsPolicy.saveRequest(selectedMode)
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
