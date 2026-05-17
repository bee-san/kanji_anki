package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.importing.NoteTypeMapping
import dev.bee.kanjianki.domain.repository.ImportSettingsRepository

class RoomImportSettingsRepository(
    private val settings: SettingsDao,
) : ImportSettingsRepository {
    override suspend fun get(): ImportSettings {
        val defaults = ImportSettings()
        val mappingDefaults = defaults.noteMapping
        val noteMapping = runCatching {
            NoteTypeMapping(
                noteTypeName = string(KEY_NOTE_TYPE, mappingDefaults.noteTypeName),
                templateName = string(KEY_TEMPLATE_NAME, mappingDefaults.templateName),
                expressionField = string(KEY_EXPRESSION_FIELD, mappingDefaults.expressionField),
                readingField = string(KEY_READING_FIELD, mappingDefaults.readingField),
                meaningField = string(KEY_MEANING_FIELD, mappingDefaults.meaningField),
                sentenceField = string(KEY_SENTENCE_FIELD, mappingDefaults.sentenceField),
                frequencyField = string(KEY_FREQUENCY_FIELD, mappingDefaults.frequencyField),
                frequencySortField = string(KEY_FREQUENCY_SORT_FIELD, mappingDefaults.frequencySortField),
            )
        }.getOrDefault(mappingDefaults)

        return runCatching {
            ImportSettings(
                noteMapping = noteMapping,
                matureDays = int(KEY_MATURE_DAYS, defaults.matureDays),
                matureSupportThreshold = int(KEY_MATURE_SUPPORT_THRESHOLD, defaults.matureSupportThreshold),
                importActiveCards = boolean(KEY_IMPORT_ACTIVE_CARDS, defaults.importActiveCards),
                importSuspendedCards = boolean(KEY_IMPORT_SUSPENDED_CARDS, defaults.importSuspendedCards),
                importTaggedCards = boolean(KEY_IMPORT_TAGGED_CARDS, defaults.importTaggedCards),
                importTags = list(KEY_IMPORT_TAGS, defaults.importTags),
                importWeakCards = boolean(KEY_IMPORT_WEAK_CARDS, defaults.importWeakCards),
                importWeakFsrsDifficultyThreshold = double(
                    KEY_IMPORT_WEAK_FSRS_DIFFICULTY_THRESHOLD,
                    defaults.importWeakFsrsDifficultyThreshold,
                ),
                importWeakLapsesThreshold = int(KEY_IMPORT_WEAK_LAPSES_THRESHOLD, defaults.importWeakLapsesThreshold),
                importMinMatchingCardsPerKanji = int(
                    KEY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
                    defaults.importMinMatchingCardsPerKanji,
                ),
                importBrowserQueryCards = boolean(KEY_IMPORT_BROWSER_QUERY_CARDS, defaults.importBrowserQueryCards),
                importBrowserQuery = string(KEY_IMPORT_BROWSER_QUERY, defaults.importBrowserQuery),
                suspendedRankMin = int(KEY_SUSPENDED_RANK_MIN, defaults.suspendedRankMin),
                suspendedRankMax = int(KEY_SUSPENDED_RANK_MAX, defaults.suspendedRankMax),
                newCardSortMode = NewCardSortMode.fromWireName(
                    string(KEY_NEW_CARD_SORT_MODE, defaults.newCardSortMode.wireName),
                ),
            )
        }.getOrDefault(defaults)
    }

    override suspend fun save(
        settings: ImportSettings,
        updatedAtMillis: Long,
    ) {
        val mapping = settings.noteMapping
        this.settings.upsertAll(
            listOf(
                KEY_NOTE_TYPE to mapping.noteTypeName,
                KEY_TEMPLATE_NAME to mapping.templateName,
                KEY_EXPRESSION_FIELD to mapping.expressionField,
                KEY_READING_FIELD to mapping.readingField,
                KEY_MEANING_FIELD to mapping.meaningField,
                KEY_SENTENCE_FIELD to mapping.sentenceField,
                KEY_FREQUENCY_FIELD to mapping.frequencyField,
                KEY_FREQUENCY_SORT_FIELD to mapping.frequencySortField,
                KEY_MATURE_DAYS to settings.matureDays.toString(),
                KEY_MATURE_SUPPORT_THRESHOLD to settings.matureSupportThreshold.toString(),
                KEY_IMPORT_ACTIVE_CARDS to settings.importActiveCards.toString(),
                KEY_IMPORT_SUSPENDED_CARDS to settings.importSuspendedCards.toString(),
                KEY_IMPORT_TAGGED_CARDS to settings.importTaggedCards.toString(),
                KEY_IMPORT_TAGS to settings.importTags.joinToString(TAG_SEPARATOR),
                KEY_IMPORT_WEAK_CARDS to settings.importWeakCards.toString(),
                KEY_IMPORT_WEAK_FSRS_DIFFICULTY_THRESHOLD to settings.importWeakFsrsDifficultyThreshold.toString(),
                KEY_IMPORT_WEAK_LAPSES_THRESHOLD to settings.importWeakLapsesThreshold.toString(),
                KEY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI to settings.importMinMatchingCardsPerKanji.toString(),
                KEY_IMPORT_BROWSER_QUERY_CARDS to settings.importBrowserQueryCards.toString(),
                KEY_IMPORT_BROWSER_QUERY to settings.importBrowserQuery,
                KEY_SUSPENDED_RANK_MIN to settings.suspendedRankMin.toString(),
                KEY_SUSPENDED_RANK_MAX to settings.suspendedRankMax.toString(),
                KEY_NEW_CARD_SORT_MODE to settings.newCardSortMode.wireName,
            ).map { (key, value) ->
                SettingEntity(
                    key = key,
                    value = value,
                    updatedAt = updatedAtMillis,
                )
            },
        )
    }

    private suspend fun string(
        key: String,
        default: String,
    ): String = settings.get(key)?.value ?: default

    private suspend fun int(
        key: String,
        default: Int,
    ): Int = settings.get(key)?.value?.toIntOrNull() ?: default

    private suspend fun double(
        key: String,
        default: Double,
    ): Double = settings.get(key)?.value?.toDoubleOrNull() ?: default

    private suspend fun boolean(
        key: String,
        default: Boolean,
    ): Boolean = settings.get(key)?.value?.toBooleanStrictOrNull() ?: default

    private suspend fun list(
        key: String,
        default: List<String>,
    ): List<String> = settings.get(key)?.value
        ?.split(TAG_SEPARATOR)
        ?.filter(String::isNotBlank)
        ?: default

    companion object {
        private const val TAG_SEPARATOR = "\n"
        private const val KEY_NOTE_TYPE = "sync.note_type"
        private const val KEY_TEMPLATE_NAME = "sync.template_name"
        private const val KEY_EXPRESSION_FIELD = "sync.field.expression"
        private const val KEY_READING_FIELD = "sync.field.reading"
        private const val KEY_MEANING_FIELD = "sync.field.meaning"
        private const val KEY_SENTENCE_FIELD = "sync.field.sentence"
        private const val KEY_FREQUENCY_FIELD = "sync.field.frequency"
        private const val KEY_FREQUENCY_SORT_FIELD = "sync.field.frequency_sort"
        private const val KEY_MATURE_DAYS = "sync.mature_days"
        private const val KEY_MATURE_SUPPORT_THRESHOLD = "sync.mature_support_threshold"
        private const val KEY_IMPORT_ACTIVE_CARDS = "sync.import.active_cards"
        private const val KEY_IMPORT_SUSPENDED_CARDS = "sync.import.suspended_cards"
        private const val KEY_IMPORT_TAGGED_CARDS = "sync.import.tagged_cards"
        private const val KEY_IMPORT_TAGS = "sync.import.tags"
        private const val KEY_IMPORT_WEAK_CARDS = "sync.import.weak_cards"
        private const val KEY_IMPORT_WEAK_FSRS_DIFFICULTY_THRESHOLD = "sync.import.weak_fsrs_difficulty_threshold"
        private const val KEY_IMPORT_WEAK_LAPSES_THRESHOLD = "sync.import.weak_lapses_threshold"
        private const val KEY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI = "sync.import.min_matching_cards_per_kanji"
        private const val KEY_IMPORT_BROWSER_QUERY_CARDS = "sync.import.browser_query_cards"
        private const val KEY_IMPORT_BROWSER_QUERY = "sync.import.browser_query"
        private const val KEY_SUSPENDED_RANK_MIN = "sync.import.suspended_rank_min"
        private const val KEY_SUSPENDED_RANK_MAX = "sync.import.suspended_rank_max"
        private const val KEY_NEW_CARD_SORT_MODE = "sync.new_card_sort_mode"
    }
}
