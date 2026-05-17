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
        val values = settings.getAll(ALL_KEYS).associate { it.key to it.value }
        val mappingDefaults = defaults.noteMapping
        val noteMapping = runCatching {
            NoteTypeMapping(
                noteTypeName = values.string(KEY_NOTE_TYPE, mappingDefaults.noteTypeName),
                templateName = values.string(KEY_TEMPLATE_NAME, mappingDefaults.templateName),
                expressionField = values.string(KEY_EXPRESSION_FIELD, mappingDefaults.expressionField),
                readingField = values.string(KEY_READING_FIELD, mappingDefaults.readingField),
                meaningField = values.string(KEY_MEANING_FIELD, mappingDefaults.meaningField),
                sentenceField = values.string(KEY_SENTENCE_FIELD, mappingDefaults.sentenceField),
                frequencyField = values.string(KEY_FREQUENCY_FIELD, mappingDefaults.frequencyField),
                frequencySortField = values.string(KEY_FREQUENCY_SORT_FIELD, mappingDefaults.frequencySortField),
            )
        }.getOrDefault(mappingDefaults)

        return runCatching {
            ImportSettings(
                noteMapping = noteMapping,
                matureDays = values.int(KEY_MATURE_DAYS, defaults.matureDays),
                matureSupportThreshold = values.int(KEY_MATURE_SUPPORT_THRESHOLD, defaults.matureSupportThreshold),
                importActiveCards = values.boolean(KEY_IMPORT_ACTIVE_CARDS, defaults.importActiveCards),
                importSuspendedCards = values.boolean(KEY_IMPORT_SUSPENDED_CARDS, defaults.importSuspendedCards),
                importTaggedCards = values.boolean(KEY_IMPORT_TAGGED_CARDS, defaults.importTaggedCards),
                importTags = values.list(KEY_IMPORT_TAGS, defaults.importTags),
                importWeakCards = values.boolean(KEY_IMPORT_WEAK_CARDS, defaults.importWeakCards),
                importWeakFsrsDifficultyThreshold = values.double(
                    KEY_IMPORT_WEAK_FSRS_DIFFICULTY_THRESHOLD,
                    defaults.importWeakFsrsDifficultyThreshold,
                ),
                importWeakLapsesThreshold = values.int(
                    KEY_IMPORT_WEAK_LAPSES_THRESHOLD,
                    defaults.importWeakLapsesThreshold,
                ),
                importMinMatchingCardsPerKanji = values.int(
                    KEY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
                    defaults.importMinMatchingCardsPerKanji,
                ),
                importBrowserQueryCards = values.boolean(
                    KEY_IMPORT_BROWSER_QUERY_CARDS,
                    defaults.importBrowserQueryCards,
                ),
                importBrowserQuery = values.string(KEY_IMPORT_BROWSER_QUERY, defaults.importBrowserQuery),
                suspendedRankMin = values.int(KEY_SUSPENDED_RANK_MIN, defaults.suspendedRankMin),
                suspendedRankMax = values.int(KEY_SUSPENDED_RANK_MAX, defaults.suspendedRankMax),
                newCardSortMode = NewCardSortMode.fromWireName(
                    values.string(KEY_NEW_CARD_SORT_MODE, defaults.newCardSortMode.wireName),
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

    private fun Map<String, String>.string(
        key: String,
        default: String,
    ): String = get(key) ?: default

    private fun Map<String, String>.int(
        key: String,
        default: Int,
    ): Int = get(key)?.toIntOrNull() ?: default

    private fun Map<String, String>.double(
        key: String,
        default: Double,
    ): Double = get(key)?.toDoubleOrNull() ?: default

    private fun Map<String, String>.boolean(
        key: String,
        default: Boolean,
    ): Boolean = get(key)?.toBooleanStrictOrNull() ?: default

    private fun Map<String, String>.list(
        key: String,
        default: List<String>,
    ): List<String> = get(key)
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
        private val ALL_KEYS = listOf(
            KEY_NOTE_TYPE,
            KEY_TEMPLATE_NAME,
            KEY_EXPRESSION_FIELD,
            KEY_READING_FIELD,
            KEY_MEANING_FIELD,
            KEY_SENTENCE_FIELD,
            KEY_FREQUENCY_FIELD,
            KEY_FREQUENCY_SORT_FIELD,
            KEY_MATURE_DAYS,
            KEY_MATURE_SUPPORT_THRESHOLD,
            KEY_IMPORT_ACTIVE_CARDS,
            KEY_IMPORT_SUSPENDED_CARDS,
            KEY_IMPORT_TAGGED_CARDS,
            KEY_IMPORT_TAGS,
            KEY_IMPORT_WEAK_CARDS,
            KEY_IMPORT_WEAK_FSRS_DIFFICULTY_THRESHOLD,
            KEY_IMPORT_WEAK_LAPSES_THRESHOLD,
            KEY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
            KEY_IMPORT_BROWSER_QUERY_CARDS,
            KEY_IMPORT_BROWSER_QUERY,
            KEY_SUSPENDED_RANK_MIN,
            KEY_SUSPENDED_RANK_MAX,
            KEY_NEW_CARD_SORT_MODE,
        )
    }
}
