package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsStore
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.SettingsCommands

/**
 * Where one settings edit goes, for both hosts.
 *
 * Four destinations, and which one an edit takes is not a host decision: a keybinding, an
 * automation time, and an update toggle are all device-local, and everything else is a
 * portable collection setting. Each host had — or in Android's case, was missing — its own
 * copy of this fan-out, which is a bug shaped like duplication: Android's `persistSettings`
 * only knew about `settingsCommandFor`, so a keybinding or reminder edit dispatched there
 * was accepted by the UI and silently dropped.
 *
 * The order matters and is asserted by test. The device-local mappers run first because
 * they are the specific ones: both refuse an id they do not own by returning null, while
 * `settingsCommandFor` is the general fallback. Running the fallback first would let a
 * namespace collision write to the collection database.
 */
object SettingsEditWriter {
    /**
     * Persists [action], returning what it was recognized as.
     *
     * A [SettingsEditOutcome.Ignored] is not an error: it is a host command the shell turned
     * into an effect or performed itself, an edit refused because the value would not
     * change, or a keystroke another command holds. Nothing to write in any of those cases.
     */
    suspend fun write(
        action: KaniAction.Settings,
        deviceSettings: DeviceSettingsStore,
        settingsUseCases: SettingsUseCases,
        keyboardPlatform: KeyboardPlatform,
    ): SettingsEditOutcome {
        // A host command is nobody's to persist — a backup picker, an update check, an OS
        // permission page. Checked first so it can never be mistaken for an unknown id and
        // fall through to the collection mapper.
        if (action is KaniAction.Settings.Command && SettingsCommands.isHostCommand(action.id)) {
            return SettingsEditOutcome.Ignored
        }

        val keybindings = DesktopSettingsModel.keybindingEditFor(
            action = action,
            stored = deviceSettings.read(DeviceSettingKeys.studyKeybindings),
            platform = keyboardPlatform,
        )
        if (keybindings != null) {
            deviceSettings.edit { put(DeviceSettingKeys.studyKeybindings, keybindings) }
            return SettingsEditOutcome.Keybindings
        }

        val automation = DesktopSettingsModel.automationEditFor(
            action = action,
            current = AutomationSettingsStore.read(deviceSettings.snapshot()),
        )
        if (automation != null) {
            deviceSettings.edit { AutomationSettingsStore.write(this, automation) }
            return SettingsEditOutcome.Automation
        }

        val update = DesktopSettingsModel.updateEditFor(
            action = action,
            current = UpdateSettingsStore.read(deviceSettings.snapshot()),
        )
        if (update != null) {
            deviceSettings.edit { UpdateSettingsStore.write(this, update) }
            return SettingsEditOutcome.Update
        }

        // The current snapshot resolves paired commands: a ladder threshold carries both
        // values, so the untouched one is read here rather than clobbered.
        val command = DesktopSettingsModel.settingsCommandFor(action, settingsUseCases.load())
            ?: return SettingsEditOutcome.Ignored
        settingsUseCases.save(command)
        return SettingsEditOutcome.Collection
    }
}

/**
 * What an edit was recognized as, for tests and for a host that wants to react.
 *
 * Returned rather than kept private because the three paths have different consequences a
 * host may owe follow-up work for — an automation write has to re-arm the host's
 * scheduler, and a collection write does not.
 */
enum class SettingsEditOutcome {
    /** Written to the device store's keybinding key. */
    Keybindings,

    /** Written to the device store's automation keys; the host should re-arm its schedules. */
    Automation,

    /**
     * Written to the device store's update keys.
     *
     * The host owes follow-up work here too: turning automatic updates on has to schedule
     * the background check, and turning them off has to cancel it, or the stored toggle and
     * the actual schedule disagree.
     */
    Update,

    /** Saved to the collection database as a portable setting. */
    Collection,

    /** Recognized and deliberately not persisted: a host command, or a no-op edit. */
    Ignored,
}
