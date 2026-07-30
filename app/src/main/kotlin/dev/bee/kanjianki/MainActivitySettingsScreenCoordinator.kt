package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsSectionTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsScreenCoordinator(private val activity: MainActivitySettings) {
    fun settingsScreenModel(): SettingsScreenModel {
        return settingsScreenModel(
            cards = settingsHubCardModels(),
            onHome = Runnable { activity.renderHome() },
        )
    }

    fun settingsImportSyncScreenModel(): SettingsSubmenuScreenModel {
        val snapshot = activity.loadSettingsSnapshot()
        val current = snapshot.sync
        val device = activity.loadSettingsDeviceState()
        return submenuScreenModel(
            title = SettingsTextCopy.settingsAnkiSourceTitle(),
            body = SettingsTextCopy.settingsAnkiSourceBody(),
            panels = listOf(
                activity.noteTypeSettingsPanelModel(current),
                activity.importFilterSettingsPanelModel(current, snapshot.tagRepairedCards),
                activity.frequencyRangeSettingsPanelModel(current),
                activity.autoSyncSettingsPanelModel(device),
            ),
        )
    }

    fun settingsStudyBehaviorScreenModel(): SettingsSubmenuScreenModel {
        val snapshot = activity.loadSettingsSnapshot()
        val current = snapshot.sync
        return submenuScreenModel(
            title = SettingsTextCopy.settingsStudyBehaviorTitle(),
            body = SettingsTextCopy.settingsStudyBehaviorBody(),
            panels = listOf(
                MainActivitySettingsStudySortPanel(activity).newCardSortSettingsPanelModel(current),
                MainActivitySettingsDeckLimitsPanel(activity).deckLimitsSettingsPanelModel(current),
                MainActivitySettingsWorkloadPanel(activity).workloadSettingsPanelModel(snapshot),
                MainActivitySettingsRetentionPanel(activity).retentionSettingsPanelModel(snapshot),
                MainActivitySettingsPersonalizedScheduling(activity).panelModel(snapshot),
                activity.learningStepsSettingsPanelModel(snapshot),
                MainActivitySettingsStudyAheadPanel(activity).studyAheadSettingsPanelModel(snapshot),
                MainActivitySettingsStudyLadder(activity).studyLadderSettingsPanelModel(snapshot),
                activity.ladderThresholdSettingsPanelModel(snapshot),
                MainActivitySettingsFlashcardGesture(activity).panelModel(),
            ),
        )
    }

    fun settingsAutomationScreenModel(): SettingsSubmenuScreenModel {
        val device = activity.loadSettingsDeviceState()
        return submenuScreenModel(
            title = SettingsTextCopy.settingsAutomationTitle(),
            body = SettingsTextCopy.settingsAutomationBody(),
            panels = listOf(
                activity.reminderSettingsPanelModel(device),
                SettingsUpdateOverviewPanelModel(
                    settingsUpdatePanelModel(
                        activity = activity,
                        title = SettingsTextCopy.appUpdatesTitle(),
                        status = device.autoUpdate,
                        betaUpdatesEnabled = device.betaUpdatesEnabled,
                    ),
                    SettingsTextCopy.openUpdaterLabel(),
                ) {
                    activity.renderUpdate()
                },
                activity.debugLogSettingsPanelModel(device),
                activity.backupSettingsPanelModel(),
            ),
        )
    }

    fun settingsAppearanceScreenModel(): SettingsSubmenuScreenModel {
        val snapshot = activity.loadSettingsSnapshot()
        return submenuScreenModel(
            title = SettingsTextCopy.settingsAppearanceTitle(),
            body = SettingsTextCopy.settingsAppearanceBody(),
            panels = listOf(
                activity.themeSettingsPanelModel(snapshot),
            ),
        )
    }

    fun settingsDisplayDataScreenModel(): SettingsSubmenuScreenModel {
        val referenceData = MainActivitySettingsReferenceData(activity)
        val panels = buildList {
            add(referenceData.dataLicenseSettingsPanelModel())
            add(SettingsReferenceDataLinkModel(
                title = dev.bee.kanjianki.core.HowKaniWorksCopy.pageTitle(),
                body = dev.bee.kanjianki.core.HomeTextCopy.howKaniWorksLinkBody(),
                actionLabel = dev.bee.kanjianki.core.HomeTextCopy.howKaniWorksLinkAction(),
                onAction = Runnable { activity.renderHowItWorks() },
            ))
            referenceData.shareDebugLogPanelModelOrNull()?.let(::add)
        }
        return submenuScreenModel(
            title = SettingsTextCopy.settingsReferenceDataTitle(),
            body = SettingsTextCopy.settingsReferenceDataBody(),
            panels = panels,
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
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(10),
                contentDescription = SettingsSectionTextCopy.sectionOpenDescription(SettingsTextCopy.settingsStudyBehaviorTitle()),
                onOpen = Runnable { activity.renderSettingsStudyBehavior() },
            ),
            SettingsHubCardModel(
                routeKey = MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE,
                title = SettingsTextCopy.settingsAutomationTitle(),
                summary = SettingsTextCopy.settingsAutomationBody(),
                iconRes = R.drawable.ic_sync_24,
                panelCount = SettingsSectionTextCopy.settingsCategoryPanelCount(4),
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
