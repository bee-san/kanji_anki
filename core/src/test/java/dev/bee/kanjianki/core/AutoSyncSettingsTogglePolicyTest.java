package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoSyncSettingsTogglePolicyTest {
    @Test
    public void enablePreservesEnabledFlagAndCopy() {
        AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.enable();

        assertTrue(result.enabled());
        assertEquals("Daily Anki sync turned on.", result.message());
        assertTrue(AutoSyncSettingsTogglePolicy.ToggleResult.class.isRecord());
        assertEquals(
                "ToggleResult[enabled=true, message=Daily Anki sync turned on.]",
                result.toString()
        );
    }

    @Test
    public void disablePreservesDisabledFlagAndCopy() {
        AutoSyncSettingsTogglePolicy.ToggleResult result = AutoSyncSettingsTogglePolicy.disable();

        assertFalse(result.enabled());
        assertEquals("Daily Anki sync turned off.", result.message());
    }
}
