package dev.bee.kanjianki.core

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
    }

    private fun request(selectedMode: String?): NewCardSortSettingsPolicy.SaveRequest {
        return NewCardSortSettingsPolicy.saveRequest(selectedMode)
    }
}
