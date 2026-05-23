package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class RetentionSettingsPolicyTest {
    @Test
    public void saveRequestPreservesLatestMultipliersAndStoresRetentionPercent() {
        RecordsSchedulerModels.SchedulerParameters latest = parameters();

        RetentionSettingsPolicy.SaveResult result = RetentionSettingsPolicy.saveRequest(95, false, " 1-500=95% ", latest);

        assertTrue(result.valid);
        assertEquals(0.95, result.parameters.targetRetention, 0.001);
        assertEquals(latest.againMultiplier, result.parameters.againMultiplier, 0.001);
        assertEquals(latest.hardMultiplier, result.parameters.hardMultiplier, 0.001);
        assertEquals(latest.goodMultiplier, result.parameters.goodMultiplier, 0.001);
        assertEquals(latest.easyMultiplier, result.parameters.easyMultiplier, 0.001);
        assertEquals(latest.lastAdjustedAtMillis, result.parameters.lastAdjustedAtMillis);
        assertEquals(latest.lastAdjustmentReviewCount, result.parameters.lastAdjustmentReviewCount);
        assertFalse(result.parameters.frequencyRetentionEnabled);
        assertEquals("1-500=95%", result.parameters.frequencyRetentionRanges);
        assertEquals("FSRS retention saved.", result.message);
    }

    @Test
    public void saveRequestValidatesEnabledFrequencyRanges() {
        RetentionSettingsPolicy.SaveResult result = RetentionSettingsPolicy.saveRequest(
                90,
                true,
                "1-500=95%\n501-20000=85%",
                parameters()
        );

        assertTrue(result.valid);
        assertTrue(result.parameters.frequencyRetentionEnabled);
        assertEquals("1-500=95%\n501-20000=85%", result.parameters.frequencyRetentionRanges);
    }

    @Test
    public void saveRequestRejectsInvalidEnabledFrequencyRanges() {
        RetentionSettingsPolicy.SaveResult result = RetentionSettingsPolicy.saveRequest(90, true, "500-1=90%", parameters());

        assertFalse(result.valid);
        assertNull(result.parameters);
        assertEquals("Line 1: Use ranks 1-20000 in ascending order.", result.message);
    }

    @Test
    public void saveRequestIgnoresInvalidDisabledFrequencyRangesLikePreviousUi() {
        RetentionSettingsPolicy.SaveResult result = RetentionSettingsPolicy.saveRequest(90, false, "500-1=90%", parameters());

        assertTrue(result.valid);
        assertFalse(result.parameters.frequencyRetentionEnabled);
        assertEquals("500-1=90%", result.parameters.frequencyRetentionRanges);
    }

    private static RecordsSchedulerModels.SchedulerParameters parameters() {
        return new RecordsSchedulerModels.SchedulerParameters(0.88, 0.4, 1.1, 2.2, 3.3, 123L, 45);
    }
}
