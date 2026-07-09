package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

private fun Array<out Any?>.toAnyArray(): Array<Any?> = Array(size) { index -> this[index] }

abstract class RecordsSyncModels protected constructor() : RecordsBase() {
    class Settings(
        modelNameArg: String?,
        templateNameArg: String?,
        expressionFieldArg: String?,
        readingFieldArg: String?,
        meaningFieldArg: String?,
        sentenceFieldArg: String?,
        vararg rest: Any?,
    ) {
        @JvmField val modelName: String = nullToEmpty(modelNameArg)
        @JvmField val templateName: String = nullToEmpty(templateNameArg)
        @JvmField val expressionField: String = nullToEmpty(expressionFieldArg)
        @JvmField val readingField: String = nullToEmpty(readingFieldArg)
        @JvmField val meaningField: String = nullToEmpty(meaningFieldArg)
        @JvmField val sentenceField: String = nullToEmpty(sentenceFieldArg)
        @JvmField val frequencyField: String
        @JvmField val frequencySortField: String
        @JvmField val matureDays: Int
        @JvmField val matureSupportThreshold: Int
        @JvmField val suspendedRankMin: Int
        @JvmField val suspendedRankMax: Int
        @JvmField val suspendedRankCutoff: Int
        @JvmField val activeQueueCap: Int
        @JvmField val newPerDay: Int
        @JvmField val writingTriggerMissDays: Int
        @JvmField val recognitionPromotionPasses: Int
        @JvmField val realDueReviewsToMove: Int
        @JvmField val ladderPromotionIntervalDays: Int
        @JvmField val ladderDemotionFailStreak: Int
        @JvmField val ladderPromotionMinPasses: Int
        @JvmField val importActiveCards: Boolean
        @JvmField val importSuspendedCards: Boolean
        @JvmField val importTaggedCards: Boolean
        @JvmField val importTags: List<String>
        @JvmField val importWeakCards: Boolean
        @JvmField val importWeakFsrsDifficultyThreshold: Double
        @JvmField val importWeakLapsesThreshold: Int
        @JvmField val importMinMatchingCardsPerKanji: Int
        @JvmField val importBrowserQueryCards: Boolean
        @JvmField val importBrowserQuery: String
        @JvmField val newCardSortMode: String

        init {
            val args = SettingsArgs.from(rest.toAnyArray())
            frequencyField = args.frequencyField
            frequencySortField = args.frequencySortField
            matureDays = args.matureDays
            matureSupportThreshold = args.matureSupportThreshold
            val rankRange = SettingsInputRules.normalizedRankRange(args.suspendedRankMin, args.suspendedRankMax)
            suspendedRankMin = rankRange.minRank
            suspendedRankMax = rankRange.maxRank
            suspendedRankCutoff = rankRange.maxRank
            activeQueueCap = args.activeQueueCap
            newPerDay = args.newPerDay
            writingTriggerMissDays = maxOf(1, args.writingTriggerMissDays)
            recognitionPromotionPasses = maxOf(1, args.recognitionPromotionPasses)
            realDueReviewsToMove = maxOf(1, args.realDueReviewsToMove)
            ladderPromotionIntervalDays = maxOf(1, args.ladderPromotionIntervalDays)
            ladderDemotionFailStreak = maxOf(1, args.ladderDemotionFailStreak)
            ladderPromotionMinPasses = maxOf(1, args.ladderPromotionMinPasses)
            importActiveCards = args.importActiveCards
            importSuspendedCards = args.importSuspendedCards
            importTags = Collections.unmodifiableList(normalizeImportTags(args.importTags))
            importTaggedCards = args.importTaggedCards && importTags.isNotEmpty()
            importWeakCards = args.importWeakCards
            importWeakFsrsDifficultyThreshold = if (finitePositive(args.importWeakFsrsDifficultyThreshold)) {
                maxOf(1.0, minOf(10.0, args.importWeakFsrsDifficultyThreshold))
            } else {
                DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY
            }
            importWeakLapsesThreshold = maxOf(1, minOf(100, args.importWeakLapsesThreshold))
            importMinMatchingCardsPerKanji = maxOf(1, minOf(1000, args.importMinMatchingCardsPerKanji))
            importBrowserQueryCards = args.importBrowserQueryCards
            importBrowserQuery = nullToEmpty(args.importBrowserQuery)
            newCardSortMode = normalizeNewCardSortMode(args.newCardSortMode)
        }

        fun importTaggedCardsEnabled(): Boolean = importTaggedCards

        fun hasImportSourceEnabled(): Boolean {
            return importActiveCards || importSuspendedCards || importTaggedCardsEnabled() || importWeakCards || browserQueryImportEnabled()
        }

        fun browserQueryImportEnabled(): Boolean {
            return importBrowserQueryCards && normalizedBrowserQuery().isNotEmpty()
        }

        fun normalizedBrowserQuery(): String = importBrowserQuery.trim()

        fun importTagsText(): String = importTags.joinToString(" ")

        fun requiredFields(): List<String> {
            val fields = ArrayList<String>()
            addRequiredField(fields, expressionField)
            addRequiredField(fields, readingField)
            addRequiredField(fields, meaningField)
            addRequiredField(fields, sentenceField)
            addRequiredField(fields, frequencyField)
            addRequiredField(fields, frequencySortField)
            return fields
        }

        companion object {
            @JvmStatic
            fun kikuDefaults(): Settings {
                return Settings(
                    "Kiku",
                    "Mining",
                    "Expression",
                    "ExpressionReading",
                    "MainDefinition",
                    "Sentence",
                    "Frequency",
                    "FreqSort",
                    21,
                    2,
                    DEFAULT_SUSPENDED_RANK_MIN,
                    DEFAULT_SUSPENDED_RANK_MAX,
                    24,
                    3,
                    DEFAULT_WRITING_TRIGGER_MISS_DAYS,
                    DEFAULT_RECOGNITION_PROMOTION_PASSES,
                    DEFAULT_REAL_DUE_REVIEWS_TO_MOVE,
                    DEFAULT_IMPORT_ACTIVE_CARDS,
                    DEFAULT_IMPORT_SUSPENDED_CARDS,
                    DEFAULT_IMPORT_TAGGED_CARDS,
                    emptyList<String>(),
                    DEFAULT_IMPORT_WEAK_CARDS,
                    DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
                    DEFAULT_IMPORT_WEAK_LAPSES,
                    DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
                    DEFAULT_IMPORT_BROWSER_QUERY_CARDS,
                    DEFAULT_IMPORT_BROWSER_QUERY,
                    DEFAULT_NEW_CARD_SORT_MODE,
                    DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                    DEFAULT_LADDER_DEMOTION_FAIL_STREAK,
                    DEFAULT_LADDER_PROMOTION_MIN_PASSES,
                )
            }

            @JvmStatic
            fun normalizeNewCardSortMode(value: String?): String {
                if (
                    NEW_CARD_SORT_FREQUENCY == value ||
                    NEW_CARD_SORT_FSRS_DIFFICULTY == value ||
                    NEW_CARD_SORT_RETRIEVABILITY_RISK == value ||
                    NEW_CARD_SORT_KANI_WEAKNESS == value ||
                    NEW_CARD_SORT_BALANCED_PRIORITY == value
                ) {
                    return value
                }
                return DEFAULT_NEW_CARD_SORT_MODE
            }

            @JvmStatic
            protected fun finitePositive(value: Double): Boolean {
                return value > 0.0 && !value.isNaN() && !value.isInfinite()
            }

            @JvmStatic
            protected fun normalizeImportTags(rawTags: List<String>): List<String> {
                if (rawTags.isEmpty()) {
                    return emptyList()
                }
                val parsed = LinkedHashSet<String>()
                for (tag in rawTags) {
                    val trimmed = tag.trim()
                    if (trimmed.isNotEmpty()) {
                        parsed.add(trimmed)
                    }
                }
                return ArrayList(parsed)
            }

            @JvmStatic
            protected fun addRequiredField(fields: MutableList<String>, value: String?) {
                val trimmed = value?.trim().orEmpty()
                if (trimmed.isNotEmpty() && !fields.contains(trimmed)) {
                    fields.add(trimmed)
                }
            }
        }

        protected class SettingsArgs {
            var frequencyField: String = ""
            var frequencySortField: String = ""
            var matureDays: Int = 0
            var matureSupportThreshold: Int = 0
            var suspendedRankMin: Int = DEFAULT_SUSPENDED_RANK_MIN
            var suspendedRankMax: Int = 0
            var activeQueueCap: Int = 0
            var newPerDay: Int = 0
            var writingTriggerMissDays: Int = DEFAULT_WRITING_TRIGGER_MISS_DAYS
            var recognitionPromotionPasses: Int = DEFAULT_RECOGNITION_PROMOTION_PASSES
            var realDueReviewsToMove: Int = DEFAULT_REAL_DUE_REVIEWS_TO_MOVE
            var ladderPromotionIntervalDays: Int = DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS
            var ladderDemotionFailStreak: Int = DEFAULT_LADDER_DEMOTION_FAIL_STREAK
            var ladderPromotionMinPasses: Int = DEFAULT_LADDER_PROMOTION_MIN_PASSES
            var importActiveCards: Boolean = DEFAULT_IMPORT_ACTIVE_CARDS
            var importSuspendedCards: Boolean = DEFAULT_IMPORT_SUSPENDED_CARDS
            var importTaggedCards: Boolean = DEFAULT_IMPORT_TAGGED_CARDS
            var importTags: List<String> = emptyList()
            var importWeakCards: Boolean = DEFAULT_IMPORT_WEAK_CARDS
            var importWeakFsrsDifficultyThreshold: Double = DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY
            var importWeakLapsesThreshold: Int = DEFAULT_IMPORT_WEAK_LAPSES
            var importMinMatchingCardsPerKanji: Int = DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI
            var importBrowserQueryCards: Boolean = DEFAULT_IMPORT_BROWSER_QUERY_CARDS
            var importBrowserQuery: String = DEFAULT_IMPORT_BROWSER_QUERY
            var newCardSortMode: String = DEFAULT_NEW_CARD_SORT_MODE

            companion object {
                @JvmStatic
                fun from(rest: Array<Any?>): SettingsArgs {
                    requireArgCount(CONTEXT_SETTINGS, rest, 7, 8, 9, 10, 11, 19, 21, 22, 24, 25)
                    val args = SettingsArgs()
                    args.frequencyField = stringArg(rest, 0, CONTEXT_SETTINGS).orEmpty()
                    args.frequencySortField = stringArg(rest, 1, CONTEXT_SETTINGS).orEmpty()
                    args.matureDays = intArg(rest, 2, CONTEXT_SETTINGS)
                    args.matureSupportThreshold = intArg(rest, 3, CONTEXT_SETTINGS)
                    if (rest.size <= 8) {
                        args.suspendedRankMax = intArg(rest, 4, CONTEXT_SETTINGS)
                        args.activeQueueCap = intArg(rest, 5, CONTEXT_SETTINGS)
                        args.newPerDay = intArg(rest, 6, CONTEXT_SETTINGS)
                        if (rest.size == 8) {
                            args.writingTriggerMissDays = intArg(rest, 7, CONTEXT_SETTINGS)
                        }
                    } else {
                        args.suspendedRankMin = intArg(rest, 4, CONTEXT_SETTINGS)
                        args.suspendedRankMax = intArg(rest, 5, CONTEXT_SETTINGS)
                        args.activeQueueCap = intArg(rest, 6, CONTEXT_SETTINGS)
                        args.newPerDay = intArg(rest, 7, CONTEXT_SETTINGS)
                        args.writingTriggerMissDays = intArg(rest, 8, CONTEXT_SETTINGS)
                        if (rest.size >= 10) {
                            args.recognitionPromotionPasses = intArg(rest, 9, CONTEXT_SETTINGS)
                        }
                        if (rest.size >= 11) {
                            args.realDueReviewsToMove = intArg(rest, 10, CONTEXT_SETTINGS)
                        } else if (rest.size >= 10) {
                            args.realDueReviewsToMove = maxOf(args.writingTriggerMissDays, args.recognitionPromotionPasses)
                        }
                    }
                    if (rest.size >= 19) {
                        args.importActiveCards = booleanArg(rest, 11, CONTEXT_SETTINGS)
                        args.importSuspendedCards = booleanArg(rest, 12, CONTEXT_SETTINGS)
                        args.importTaggedCards = booleanArg(rest, 13, CONTEXT_SETTINGS)
                        args.importTags = stringListArg(rest, 14, CONTEXT_SETTINGS)
                        args.importWeakCards = booleanArg(rest, 15, CONTEXT_SETTINGS)
                        args.importWeakFsrsDifficultyThreshold = doubleArg(rest, 16, CONTEXT_SETTINGS)
                        args.importWeakLapsesThreshold = intArg(rest, 17, CONTEXT_SETTINGS)
                        args.importMinMatchingCardsPerKanji = intArg(rest, 18, CONTEXT_SETTINGS)
                    }
                    if (rest.size >= 21) {
                        args.importBrowserQueryCards = booleanArg(rest, 19, CONTEXT_SETTINGS)
                        args.importBrowserQuery = stringArg(rest, 20, CONTEXT_SETTINGS).orEmpty()
                    }
                    if (rest.size >= 22) {
                        args.newCardSortMode = stringArg(rest, 21, CONTEXT_SETTINGS).orEmpty()
                    }
                    if (rest.size >= 24) {
                        args.ladderPromotionIntervalDays = intArg(rest, 22, CONTEXT_SETTINGS)
                        args.ladderDemotionFailStreak = intArg(rest, 23, CONTEXT_SETTINGS)
                    } else {
                        args.ladderDemotionFailStreak = args.realDueReviewsToMove
                    }
                    if (rest.size >= 25) {
                        args.ladderPromotionMinPasses = intArg(rest, 24, CONTEXT_SETTINGS)
                    }
                    return args
                }

                @JvmStatic
                protected fun doubleArg(args: Array<Any?>, index: Int, context: String): Double {
                    val value = arg(args, index, context)
                    return (value as Number).toDouble()
                }

                @JvmStatic
                protected fun stringListArg(args: Array<Any?>, index: Int, context: String): List<String> {
                    val value = arg(args, index, context)
                    if (value == null) {
                        return emptyList()
                    }
                    if (value is List<*>) {
                        val out = ArrayList<String>()
                        for (item in value) {
                            if (item != null) {
                                out.add(item.toString())
                            }
                        }
                        return out
                    }
                    return parseImportTags(value.toString())
                }
            }
        }
    }

    class Note(
        @JvmField val noteId: Long,
        @JvmField val modelId: Long,
        @JvmField val modelName: String,
        fields: Map<String, String>,
        tags: List<String>,
    ) {
        constructor(noteId: Long, modelName: String, fields: Map<String, String>, tags: List<String>) : this(noteId, 0L, modelName, fields, tags)

        @JvmField val fields: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(fields))
        @JvmField val tags: List<String> = Collections.unmodifiableList(ArrayList(tags))

        fun field(name: String?): String {
            if (name == null) {
                return ""
            }
            return nullToEmpty(fields[name])
        }
        fun expression(settings: Settings): String = field(settings.expressionField)
        fun reading(settings: Settings): String = field(settings.readingField)
        fun meaning(settings: Settings): String = field(settings.meaningField)
        fun sentence(settings: Settings): String = field(settings.sentenceField)
    }

    class Card {
        @JvmField val cardId: Long
        @JvmField val noteId: Long
        @JvmField val ord: Int
        @JvmField val deckId: String
        @JvmField val deckName: String
        @JvmField val queue: Int
        @JvmField val type: Int
        @JvmField val due: Int
        @JvmField val intervalDays: Int
        @JvmField val reps: Int
        @JvmField val lapses: Int
        @JvmField val suspended: Boolean
        @JvmField val fsrsStability: Double?
        @JvmField val fsrsDifficulty: Double?
        @JvmField val fsrsRetrievability: Double?
        @JvmField val browserQueryMatched: Boolean

        constructor(cardId: Long, noteId: Long, ord: Int, firstDeckValue: String?, vararg rest: Any?) {
            val args = CardArgs.from(firstDeckValue, rest.toAnyArray())
            this.cardId = cardId
            this.noteId = noteId
            this.ord = ord
            this.deckId = nullToEmpty(args.deckId)
            this.deckName = nullToEmpty(args.deckName)
            this.queue = args.queue
            this.type = args.type
            this.due = args.due
            this.intervalDays = args.intervalDays
            this.reps = args.reps
            this.lapses = args.lapses
            this.suspended = args.suspended
            this.fsrsStability = args.fsrsStability
            this.fsrsDifficulty = args.fsrsDifficulty
            this.fsrsRetrievability = args.fsrsRetrievability
            this.browserQueryMatched = false
        }

        private constructor(cardId: Long, noteId: Long, ord: Int, args: CardArgs, browserQueryMatched: Boolean) {
            this.cardId = cardId
            this.noteId = noteId
            this.ord = ord
            this.deckId = nullToEmpty(args.deckId)
            this.deckName = nullToEmpty(args.deckName)
            this.queue = args.queue
            this.type = args.type
            this.due = args.due
            this.intervalDays = args.intervalDays
            this.reps = args.reps
            this.lapses = args.lapses
            this.suspended = args.suspended
            this.fsrsStability = args.fsrsStability
            this.fsrsDifficulty = args.fsrsDifficulty
            this.fsrsRetrievability = args.fsrsRetrievability
            this.browserQueryMatched = browserQueryMatched
        }

        fun withBrowserQueryMatched(matched: Boolean): Card {
            if (matched == browserQueryMatched) {
                return this
            }
            val args = CardArgs().also {
                it.deckId = deckId
                it.deckName = deckName
                it.queue = queue
                it.type = type
                it.due = due
                it.intervalDays = intervalDays
                it.reps = reps
                it.lapses = lapses
                it.suspended = suspended
                it.fsrsStability = fsrsStability
                it.fsrsDifficulty = fsrsDifficulty
                it.fsrsRetrievability = fsrsRetrievability
            }
            return Card(cardId, noteId, ord, args, matched)
        }

        protected class CardArgs {
            var deckId: String = ""
            var deckName: String = ""
            var queue: Int = 0
            var type: Int = 0
            var due: Int = 0
            var intervalDays: Int = 0
            var reps: Int = 0
            var lapses: Int = 0
            var suspended: Boolean = false
            var fsrsStability: Double? = null
            var fsrsDifficulty: Double? = null
            var fsrsRetrievability: Double? = null

            companion object {
                @JvmStatic
                fun from(firstDeckValue: String?, rest: Array<Any?>): CardArgs {
                    requireArgCount(CONTEXT_CARD, rest, 7, 10, 11)
                    val args = CardArgs()
                    var offset = 0
                    args.deckId = nullToEmpty(firstDeckValue)
                    args.deckName = nullToEmpty(firstDeckValue)
                    if (rest.size == 11) {
                        args.deckName = stringArg(rest, 0, CONTEXT_CARD).orEmpty()
                        offset = 1
                    }
                    args.queue = intArg(rest, offset, CONTEXT_CARD)
                    args.type = intArg(rest, offset + 1, CONTEXT_CARD)
                    args.due = intArg(rest, offset + 2, CONTEXT_CARD)
                    args.intervalDays = intArg(rest, offset + 3, CONTEXT_CARD)
                    args.reps = intArg(rest, offset + 4, CONTEXT_CARD)
                    args.lapses = intArg(rest, offset + 5, CONTEXT_CARD)
                    args.suspended = booleanArg(rest, offset + 6, CONTEXT_CARD)
                    if (rest.size - offset == 10) {
                        args.fsrsStability = nullableDoubleArg(rest, offset + 7, CONTEXT_CARD)
                        args.fsrsDifficulty = nullableDoubleArg(rest, offset + 8, CONTEXT_CARD)
                        args.fsrsRetrievability = nullableDoubleArg(rest, offset + 9, CONTEXT_CARD)
                    }
                    return args
                }
            }
        }

        fun mature(matureDays: Int): Boolean = !suspended && intervalDays >= matureDays
        fun active(): Boolean = !suspended
    }

    class CollectionSnapshot(
        notes: List<Note>,
        cards: List<Card>,
    ) {
        @JvmField val notes: List<Note> = Collections.unmodifiableList(ArrayList(notes))
        @JvmField val cards: List<Card> = Collections.unmodifiableList(ArrayList(cards))

        fun notesById(): Map<Long, Note> {
            val map = LinkedHashMap<Long, Note>()
            for (note in notes) {
                map[note.noteId] = note
            }
            return map
        }
    }
}
