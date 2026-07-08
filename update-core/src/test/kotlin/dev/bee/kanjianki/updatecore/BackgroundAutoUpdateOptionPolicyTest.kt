package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAutoUpdateOptionPolicyTest {
    @Test
    fun optionStaysVisibleUntilBackgroundUpdatesAreFullyConfigured() {
        assertTrue(BackgroundAutoUpdateOptionPolicy.optionVisible(false, false))
        assertTrue(BackgroundAutoUpdateOptionPolicy.optionVisible(false, true))
        assertTrue(BackgroundAutoUpdateOptionPolicy.optionVisible(true, false))
        assertFalse(BackgroundAutoUpdateOptionPolicy.optionVisible(true, true))
    }

    @Test
    fun autoUpdatesAreEnabledOnlyWhenCurrentlyDisabled() {
        assertTrue(BackgroundAutoUpdateOptionPolicy.shouldEnableAutoUpdates(false))
        assertFalse(BackgroundAutoUpdateOptionPolicy.shouldEnableAutoUpdates(true))
    }

    @Test
    fun installSettingsOpenOnlyWhilePermissionIsMissing() {
        assertTrue(BackgroundAutoUpdateOptionPolicy.shouldOpenInstallSettings(false))
        assertFalse(BackgroundAutoUpdateOptionPolicy.shouldOpenInstallSettings(true))
    }
}
