package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader

/**
 * Reads and writes the Update section's device-local state, over one key list.
 *
 * The same shape as [AutomationSettingsStore] and for the same reason: a section that read
 * `auto_update_enabled` and wrote `autoUpdateEnabled` would appear to save and then show
 * the old value on the next load, and nothing that asserted on the returned state would
 * notice. One reader, one writer, one key list.
 *
 * Deliberately not portable state. Every key here is on
 * `DeviceSettingKeys.portableExclusionStorageNames`, because a pending-artifact filename
 * and an install channel describe *this* machine: restoring a phone's backup onto a laptop
 * must not tell the laptop it has an APK staged.
 */
object UpdateSettingsStore {
    fun read(settings: DeviceSettingsReader): DesktopSettingsModel.UpdateState {
        val defaults = DesktopSettingsModel.UpdateState()
        return defaults.copy(
            autoUpdateEnabled = settings.read(DeviceSettingKeys.autoUpdateEnabled)
                ?: defaults.autoUpdateEnabled,
            betaUpdatesEnabled = settings.read(DeviceSettingKeys.betaUpdatesEnabled)
                ?: defaults.betaUpdatesEnabled,
            lastCheckAtMillis = settings.read(DeviceSettingKeys.autoUpdateLastCheckAt)
                ?: defaults.lastCheckAtMillis,
            lastResult = settings.read(DeviceSettingKeys.autoUpdateLastResult)
                ?: defaults.lastResult,
            lastVersion = settings.read(DeviceSettingKeys.autoUpdateLastVersion)
                ?: defaults.lastVersion,
            pendingPackage = settings.read(DeviceSettingKeys.autoUpdatePendingPackage)
                ?: defaults.pendingPackage,
            pendingMessage = settings.read(DeviceSettingKeys.autoUpdatePendingMessage)
                ?: defaults.pendingMessage,
        )
    }

    /**
     * Writes the two fields the section can edit.
     *
     * Only two. The last check, last result, last version, and the staged artifact are the
     * update checker's record of what it did; the section reports them and must never write
     * them, or opening Settings would claim a check had run that never did — the same
     * ownership line the auto-sync timestamps sit on.
     *
     * [DesktopSettingsModel.UpdateState.installedVersion] and `canInstall` are not stored
     * at all: the first is the running build's own version and the second is an answer the
     * host has to re-ask every load, because the user can revoke the install permission
     * between two renders.
     */
    fun write(editor: DeviceSettingsEditor, state: DesktopSettingsModel.UpdateState) {
        with(editor) {
            put(DeviceSettingKeys.autoUpdateEnabled, state.autoUpdateEnabled)
            put(DeviceSettingKeys.betaUpdatesEnabled, state.betaUpdatesEnabled)
        }
    }
}
