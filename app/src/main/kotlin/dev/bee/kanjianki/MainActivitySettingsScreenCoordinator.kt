package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivitySettingsScreenCoordinator(private val activity: MainActivitySettings) {
    fun settingsScreenModel(): SettingsScreenModel {
        val current = activity.settings()
        return settingsScreenModel(
            settingsHeroModel(current),
            settingsCategoryModels(current),
            Runnable { activity.renderHome() },
        )
    }

    private fun settingsHeroModel(current: RecordsSyncModels.Settings): SettingsAutomationHeroModel {
        val reminder = activity.store.reminderSettings()
        val autoSync = activity.store.autoSyncSettings()
        val autoUpdate = activity.store.autoUpdateStatus()
        val reminderBlocked = reminder.enabled && !ReminderScheduler.notificationsAllowed(activity)
        return SettingsAutomationHeroModel(
            cockpitLabel = SettingsTextCopy.settingsCockpitLabel(),
            title = MainActivityBase.NAV_SETTINGS,
            body = SettingsTextCopy.settingsHeroBody(),
            rows = listOf(
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.noteTypeStatusLabel(),
                        StudyTextCopy.compact(current.modelName, 56),
                        SettingsAutomationHeroColors.studyPlum,
                    ),
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.importFiltersStatusLabel(),
                        StudyTextCopy.compact(SettingsTextCopy.settingsImportSummary(current), 56),
                        SettingsAutomationHeroColors.teal,
                    ),
                ),
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.importRanksStatusLabel(),
                        StudyTextCopy.compact("${current.suspendedRankMin}-${current.suspendedRankMax}", 56),
                        SettingsAutomationHeroColors.teal,
                    ),
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.reminderStatusLabel(),
                        StudyTextCopy.compact(
                            SettingsTextCopy.settingsReminderSummary(
                                reminder.enabled,
                                reminderBlocked,
                                reminder.displayTime(),
                            ),
                            56,
                        ),
                        if (reminder.enabled) SettingsAutomationHeroColors.teal else SettingsAutomationHeroColors.muted,
                    ),
                ),
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.dailySyncStatusLabel(),
                        StudyTextCopy.compact(
                            SettingsTextCopy.settingsAutoSyncSummary(
                                autoSync.configured,
                                autoSync.enabled,
                                autoSync.displayTime(),
                            ),
                            56,
                        ),
                        if (autoSync.enabled) SettingsAutomationHeroColors.teal else SettingsAutomationHeroColors.muted,
                    ),
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.updatesStatusLabel(),
                        StudyTextCopy.compact(
                            SettingsTextCopy.settingsUpdateSummary(
                                autoUpdate.hasPendingUpdate(),
                                autoUpdate.enabled,
                            ),
                            56,
                        ),
                        if (autoUpdate.hasPendingUpdate()) {
                            SettingsAutomationHeroColors.coral
                        } else {
                            SettingsAutomationHeroColors.studyPinkDark
                        },
                    ),
                ),
                listOf(
                    SettingsAutomationHeroPillModel(
                        SettingsTextCopy.matchingCardsStatusLabel(),
                        StudyTextCopy.compact(SettingsTextCopy.matchingCardsSummary(current), 56),
                        SettingsAutomationHeroColors.studyPlum,
                    ),
                ),
            ),
        )
    }

    private fun settingsCategoryModels(current: RecordsSyncModels.Settings): List<SettingsCategorySectionModel> {
        return listOf(
            settingsAnkiSourceCategoryModel(
                activity.settingsAnkiExpanded,
                Runnable {
                    activity.settingsAnkiExpanded = !activity.settingsAnkiExpanded
                    activity.renderSettings(true)
                },
                activity.noteTypeSettingsPanelModel(current),
                activity.importFilterSettingsPanelModel(current),
                activity.frequencyRangeSettingsPanelModel(current),
            ),
            settingsStudyBehaviorCategoryModel(
                activity.settingsStudyExpanded,
                Runnable {
                    activity.settingsStudyExpanded = !activity.settingsStudyExpanded
                    activity.renderSettings(true)
                },
                MainActivitySettingsStudySortPanel(activity).newCardSortSettingsPanelModel(current),
                MainActivitySettingsDeckLimitsPanel(activity).deckLimitsSettingsPanelModel(current),
                MainActivitySettingsWorkloadPanel(activity).workloadSettingsPanelModel(),
                MainActivitySettingsRetentionPanel(activity).retentionSettingsPanelModel(),
                activity.learningStepsSettingsPanelModel(),
                MainActivitySettingsStudyAheadPanel(activity).studyAheadSettingsPanelModel(),
                MainActivitySettingsStudyLadder(activity).studyLadderSettingsPanelModel(),
                activity.ladderThresholdSettingsPanelModel(),
            ),
            settingsAutomationCategoryModel(
                activity.settingsSyncExpanded,
                Runnable {
                    activity.settingsSyncExpanded = !activity.settingsSyncExpanded
                    activity.renderSettings(true)
                },
                activity.reminderSettingsPanelModel(),
                activity.autoSyncSettingsPanelModel(),
                SettingsUpdateOverviewPanelModel(
                    settingsUpdatePanelModel(
                        activity = activity,
                        title = SettingsTextCopy.appUpdatesTitle(),
                    ),
                    SettingsTextCopy.openUpdaterLabel(),
                ) {
                    activity.renderUpdate()
                },
            ),
            settingsReferenceDataCategoryModel(
                activity.settingsAppExpanded,
                Runnable {
                    activity.settingsAppExpanded = !activity.settingsAppExpanded
                    activity.renderSettings(true)
                },
                MainActivitySettingsReferenceData(activity).dataLicenseSettingsPanelModel(),
            ),
        )
    }
}
