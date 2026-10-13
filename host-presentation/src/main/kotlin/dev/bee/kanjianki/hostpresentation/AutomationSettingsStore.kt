package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader

/**
 * The bridge between the Automation section's state and the device-settings store.
 *
 * Its own file rather than lines inside each host, because the read and the write have to
 * agree on every key: a section that read `reminder_hour` and wrote `reminderHour` would
 * appear to save and then silently show the old value on the next load, which is the
 * failure this whole indirection exists to prevent. One reader, one writer, one key list.
 *
 * Deliberately not portable state. Every key here is on
 * `DeviceSettingKeys.portableExclusionStorageNames`, so restoring a phone's backup onto a
 * laptop cannot hand the laptop a 19:00 alarm no OS ever armed, or tell it a sync has been
 * configured against a provider it has never reached.
 */
object AutomationSettingsStore {
    /**
     * The stored automation state, with the reviewed defaults where nothing was written.
     *
     * Normalized on the way out through [DesktopSettingsModel.normalizeAutomation], so a
     * value written by an older build — or by hand — is rendered as the policy would treat
     * it rather than as though the user had chosen it. A store that has never been touched
     * yields exactly `AutomationState()`.
     *
     * [notificationsBlocked] and the backup fields are not read here: the first is runtime
     * state the host asks its notifier for, and the archive count comes from the host's own
     * backup directory. Both are the caller's to fill in — see the `automation` parameter
     * of [DesktopSettingsModel.screen].
     */
    fun read(settings: DeviceSettingsReader): DesktopSettingsModel.AutomationState {
        val defaults = DesktopSettingsModel.AutomationState()
        return DesktopSettingsModel.normalizeAutomation(
            defaults.copy(
                reminderEnabled = settings.read(DeviceSettingKeys.reminderEnabled) ?: defaults.reminderEnabled,
                reminderHour = settings.read(DeviceSettingKeys.reminderHour) ?: defaults.reminderHour,
                reminderMinute = settings.read(DeviceSettingKeys.reminderMinute) ?: defaults.reminderMinute,
                reminderMaxPerDay = settings.read(DeviceSettingKeys.reminderMaxPerDay)
                    ?: defaults.reminderMaxPerDay,
                reminderQuietStartMinute = settings.read(DeviceSettingKeys.reminderQuietStartMinute)
                    ?: defaults.reminderQuietStartMinute,
                reminderQuietEndMinute = settings.read(DeviceSettingKeys.reminderQuietEndMinute)
                    ?: defaults.reminderQuietEndMinute,
                autoSyncConfigured = settings.read(DeviceSettingKeys.autoSyncConfigured)
                    ?: defaults.autoSyncConfigured,
                autoSyncEnabled = settings.read(DeviceSettingKeys.autoSyncEnabled) ?: defaults.autoSyncEnabled,
                autoSyncHour = settings.read(DeviceSettingKeys.autoSyncHour) ?: defaults.autoSyncHour,
                autoSyncMinute = settings.read(DeviceSettingKeys.autoSyncMinute) ?: defaults.autoSyncMinute,
                autoSyncLastSuccessAtMillis = settings.read(DeviceSettingKeys.autoSyncLastSuccessAt)
                    ?: defaults.autoSyncLastSuccessAtMillis,
                autoSyncLastAttemptAtMillis = settings.read(DeviceSettingKeys.autoSyncLastAttemptAt)
                    ?: defaults.autoSyncLastAttemptAtMillis,
                autoSyncNextRunAtMillis = settings.read(DeviceSettingKeys.autoSyncNextRunAt)
                    ?: defaults.autoSyncNextRunAtMillis,
                debugLogEnabled = settings.read(DeviceSettingKeys.debugLogEnabled) ?: defaults.debugLogEnabled,
            ),
        )
    }

    /**
     * Writes the user-editable fields of [state].
     *
     * Only the ten the section can edit. The auto-sync timestamps and `configured` flag
     * are the sync runner's to own — the settings screen reports them and must never write
     * them, or opening Settings would claim a sync had been configured that never ran.
     */
    fun write(editor: DeviceSettingsEditor, state: DesktopSettingsModel.AutomationState) {
        with(editor) {
            put(DeviceSettingKeys.reminderEnabled, state.reminderEnabled)
            put(DeviceSettingKeys.reminderHour, state.reminderHour)
            put(DeviceSettingKeys.reminderMinute, state.reminderMinute)
            put(DeviceSettingKeys.reminderMaxPerDay, state.reminderMaxPerDay)
            put(DeviceSettingKeys.reminderQuietStartMinute, state.reminderQuietStartMinute)
            put(DeviceSettingKeys.reminderQuietEndMinute, state.reminderQuietEndMinute)
            put(DeviceSettingKeys.autoSyncEnabled, state.autoSyncEnabled)
            put(DeviceSettingKeys.autoSyncHour, state.autoSyncHour)
            put(DeviceSettingKeys.autoSyncMinute, state.autoSyncMinute)
            put(DeviceSettingKeys.debugLogEnabled, state.debugLogEnabled)
        }
    }
}
