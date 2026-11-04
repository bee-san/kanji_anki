package dev.bee.kanjianki.backup.core

import dev.bee.kanjianki.backup.core.CrossPlatformRestorePlanner.Host
import dev.bee.kanjianki.platform.DeviceSettingKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossPlatformRestorePlannerTest {
    private val deviceLocalKey = DeviceSettingKeys.reminderEnabled.storageName
    private val providerKey = DeviceSettingKeys.providerEndpoint.storageName
    private val portableKey = "study_ladder_order"

    @Test
    fun sameHostCleanBackupNeedsNoResetOrRevalidation() {
        val plan = CrossPlatformRestorePlanner.plan(
            backupHost = Host.DESKTOP,
            destinationHost = Host.DESKTOP,
            backupSettingsKeys = listOf(portableKey),
        )
        assertTrue(plan.keysToReset.isEmpty())
        assertTrue(plan.backupWasClean)
        assertFalse(plan.requiresProviderRevalidation)
    }

    @Test
    fun crossHostRestoreAlwaysRevalidatesProvider() {
        val plan = CrossPlatformRestorePlanner.plan(
            backupHost = Host.ANDROID,
            destinationHost = Host.DESKTOP,
            backupSettingsKeys = listOf(portableKey),
        )
        assertTrue(plan.keysToReset.isEmpty())
        assertTrue(plan.backupWasClean)
        assertTrue(plan.requiresProviderRevalidation)
    }

    @Test
    fun dirtyBackupResetsDeviceLocalKeysAndRevalidates() {
        val plan = CrossPlatformRestorePlanner.plan(
            backupHost = Host.DESKTOP,
            destinationHost = Host.DESKTOP,
            backupSettingsKeys = listOf(portableKey, deviceLocalKey, providerKey),
        )
        assertEquals(setOf(deviceLocalKey, providerKey), plan.keysToReset)
        assertFalse(plan.backupWasClean)
        assertTrue(plan.requiresProviderRevalidation)
    }

    @Test
    fun unknownOriginForcesRevalidation() {
        val plan = CrossPlatformRestorePlanner.plan(
            backupHost = Host.UNKNOWN,
            destinationHost = Host.DESKTOP,
            backupSettingsKeys = listOf(portableKey),
        )
        assertTrue(plan.requiresProviderRevalidation)
    }

    @Test
    fun unknownDestinationForcesRevalidation() {
        val plan = CrossPlatformRestorePlanner.plan(
            backupHost = Host.DESKTOP,
            destinationHost = Host.UNKNOWN,
            backupSettingsKeys = listOf(portableKey),
        )
        assertTrue(plan.requiresProviderRevalidation)
    }
}
