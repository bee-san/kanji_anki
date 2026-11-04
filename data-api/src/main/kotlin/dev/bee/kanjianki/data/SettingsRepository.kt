package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels

/** Portable scheduler, import, study, and appearance settings. */
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
    val fsrsPersonalizationEnabled: Boolean,
    val fsrsFitSummaryJson: String,
)

sealed interface SettingsSaveCommand {
    data class Sync(
        val settings: RecordsSyncModels.Settings,
        val tagRepairedCards: Boolean,
    ) : SettingsSaveCommand

    data class NoteTypeFields(
        val modelName: String,
        val expressionField: String,
        val readingField: String,
        val meaningField: String,
        val sentenceField: String,
        val frequencyField: String,
        val frequencySortField: String,
    ) : SettingsSaveCommand

    data class ImportFilters(
        val activeCards: Boolean,
        val suspendedCards: Boolean,
        val taggedCards: Boolean,
        val tags: String,
        val weakCards: Boolean,
        val weakDifficulty: Double,
        val weakLapses: Int,
        val minMatchingCards: Int,
        val browserQueryCards: Boolean,
        val browserQuery: String,
        val tagRepairedCards: Boolean,
    ) : SettingsSaveCommand

    data class FrequencyRange(val minRank: Int, val maxRank: Int) : SettingsSaveCommand

    data class DeckLimits(val newPerDay: Int, val activeQueueCap: Int) : SettingsSaveCommand

    data class LadderThresholds(
        val promotionIntervalDays: Int,
        val demotionFailStreak: Int,
    ) : SettingsSaveCommand

    data class AdaptiveWorkload(val value: AdaptiveWorkloadSnapshot) : SettingsSaveCommand

    data class StudyAhead(val minutes: Int) : SettingsSaveCommand

    data class StudyLadder(val value: RecordsBase.StudyLadderSettings) : SettingsSaveCommand

    data class NewCardSort(val mode: String) : SettingsSaveCommand

    data class Theme(val choice: KaniThemeChoice) : SettingsSaveCommand

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
