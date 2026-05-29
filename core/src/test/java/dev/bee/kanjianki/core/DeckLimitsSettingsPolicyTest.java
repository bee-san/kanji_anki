package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DeckLimitsSettingsPolicyTest {
    @Test
    public void normalizeNewPerDayKeepsAnkiStyleBoundedWholeNumber() {
        assertEquals(0, DeckLimitsSettingsPolicy.normalizeNewPerDay(-5));
        assertEquals(12, DeckLimitsSettingsPolicy.normalizeNewPerDay(12));
        assertEquals(999, DeckLimitsSettingsPolicy.normalizeNewPerDay(2000));
    }

    @Test
    public void saveRequestNormalizesInputTextAndReportsSavedValue() {
        DeckLimitsSettingsPolicy.SaveRequest request = DeckLimitsSettingsPolicy.saveNewPerDay(" 42 ");

        assertEquals(42, request.newPerDay);
        assertEquals("New cards/day saved: 42", request.message);
    }

    @Test
    public void saveRequestFallsBackToDefaultForMalformedInput() {
        DeckLimitsSettingsPolicy.SaveRequest request = DeckLimitsSettingsPolicy.saveNewPerDay("not a number", 24);

        assertEquals(24, request.newPerDay);
        assertEquals("New cards/day saved: 24", request.message);
    }
}
