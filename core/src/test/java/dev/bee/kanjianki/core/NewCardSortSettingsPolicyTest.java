package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NewCardSortSettingsPolicyTest {
    @Test
    public void saveRequestPreservesSelectedKnownModes() {
        assertEquals(RecordsBase.NEW_CARD_SORT_FREQUENCY, request(RecordsBase.NEW_CARD_SORT_FREQUENCY).mode);
        assertEquals(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, request(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY).mode);
        assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, request(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK).mode);
        assertEquals(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS, request(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS).mode);
    }

    @Test
    public void saveRequestNormalizesUnknownModesLikeSettingsModel() {
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, request("fastest").mode);
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, request(null).mode);
    }

    @Test
    public void saveRequestPreservesExistingToastCopy() {
        assertEquals("New card sort saved.", request(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK).message);
    }

    private static NewCardSortSettingsPolicy.SaveRequest request(String selectedMode) {
        return NewCardSortSettingsPolicy.saveRequest(selectedMode);
    }
}
