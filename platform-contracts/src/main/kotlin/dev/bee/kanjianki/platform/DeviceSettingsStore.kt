package dev.bee.kanjianki.platform

/**
 * Device-local settings that must not be embedded in Kani's portable database
 * backups. Implementations must commit one [edit] block atomically before
 * returning.
 */
interface DeviceSettingsStore {
    fun contains(key: DeviceSettingKey<*>): Boolean

    fun <T : Any> read(key: DeviceSettingKey<T>): T?

    fun edit(block: DeviceSettingsEditor.() -> Unit)
}

interface DeviceSettingsEditor {
    fun <T : Any> put(key: DeviceSettingKey<T>, value: T)

    fun remove(key: DeviceSettingKey<*>)
}

enum class DeviceSettingValueType {
    BOOLEAN,
    INT,
    LONG,
    STRING,
}

class DeviceSettingKey<T : Any> internal constructor(
    val storageName: String,
    val valueType: DeviceSettingValueType,
)

/**
 * Stable device-local key namespace shared by host adapters.
 *
 * Authentication keys are references into a host secret store. API keys and
 * other secret values must never be written to this store.
 */
object DeviceSettingKeys {
    val legacySqliteMigrationComplete = booleanKey("device_settings_migration_v1_complete")

    val reminderEnabled = booleanKey("reminder_enabled")
    val reminderHour = intKey("reminder_hour")
    val reminderMinute = intKey("reminder_minute")
    val reminderQuietStartMinute = intKey("reminder_quiet_start_minute")
    val reminderQuietEndMinute = intKey("reminder_quiet_end_minute")
    val reminderMaxPerDay = intKey("reminder_max_per_day")
    val reviewReminderDayStart = longKey("review_reminder_day_start")
    val reviewReminderCount = intKey("review_reminder_count")
    val reminderLastPostedAt = longKey("reminder_last_posted_at")
    val reminderLastPostedSignature = stringKey("reminder_last_posted_signature")
    val reminderStateDayStart = longKey("reminder_state_day_start")
    val reminderDueShownToday = intKey("reminder_due_shown_today")
    val reminderStreakShownToday = intKey("reminder_streak_shown_today")
    val reminderSyncShownToday = intKey("reminder_sync_shown_today")
    val reminderDismissedFamiliesToday = stringKey("reminder_dismissed_families_today")
    val reminderDailyOverrideUsedToday = booleanKey("reminder_daily_override_used_today")

    val autoSyncConfigured = booleanKey("auto_sync_configured")
    val autoSyncEnabled = booleanKey("auto_sync_enabled")
    val autoSyncHour = intKey("auto_sync_hour")
    val autoSyncMinute = intKey("auto_sync_minute")
    val autoSyncLastAttemptAt = longKey("auto_sync_last_attempt_at")
    val autoSyncLastSuccessAt = longKey("auto_sync_last_success_at")
    val autoSyncNextRunAt = longKey("auto_sync_next_run_at")

    val autoUpdateEnabled = booleanKey("auto_update_enabled")
    val autoUpdateLastCheckAt = longKey("auto_update_last_check_at")
    val autoUpdateLastResult = stringKey("auto_update_last_result")
    val autoUpdateLastVersion = stringKey("auto_update_last_version")
    val autoUpdatePendingPackage = stringKey("auto_update_pending_apk")
    val autoUpdatePendingMessage = stringKey("auto_update_pending_message")
    val updateCheckFailedAt = longKey("update_check_failed_at")
    val updatePermissionPromptShown = booleanKey("update_permission_prompt_shown")
    val updatePermissionPromptLastVersion = stringKey("update_permission_prompt_last_version")
    val debugLogEnabled = booleanKey("debug_log_enabled")

    val providerEndpoint = stringKey("provider_endpoint")
    val providerPermissionReference = stringKey("provider_permission_reference")
    val providerAuthReference = stringKey("provider_auth_reference")
    val windowX = intKey("window_x")
    val windowY = intKey("window_y")
    val windowWidth = intKey("window_width")
    val windowHeight = intKey("window_height")
    val windowMaximized = booleanKey("window_maximized")
    val trayEnabled = booleanKey("tray_enabled")
    val runAtLogin = booleanKey("run_at_login")
    val hostProfilePath = stringKey("host_profile_path")
    val hostBackupPath = stringKey("host_backup_path")

    private fun booleanKey(name: String) =
        DeviceSettingKey<Boolean>(name, DeviceSettingValueType.BOOLEAN)

    private fun intKey(name: String) =
        DeviceSettingKey<Int>(name, DeviceSettingValueType.INT)

    private fun longKey(name: String) =
        DeviceSettingKey<Long>(name, DeviceSettingValueType.LONG)

    private fun stringKey(name: String) =
        DeviceSettingKey<String>(name, DeviceSettingValueType.STRING)
}
