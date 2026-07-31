package dev.bee.kanjianki.backup.core

import dev.bee.kanjianki.platform.DeviceSettingKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupSanitizerTest {
    @Test
    fun deviceLocalKeysAreRecognized() {
        assertTrue(PortableBackupSanitizer.isDeviceLocal(DeviceSettingKeys.reminderEnabled.storageName))
        assertTrue(PortableBackupSanitizer.isDeviceLocal(DeviceSettingKeys.autoSyncEnabled.storageName))
        assertTrue(PortableBackupSanitizer.isDeviceLocal(DeviceSettingKeys.windowX.storageName))
        assertTrue(PortableBackupSanitizer.isDeviceLocal(DeviceSettingKeys.providerEndpoint.storageName))
        // The migration bookkeeping marker is NOT device-local for portability.
        assertFalse(PortableBackupSanitizer.isDeviceLocal(DeviceSettingKeys.legacySqliteMigrationComplete.storageName))
        // Portable Kani state (arbitrary scheduler keys) is kept.
        assertFalse(PortableBackupSanitizer.isDeviceLocal("scheduler_target_retention"))
        assertFalse(PortableBackupSanitizer.isDeviceLocal("study_ladder_order"))
    }

    @Test
    fun exportSplitsPortableFromDeviceLocalKeys() {
        val keys = listOf(
            "scheduler_target_retention",
            DeviceSettingKeys.reminderEnabled.storageName,
            "study_ladder_order",
            DeviceSettingKeys.autoUpdateEnabled.storageName,
        )
        assertEquals(
            setOf(DeviceSettingKeys.reminderEnabled.storageName, DeviceSettingKeys.autoUpdateEnabled.storageName),
            PortableBackupSanitizer.keysToExclude(keys),
        )
        assertEquals(
            setOf("scheduler_target_retention", "study_ladder_order"),
            PortableBackupSanitizer.portableKeys(keys),
        )
    }

    @Test
    fun cleanPortableBackupHasNoDeviceLocalKeys() {
        assertTrue(
            PortableBackupSanitizer.isCleanPortableBackup(listOf("scheduler_target_retention", "study_ladder_order")),
        )
        assertFalse(
            PortableBackupSanitizer.isCleanPortableBackup(
                listOf("scheduler_target_retention", DeviceSettingKeys.windowMaximized.storageName),
            ),
        )
    }

    @Test
    fun legacyBackupKeysAreResetOnRestore() {
        val legacyBackupKeys = listOf(
            "scheduler_target_retention",
            DeviceSettingKeys.reminderHour.storageName,
            DeviceSettingKeys.hostProfilePath.storageName,
        )
        assertEquals(
            setOf(DeviceSettingKeys.reminderHour.storageName, DeviceSettingKeys.hostProfilePath.storageName),
            PortableBackupSanitizer.keysToResetOnRestore(legacyBackupKeys),
        )
    }

    @Test
    fun allowlistCoversEveryDeviceLocalKeyClass() {
        // Sanity: the allowlist is non-trivial and stable in size.
        assertTrue(PortableBackupSanitizer.excludedStorageNames.size >= 40)
        assertTrue(PortableBackupSanitizer.excludedStorageNames.contains("debug_log_enabled"))
    }
}
