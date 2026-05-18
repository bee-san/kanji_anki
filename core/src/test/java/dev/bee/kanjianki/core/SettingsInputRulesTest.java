package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SettingsInputRulesTest {
    @Test
    public void importThresholdValidationPreservesSettingsBounds() {
        assertTrue(SettingsInputRules.validImportThresholds(1.0, 1, 1));
        assertTrue(SettingsInputRules.validImportThresholds(10.0, 100, 1000));
        assertTrue(SettingsInputRules.validImportThresholds(7.5, 3, 2));

        assertFalse(SettingsInputRules.validImportThresholds(0.999, 3, 2));
        assertFalse(SettingsInputRules.validImportThresholds(10.001, 3, 2));
        assertFalse(SettingsInputRules.validImportThresholds(Double.NaN, 3, 2));
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 0, 2));
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 101, 2));
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 3, 0));
        assertFalse(SettingsInputRules.validImportThresholds(7.5, 3, 1001));
    }

    @Test
    public void rankSliderConversionPreservesClampedOneBasedRanks() {
        assertEquals(0, SettingsInputRules.rankSliderProgress(-20));
        assertEquals(0, SettingsInputRules.rankSliderProgress(1));
        assertEquals(9, SettingsInputRules.rankSliderProgress(10));
        assertEquals(19999, SettingsInputRules.rankSliderProgress(20000));
        assertEquals(19999, SettingsInputRules.rankSliderProgress(50_000));

        assertEquals(1, SettingsInputRules.rankFromSliderProgress(-4));
        assertEquals(1, SettingsInputRules.rankFromSliderProgress(0));
        assertEquals(10, SettingsInputRules.rankFromSliderProgress(9));
        assertEquals(20000, SettingsInputRules.rankFromSliderProgress(19999));
        assertEquals(20000, SettingsInputRules.rankFromSliderProgress(50_000));
    }
}
