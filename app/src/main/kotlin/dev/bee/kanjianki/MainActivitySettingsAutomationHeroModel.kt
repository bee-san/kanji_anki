package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.reminders.ReminderScheduler

data class SettingsAutomationHeroPillModel(
    val label: String,
    val value: String,
    val valueColor: Int,
)

data class SettingsAutomationHeroModel(
    val cockpitLabel: String,
    val title: String,
    val body: String,
    val rows: List<List<SettingsAutomationHeroPillModel>>,
)

internal fun settingsAutomationHeroModel(
    activity: MainActivitySettings,
    current: RecordsSyncModels.Settings,
    reminder: LocalStoreBase.ReminderSettings,
    autoSync: LocalStoreBase.AutoSyncSettings,
    autoUpdate: LocalStoreBase.AutoUpdateStatus
): SettingsAutomationHeroModel {
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
                    MainActivityUiSupport.STUDY_PLUM
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.importFiltersStatusLabel(),
                    StudyTextCopy.compact(SettingsTextCopy.settingsImportSummary(current), 56),
                    MainActivityUiSupport.TEAL
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.importRanksStatusLabel(),
                    StudyTextCopy.compact("${current.suspendedRankMin}-${current.suspendedRankMax}", 56),
                    MainActivityUiSupport.TEAL
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.reminderStatusLabel(),
                    StudyTextCopy.compact(
                        SettingsTextCopy.settingsReminderSummary(
                            reminder.enabled,
                            reminderBlocked,
                            reminder.displayTime()
                        ),
                        56
                    ),
                    if (reminder.enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.dailySyncStatusLabel(),
                    StudyTextCopy.compact(
                        SettingsTextCopy.settingsAutoSyncSummary(
                            autoSync.configured,
                            autoSync.enabled,
                            autoSync.displayTime()
                        ),
                        56
                    ),
                    if (autoSync.enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.updatesStatusLabel(),
                    StudyTextCopy.compact(
                        SettingsTextCopy.settingsUpdateSummary(
                            autoUpdate.hasPendingUpdate(),
                            autoUpdate.enabled
                        ),
                        56
                    ),
                    if (autoUpdate.hasPendingUpdate()) MainActivityUiSupport.CORAL else MainActivityUiSupport.STUDY_PINK_DARK
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.matchingCardsStatusLabel(),
                    StudyTextCopy.compact(SettingsTextCopy.matchingCardsSummary(current), 56),
                    MainActivityUiSupport.STUDY_PLUM
                )
            )
        )
    )
}
