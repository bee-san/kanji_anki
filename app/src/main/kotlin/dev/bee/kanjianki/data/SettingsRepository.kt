package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StoreResult
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy

/** Typed settings persistence for feature and platform consumers. */
interface SettingsRepository {
    suspend fun load(): StoreResult<SettingsSnapshot>

    suspend fun save(command: SettingsSaveCommand): StoreResult<Unit>

    suspend fun commitFsrsFit(command: CommitFsrsFitCommand): StoreResult<Boolean>
}

data class SettingsSnapshot(
    val sync: RecordsSyncModels.Settings,
    val tagRepairedCards: Boolean,
    val adaptiveWorkload: AdaptiveWorkloadSnapshot,
    val studyAheadMinutes: Int,
    val studyLadder: RecordsBase.StudyLadderSettings,
    val schedulerParameters: RecordsSchedulerModels.SchedulerParameters,
    val schedulerFsrsWeights: List<Double>?,
    val learningSteps: RecordsSchedulerModels.LearningStepSettings,
    val themeChoice: KaniThemeChoice,
    val reminder: ReminderSettingsSnapshot,
    val reminderAntiSpam: ReminderAntiSpamSettingsSnapshot,
    val autoSync: AutoSyncSettingsSnapshot,
    val autoUpdate: AutoUpdateStatusSnapshot,
    val debugLogEnabled: Boolean,
    val fsrsPersonalizationEnabled: Boolean,
    val fsrsFitSummaryJson: String,
    val updateCheckFailedAtMillis: Long,
    val installPermissionPromptShown: Boolean,
    val installPermissionPromptLastVersion: String,
)

data class ReminderSettingsSnapshot(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
) {
    fun normalized(): ReminderSettingsSnapshot {
        val value = TimeOfDaySettingsPolicy.normalizeReminder(enabled, hour, minute)
        return ReminderSettingsSnapshot(value.enabled, value.hour, value.minute)
    }

    fun displayTime(): String = TimeOfDaySettingsPolicy.displayTime(hour, minute)
}

data class ReminderAntiSpamSettingsSnapshot(
    val quietStartMinuteOfDay: Int,
    val quietEndMinuteOfDay: Int,
    val maxRemindersPerDay: Int,
)

data class AutoSyncSettingsSnapshot(
    val configured: Boolean,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val lastAttemptAtMillis: Long,
    val lastSuccessAtMillis: Long,
    val nextRunAtMillis: Long,
) {
    fun normalized(): AutoSyncSettingsSnapshot {
        val value = TimeOfDaySettingsPolicy.normalizeAutoSync(
            configured,
            enabled,
            hour,
            minute,
            lastAttemptAtMillis,
            lastSuccessAtMillis,
            nextRunAtMillis,
        )
        return AutoSyncSettingsSnapshot(
            value.configured,
            value.enabled,
            value.hour,
            value.minute,
            value.lastAttemptAtMillis,
            value.lastSuccessAtMillis,
            value.nextRunAtMillis,
        )
    }

    fun displayTime(): String = TimeOfDaySettingsPolicy.displayTime(hour, minute)
}

data class AutoUpdateStatusSnapshot(
    val enabled: Boolean,
    val lastCheckAtMillis: Long,
    val lastResult: String,
    val lastVersion: String,
    val pendingApkName: String,
    val pendingMessage: String,
) {
    fun hasPendingUpdate(): Boolean = AutoUpdateStatusPolicy.hasPendingUpdate(pendingApkName)
}

sealed interface SettingsSaveCommand {
    data class Sync(
        val settings: RecordsSyncModels.Settings,
        val tagRepairedCards: Boolean,
    ) : SettingsSaveCommand

    data class AdaptiveWorkload(val value: AdaptiveWorkloadSnapshot) : SettingsSaveCommand

    data class StudyAhead(val minutes: Int) : SettingsSaveCommand

    data class StudyLadder(val value: RecordsBase.StudyLadderSettings) : SettingsSaveCommand

    data class NewCardSort(val mode: String) : SettingsSaveCommand

    data class Theme(val choice: KaniThemeChoice) : SettingsSaveCommand

    data class Reminder(val value: ReminderSettingsSnapshot) : SettingsSaveCommand

    data class ReminderAntiSpam(val value: ReminderAntiSpamSettingsSnapshot) : SettingsSaveCommand

    data class ReminderPosted(
        val postedAtMillis: Long,
        val family: String,
        val signature: String,
        val dailyTimeOverride: Boolean,
    ) : SettingsSaveCommand

    data class ReminderDismissed(
        val dismissedAtMillis: Long,
        val family: String,
    ) : SettingsSaveCommand

    data class AutoSync(val value: AutoSyncSettingsSnapshot) : SettingsSaveCommand

    data class AutoSyncEnabled(val enabled: Boolean) : SettingsSaveCommand

    data class AutoSyncScheduled(val nextRunAtMillis: Long) : SettingsSaveCommand

    data class AutoSyncAttempt(
        val attemptedAtMillis: Long,
        val success: Boolean,
    ) : SettingsSaveCommand

    data class AutoUpdateEnabled(val enabled: Boolean) : SettingsSaveCommand

    data class AutoUpdateResult(
        val checkedAtMillis: Long,
        val result: String,
        val version: String,
        val pendingApkName: String,
        val pendingMessage: String,
    ) : SettingsSaveCommand

    data class ClearPendingAutoUpdate(val result: String) : SettingsSaveCommand

    data class UpdateCheckFailed(val failedAtMillis: Long) : SettingsSaveCommand

    data object ClearUpdateCheckFailed : SettingsSaveCommand

    data class InstallPermissionPrompted(val version: String) : SettingsSaveCommand

    data class DebugLogEnabled(val enabled: Boolean) : SettingsSaveCommand

    data class SchedulerParameters(
        val value: RecordsSchedulerModels.SchedulerParameters,
    ) : SettingsSaveCommand

    data class SchedulerFsrsWeights(val weights: List<Double>?) : SettingsSaveCommand

    data class FsrsPersonalizationEnabled(val enabled: Boolean) : SettingsSaveCommand

    data class FsrsFitSummary(val summaryJson: String) : SettingsSaveCommand

    data object ResetFsrsPersonalization : SettingsSaveCommand

    data class LearningSteps(
        val value: RecordsSchedulerModels.LearningStepSettings,
    ) : SettingsSaveCommand
}

data class CommitFsrsFitCommand(
    val weightsToAdopt: List<Double>?,
    val summaryJson: String,
    val disabledSummaryJson: String?,
    val preserveExistingWeights: Boolean,
)
