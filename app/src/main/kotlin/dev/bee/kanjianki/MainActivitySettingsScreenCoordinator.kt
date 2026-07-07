package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsSectionTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.reminders.ReminderScheduler

internal class MainActivitySettingsScreenCoordinator(private val activity: MainActivitySettings) {
    fun settingsScreenModel(): SettingsScreenModel {
        val current = activity.settings()
        return settingsScreenModel(
            hero = settingsHeroModel(current),
            cards = settingsHubCardModels(),
            onHome = Runnable { activity.renderHome() },
        )
    }

    fun settingsImportSyncScreenModel(): SettingsSubmenuScreenModel {
        val current = activity.settings()
        return submenuScreenModel(
            title = SettingsTextCopy.settingsAnkiSourceTitle(),
            body = SettingsTextCopy.settingsAnkiSourceBody(),
            panels = listOf(
                activity.noteTypeSettingsPanelModel(current),
                activity.importFilterSettingsPanelModel(current),
                activity.frequencyRangeSettingsPanelModel(current),
                activity.autoSyncSettingsPanelModel(),
            ),
        )
    }

    fun settingsStudyBehaviorScreenModel(): SettingsSubmenuScreenModel {
        val current = activity.settings()
        return submenuScreenModel(
            title = SettingsTextCopy.settingsStudyBehaviorTitle(),
            body = SettingsTextCopy.settingsStudyBehaviorBody(),
            panels = listOf(
                MainActivitySettingsStudySortPanel(activity).newCardSortSettingsPanelModel(current),
                MainActivitySettingsDeckLimitsPanel(activity).deckLimitsSettingsPanelModel(current),
                MainActivitySettingsWorkloadPanel(activity).workloadSettingsPanelModel(),
                MainActivitySettingsRetentionPanel(activity).retentionSettingsPanelModel(),
                activity.learningStepsSettingsPanelModel(),
                MainActivitySettingsStudyAheadPanel(activity).studyAheadSettingsPanelModel(),
                MainActivitySettingsStudyLadder(activity).studyLadderSettingsPanelModel(),
                activity.ladderThresholdSettingsPanelModel(),
            ),
        )
    }

    fun settingsAutomationScreenModel(): SettingsSubmenuScreenModel {
        return submenuScreenModel(
            title = SettingsTextCopy.settingsAutomationTitle(),
            body = SettingsTextCopy.settingsAutomationBody(),
            panels = listOf(
                activity.reminderSettingsPanelModel(),
                SettingsUpdateOverviewPanelModel(
                    settingsUpdatePanelModel(
                        activity = activity,
                        title = SettingsTextCopy.appUpdatesTitle(),
                    ),
                    SettingsTextCopy.openUpdaterLabel(),
                ) {
                    activity.renderUpdate()
                },
                activity.debugLogSettingsPanelModel(),
            ),
        )
    }

    fun settingsAppearanceScreenModel(): SettingsSubmenuScreenModel {
        return submenuScreenModel(
            title = SettingsTextCopy.settingsAppearanceTitle(),
            body = SettingsTextCopy.settingsAppearanceBody(),
            panels = listOf(
                activity.themeSettingsPanelModel(),
            ),
        )
    }

    fun settingsDisplayDataScreenModel(): SettingsSubmenuScreenModel {
        val referenceData = MainActivitySettingsReferenceData(activity)
        val panels = buildList {
            add(referenceData.dataLicenseSettingsPanelModel())
            referenceData.shareDebugLogPanelModelOrNull()?.let(::add)
        }
        return submenuScreenModel(
            title = SettingsTextCopy.settingsReferenceDataTitle(),
            body = SettingsTextCopy.settingsReferenceDataBody(),
            panels = panels,
        )
    }

    private fun settingsHeroModel(current: RecordsSyncModels.Settings): SettingsAutomationHeroModel {
        val reminder = activity.store.reminderSettings()
        val autoSync = activity.store.autoSyncSettings()
        val autoUpdate = activity.store.autoUpdateStatus()
        val reminderBlocked = reminder.enabled && !ReminderScheduler.notificationsAllowed(activity)
        return SettingsAutomationHeroModel(
            cockpitLabel = SettingsTextCopy.settingsCockpitLabel(),
            title = SettingsTextCopy.settingsTitle(),
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

    private fun settingsHubCardModels(): List<SettingsHubCardModel> {
        return listOf(
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE,
                title = SettingsTextCopy.settingsAnkiSourceTitle(),
                summary = SettingsTextCopy.settingsAnkiSourceBody(),
                iconRes = R.drawable.ic_book_24,
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(4),
                contentDescription = SettingsSectionTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAnkiSourceTitle()),
                onOpen = Runnable { activity.renderSettingsImportSync() },
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
                title = SettingsTextCopy.settingsStudyBehaviorTitle(),
                summary = SettingsTextCopy.settingsStudyBehaviorBody(),
                iconRes = R.drawable.ic_study_24,
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(8),
                contentDescription = SettingsSectionTextCopy.sectionOpenDescription(SettingsTextCopy.settingsStudyBehaviorTitle()),
                onOpen = Runnable { activity.renderSettingsStudyBehavior() },
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE,
                title = SettingsTextCopy.settingsAutomationTitle(),
                summary = SettingsTextCopy.settingsAutomationBody(),
                iconRes = R.drawable.ic_sync_24,
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(3),
                contentDescription = SettingsSectionTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAutomationTitle()),
                onOpen = Runnable { activity.renderSettingsAutomation() },
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE,
                title = SettingsTextCopy.settingsAppearanceTitle(),
                summary = SettingsTextCopy.settingsAppearanceBody(),
                iconRes = R.drawable.ic_eye_24,
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(1),
                contentDescription = SettingsSectionTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAppearanceTitle()),
                onOpen = Runnable { activity.renderSettingsAppearance() },
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE,
                title = SettingsTextCopy.settingsReferenceDataTitle(),
                summary = SettingsTextCopy.settingsReferenceDataBody(),
                iconRes = R.drawable.ic_sparkle_24,
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(1),
                contentDescription = SettingsSectionTextCopy.sectionOpenDescription(SettingsTextCopy.settingsReferenceDataTitle()),
                onOpen = Runnable { activity.renderSettingsDisplayData() },
            ),
        )
    }

    private fun submenuScreenModel(
        title: String,
        body: String,
        panels: List<SettingsPanelModel>,
    ): SettingsSubmenuScreenModel {
        return SettingsSubmenuScreenModel(
            homeLabel = HomeTextCopy.homeLabel(),
            onHome = Runnable { activity.renderHome() },
            backLabel = SettingsTextCopy.backToSettingsLabel(),
            onBack = Runnable { activity.renderSettings(true) },
            title = title,
            body = body,
            panels = panels,
        )
    }
}
