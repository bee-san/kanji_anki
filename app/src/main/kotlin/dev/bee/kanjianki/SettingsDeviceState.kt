package dev.bee.kanjianki

import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy

internal data class SettingsReminderState(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
) {
    fun normalized(): SettingsReminderState {
        val value = TimeOfDaySettingsPolicy.normalizeReminder(enabled, hour, minute)
        return SettingsReminderState(value.enabled, value.hour, value.minute)
    }

    fun displayTime(): String = TimeOfDaySettingsPolicy.displayTime(hour, minute)
}

internal data class SettingsReminderAntiSpamState(
    val quietStartMinuteOfDay: Int,
    val quietEndMinuteOfDay: Int,
    val maxRemindersPerDay: Int,
) {
    fun normalized(): SettingsReminderAntiSpamState = SettingsReminderAntiSpamState(
        ReminderAntiSpamPolicy.normalizeMinuteOfDay(
            quietStartMinuteOfDay,
            ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE,
        ),
        ReminderAntiSpamPolicy.normalizeMinuteOfDay(
            quietEndMinuteOfDay,
            ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE,
        ),
        ReminderAntiSpamPolicy.normalizeMaxPerDay(maxRemindersPerDay),
    )
}

internal data class SettingsAutoSyncState(
    val configured: Boolean,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long,
    val nextRunAt: Long,
) {
    fun normalized(): SettingsAutoSyncState {
        val value = TimeOfDaySettingsPolicy.normalizeAutoSync(
            configured,
            enabled,
            hour,
            minute,
            lastAttemptAt,
            lastSuccessAt,
            nextRunAt,
        )
        return SettingsAutoSyncState(
            value.configured,
            value.enabled,
            value.hour,
            value.minute,
            value.lastAttemptAtMillis,
            value.lastSuccessAtMillis,
            value.nextRunAtMillis,
        )
    }

    fun displayTime(): String = TimeOfDaySettingsPolicy.displayTime(hour, minute)
}

internal data class SettingsAutoUpdateState(
    val enabled: Boolean,
    val lastCheckAtMillis: Long,
    val lastResult: String,
    val lastVersion: String,
    val pendingPackage: String,
    val pendingMessage: String,
) {
    fun hasPendingUpdate(): Boolean =
        AutoUpdateStatusPolicy.hasPendingUpdate(pendingPackage)
}

internal data class SettingsDeviceState(
    val reminder: SettingsReminderState,
    val reminderAntiSpam: SettingsReminderAntiSpamState,
    val autoSync: SettingsAutoSyncState,
    val autoUpdate: SettingsAutoUpdateState,
    val debugLogEnabled: Boolean,
)

internal fun DeviceSettingsReader.settingsDeviceState(): SettingsDeviceState {
    val update = AutoUpdateStatusPolicy.normalize(
        read(DeviceSettingKeys.autoUpdateEnabled) ?: true,
        read(DeviceSettingKeys.autoUpdateLastCheckAt) ?: 0L,
        read(DeviceSettingKeys.autoUpdateLastResult)
            ?: AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT,
        read(DeviceSettingKeys.autoUpdateLastVersion),
        read(DeviceSettingKeys.autoUpdatePendingPackage),
        read(DeviceSettingKeys.autoUpdatePendingMessage),
    )
    return SettingsDeviceState(
        reminder = SettingsReminderState(
            read(DeviceSettingKeys.reminderEnabled) ?: false,
            read(DeviceSettingKeys.reminderHour)
                ?: TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR,
            read(DeviceSettingKeys.reminderMinute)
                ?: TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE,
        ).normalized(),
        reminderAntiSpam = SettingsReminderAntiSpamState(
            read(DeviceSettingKeys.reminderQuietStartMinute)
                ?: ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE,
            read(DeviceSettingKeys.reminderQuietEndMinute)
                ?: ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE,
            read(DeviceSettingKeys.reminderMaxPerDay)
                ?: ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY,
        ).normalized(),
        autoSync = SettingsAutoSyncState(
            read(DeviceSettingKeys.autoSyncConfigured) ?: false,
            read(DeviceSettingKeys.autoSyncEnabled) ?: false,
            read(DeviceSettingKeys.autoSyncHour)
                ?: TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_HOUR,
            read(DeviceSettingKeys.autoSyncMinute)
                ?: TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_MINUTE,
            read(DeviceSettingKeys.autoSyncLastAttemptAt) ?: 0L,
            read(DeviceSettingKeys.autoSyncLastSuccessAt) ?: 0L,
            read(DeviceSettingKeys.autoSyncNextRunAt) ?: 0L,
        ).normalized(),
        autoUpdate = SettingsAutoUpdateState(
            update.enabled(),
            update.lastCheckAtMillis(),
            update.lastResult(),
            update.lastVersion(),
            update.pendingApkName(),
            update.pendingMessage(),
        ),
        debugLogEnabled = read(DeviceSettingKeys.debugLogEnabled) ?: false,
    )
}

internal fun DeviceSettingsStore.saveReminder(value: SettingsReminderState) {
    val normalized = value.normalized()
    edit {
        put(DeviceSettingKeys.reminderEnabled, normalized.enabled)
        put(DeviceSettingKeys.reminderHour, normalized.hour)
        put(DeviceSettingKeys.reminderMinute, normalized.minute)
    }
}

internal fun DeviceSettingsStore.saveReminderAntiSpam(value: SettingsReminderAntiSpamState) {
    val normalized = value.normalized()
    edit {
        put(DeviceSettingKeys.reminderQuietStartMinute, normalized.quietStartMinuteOfDay)
        put(DeviceSettingKeys.reminderQuietEndMinute, normalized.quietEndMinuteOfDay)
        put(DeviceSettingKeys.reminderMaxPerDay, normalized.maxRemindersPerDay)
    }
}

internal fun DeviceSettingsStore.setAutoSyncEnabled(enabled: Boolean) {
    edit {
        put(DeviceSettingKeys.autoSyncConfigured, true)
        put(DeviceSettingKeys.autoSyncEnabled, enabled)
    }
}

internal fun DeviceSettingsStore.setAutoUpdateEnabled(enabled: Boolean) {
    edit {
        put(DeviceSettingKeys.autoUpdateEnabled, enabled)
    }
}

internal fun DeviceSettingsStore.setDebugLogEnabled(enabled: Boolean) {
    edit {
        put(DeviceSettingKeys.debugLogEnabled, enabled)
    }
}
