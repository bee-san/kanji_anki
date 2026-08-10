package dev.bee.kanjianki.data

import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingValueType
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsStore

/**
 * Copies pre-split host settings out of the portable SQLite settings table.
 * The device commit precedes SQLite cleanup so interruption can only leave a
 * retryable duplicate, never lose the legacy value.
 */
internal class LegacyDeviceSettingsMigration(
    private val deviceStore: DeviceSettingsStore,
    private val readLegacyValues: (List<String>) -> Map<String, String>,
    private val deleteLegacyValues: (List<String>) -> Unit,
) {
    fun migrate() {
        synchronized(MIGRATION_LOCK) {
            val storedValues = readLegacyValues(LEGACY_STORAGE_NAMES)
            val legacyValues = LEGACY_KEYS.mapNotNull { key ->
                storedValues[key.storageName]?.let { key to it }
            }
            if (deviceStore.read(DeviceSettingKeys.legacySqliteMigrationComplete) != true) {
                deviceStore.edit {
                    if (read(DeviceSettingKeys.legacySqliteMigrationComplete) != true) {
                        legacyValues.forEach { (key, value) ->
                            if (!contains(key)) {
                                putLegacyValue(key, value)
                            }
                        }
                        put(DeviceSettingKeys.legacySqliteMigrationComplete, true)
                    }
                }
            }

            if (legacyValues.isNotEmpty()) {
                deleteLegacyValues(LEGACY_STORAGE_NAMES)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DeviceSettingsEditor.putLegacyValue(
        key: DeviceSettingKey<*>,
        value: String,
    ) {
        when (key.valueType) {
            DeviceSettingValueType.BOOLEAN -> value.toIntOrNull()?.let {
                put(key as DeviceSettingKey<Boolean>, it == 1)
            }
            DeviceSettingValueType.INT -> value.toIntOrNull()?.let {
                put(key as DeviceSettingKey<Int>, it)
            }
            DeviceSettingValueType.LONG -> value.toLongOrNull()?.let {
                put(key as DeviceSettingKey<Long>, it)
            }
            DeviceSettingValueType.STRING ->
                put(key as DeviceSettingKey<String>, value)
        }
    }

    internal companion object {
        val LEGACY_KEYS: List<DeviceSettingKey<*>> = listOf(
            DeviceSettingKeys.reminderEnabled,
            DeviceSettingKeys.reminderHour,
            DeviceSettingKeys.reminderMinute,
            DeviceSettingKeys.reminderQuietStartMinute,
            DeviceSettingKeys.reminderQuietEndMinute,
            DeviceSettingKeys.reminderMaxPerDay,
            DeviceSettingKeys.reviewReminderDayStart,
            DeviceSettingKeys.reviewReminderCount,
            DeviceSettingKeys.reminderLastPostedAt,
            DeviceSettingKeys.reminderLastPostedSignature,
            DeviceSettingKeys.reminderStateDayStart,
            DeviceSettingKeys.reminderDueShownToday,
            DeviceSettingKeys.reminderStreakShownToday,
            DeviceSettingKeys.reminderSyncShownToday,
            DeviceSettingKeys.reminderDismissedFamiliesToday,
            DeviceSettingKeys.reminderDailyOverrideUsedToday,
            DeviceSettingKeys.autoSyncConfigured,
            DeviceSettingKeys.autoSyncEnabled,
            DeviceSettingKeys.autoSyncHour,
            DeviceSettingKeys.autoSyncMinute,
            DeviceSettingKeys.autoSyncLastAttemptAt,
            DeviceSettingKeys.autoSyncLastSuccessAt,
            DeviceSettingKeys.autoSyncNextRunAt,
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
            DeviceSettingKeys.debugLogEnabled,
            DeviceSettingKeys.flashcardSwipeGestureEnabled,
        )
        val LEGACY_STORAGE_NAMES: List<String> = LEGACY_KEYS.map { it.storageName }
        private val MIGRATION_LOCK = Any()
    }
}
