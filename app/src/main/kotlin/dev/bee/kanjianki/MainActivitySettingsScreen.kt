package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivitySettingsScreen(private val activity: MainActivitySettings) {
    fun renderSettings(preserveScroll: Boolean) {
        val previousScroll = activity.contentScroll
        val scrollY = if (preserveScroll && previousScroll != null) {
            previousScroll.scrollY
        } else {
            0
        }
        val current = activity.settings()
        activity.composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE, scrollY) {
            SettingsScreen(settingsScreenModel(current))
        }
    }

    fun settingsScreenModel(current: RecordsSyncModels.Settings): SettingsScreenModel {
        return settingsScreenModel(
            settingsAutomationHeroModel(
                current,
                activity.store.reminderSettings(),
                activity.store.autoSyncSettings(),
                activity.store.autoUpdateStatus(),
                ReminderScheduler.notificationsAllowed(activity)
            ),
            listOf(
                settingsCategoryModel(
                    SettingsTextCopy.settingsAnkiSourceTitle(),
                    SettingsTextCopy.settingsAnkiSourceBody(),
                    R.drawable.ic_book_24,
                    activity.settingsAnkiExpanded,
                    Runnable {
                        activity.settingsAnkiExpanded = !activity.settingsAnkiExpanded
                        activity.renderSettings(true)
                    },
                    activity.noteTypeSettingsPanelModel(current),
                    activity.importFilterSettingsPanelModel(current),
                    activity.frequencyRangeSettingsPanelModel(current)
                ),
                settingsCategoryModel(
                    SettingsTextCopy.settingsStudyBehaviorTitle(),
                    SettingsTextCopy.settingsStudyBehaviorBody(),
                    R.drawable.ic_study_24,
                    activity.settingsStudyExpanded,
                    Runnable {
                        activity.settingsStudyExpanded = !activity.settingsStudyExpanded
                        activity.renderSettings(true)
                    },
                    activity.newCardSortSettingsPanelModel(current),
                    activity.workloadSettingsPanelModel(),
                    activity.retentionSettingsPanelModel(),
                    activity.learningStepsSettingsPanelModel(),
                    activity.studyAheadSettingsPanelModel(),
                    activity.studyLadderSettingsPanelModel(),
                    activity.ladderThresholdSettingsPanelModel()
                ),
                settingsCategoryModel(
                    SettingsTextCopy.settingsAutomationTitle(),
                    SettingsTextCopy.settingsAutomationBody(),
                    R.drawable.ic_sync_24,
                    activity.settingsSyncExpanded,
                    Runnable {
                        activity.settingsSyncExpanded = !activity.settingsSyncExpanded
                        activity.renderSettings(true)
                    },
                    activity.reminderSettingsPanelModel(),
                    activity.autoSyncSettingsPanelModel(),
                    activity.updateSettingsPanelModel()
                ),
                settingsCategoryModel(
                    SettingsTextCopy.settingsReferenceDataTitle(),
                    SettingsTextCopy.settingsReferenceDataBody(),
                    R.drawable.ic_sparkle_24,
                    activity.settingsAppExpanded,
                    Runnable {
                        activity.settingsAppExpanded = !activity.settingsAppExpanded
                        activity.renderSettings(true)
                    },
                    activity.dataLicenseSettingsPanelModel()
                )
            ),
            Runnable { activity.renderHome() }
        )
    }

    private fun settingsCategoryModel(
        title: String,
        summary: String,
        iconRes: Int,
        expanded: Boolean,
        toggle: Runnable,
        vararg panels: SettingsPanelModel,
    ): SettingsCategorySectionModel {
        return settingsCategorySectionModel(
            title,
            summary,
            iconRes,
            expanded,
            toggle,
            panels.toList()
        )
    }
}
