package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.SettingsSectionTextCopy
import dev.bee.kanjianki.presentation.SettingsCategory
import dev.bee.kanjianki.presentation.SettingsRoot
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.SettingsSection

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
    fun screen(section: SettingsSection): SettingsScreen = when (section) {
        SettingsSection.ROOT -> SettingsScreen(section = section, root = root())
        else -> SettingsScreen(section = section)
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
