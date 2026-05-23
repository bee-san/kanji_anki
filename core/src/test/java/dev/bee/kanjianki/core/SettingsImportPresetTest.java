package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class SettingsImportPresetTest {
    @Test
    public void defaultsPreserveImportPresetOrderAndLabels() {
        List<SettingsImportPreset> presets = SettingsImportPreset.defaults();
        SettingsImportPreset firstPreset = presets.get(0);

        assertEquals(5, presets.size());
        assertEquals("Suspended only", presets.get(0).label());
        assertEquals("Kani tag", presets.get(1).label());
        assertEquals("Leech tag", presets.get(2).label());
        assertEquals("Mining deck", presets.get(3).label());
        assertEquals("Recent fails", presets.get(4).label());
        assertThrows(UnsupportedOperationException.class, () -> presets.add(firstPreset));
    }

    @Test
    public void defaultsPreservePresetValues() {
        SettingsImportPreset suspended = SettingsImportPreset.defaults().get(0);
        assertFalse(suspended.activeCards());
        assertTrue(suspended.suspendedCards());
        assertFalse(suspended.taggedCards());
        assertEquals("", suspended.tags());
        assertFalse(suspended.weakCards());
        assertEquals(7.0, suspended.weakDifficulty(), 0.0);
        assertEquals(2, suspended.weakLapses());
        assertEquals(1, suspended.minMatchingCards());
        assertFalse(suspended.browserQueryCards());
        assertEquals("", suspended.browserQuery());

        SettingsImportPreset leech = SettingsImportPreset.defaults().get(2);
        assertFalse(leech.suspendedCards());
        assertTrue(leech.taggedCards());
        assertEquals("leech", leech.tags());
        assertFalse(leech.browserQueryCards());

        SettingsImportPreset mining = SettingsImportPreset.defaults().get(3);
        assertFalse(mining.taggedCards());
        assertTrue(mining.browserQueryCards());
        assertEquals("deck:Mining", mining.browserQuery());
    }

    @Test
    public void boolFlagPreservesStoredBooleanEncoding() {
        assertEquals(1, SettingsImportPreset.boolFlag(true));
        assertEquals(0, SettingsImportPreset.boolFlag(false));
    }
}
