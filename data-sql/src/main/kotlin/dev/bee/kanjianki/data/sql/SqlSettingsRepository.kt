package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.FsrsPersonalization
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingValuePolicy
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.core.SyncSettingsStore
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.CommitFsrsFitCommand
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import java.util.Locale

/**
 * Driver-neutral settings persistence. Android keeps using its LocalStore
 * facade until the Goal 184 composition switch.
 */
class SqlSettingsRepository(
    private val database: SqlDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : SettingsRepository {
    override suspend fun load() = safeSqlStoreCall {
        val values = database.readSnapshot { loadSettings() }
        val snapshotStore = SnapshotSettingsStore(values)
        val snapshot = snapshotStore.toSnapshot()
        if (snapshotStore.repairs.isNotEmpty()) {
            database.write {
                val writer = SettingsWriter(this, clock)
                snapshotStore.repairs.forEach(writer::putInt)
            }
        }
        snapshot
    }

    override suspend fun save(command: SettingsSaveCommand) = safeSqlStoreCall {
        database.write {
            val writer = SettingsWriter(this, clock)
            when (command) {
                is SettingsSaveCommand.Sync ->
                    writer.saveSync(command.settings, command.tagRepairedCards)
                is SettingsSaveCommand.NoteTypeFields -> {
                    writer.putString(SyncSettings.NOTE_TYPE_SETTING_KEY, command.modelName)
                    writer.putString(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, command.expressionField)
                    writer.putString(SyncSettings.READING_FIELD_SETTING_KEY, command.readingField)
                    writer.putString(SyncSettings.MEANING_FIELD_SETTING_KEY, command.meaningField)
                    writer.putString(SyncSettings.SENTENCE_FIELD_SETTING_KEY, command.sentenceField)
                    writer.putString(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, command.frequencyField)
                    writer.putString(
                        SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY,
                        command.frequencySortField,
                    )
                }
                is SettingsSaveCommand.ImportFilters -> {
                    writer.putBoolean(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, command.activeCards)
                    writer.putBoolean(
                        SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY,
                        command.suspendedCards,
                    )
                    writer.putBoolean(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, command.taggedCards)
                    writer.putString(SyncSettings.IMPORT_TAGS_SETTING_KEY, command.tags)
                    writer.putBoolean(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, command.weakCards)
                    writer.putDouble(
                        SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY,
                        command.weakDifficulty,
                    )
                    writer.putInt(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, command.weakLapses)
                    writer.putInt(
                        SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY,
                        command.minMatchingCards,
                    )
                    writer.putBoolean(
                        SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY,
                        command.browserQueryCards,
                    )
                    writer.putString(
                        SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY,
                        command.browserQuery,
                    )
                    writer.putBoolean(
                        SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY,
                        command.tagRepairedCards,
                    )
                }
                is SettingsSaveCommand.FrequencyRange -> {
                    writer.putInt(SUSPENDED_RANK_MIN_KEY, command.minRank)
                    writer.putInt(SUSPENDED_RANK_MAX_KEY, command.maxRank)
                }
                is SettingsSaveCommand.DeckLimits -> {
                    writer.putInt(SyncSettings.NEW_PER_DAY_SETTING_KEY, command.newPerDay)
                    writer.putInt(SyncSettings.ACTIVE_QUEUE_CAP_SETTING_KEY, command.activeQueueCap)
                }
                is SettingsSaveCommand.LadderThresholds -> {
                    writer.putInt(
                        SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
                        command.promotionIntervalDays,
                    )
                    writer.markStatsDirty()
                    writer.putInt(
                        SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
                        command.demotionFailStreak,
                    )
                    writer.markStatsDirty()
                    writer.putInt(
                        SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
                        command.demotionFailStreak,
                    )
                    writer.markStatsDirty()
                }
                is SettingsSaveCommand.AdaptiveWorkload -> {
                    writer.putInt(
                        AdaptiveLoadPlanner.SETTING_KEY,
                        AdaptiveLoadPlanner.snapWorkloadPercent(command.value.workPercent),
                    )
                    writer.putInt(
                        ADAPTIVE_LOAD_MAX_ITEMS_KEY,
                        AdaptiveLoadPlanner.normalizeMaxItems(command.value.maxItems),
                    )
                    writer.putString(
                        AdaptiveLoadPlanner.MODE_SETTING_KEY,
                        AdaptiveLoadPlanner.normalizeWorkloadMode(command.value.mode),
                    )
                }
                is SettingsSaveCommand.StudyAhead ->
                    writer.putInt(
                        STUDY_AHEAD_MINUTES_KEY,
                        SettingsInputRules.normalizeStudyAheadMinutes(command.minutes),
                    )
                is SettingsSaveCommand.StudyLadder -> {
                    writer.saveStudyLadder(command.value)
                    writer.markStatsDirty()
                }
                is SettingsSaveCommand.NewCardSort ->
                    writer.putString(
                        SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY,
                        NewCardSortSettingsPolicy.saveRequest(command.mode).mode,
                    )
                is SettingsSaveCommand.Theme ->
                    writer.putString(KaniThemeChoice.SETTING_KEY, command.choice.storageKey)
                is SettingsSaveCommand.SchedulerParameters ->
                    writer.saveSchedulerParameters(command.value)
                is SettingsSaveCommand.SchedulerFsrsWeights -> {
                    writer.putString(
                        FsrsPersonalization.WEIGHTS_SETTING_KEY,
                        command.weights?.toDoubleArray()?.let(FsrsPersonalization::encodeWeights)
                            .orEmpty(),
                    )
                    writer.markStatsDirty()
                }
                is SettingsSaveCommand.FsrsPersonalizationEnabled -> {
                    writer.putBoolean(FsrsPersonalization.ENABLED_SETTING_KEY, command.enabled)
                    if (!command.enabled) {
                        writer.putString(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
                        writer.markStatsDirty()
                    }
                }
                is SettingsSaveCommand.FsrsFitSummary ->
                    writer.putString(
                        FsrsPersonalization.FIT_SUMMARY_SETTING_KEY,
                        command.summaryJson,
                    )
                SettingsSaveCommand.ResetFsrsPersonalization -> {
                    writer.putString(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
                    writer.putString(FsrsPersonalization.FIT_SUMMARY_SETTING_KEY, "")
                    writer.markStatsDirty()
                }
                is SettingsSaveCommand.LearningSteps ->
                    writer.saveLearningSteps(command.value)
            }
        }
        Unit
    }

    override suspend fun commitFsrsFit(command: CommitFsrsFitCommand) = safeSqlStoreCall {
        database.write {
            val writer = SettingsWriter(this, clock)
            val encoded = command.weightsToAdopt
                ?.toDoubleArray()
                ?.let(FsrsPersonalization::encodeWeights)
            val enabled = writer.getInt(
                FsrsPersonalization.ENABLED_SETTING_KEY,
                FsrsPersonalization.ENABLED_SETTING_DEFAULT,
            ) == 1
            val adopted = encoded != null && enabled
            when {
                adopted -> {
                    writer.putString(FsrsPersonalization.WEIGHTS_SETTING_KEY, encoded)
                    writer.markStatsDirty()
                }
                encoded == null && !command.preserveExistingWeights -> {
                    writer.putString(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")
                    writer.markStatsDirty()
                }
            }
            writer.putString(
                FsrsPersonalization.FIT_SUMMARY_SETTING_KEY,
                if (encoded != null && !enabled) {
                    command.disabledSummaryJson ?: command.summaryJson
                } else {
                    command.summaryJson
                },
            )
            adopted
        }
    }

    private fun SqlReadScope.loadSettings(): Map<String, String> =
        queryList("SELECT key, value FROM settings ORDER BY key") { row ->
            row.text(0) to row.text(1)
        }.toMap(LinkedHashMap())

    private class SnapshotSettingsStore(
        initialValues: Map<String, String>,
    ) : SyncSettingsStore {
        private val values = LinkedHashMap(initialValues)
        val repairs = LinkedHashMap<String, Int>()

        override fun getIntSetting(key: String, fallback: Int): Int =
            values[key]?.let { SettingValuePolicy.parseInt(it, fallback) } ?: fallback

        override fun getStringSetting(key: String, fallback: String?): String? =
            values[key] ?: fallback

        override fun getDoubleSetting(key: String, fallback: Double): Double =
            values[key]?.let { SettingValuePolicy.parseDouble(it, fallback) } ?: fallback

        override fun putIntSetting(key: String, value: Int) {
            values[key] = value.toString()
            repairs[key] = value
        }

        fun toSnapshot(): SettingsSnapshot {
            val defaults = RecordsSchedulerModels.SchedulerParameters.defaults()
            val learningDefaults = RecordsSchedulerModels.LearningStepSettings.defaults()
            return SettingsSnapshot(
                sync = SyncSettings.fromStore(this),
                tagRepairedCards = SyncSettings.tagRepairedCards(this),
                adaptiveWorkload = AdaptiveWorkloadSnapshot(
                    workPercent = AdaptiveLoadPlanner.snapWorkloadPercent(
                        getIntSetting(
                            AdaptiveLoadPlanner.SETTING_KEY,
                            AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                        ),
                    ),
                    maxItems = AdaptiveLoadPlanner.normalizeMaxItems(
                        getIntSetting(
                            ADAPTIVE_LOAD_MAX_ITEMS_KEY,
                            AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
                        ),
                    ),
                    mode = AdaptiveLoadPlanner.normalizeWorkloadMode(
                        getStringSetting(
                            AdaptiveLoadPlanner.MODE_SETTING_KEY,
                            AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE,
                        ),
                    ),
                ),
                studyAheadMinutes = SettingsInputRules.normalizeStudyAheadMinutes(
                    getIntSetting(
                        STUDY_AHEAD_MINUTES_KEY,
                        SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES,
                    ),
                ),
                studyLadder = RecordsBase.StudyLadderSettings.fromStored(
                    getStringSetting(STUDY_LADDER_ORDER_KEY, ""),
                    getStringSetting(STUDY_LADDER_ENABLED_KEY, ""),
                    getStringSetting(ADAPTIVE_REPAIR_ORDER_KEY, null),
                    getStringSetting(ADAPTIVE_REPAIR_ENABLED_KEY, null),
                ),
                schedulerParameters = RecordsSchedulerModels.SchedulerParameters(
                    getDoubleSetting(SCHEDULER_TARGET_RETENTION_KEY, defaults.targetRetention),
                ).withFrequencyRetention(
                    getIntSetting(
                        SCHEDULER_FREQUENCY_RETENTION_ENABLED_KEY,
                        defaults.frequencyRetentionEnabled.toSettingInt(),
                    ) == 1,
                    getStringSetting(
                        SCHEDULER_FREQUENCY_RETENTION_RANGES_KEY,
                        defaults.frequencyRetentionRanges,
                    ),
                ),
                schedulerFsrsWeights = schedulerFsrsWeights(),
                learningSteps = RecordsSchedulerModels.LearningStepSettings(
                    RecordsSchedulerModels.LearningStepSettings.parseSteps(
                        getStringSetting(NEW_LEARNING_STEPS_KEY, learningDefaults.newStepsText()),
                        learningDefaults.newStepsMinutes,
                    ),
                    RecordsSchedulerModels.LearningStepSettings.parseSteps(
                        getStringSetting(
                            REVIEW_RELEARNING_STEPS_KEY,
                            learningDefaults.reviewStepsText(),
                        ),
                        learningDefaults.reviewStepsMinutes,
                        true,
                    ),
                ),
                themeChoice = KaniThemeChoice.fromStorageKey(
                    getStringSetting(KaniThemeChoice.SETTING_KEY, null),
                ),
                fsrsPersonalizationEnabled = getIntSetting(
                    FsrsPersonalization.ENABLED_SETTING_KEY,
                    FsrsPersonalization.ENABLED_SETTING_DEFAULT,
                ) == 1,
                fsrsFitSummaryJson = getStringSetting(
                    FsrsPersonalization.FIT_SUMMARY_SETTING_KEY,
                    "",
                ).orEmpty(),
            )
        }

        private fun schedulerFsrsWeights(): List<Double>? =
            try {
                FsrsPersonalization.decodeWeights(
                    getStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "").orEmpty(),
                )?.toList()
            } catch (_: RuntimeException) {
                null
            }
    }

    private class SettingsWriter(
        private val session: SqlSession,
        private val clock: () -> Long,
    ) {
        fun getInt(key: String, fallback: Int): Int {
            val value = session.queryOneOrNull(
                "SELECT value FROM settings WHERE key = ? LIMIT 1",
                bind = { bindText(1, key) },
            ) { row -> row.text(0) }
            return value?.let { SettingValuePolicy.parseInt(it, fallback) } ?: fallback
        }

        fun putInt(key: String, value: Int) = putString(key, value.toString())

        fun putBoolean(key: String, value: Boolean) = putInt(key, value.toSettingInt())

        fun putDouble(key: String, value: Double) =
            putString(key, String.format(Locale.ROOT, "%.4f", value))

        fun putString(key: String, value: String?) {
            session.executeBound(
                """
                INSERT INTO settings(key, value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
                """.trimIndent(),
            ) {
                bindText(1, key)
                bindText(2, value.orEmpty())
                bindLong(3, clock())
            }
        }

        fun markStatsDirty() {
            session.executeBound(
                """
                INSERT INTO stats_cache_state(key, value)
                VALUES (?, 2)
                ON CONFLICT(key) DO UPDATE SET value = value + 1
                """.trimIndent(),
            ) {
                bindText(1, STATS_SOURCE_VERSION_KEY)
            }
        }

        fun saveStudyLadder(settings: RecordsBase.StudyLadderSettings) {
            putString(STUDY_LADDER_ORDER_KEY, settings.orderText())
            putString(STUDY_LADDER_ENABLED_KEY, settings.enabledText())
            putString(ADAPTIVE_REPAIR_ORDER_KEY, settings.repairOrderText())
            putString(ADAPTIVE_REPAIR_ENABLED_KEY, settings.repairEnabledText())
        }

        fun saveSchedulerParameters(parameters: RecordsSchedulerModels.SchedulerParameters) {
            putDouble(SCHEDULER_TARGET_RETENTION_KEY, parameters.targetRetention)
            putBoolean(
                SCHEDULER_FREQUENCY_RETENTION_ENABLED_KEY,
                parameters.frequencyRetentionEnabled,
            )
            putString(
                SCHEDULER_FREQUENCY_RETENTION_RANGES_KEY,
                parameters.frequencyRetentionRanges,
            )
        }

        fun saveLearningSteps(settings: RecordsSchedulerModels.LearningStepSettings) {
            putString(NEW_LEARNING_STEPS_KEY, settings.newStepsText())
            putString(REVIEW_RELEARNING_STEPS_KEY, settings.reviewStepsText())
        }

        fun saveSync(settings: RecordsSyncModels.Settings, tagRepairedCards: Boolean) {
            val defaults = RecordsSyncModels.Settings.kikuDefaults()
            putString(SyncSettings.NOTE_TYPE_SETTING_KEY, settings.modelName)
            putString(SyncSettings.TEMPLATE_SETTING_KEY, settings.templateName)
            putString(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, settings.expressionField)
            putString(SyncSettings.READING_FIELD_SETTING_KEY, settings.readingField)
            putString(SyncSettings.MEANING_FIELD_SETTING_KEY, settings.meaningField)
            putString(SyncSettings.SENTENCE_FIELD_SETTING_KEY, settings.sentenceField)
            putString(SyncSettings.FREQUENCY_FIELD_SETTING_KEY, settings.frequencyField)
            putString(SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY, settings.frequencySortField)
            putInt(
                SyncSettings.MATURE_DAYS_SETTING_KEY,
                positiveOrDefault(settings.matureDays, defaults.matureDays),
            )
            putInt(
                SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY,
                positiveOrDefault(
                    settings.matureSupportThreshold,
                    defaults.matureSupportThreshold,
                ),
            )
            markStatsDirty()
            putInt(SUSPENDED_RANK_MIN_KEY, settings.suspendedRankMin)
            putInt(SUSPENDED_RANK_MAX_KEY, settings.suspendedRankMax)
            putInt(
                SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY,
                settings.writingTriggerMissDays,
            )
            putInt(
                SyncSettings.RECOGNITION_PROMOTION_PASSES_SETTING_KEY,
                settings.recognitionPromotionPasses,
            )
            putInt(
                SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
                settings.realDueReviewsToMove,
            )
            markStatsDirty()
            putInt(
                SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
                settings.ladderPromotionIntervalDays,
            )
            markStatsDirty()
            putInt(
                SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
                settings.ladderDemotionFailStreak,
            )
            markStatsDirty()
            putInt(
                SyncSettings.LADDER_PROMOTION_MIN_PASSES_SETTING_KEY,
                settings.ladderPromotionMinPasses,
            )
            putBoolean(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, settings.importActiveCards)
            putBoolean(
                SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY,
                settings.importSuspendedCards,
            )
            putBoolean(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, settings.importTaggedCards)
            putString(SyncSettings.IMPORT_TAGS_SETTING_KEY, settings.importTagsText())
            putBoolean(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, settings.importWeakCards)
            putDouble(
                SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY,
                settings.importWeakFsrsDifficultyThreshold,
            )
            putInt(
                SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY,
                settings.importWeakLapsesThreshold,
            )
            putInt(
                SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY,
                settings.importMinMatchingCardsPerKanji,
            )
            putBoolean(
                SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY,
                settings.importBrowserQueryCards,
            )
            putString(
                SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY,
                settings.importBrowserQuery,
            )
            putString(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, settings.newCardSortMode)
            putInt(SyncSettings.NEW_PER_DAY_SETTING_KEY, settings.newPerDay)
            putInt(SyncSettings.ACTIVE_QUEUE_CAP_SETTING_KEY, settings.activeQueueCap)
            putBoolean(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, tagRepairedCards)
        }

        private fun positiveOrDefault(value: Int, fallback: Int): Int =
            if (value > 0) value else fallback
    }

    internal companion object {
        /**
         * Reads the typed settings snapshot within an existing read scope,
         * without the read-repair write-back that [load] performs. Shared with
         * the Study queue read so both surfaces derive settings identically.
         */
        fun readSnapshot(scope: SqlReadScope): SettingsSnapshot {
            val values = scope.queryList("SELECT key, value FROM settings ORDER BY key") { row ->
                row.text(0) to row.text(1)
            }.toMap(LinkedHashMap())
            return SnapshotSettingsStore(values).toSnapshot()
        }

        const val SUSPENDED_RANK_MIN_KEY = "suspended_rank_min"
        const val SUSPENDED_RANK_MAX_KEY = "suspended_rank_max"
        const val ADAPTIVE_LOAD_MAX_ITEMS_KEY = "adaptive_load_max_items"
        const val STUDY_AHEAD_MINUTES_KEY = "study_ahead_minutes"
        const val STUDY_LADDER_ORDER_KEY = "study_ladder_order"
        const val STUDY_LADDER_ENABLED_KEY = "study_ladder_enabled"
        const val ADAPTIVE_REPAIR_ORDER_KEY = "adaptive_repair_order"
        const val ADAPTIVE_REPAIR_ENABLED_KEY = "adaptive_repair_enabled"
        const val SCHEDULER_TARGET_RETENTION_KEY = "scheduler_target_retention"
        const val SCHEDULER_FREQUENCY_RETENTION_ENABLED_KEY =
            "scheduler_frequency_retention_enabled"
        const val SCHEDULER_FREQUENCY_RETENTION_RANGES_KEY =
            "scheduler_frequency_retention_ranges"
        const val NEW_LEARNING_STEPS_KEY = "new_learning_steps_minutes"
        const val REVIEW_RELEARNING_STEPS_KEY = "review_relearning_steps_minutes"
        const val STATS_SOURCE_VERSION_KEY = "stats_source_version"

        fun Boolean.toSettingInt(): Int = if (this) 1 else 0
    }
}
