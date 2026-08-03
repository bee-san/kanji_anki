package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.SettingsSectionTextCopy
import dev.bee.kanjianki.core.SettingsThemeTextCopy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.SettingsCategory
import dev.bee.kanjianki.presentation.SettingsChoiceOption
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsRoot
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.SettingsSectionContent

/**
 * Maps a [SettingsSection] to the portable [SettingsScreen] the shared surface renders.
 *
 * Today this covers the root category menu, from `SettingsSectionTextCopy` — the same
 * titles and summaries the Android host shows. Every leaf section is
 * [SettingsScreen]'s honest placeholder: Settings is the app's largest surface (~40
 * Android panels), and it is ported section by section (later Goal 198 slices), so a
 * section not yet shared says so rather than rendering an empty panel.
 *
 * A capability [notice] rides on the category the platform limits — desktop reminders
 * fire only while the window is open — so the limitation is visible at the menu, not
 * discovered after the user opens a section that cannot do what Android's does.
 */
internal object DesktopSettingsModel {
    fun screen(section: SettingsSection, theme: KaniThemeChoice): SettingsScreen = when (section) {
        SettingsSection.ROOT -> SettingsScreen(section = section, root = root())
        SettingsSection.APPEARANCE -> SettingsScreen(section = section, content = appearance(theme))
        else -> SettingsScreen(section = section)
    }

    /**
     * The Appearance section: one theme choice over every [KaniThemeChoice].
     *
     * Fully shareable and unblocked — the palette resolution and the write command
     * (`SettingsSaveCommand.Theme`) are already in shared modules, so this is the first
     * real ported section rather than a placeholder. Each option dispatches a
     * [KaniAction.Settings.SetChoice] keyed by [KaniThemeChoice.SETTING_KEY]; the host
     * maps that back to the theme command and the window re-themes on the reload.
     */
    private fun appearance(theme: KaniThemeChoice): SettingsSectionContent.Controls =
        SettingsSectionContent.Controls(
            title = SettingsSectionTextCopy.settingsAppearanceTitle(),
            controls = listOf(
                SettingsControl.Choice(
                    label = SettingsSectionTextCopy.settingsAppearanceBody(),
                    selectedId = theme.storageKey,
                    options = KaniThemeChoice.entries.map { choice ->
                        SettingsChoiceOption(
                            id = choice.storageKey,
                            label = SettingsThemeTextCopy.themeTitle(choice),
                            action = KaniAction.Settings.SetChoice(
                                key = KaniThemeChoice.SETTING_KEY,
                                optionId = choice.storageKey,
                            ),
                        )
                    },
                ),
            ),
        )

    /**
     * The persistence command a settings [action] means, or null if it is not one the
     * desktop app currently persists.
     *
     * The inverse of the [SettingsControl.action]s [screen] builds: a control here
     * dispatches a keyed [KaniAction.Settings], and this turns it back into the concrete
     * `SettingsSaveCommand`. Kept a pure function so the round trip — control key to
     * command — is unit-testable without a store. Only the ported edits map today; the
     * `null` branch is reached only by an edit whose control is not yet rendered.
     */
    fun settingsCommandFor(action: KaniAction.Settings): SettingsSaveCommand? = when (action) {
        is KaniAction.Settings.SetChoice -> when (action.key) {
            KaniThemeChoice.SETTING_KEY ->
                SettingsSaveCommand.Theme(KaniThemeChoice.fromStorageKey(action.optionId))
            else -> null
        }
        is KaniAction.Settings.SetToggle -> null
        is KaniAction.Settings.Command -> null
    }

    private fun root(): SettingsRoot = SettingsRoot(
        title = SettingsSectionTextCopy.settingsTitle(),
        categories = listOf(
            SettingsCategory(
                section = SettingsSection.IMPORT_SYNC,
                title = SettingsSectionTextCopy.settingsAnkiSourceTitle(),
                summary = SettingsSectionTextCopy.settingsAnkiSourceBody(),
            ),
            SettingsCategory(
                section = SettingsSection.STUDY_BEHAVIOR,
                title = SettingsSectionTextCopy.settingsStudyBehaviorTitle(),
                summary = SettingsSectionTextCopy.settingsStudyBehaviorBody(),
            ),
            SettingsCategory(
                section = SettingsSection.AUTOMATION,
                title = SettingsSectionTextCopy.settingsAutomationTitle(),
                summary = SettingsSectionTextCopy.settingsAutomationBody(),
                notices = listOf(REMINDER_NOTICE),
            ),
            SettingsCategory(
                section = SettingsSection.APPEARANCE,
                title = SettingsSectionTextCopy.settingsAppearanceTitle(),
                summary = SettingsSectionTextCopy.settingsAppearanceBody(),
            ),
            SettingsCategory(
                section = SettingsSection.DISPLAY_DATA,
                title = SettingsSectionTextCopy.settingsReferenceDataTitle(),
                summary = SettingsSectionTextCopy.settingsReferenceDataBody(),
            ),
        ),
    )

    // Threaded as a truthful capability line rather than hidden: the desktop
    // reminder/notification surface (Goal 203) has no OS-scheduled worker, so a
    // reminder only evaluates while the window is open.
    private const val REMINDER_NOTICE =
        "On desktop, reminders are evaluated while Kani is open."
}
