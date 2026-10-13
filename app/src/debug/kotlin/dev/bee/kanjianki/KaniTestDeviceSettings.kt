package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.data.AndroidDeviceSettingsStore
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingKeys

/**
 * Clears device-local settings that [KaniTestDatabase.delete] does not touch.
 *
 * Update and sync state used to live in the `settings` table, so deleting the
 * database reset it. It now lives in [AndroidDeviceSettingsStore] — SharedPreferences,
 * deliberately outside the portable database so it is not carried by backups — and
 * SharedPreferences survive `deleteDatabase` and the whole instrumentation run.
 *
 * A `@Before` that deletes the database therefore no longer produces the pristine
 * state it reads as producing, and the resulting failures are order-dependent: a test
 * asserting a default passes or fails on whether a sibling that writes the same key
 * happened to run first. That is worse than a plain failure, because a green run is no
 * longer evidence.
 *
 * Reset by key group rather than wholesale. Clearing the entire store would also drop
 * [DeviceSettingKeys.legacySqliteMigrationComplete] and make every test re-run the
 * legacy migration, which is not what any caller is asking for.
 */
object KaniTestDeviceSettings {
    /**
     * Every key the update flow reads or writes.
     *
     * Named individually rather than matched by prefix so that adding an update
     * setting is a deliberate decision about test isolation: a prefix match would
     * silently adopt new keys, and silently miss one that does not share the prefix
     * (as [DeviceSettingKeys.updateCheckFailedAt] does not share `auto_update_`).
     */
    private val UPDATE_KEYS: List<DeviceSettingKey<*>> = listOf(
        DeviceSettingKeys.autoUpdateEnabled,
        DeviceSettingKeys.autoUpdateLastCheckAt,
        DeviceSettingKeys.autoUpdateLastResult,
        DeviceSettingKeys.autoUpdateLastVersion,
        DeviceSettingKeys.autoUpdatePendingPackage,
        DeviceSettingKeys.autoUpdatePendingMessage,
        DeviceSettingKeys.betaUpdatesEnabled,
        DeviceSettingKeys.updateCheckFailedAt,
        DeviceSettingKeys.updatePermissionPromptShown,
        DeviceSettingKeys.updatePermissionPromptLastVersion,
    )

    /**
     * Removes all update state, so the next read sees a first-run device.
     *
     * Removes rather than zeroes, because "no automatic check has run" and "a check
     * ran and reported 0" are different states and only the former is a fresh device.
     */
    @JvmStatic
    fun clearUpdateState(context: Context) {
        clear(context, UPDATE_KEYS)
    }

    private fun clear(context: Context, keys: List<DeviceSettingKey<*>>) {
        // A new store wraps the same process-wide SharedPreferences instance as the
        // container's, so this is visible to a LocalStore that is already open.
        AndroidDeviceSettingsStore(context.applicationContext).edit {
            keys.filter { contains(it) }.forEach { remove(it) }
        }
    }
}
