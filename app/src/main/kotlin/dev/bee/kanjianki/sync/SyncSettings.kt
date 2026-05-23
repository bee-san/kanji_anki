package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.syncdomain.ImportSettingsRepairPolicy

internal object SyncSettings {
    const val NOTE_TYPE_SETTING_KEY = "note_type_name"
    const val EXPRESSION_FIELD_SETTING_KEY = "expression_field"
    const val READING_FIELD_SETTING_KEY = "reading_field"
    const val MEANING_FIELD_SETTING_KEY = "meaning_field"
    const val SENTENCE_FIELD_SETTING_KEY = "sentence_field"
    const val FREQUENCY_FIELD_SETTING_KEY = "frequency_field"
    const val FREQUENCY_SORT_FIELD_SETTING_KEY = "frequency_sort_field"
    const val WRITING_TRIGGER_MISS_DAYS_SETTING_KEY = "writing_trigger_miss_days"
    const val RECOGNITION_PROMOTION_PASSES_SETTING_KEY = "recognition_promotion_passes"
    const val REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY = "real_due_reviews_to_move"
    const val LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY = "ladder_promotion_interval_days"
    const val LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY = "ladder_demotion_fail_streak"
    const val IMPORT_ACTIVE_CARDS_SETTING_KEY = "import_active_cards"
    const val IMPORT_SUSPENDED_CARDS_SETTING_KEY = "import_suspended_cards"
    const val IMPORT_TAGGED_CARDS_SETTING_KEY = "import_tagged_cards"
    const val IMPORT_TAGS_SETTING_KEY = "import_tags"
    const val IMPORT_WEAK_CARDS_SETTING_KEY = "import_weak_cards"
    const val IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY = "import_weak_fsrs_difficulty_threshold"
    const val IMPORT_WEAK_LAPSES_SETTING_KEY = "import_weak_lapses_threshold"
    const val IMPORT_MIN_MATCHING_CARDS_SETTING_KEY = "import_min_matching_cards_per_kanji"
    const val IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY = "import_browser_query_cards"
    const val IMPORT_BROWSER_QUERY_SETTING_KEY = "import_browser_query"
    const val NEW_CARD_SORT_MODE_SETTING_KEY = "new_card_sort_mode"

    private const val ABSENT_INT_SETTING = Int.MIN_VALUE
    private val ABSENT_DOUBLE_SETTING = Double.NaN

    @JvmStatic
    fun fromStore(store: LocalStore?): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        repairOldDefaultImportSettings(store)
        val modelName = if (store == null) {
            defaults.modelName
        } else {
            nonBlank(store.getStringSetting(NOTE_TYPE_SETTING_KEY, defaults.modelName), defaults.modelName)
        }
        val expressionField = fieldSetting(store, EXPRESSION_FIELD_SETTING_KEY, defaults.expressionField, true)
        val readingField = fieldSetting(store, READING_FIELD_SETTING_KEY, defaults.readingField, false)
        val meaningField = fieldSetting(store, MEANING_FIELD_SETTING_KEY, defaults.meaningField, false)
        val sentenceField = fieldSetting(store, SENTENCE_FIELD_SETTING_KEY, defaults.sentenceField, false)
        val frequencyField = fieldSetting(store, FREQUENCY_FIELD_SETTING_KEY, defaults.frequencyField, false)
        val frequencySortField = fieldSetting(store, FREQUENCY_SORT_FIELD_SETTING_KEY, defaults.frequencySortField, false)
        val minRank = store?.getIntSetting("suspended_rank_min", defaults.suspendedRankMin)
            ?: defaults.suspendedRankMin
        val maxRank = store?.getIntSetting(
            "suspended_rank_max",
            store.getIntSetting("suspended_rank_cutoff", defaults.suspendedRankMax),
        ) ?: defaults.suspendedRankMax
        val writingTriggerMissDays = store?.getIntSetting(
            WRITING_TRIGGER_MISS_DAYS_SETTING_KEY,
            defaults.writingTriggerMissDays,
        ) ?: defaults.writingTriggerMissDays
        val recognitionPromotionPasses = store?.getIntSetting(
            RECOGNITION_PROMOTION_PASSES_SETTING_KEY,
            defaults.recognitionPromotionPasses,
        ) ?: defaults.recognitionPromotionPasses
        val realDueReviewsToMove = store?.getIntSetting(
            REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
            defaults.realDueReviewsToMove,
        ) ?: defaults.realDueReviewsToMove
        val ladderPromotionIntervalDays = store?.getIntSetting(
            LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
            defaults.ladderPromotionIntervalDays,
        ) ?: defaults.ladderPromotionIntervalDays
        val ladderDemotionFailStreak = store?.getIntSetting(
            LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
            store.getIntSetting(REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, defaults.ladderDemotionFailStreak),
        ) ?: defaults.ladderDemotionFailStreak
        val importActiveCards = boolSetting(store, IMPORT_ACTIVE_CARDS_SETTING_KEY, defaults.importActiveCards)
        val importSuspendedCards = boolSetting(store, IMPORT_SUSPENDED_CARDS_SETTING_KEY, defaults.importSuspendedCards)
        val importTaggedCards = boolSetting(store, IMPORT_TAGGED_CARDS_SETTING_KEY, defaults.importTaggedCards)
        val importTags = store?.getStringSetting(IMPORT_TAGS_SETTING_KEY, defaults.importTagsText())
            ?: defaults.importTagsText()
        val importWeakCards = boolSetting(store, IMPORT_WEAK_CARDS_SETTING_KEY, defaults.importWeakCards)
        val importWeakFsrsDifficulty = store?.getDoubleSetting(
            IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY,
            defaults.importWeakFsrsDifficultyThreshold,
        ) ?: defaults.importWeakFsrsDifficultyThreshold
        val importWeakLapses = store?.getIntSetting(
            IMPORT_WEAK_LAPSES_SETTING_KEY,
            defaults.importWeakLapsesThreshold,
        ) ?: defaults.importWeakLapsesThreshold
        val importMinMatchingCards = store?.getIntSetting(
            IMPORT_MIN_MATCHING_CARDS_SETTING_KEY,
            defaults.importMinMatchingCardsPerKanji,
        ) ?: defaults.importMinMatchingCardsPerKanji
        val importBrowserQueryCards = boolSetting(
            store,
            IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY,
            defaults.importBrowserQueryCards,
        )
        val importBrowserQuery = nullToEmpty(
            store?.getStringSetting(
                IMPORT_BROWSER_QUERY_SETTING_KEY,
                defaults.importBrowserQuery,
            ),
        )
        val newCardSortMode = store?.getStringSetting(NEW_CARD_SORT_MODE_SETTING_KEY, defaults.newCardSortMode)
            ?: defaults.newCardSortMode
        return RecordsSyncModels.Settings(
            modelName,
            defaults.templateName,
            expressionField,
            readingField,
            meaningField,
            sentenceField,
            frequencyField,
            frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            minRank,
            maxRank,
            defaults.activeQueueCap,
            defaults.newPerDay,
            writingTriggerMissDays,
            recognitionPromotionPasses,
            realDueReviewsToMove,
            importActiveCards,
            importSuspendedCards,
            importTaggedCards,
            RecordsBase.parseImportTags(importTags),
            importWeakCards,
            importWeakFsrsDifficulty,
            importWeakLapses,
            importMinMatchingCards,
            importBrowserQueryCards,
            importBrowserQuery,
            RecordsSyncModels.Settings.normalizeNewCardSortMode(newCardSortMode),
            ladderPromotionIntervalDays,
            ladderDemotionFailStreak,
        )
    }

    private fun repairOldDefaultImportSettings(store: LocalStore?) {
        if (store == null) {
            return
        }
        val repair = ImportSettingsRepairPolicy.oldDefaultRepair(
            storedImportSettings(store),
            RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
            RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
            RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
        )
        if (repair.shouldRepair()) {
            store.putIntSetting(IMPORT_ACTIVE_CARDS_SETTING_KEY, repair.importActiveCards())
            store.putIntSetting(IMPORT_SUSPENDED_CARDS_SETTING_KEY, repair.importSuspendedCards())
        }
    }

    private fun storedImportSettings(store: LocalStore): ImportSettingsRepairPolicy.StoredImportSettings {
        return ImportSettingsRepairPolicy.StoredImportSettings()
            .importActiveCards(nullableIntSetting(store, IMPORT_ACTIVE_CARDS_SETTING_KEY))
            .importSuspendedCards(nullableIntSetting(store, IMPORT_SUSPENDED_CARDS_SETTING_KEY))
            .importTaggedCards(nullableIntSetting(store, IMPORT_TAGGED_CARDS_SETTING_KEY))
            .importTags(store.getStringSetting(IMPORT_TAGS_SETTING_KEY, null))
            .importWeakCards(nullableIntSetting(store, IMPORT_WEAK_CARDS_SETTING_KEY))
            .importWeakFsrsDifficulty(nullableDoubleSetting(store, IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY))
            .importWeakLapses(nullableIntSetting(store, IMPORT_WEAK_LAPSES_SETTING_KEY))
            .importMinMatchingCards(nullableIntSetting(store, IMPORT_MIN_MATCHING_CARDS_SETTING_KEY))
    }

    @JvmStatic
    private fun nullableIntSetting(store: LocalStore, key: String): Int? {
        val value = store.getIntSetting(key, ABSENT_INT_SETTING)
        return if (value == ABSENT_INT_SETTING) null else value
    }

    @JvmStatic
    private fun nullableDoubleSetting(store: LocalStore, key: String): Double? {
        val value = store.getDoubleSetting(key, ABSENT_DOUBLE_SETTING)
        return if (value.isNaN()) null else value
    }

    @JvmStatic
    private fun boolSetting(store: LocalStore?, key: String, fallback: Boolean): Boolean {
        if (store == null) {
            return fallback
        }
        return store.getIntSetting(key, if (fallback) 1 else 0) == 1
    }

    @JvmStatic
    private fun fieldSetting(store: LocalStore?, key: String, fallback: String, required: Boolean): String {
        if (store == null) {
            return fallback
        }
        val value = store.getStringSetting(key, null) ?: return fallback
        val trimmed = value.trim()
        return if (required && trimmed.isEmpty()) fallback else trimmed
    }

    @JvmStatic
    private fun nonBlank(value: String?, fallback: String): String {
        if (value == null || value.trim().isEmpty()) {
            return fallback
        }
        return value.trim()
    }

    @JvmStatic
    private fun nullToEmpty(value: String?): String {
        return value ?: ""
    }
}
