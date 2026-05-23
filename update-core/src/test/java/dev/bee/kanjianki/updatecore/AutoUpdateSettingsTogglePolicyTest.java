package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoUpdateSettingsTogglePolicyTest {
    @Test
    public void toggleTurnsEnabledStatusOffWithExistingCopy() {
        AutoUpdateSettingsTogglePolicy.ToggleResult result = AutoUpdateSettingsTogglePolicy.toggle(true);

        assertFalse(result.enabled());
        assertEquals("Automatic updates turned off.", result.message());
    }

    @Test
    public void toggleTurnsDisabledStatusOnWithExistingCopy() {
        AutoUpdateSettingsTogglePolicy.ToggleResult result = AutoUpdateSettingsTogglePolicy.toggle(false);

        assertTrue(result.enabled());
        assertEquals("Automatic updates turned on.", result.message());
    }
}
