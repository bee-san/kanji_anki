package dev.bee.kanjianki.backup.core

/**
 * Plans the device-local state reset that a restore must perform. A backup is
 * portable across hosts (Android ⇄ desktop, or between two desktops), but the
 * device-local settings it may carry — reminders, auto-sync/-update schedules,
 * provider references, window geometry — describe the *source* device, not the
 * destination. On restore those keys must be reset to destination defaults, and
 * any provider projection the backup implies must be treated as stale until the
 * destination revalidates it against its own AnkiDroid/provider.
 *
 * This is the pure decision, shared by every host: given the backup's origin
 * host and the settings keys it contains, produce the set of keys to reset and
 * whether provider revalidation is required. Actually clearing rows and marking
 * projections stale stays in platform code.
 */
object CrossPlatformRestorePlanner {
    /** The host a backup was produced on, as recorded by the exporter. */
    enum class Host {
        ANDROID,
        DESKTOP,
        UNKNOWN,
    }

    data class RestorePlan(
        /** Device-local settings keys present in the backup that must be reset. */
        val keysToReset: Set<String>,
        /**
         * True when the restore crosses hosts (or the origin is unknown), so any
         * provider binding carried by the backup cannot be trusted and the
         * destination must revalidate against its own provider before syncing.
         */
        val requiresProviderRevalidation: Boolean,
        /** True when the backup already excluded all device-local keys (clean export). */
        val backupWasClean: Boolean,
    )

    /**
     * @param backupHost the host that produced the backup.
     * @param destinationHost the host performing the restore.
     * @param backupSettingsKeys every settings storage-name present in the backup.
     */
    fun plan(
        backupHost: Host,
        destinationHost: Host,
        backupSettingsKeys: Collection<String>,
    ): RestorePlan {
        val keysToReset = PortableBackupSanitizer.keysToResetOnRestore(backupSettingsKeys)
        val crossHost = backupHost != destinationHost ||
            backupHost == Host.UNKNOWN ||
            destinationHost == Host.UNKNOWN
        return RestorePlan(
            keysToReset = keysToReset,
            // A same-host restore of a clean backup can trust its provider binding;
            // anything else revalidates. Provider references are device-local, so a
            // clean backup carries none and a cross-host restore has nothing to trust.
            requiresProviderRevalidation = crossHost || keysToReset.isNotEmpty(),
            backupWasClean = keysToReset.isEmpty(),
        )
    }
}
