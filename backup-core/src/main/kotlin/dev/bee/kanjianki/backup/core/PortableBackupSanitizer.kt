package dev.bee.kanjianki.backup.core

import dev.bee.kanjianki.platform.DeviceSettingKeys

/**
 * The portable-backup device-settings boundary, shared by every host. A
 * portable backup must not carry device-local settings (reminders, auto-sync,
 * auto-update, debug, and host-only desktop keys such as window geometry or
 * provider references); on a cross-platform restore those keys must be reset on
 * the destination rather than imported.
 *
 * The versioned allowlist is [DeviceSettingKeys.portableExclusionStorageNames].
 * This class holds only the pure set operations over settings-row keys; reading
 * and writing the actual rows stays in platform code.
 */
object PortableBackupSanitizer {
    /** The settings storage names that must never appear in a portable backup. */
    val excludedStorageNames: Set<String> = DeviceSettingKeys.portableExclusionStorageNames

    fun isDeviceLocal(storageName: String): Boolean = storageName in excludedStorageNames

    /**
     * The subset of [storageNames] that must be dropped before publishing a
     * portable backup.
     */
    fun keysToExclude(storageNames: Collection<String>): Set<String> =
        storageNames.filterTo(LinkedHashSet(), ::isDeviceLocal)

    /** The subset of [storageNames] that are safe to keep in a portable backup. */
    fun portableKeys(storageNames: Collection<String>): Set<String> =
        storageNames.filterNotTo(LinkedHashSet(), ::isDeviceLocal)

    /**
     * True when [portableBackupKeys] is clean — i.e. a post-split backup that
     * contains none of the device-local keys. Used to assert the export
     * contract in tests and as a defensive check before restore.
     */
    fun isCleanPortableBackup(portableBackupKeys: Collection<String>): Boolean =
        portableBackupKeys.none(::isDeviceLocal)

    /**
     * The device-local keys present in a (possibly legacy) backup that must be
     * reset on the destination `DeviceSettingsStore` during a cross-platform
     * restore. Identical to [keysToExclude] but named for the restore side.
     */
    fun keysToResetOnRestore(backupSettingsKeys: Collection<String>): Set<String> =
        keysToExclude(backupSettingsKeys)
}
