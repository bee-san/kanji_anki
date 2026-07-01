package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.LocalStoreBase

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
    val onLongPress: Runnable? = null,
)

internal object SettingsAutomationHeroColors {
    val muted: Int = 0xFF6C5674.toInt()
    val coral: Int = 0xFFFF4C76.toInt()
    val teal: Int = 0xFF00AEB5.toInt()
    val studyPlum: Int = 0xFF4B2552.toInt()
    val studyPinkDark: Int = 0xFFDA3A7A.toInt()
}

internal fun settingsAutomationHeroModel(
    current: RecordsSyncModels.Settings,
    reminder: LocalStoreBase.ReminderSettings,
    autoSync: LocalStoreBase.AutoSyncSettings,
    autoUpdate: LocalStoreBase.AutoUpdateStatus,
    notificationsAllowed: Boolean,
): SettingsAutomationHeroModel {
    val reminderBlocked = reminder.enabled && !notificationsAllowed
    return SettingsAutomationHeroModel(
        cockpitLabel = SettingsTextCopy.settingsCockpitLabel(),
        title = SettingsTextCopy.settingsTitle(),
        body = SettingsTextCopy.settingsHeroBody(),
        rows = listOf(
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.noteTypeStatusLabel(),
                    StudyTextCopy.compact(current.modelName, 56),
                    SettingsAutomationHeroColors.studyPlum
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.importFiltersStatusLabel(),
                    StudyTextCopy.compact(SettingsTextCopy.settingsImportSummary(current), 56),
                    SettingsAutomationHeroColors.teal
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.importRanksStatusLabel(),
                    StudyTextCopy.compact("${current.suspendedRankMin}-${current.suspendedRankMax}", 56),
                    SettingsAutomationHeroColors.teal
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
                    if (reminder.enabled) SettingsAutomationHeroColors.teal else SettingsAutomationHeroColors.muted
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
                    if (autoSync.enabled) SettingsAutomationHeroColors.teal else SettingsAutomationHeroColors.muted
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
                    if (autoUpdate.hasPendingUpdate()) {
                        SettingsAutomationHeroColors.coral
                    } else {
                        SettingsAutomationHeroColors.studyPinkDark
                    }
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.matchingCardsStatusLabel(),
                    StudyTextCopy.compact(SettingsTextCopy.matchingCardsSummary(current), 56),
                    SettingsAutomationHeroColors.studyPlum
                )
            )
        )
    )
}
