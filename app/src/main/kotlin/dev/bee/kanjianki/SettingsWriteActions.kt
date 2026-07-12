package dev.bee.kanjianki

import dev.bee.kanjianki.core.LearningStepsSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.SettingsImportPreset
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy
import dev.bee.kanjianki.core.WorkloadSettingsPolicy
import dev.bee.kanjianki.sync.SyncSettings

internal object SettingsWriteActions {
    @JvmStatic
    fun saveLadderThresholds(
        request: StudyLadderThresholdPolicy.SaveResult?,
        writer: IntSettingWriter,
    ) {
        if (request == null || !request.valid) {
            return
        }
        writer.putIntSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, request.promotionDays)
        writer.putIntSetting(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, request.failStreak)
        // real_due_reviews_to_move stays as the legacy read fallback for the
        // demotion fail streak. writing_trigger_miss_days is a *days* value
        // and must not be overwritten with a fail-streak count.
        writer.putIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, request.failStreak)
    }

    @JvmStatic
    fun saveNoteTypeFields(
        request: NoteTypeFieldWriteRequest,
        writer: StringSettingWriter,
    ) {
        writer.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, request.noteType)
        writer.putStringSetting(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, request.expressionField)
        writer.putStringSetting(SyncSettings.READING_FIELD_SETTING_KEY, request.readingField)
        writer.putStringSetting(SyncSettings.MEANING_FIELD_SETTING_KEY, request.meaningField)
        writer.putStringSetting(SyncSettings.SENTENCE_FIELD_SETTING_KEY, request.sentenceField)
        writer.putStringSetting(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, request.frequencyField)
        writer.putStringSetting(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY, request.frequencySortField)
    }

    @JvmStatic
    fun saveLearningSteps(
        request: LearningStepsSettingsPolicy.SaveResult?,
        writer: LearningStepSettingsWriter,
    ) {
        if (request == null || !request.valid) {
            return
        }
        writer.saveLearningStepSettings(request.settings!!)
    }

    @JvmStatic
    fun toggleStudyLadder(
        current: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
    ): RecordsBase.StudyLadderSettings? {
        val wasEnabled = current.isEnabled(rung)
        val next = current.withRungEnabled(rung, !wasEnabled)
        return if (wasEnabled && next.enabledText() == current.enabledText()) {
            null
        } else {
            next
        }
    }

    @JvmStatic
    fun moveStudyLadder(
        current: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
        delta: Int,
    ): RecordsBase.StudyLadderSettings {
        return current.moveRung(rung, delta)
    }

    /**
     * Reorders only adaptive repair tools. Core checks and presentation variants
     * keep their fixed roles even though their legacy ladder positions remain
     * stored for backup and downgrade compatibility.
     */
    @JvmStatic
    fun moveStudySupportPriority(
        current: RecordsBase.StudyLadderSettings,
        rung: RecordsBase.LadderRung,
        delta: Int,
    ): RecordsBase.StudyLadderSettings {
        return moveStudySupportPriority(current, rung.wireName(), delta)
    }

    @JvmStatic
    fun moveStudySupportPriority(
        current: RecordsBase.StudyLadderSettings,
        taskType: String,
        delta: Int,
    ): RecordsBase.StudyLadderSettings {
        return current.moveRepairTask(taskType, delta)
    }

    @JvmStatic
    fun toggleStudyRepair(
        current: RecordsBase.StudyLadderSettings,
        taskType: String,
    ): RecordsBase.StudyLadderSettings {
        return current.withRepairTaskEnabled(taskType, !current.isRepairTaskEnabled(taskType))
    }

    @JvmStatic
    fun saveWorkload(
        request: WorkloadSettingsPolicy.SaveRequest,
        writer: WorkloadSettingsWriter,
    ) {
        request.mode?.let(writer::saveAdaptiveLoadMode)
        request.workloadPercent?.let(writer::saveAdaptiveLoadWorkPercent)
        request.maxItems?.let(writer::saveAdaptiveLoadMaxItems)
    }

    @JvmStatic
    fun saveImportFilters(
        request: ImportFilterWriteRequest,
        writer: SettingWriter,
    ) {
        writer.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.activeCards))
        writer.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.suspendedCards))
        writer.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.taggedCards))
        writer.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, request.tags)
        writer.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(request.weakCards))
        writer.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, request.weakDifficulty)
        writer.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, request.weakLapses)
        writer.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, request.minMatchingCards)
        writer.putIntSetting(
            SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY,
            SettingsImportPreset.boolFlag(request.browserQueryCards),
        )
        writer.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, request.browserQuery)
        request.tagRepairedCards?.let {
            writer.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, SettingsImportPreset.boolFlag(it))
        }
    }

    fun interface IntSettingWriter {
        fun putIntSetting(key: String, value: Int)
    }

    fun interface StringSettingWriter {
        fun putStringSetting(key: String, value: String?)
    }

    fun interface LearningStepSettingsWriter {
        fun saveLearningStepSettings(settings: RecordsSchedulerModels.LearningStepSettings)
    }

    interface WorkloadSettingsWriter {
        fun saveAdaptiveLoadMode(mode: String)

        fun saveAdaptiveLoadWorkPercent(workloadPercent: Int)

        fun saveAdaptiveLoadMaxItems(maxItems: Int)
    }

    interface SettingWriter : IntSettingWriter {
        fun putStringSetting(key: String, value: String?)

        fun putDoubleSetting(key: String, value: Double)
    }

    @JvmRecord
    internal data class ImportFilterWriteRequest(
        val activeCards: Boolean,
        val suspendedCards: Boolean,
        val taggedCards: Boolean,
        val tags: String?,
        val weakCards: Boolean,
        val weakDifficulty: Double,
        val weakLapses: Int,
        val minMatchingCards: Int,
        val browserQueryCards: Boolean,
        val browserQuery: String?,
        val tagRepairedCards: Boolean? = null,
    )

    @JvmRecord
    internal data class NoteTypeFieldWriteRequest(
        val noteType: String?,
        val expressionField: String?,
        val readingField: String?,
        val meaningField: String?,
        val sentenceField: String?,
        val frequencyField: String?,
        val frequencySortField: String?,
    )
}
