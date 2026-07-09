package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet

abstract class RecordsImportModels protected constructor() : RecordsSyncModels() {
    class SuspendedSource {
        @JvmField val kanji: String
        @JvmField val cardId: Long
        @JvmField val noteId: Long
        @JvmField val expression: String
        @JvmField val reading: String
        @JvmField val meaning: String
        @JvmField val sentence: String
        @JvmField val sourceType: String
        @JvmField val suspended: Boolean
        @JvmField val forcePractice: Boolean
        @JvmField val mature: Boolean
        @JvmField val lapses: Int
        @JvmField val intervalDays: Int
        @JvmField val reps: Int
        @JvmField val fsrsStability: Double?
        @JvmField val fsrsDifficulty: Double?
        @JvmField val fsrsRetrievability: Double?
        @JvmField val ruleTypes: List<String>

        constructor(
            kanji: String?,
            cardId: Long,
            noteId: Long,
            expression: String?,
            reading: String?,
            meaning: String?,
            sentence: String?
        ) : this(
            kanji,
            cardId,
            noteId,
            expression,
            reading,
            meaning,
            SuspendedSourceDetails.builder(sentence).build()
        )

        constructor(
            kanji: String?,
            cardId: Long,
            noteId: Long,
            expression: String?,
            reading: String?,
            meaning: String?,
            details: SuspendedSourceDetails?
        ) {
            val sourceDetails = details ?: SuspendedSourceDetails.builder("").build()
            this.kanji = nullToEmpty(kanji)
            this.cardId = cardId
            this.noteId = noteId
            this.expression = nullToEmpty(expression)
            this.reading = nullToEmpty(reading)
            this.meaning = cleanMeaning(meaning)
            this.sentence = nullToEmpty(sourceDetails.sentence)
            this.sourceType = normalizeSourceType(sourceDetails.sourceType, sourceDetails.suspended)
            this.suspended = sourceDetails.suspended
            this.forcePractice = sourceDetails.forcePractice
            this.mature = sourceDetails.mature
            this.lapses = sourceDetails.lapses.coerceAtLeast(0)
            this.intervalDays = sourceDetails.intervalDays.coerceAtLeast(0)
            this.reps = sourceDetails.reps.coerceAtLeast(0)
            this.fsrsStability = sourceDetails.fsrsStability
            this.fsrsDifficulty = sourceDetails.fsrsDifficulty
            this.fsrsRetrievability = sourceDetails.fsrsRetrievability
            this.ruleTypes = Collections.unmodifiableList(normalizeRuleTypes(sourceDetails.ruleTypes, this.sourceType))
        }

        companion object {
            @JvmStatic
            protected fun normalizeSourceType(sourceType: String?, suspended: Boolean): String {
                if (sourceType != null && sourceType.trim().isNotEmpty()) {
                    return sourceType.trim()
                }
                return if (suspended) SOURCE_SUSPENDED else SOURCE_ACTIVE
            }

            @JvmStatic
            protected fun normalizeRuleTypes(requested: List<String?>?, fallback: String?): List<String> {
                val out = LinkedHashSet<String>()
                for (rule in nullToEmptyList(requested)) {
                    val trimmed = rule?.trim().orEmpty()
                    if (trimmed.isNotEmpty()) {
                        out.add(trimmed)
                    }
                }
                if (out.isEmpty() && fallback != null && fallback.trim().isNotEmpty()) {
                    out.add(fallback.trim())
                }
                return ArrayList(out)
            }
        }
    }

    class SuspendedSourceDetails internal constructor(builder: Builder) {
        internal val sentence: String? = builder.sentence
        internal val sourceType: String? = builder.sourceType
        internal val suspended: Boolean = builder.suspended
        internal val forcePractice: Boolean = builder.forcePractice
        internal val mature: Boolean = builder.mature
        internal val lapses: Int = builder.lapses
        internal val intervalDays: Int = builder.intervalDays
        internal val reps: Int = builder.reps
        internal val fsrsStability: Double? = builder.fsrsStability
        internal val fsrsDifficulty: Double? = builder.fsrsDifficulty
        internal val fsrsRetrievability: Double? = builder.fsrsRetrievability
        internal val ruleTypes: List<String?> = Collections.unmodifiableList(ArrayList(nullToEmptyList(builder.ruleTypes)))

        class Builder internal constructor(internal val sentence: String?) {
            internal var sourceType: String? = SOURCE_SUSPENDED
            internal var suspended: Boolean = true
            internal var forcePractice: Boolean = true
            internal var mature: Boolean = false
            internal var lapses: Int = 0
            internal var intervalDays: Int = 0
            internal var reps: Int = 0
            internal var fsrsStability: Double? = null
            internal var fsrsDifficulty: Double? = null
            internal var fsrsRetrievability: Double? = null
            internal var ruleTypes: List<String?> = Collections.emptyList()

            fun sourceType(sourceType: String?): Builder {
                this.sourceType = sourceType
                return this
            }

            fun suspended(suspended: Boolean): Builder {
                this.suspended = suspended
                return this
            }

            fun forcePractice(forcePractice: Boolean): Builder {
                this.forcePractice = forcePractice
                return this
            }

            fun mature(mature: Boolean): Builder {
                this.mature = mature
                return this
            }

            fun reviewStats(lapses: Int, intervalDays: Int, reps: Int): Builder {
                this.lapses = lapses
                this.intervalDays = intervalDays
                this.reps = reps
                return this
            }

            fun fsrs(stability: Double?, difficulty: Double?, retrievability: Double?): Builder {
                this.fsrsStability = stability
                this.fsrsDifficulty = difficulty
                this.fsrsRetrievability = retrievability
                return this
            }

            fun ruleTypes(ruleTypes: List<String?>?): Builder {
                this.ruleTypes = ArrayList(nullToEmptyList(ruleTypes))
                return this
            }

            fun build(): SuspendedSourceDetails = SuspendedSourceDetails(this)
        }

        companion object {
            @JvmStatic
            fun builder(sentence: String?): Builder = Builder(sentence)
        }
    }

    class SuspendedImport(
        kanji: String?,
        @JvmField val jitenRank: Int?,
        @JvmField val rankKnown: Boolean,
        @JvmField val cutoffUsed: Int,
        sources: List<SuspendedSource>
    ) {
        @JvmField val kanji: String = nullToEmpty(kanji)
        @JvmField val sources: List<SuspendedSource> = Collections.unmodifiableList(ArrayList(sources))
    }

    class Example(
        sourceType: String?,
        @JvmField val cardId: Long,
        @JvmField val noteId: Long,
        expression: String?,
        reading: String?,
        meaning: String?,
        vararg rest: Any?
    ) {
        @JvmField val sourceType: String = nullToEmpty(sourceType)
        @JvmField val expression: String = nullToEmpty(expression)
        @JvmField val reading: String = nullToEmpty(reading)
        @JvmField val meaning: String = cleanMeaning(meaning)
        @JvmField val sentence: String
        @JvmField val mature: Boolean
        @JvmField val lapses: Int
        @JvmField val intervalDays: Int
        @JvmField val reps: Int
        @JvmField val fsrsStability: Double?
        @JvmField val fsrsDifficulty: Double?
        @JvmField val fsrsRetrievability: Double?

        init {
            val args = ExampleArgs.from(rest)
            sentence = nullToEmpty(args.sentence)
            mature = args.mature
            lapses = args.lapses
            intervalDays = args.intervalDays
            reps = args.reps
            fsrsStability = args.fsrsStability
            fsrsDifficulty = args.fsrsDifficulty
            fsrsRetrievability = args.fsrsRetrievability
        }

        protected class ExampleArgs {
            var sentence: String? = null
            var mature: Boolean = false
            var lapses: Int = 0
            var intervalDays: Int = 0
            var reps: Int = 0
            var fsrsStability: Double? = null
            var fsrsDifficulty: Double? = null
            var fsrsRetrievability: Double? = null

            companion object {
                @JvmStatic
                fun from(rest: Array<out Any?>): ExampleArgs {
                    requireArgCount(CONTEXT_EXAMPLE, rest.toAnyArray(), 3, 8)
                    val args = ExampleArgs()
                    args.sentence = stringArg(rest.toAnyArray(), 0, CONTEXT_EXAMPLE)
                    args.mature = booleanArg(rest.toAnyArray(), 1, CONTEXT_EXAMPLE)
                    args.lapses = intArg(rest.toAnyArray(), 2, CONTEXT_EXAMPLE)
                    if (rest.size == 8) {
                        args.intervalDays = intArg(rest.toAnyArray(), 3, CONTEXT_EXAMPLE)
                        args.reps = intArg(rest.toAnyArray(), 4, CONTEXT_EXAMPLE)
                        args.fsrsStability = nullableDoubleArg(rest.toAnyArray(), 5, CONTEXT_EXAMPLE)
                        args.fsrsDifficulty = nullableDoubleArg(rest.toAnyArray(), 6, CONTEXT_EXAMPLE)
                        args.fsrsRetrievability = nullableDoubleArg(rest.toAnyArray(), 7, CONTEXT_EXAMPLE)
                    }
                    return args
                }
            }
        }
    }

    class DashboardRow(
        kanji: String?,
        @JvmField val jitenRank: Int?,
        primaryMeaning: String?,
        reading: String?,
        browserSearch: String?,
        vararg rest: Any?
    ) {
        @JvmField val kanji: String = nullToEmpty(kanji)
        @JvmField val primaryMeaning: String = cleanMeaning(primaryMeaning)
        @JvmField val reading: String = nullToEmpty(reading)
        @JvmField val browserSearch: String = nullToEmpty(browserSearch)
        @JvmField val weaknessScore: Int
        @JvmField val reasonCode: String
        @JvmField val reasonText: String
        @JvmField val activeExampleCount: Int
        @JvmField val suspendedExampleCount: Int
        @JvmField val matureSupportCount: Int
        @JvmField val examples: List<Example>

        init {
            val args = rest.toAnyArray()
            requireArgCount(CONTEXT_DASHBOARD_ROW, args, 7)
            weaknessScore = intArg(args, 0, CONTEXT_DASHBOARD_ROW)
            reasonCode = nullToEmpty(stringArg(args, 1, CONTEXT_DASHBOARD_ROW))
            reasonText = nullToEmpty(stringArg(args, 2, CONTEXT_DASHBOARD_ROW))
            activeExampleCount = intArg(args, 3, CONTEXT_DASHBOARD_ROW)
            suspendedExampleCount = intArg(args, 4, CONTEXT_DASHBOARD_ROW)
            matureSupportCount = intArg(args, 5, CONTEXT_DASHBOARD_ROW)
            examples = Collections.unmodifiableList(examplesArg(args, 6))
        }

        companion object {
            @JvmStatic
            protected fun examplesArg(args: Array<Any?>, index: Int): List<Example> {
                val value = arg(args, index, CONTEXT_DASHBOARD_ROW)
                val rawExamples = value as List<*>
                val examples = ArrayList<Example>()
                for (example in rawExamples) {
                    examples.add(example as Example)
                }
                return examples
            }
        }
    }

    class KanjiInventoryItem(
        kanji: String?,
        primaryMeaning: String?,
        readings: String?,
        browserSearch: String?,
        vararg rest: Any?
    ) {
        @JvmField val kanji: String = nullToEmpty(kanji)
        @JvmField val primaryMeaning: String = cleanMeaning(primaryMeaning)
        @JvmField val readings: String = nullToEmpty(readings)
        @JvmField val browserSearch: String = nullToEmpty(browserSearch)
        @JvmField val sourceCount: Int
        @JvmField val exampleCount: Int
        @JvmField val suspended: Boolean
        @JvmField val lastSeenAtMillis: Long

        init {
            val args = rest.toAnyArray()
            requireArgCount(CONTEXT_KANJI_INVENTORY_ITEM, args, 4)
            sourceCount = intArg(args, 0, CONTEXT_KANJI_INVENTORY_ITEM).coerceAtLeast(0)
            exampleCount = intArg(args, 1, CONTEXT_KANJI_INVENTORY_ITEM).coerceAtLeast(0)
            suspended = booleanArg(args, 2, CONTEXT_KANJI_INVENTORY_ITEM)
            lastSeenAtMillis = longArg(args, 3, CONTEXT_KANJI_INVENTORY_ITEM).coerceAtLeast(0L)
        }
    }

    class SimilarKanjiPair(
        kanjiA: String?,
        kanjiB: String?,
        source: String?,
        firstSeenAtMillis: Long,
        lastSeenAtMillis: Long
    ) {
        @JvmField val kanjiA: String = nullToEmpty(kanjiA)
        @JvmField val kanjiB: String = nullToEmpty(kanjiB)
        @JvmField val source: String = nullToEmpty(source)
        @JvmField val firstSeenAtMillis: Long = firstSeenAtMillis.coerceAtLeast(0L)
        @JvmField val lastSeenAtMillis: Long = lastSeenAtMillis.coerceAtLeast(0L)
    }

    class SimilarKanjiChoiceCard(
        targetKanji: String?,
        primaryMeaning: String?,
        choices: List<String?>?,
        choiceSignature: String?,
        vararg rest: Any?
    ) {
        @JvmField val targetKanji: String = nullToEmpty(targetKanji)
        @JvmField val primaryMeaning: String = cleanMeaning(primaryMeaning)
        @JvmField val choices: List<String>
        @JvmField val choiceSignature: String = nullToEmpty(choiceSignature)
        @JvmField val dueAtMillis: Long
        @JvmField val passedAtMillis: Long
        @JvmField val lastReviewedAtMillis: Long
        @JvmField val correctCount: Int
        @JvmField val wrongCount: Int

        init {
            val args = rest.toAnyArray()
            requireArgCount(CONTEXT_SIMILAR_KANJI_CHOICE_CARD, args, 0, 5)
            this.choices = Collections.unmodifiableList(normalizeStrings(choices))
            dueAtMillis = if (args.isEmpty()) 0L else longArg(args, 0, CONTEXT_SIMILAR_KANJI_CHOICE_CARD).coerceAtLeast(0L)
            passedAtMillis = if (args.isEmpty()) 0L else longArg(args, 1, CONTEXT_SIMILAR_KANJI_CHOICE_CARD).coerceAtLeast(0L)
            lastReviewedAtMillis = if (args.isEmpty()) 0L else longArg(args, 2, CONTEXT_SIMILAR_KANJI_CHOICE_CARD).coerceAtLeast(0L)
            correctCount = if (args.isEmpty()) 0 else intArg(args, 3, CONTEXT_SIMILAR_KANJI_CHOICE_CARD).coerceAtLeast(0)
            wrongCount = if (args.isEmpty()) 0 else intArg(args, 4, CONTEXT_SIMILAR_KANJI_CHOICE_CARD).coerceAtLeast(0)
        }

        fun passed(): Boolean = passedAtMillis > 0L
    }

    class SimilarKanjiChoiceResult(
        @JvmField val card: SimilarKanjiChoiceCard?,
        selectedKanji: String?,
        @JvmField val correct: Boolean,
        repairKanji: List<String?>?
    ) {
        @JvmField val selectedKanji: String = nullToEmpty(selectedKanji)
        @JvmField val repairKanji: List<String> = Collections.unmodifiableList(normalizeStrings(repairKanji))
    }

    class MeaningKanjiChoiceCard(
        targetKanji: String?,
        primaryMeaning: String?,
        reading: String?,
        choices: List<String?>?
    ) {
        @JvmField val targetKanji: String = nullToEmpty(targetKanji).trim()
        @JvmField val primaryMeaning: String = cleanMeaning(primaryMeaning)
        @JvmField val reading: String = nullToEmpty(reading).trim()
        @JvmField val choices: List<String>

        init {
            val normalizedChoices = ArrayList<String>()
            for (choice in nullToEmptyList(choices)) {
                val value = nullToEmpty(choice).trim()
                if (value.isNotEmpty()) {
                    normalizedChoices.add(value)
                }
            }
            this.choices = Collections.unmodifiableList(normalizedChoices)
        }

        fun isCorrect(selectedKanji: String?): Boolean = targetKanji == nullToEmpty(selectedKanji).trim()
    }

    /**
     * A kanji_reading choice card (Goal 78): "How is <targetKanji> read in
     * <word>?" The learner picks the correct reading among the kanji's other
     * canonical readings. [word] is the attested prompt word, [meaning] its
     * gloss (context cue), [correctReading] the answer, and [choices] the kana
     * reading options (correct + distractors), pre-shuffled.
     */
    class KanjiReadingChoiceCard(
        targetKanji: String?,
        word: String?,
        meaning: String?,
        correctReading: String?,
        choices: List<String?>?
    ) {
        @JvmField val targetKanji: String = nullToEmpty(targetKanji).trim()
        @JvmField val word: String = nullToEmpty(word).trim()
        @JvmField val meaning: String = cleanMeaning(meaning)
        @JvmField val correctReading: String = nullToEmpty(correctReading).trim()
        @JvmField val choices: List<String>

        init {
            val normalizedChoices = ArrayList<String>()
            for (choice in nullToEmptyList(choices)) {
                val value = nullToEmpty(choice).trim()
                if (value.isNotEmpty() && !normalizedChoices.contains(value)) {
                    normalizedChoices.add(value)
                }
            }
            this.choices = Collections.unmodifiableList(normalizedChoices)
        }

        fun isCorrect(selectedReading: String?): Boolean =
            correctReading == nullToEmpty(selectedReading).trim()
    }

    class SimilarKanjiWritingRepair(
        id: Long,
        targetKanji: String?,
        repairKanji: String?,
        choiceSignature: String?,
        wrongSelection: String?,
        promptMeaning: String?,
        vararg rest: Any?
    ) {
        @JvmField val id: Long = id.coerceAtLeast(0L)
        @JvmField val targetKanji: String = nullToEmpty(targetKanji)
        @JvmField val repairKanji: String = nullToEmpty(repairKanji)
        @JvmField val choiceSignature: String = nullToEmpty(choiceSignature)
        @JvmField val wrongSelection: String = nullToEmpty(wrongSelection)
        @JvmField val promptMeaning: String = cleanMeaning(promptMeaning)
        @JvmField val status: String
        @JvmField val dueAtMillis: Long
        @JvmField val activeToken: String
        @JvmField val attempts: Int
        @JvmField val createdAtMillis: Long
        @JvmField val updatedAtMillis: Long
        @JvmField val completedAtMillis: Long

        init {
            val args = rest.toAnyArray()
            requireArgCount(CONTEXT_SIMILAR_KANJI_WRITING_REPAIR, args, 7)
            val requestedStatus = stringArg(args, 0, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR)
            status = if (requestedStatus == null || requestedStatus.isEmpty()) "pending" else requestedStatus
            dueAtMillis = longArg(args, 1, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR).coerceAtLeast(0L)
            val requestedActiveToken = stringArg(args, 2, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR)
            activeToken = nullToEmpty(requestedActiveToken)
            attempts = intArg(args, 3, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR).coerceAtLeast(0)
            createdAtMillis = longArg(args, 4, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR).coerceAtLeast(0L)
            updatedAtMillis = longArg(args, 5, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR).coerceAtLeast(0L)
            completedAtMillis = longArg(args, 6, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR).coerceAtLeast(0L)
        }

        fun withToken(token: String?, updatedAtMillis: Long): SimilarKanjiWritingRepair {
            return SimilarKanjiWritingRepair(
                id,
                targetKanji,
                repairKanji,
                choiceSignature,
                wrongSelection,
                promptMeaning,
                status,
                dueAtMillis,
                token,
                attempts,
                createdAtMillis,
                updatedAtMillis,
                completedAtMillis
            )
        }
    }

    class KanjiTimelineEvent(
        @JvmField val id: Long,
        kanji: String?,
        @JvmField val occurredAtMillis: Long,
        eventType: String?,
        title: String?,
        detail: String?,
        vararg rest: Any?
    ) {
        @JvmField val kanji: String = nullToEmpty(kanji)
        @JvmField val eventType: String = nullToEmpty(eventType)
        @JvmField val title: String = nullToEmpty(title)
        @JvmField val detail: String = nullToEmpty(detail)
        @JvmField val sourceExpression: String
        @JvmField val sourceReading: String
        @JvmField val rating: String
        @JvmField val writingRequired: Boolean
        @JvmField val writingPassed: Boolean
        @JvmField val manualOverride: Boolean
        @JvmField val weaknessScore: Int?
        @JvmField val matureSupportCount: Int?
        @JvmField val syncId: Long?
        @JvmField val dedupeKey: String?

        init {
            val args = rest.toAnyArray()
            requireArgCount(CONTEXT_KANJI_TIMELINE_EVENT, args, 10)
            sourceExpression = nullToEmpty(stringArg(args, 0, CONTEXT_KANJI_TIMELINE_EVENT))
            sourceReading = nullToEmpty(stringArg(args, 1, CONTEXT_KANJI_TIMELINE_EVENT))
            rating = nullToEmpty(stringArg(args, 2, CONTEXT_KANJI_TIMELINE_EVENT))
            writingRequired = booleanArg(args, 3, CONTEXT_KANJI_TIMELINE_EVENT)
            writingPassed = booleanArg(args, 4, CONTEXT_KANJI_TIMELINE_EVENT)
            manualOverride = booleanArg(args, 5, CONTEXT_KANJI_TIMELINE_EVENT)
            weaknessScore = arg(args, 6, CONTEXT_KANJI_TIMELINE_EVENT) as Int?
            matureSupportCount = arg(args, 7, CONTEXT_KANJI_TIMELINE_EVENT) as Int?
            syncId = arg(args, 8, CONTEXT_KANJI_TIMELINE_EVENT) as Long?
            dedupeKey = stringArg(args, 9, CONTEXT_KANJI_TIMELINE_EVENT)
        }
    }

    companion object {
        private fun cleanMeaning(meaning: String?): String {
            return StudyCueFormatter.cleanCollectionMeaning(meaning, 96)
        }

        private fun normalizeStrings(values: List<String?>?): ArrayList<String> {
            val out = ArrayList<String>()
            for (value in nullToEmptyList(values)) {
                out.add(nullToEmpty(value))
            }
            return out
        }

        private fun Array<out Any?>.toAnyArray(): Array<Any?> = Array(size) { index -> this[index] }
    }
}
