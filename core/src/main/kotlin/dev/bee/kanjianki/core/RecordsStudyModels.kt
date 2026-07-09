package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections

private fun Array<out Any?>.toStudyArgsArray(): Array<Any?> = Array(size) { index -> this[index] }

abstract class RecordsStudyModels protected constructor() : RecordsImportModels() {
    class TaskMemory(
        state: String?,
        dueAtMillis: Long,
        stability: Double,
        difficulty: Double,
        totalReviews: Int,
        lapses: Int,
        vararg rest: Any?,
    ) {
        @JvmField
        val state: String

        @JvmField
        val dueAtMillis: Long

        @JvmField
        val stability: Double

        @JvmField
        val difficulty: Double

        @JvmField
        val totalReviews: Int

        @JvmField
        val lapses: Int

        @JvmField
        val learningStep: Int

        @JvmField
        val lastRating: String

        @JvmField
        val matureIntervalDays: Int

        @JvmField
        val consecutivePasses: Int

        @JvmField
        val lastPassedDueAtMillis: Long

        init {
            requireArgCount(CONTEXT_TASK_MEMORY, rest.toStudyArgsArray(), 3, 5)
            this.state = if (state.isNullOrEmpty()) "new" else state
            this.dueAtMillis = dueAtMillis.coerceAtLeast(0L)
            this.stability = stability
            this.difficulty = difficulty
            this.totalReviews = totalReviews.coerceAtLeast(0)
            this.lapses = lapses.coerceAtLeast(0)
            this.learningStep = intArg(rest.toStudyArgsArray(), 0, CONTEXT_TASK_MEMORY).coerceAtLeast(0)
            this.lastRating = nullToEmpty(stringArg(rest.toStudyArgsArray(), 1, CONTEXT_TASK_MEMORY))
            this.matureIntervalDays = intArg(rest.toStudyArgsArray(), 2, CONTEXT_TASK_MEMORY).coerceAtLeast(0)
            this.consecutivePasses = if (rest.size == 3) 0 else intArg(rest.toStudyArgsArray(), 3, CONTEXT_TASK_MEMORY).coerceAtLeast(0)
            this.lastPassedDueAtMillis = if (rest.size == 3) 0L else longArg(rest.toStudyArgsArray(), 4, CONTEXT_TASK_MEMORY).coerceAtLeast(0L)
        }

        fun withDueAtMillis(dueAtMillis: Long): TaskMemory {
            return TaskMemory(
                state,
                dueAtMillis,
                stability,
                difficulty,
                totalReviews,
                lapses,
                learningStep,
                lastRating,
                matureIntervalDays,
                consecutivePasses,
                lastPassedDueAtMillis,
            )
        }

        fun encode(): String {
            return state + "\t" +
                dueAtMillis + "\t" +
                stability + "\t" +
                difficulty + "\t" +
                totalReviews + "\t" +
                lapses + "\t" +
                learningStep + "\t" +
                lastRating + "\t" +
                matureIntervalDays + "\t" +
                consecutivePasses + "\t" +
                lastPassedDueAtMillis
        }

        companion object {
            @JvmStatic
            fun initial(): TaskMemory {
                return TaskMemory("new", 0L, 0.4, 5.0, 0, 0, 0, "", 0)
            }

            @JvmStatic
            fun fromStudyFields(
                state: String?,
                dueAtMillis: Long,
                stability: Double,
                difficulty: Double,
                totalReviews: Int,
                lapses: Int,
                vararg rest: Any?,
            ): TaskMemory {
                requireArgCount(CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS, rest.toStudyArgsArray(), 2)
                return TaskMemory(
                    state,
                    dueAtMillis,
                    stability,
                    difficulty,
                    totalReviews,
                    lapses,
                    intArg(rest.toStudyArgsArray(), 0, CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS),
                    "",
                    intArg(rest.toStudyArgsArray(), 1, CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS),
                )
            }

            @JvmStatic
            fun decode(encoded: String?, fallback: TaskMemory?): TaskMemory {
                val safeFallback = fallback ?: initial()
                if (encoded.isNullOrEmpty()) {
                    return safeFallback
                }
                val parts = TASK_MEMORY_SEPARATOR.split(encoded, -1)
                if (parts.size < 9) {
                    return safeFallback
                }
                return try {
                    TaskMemory(
                        parts[0],
                        parts[1].toLong(),
                        parts[2].toDouble(),
                        parts[3].toDouble(),
                        parts[4].toInt(),
                        parts[5].toInt(),
                        parts[6].toInt(),
                        parts[7],
                        parts[8].toInt(),
                        if (parts.size > 9) parts[9].toInt() else 0,
                        if (parts.size > 10) parts[10].toLong() else 0L,
                    )
                } catch (_: RuntimeException) {
                    safeFallback
                }
            }
        }
    }

    class StudyItem(
        kanji: String?,
        state: String?,
        dueAtMillis: Long,
        stability: Double,
        difficulty: Double,
        totalReviews: Int,
        vararg rest: Any?,
    ) {
        @JvmField
        val kanji: String

        @JvmField
        val state: String

        @JvmField
        val dueAtMillis: Long

        @JvmField
        val stability: Double

        @JvmField
        val difficulty: Double

        @JvmField
        val totalReviews: Int

        @JvmField
        val lapses: Int

        @JvmField
        val learningStep: Int

        @JvmField
        val writingLevel: Int

        @JvmField
        val recognitionStage: Int

        @JvmField
        val consecutiveFailedRecognitionDays: Int

        @JvmField
        val lastFailedRecognitionDayMillis: Long

        @JvmField
        val writingRemediationPending: Boolean

        @JvmField
        val suppressedByTaskType: String

        @JvmField
        val suppressedAtMillis: Long

        @JvmField
        val matureIntervalDays: Int

        @JvmField
        val answerSignature: String

        @JvmField
        val activeToken: String?

        @JvmField
        val createdAtMillis: Long

        @JvmField
        val typingMeaningMemory: TaskMemory

        @JvmField
        val meaningKanjiMemory: TaskMemory

        @JvmField
        val kanjiMeaningMemory: TaskMemory

        @JvmField
        val fontMeaningMemory: TaskMemory

        @JvmField
        val wordReadingMemory: TaskMemory

        @JvmField
        val writingRemediationMemory: TaskMemory

        @JvmField
        val rung: RecordsBase.LadderRung

        @JvmField
        val phase: RecordsBase.SchedulerPhase

        @JvmField
        val realPassStreak: Int

        @JvmField
        val realAgainStreak: Int

        @JvmField
        val lastRealReviewDueAtMillis: Long

        @JvmField
        val hasSimilarKanji: Boolean

        @JvmField
        val similarKanjiMemory: TaskMemory

        @JvmField
        val hasKanjiReading: Boolean

        @JvmField
        val kanjiReadingMemory: TaskMemory

        init {
            val args = StudyItemArgs.from(state, dueAtMillis, stability, difficulty, totalReviews, rest)
            this.kanji = nullToEmpty(kanji)
            this.state = nullToEmpty(state)
            this.dueAtMillis = dueAtMillis
            this.stability = stability
            this.difficulty = difficulty
            this.totalReviews = totalReviews
            this.lapses = args.lapses
            this.learningStep = args.learningStep
            this.writingLevel = args.writingLevel
            this.recognitionStage = args.recognitionStage.coerceIn(-1, 2)
            this.consecutiveFailedRecognitionDays = args.consecutiveFailedRecognitionDays.coerceAtLeast(0)
            this.lastFailedRecognitionDayMillis = args.lastFailedRecognitionDayMillis.coerceAtLeast(0L)
            this.writingRemediationPending = args.writingRemediationPending
            this.suppressedByTaskType = nullToEmpty(args.suppressedByTaskType)
            this.suppressedAtMillis = args.suppressedAtMillis.coerceAtLeast(0L)
            this.matureIntervalDays = args.matureIntervalDays.coerceAtLeast(0)
            this.answerSignature = nullToEmpty(args.answerSignature)
            this.activeToken = args.activeToken
            this.createdAtMillis = args.createdAtMillis
            this.typingMeaningMemory = args.typingMeaningMemory ?: TaskMemory.initial()
            this.meaningKanjiMemory = args.meaningKanjiMemory ?: TaskMemory.initial()
            this.kanjiMeaningMemory = args.kanjiMeaningMemory ?: TaskMemory.initial()
            this.fontMeaningMemory = args.fontMeaningMemory ?: TaskMemory.initial()
            this.wordReadingMemory = args.wordReadingMemory ?: TaskMemory.initial()
            this.writingRemediationMemory = args.writingRemediationMemory ?: TaskMemory.initial()
            this.rung = args.rung ?: derivedRung(this.writingRemediationPending, this.recognitionStage)
            this.phase = args.phase ?: derivedPhase(this.state, this.totalReviews, this.writingRemediationPending)
            this.realPassStreak = args.realPassStreak.coerceAtLeast(0)
            this.realAgainStreak = if (args.realAgainStreak < 0) {
                this.consecutiveFailedRecognitionDays
            } else {
                args.realAgainStreak.coerceAtLeast(0)
            }
            this.lastRealReviewDueAtMillis = args.lastRealReviewDueAtMillis.coerceAtLeast(0L)
            this.hasSimilarKanji = args.hasSimilarKanji
            this.similarKanjiMemory = args.similarKanjiMemory ?: TaskMemory.initial()
            this.hasKanjiReading = args.hasKanjiReading
            this.kanjiReadingMemory = args.kanjiReadingMemory ?: TaskMemory.initial()
        }

        fun withToken(token: String?): StudyItem {
            return copyBuilder().activeToken(token).build()
        }

        fun withSuppression(suppressedByTaskType: String?, suppressedAtMillis: Long, matureIntervalDays: Int): StudyItem {
            return copyBuilder()
                .suppressedByTaskType(suppressedByTaskType)
                .suppressedAtMillis(suppressedAtMillis)
                .matureIntervalDays(matureIntervalDays)
                .build()
        }

        fun withAnswerSignature(answerSignature: String?): StudyItem {
            return copyBuilder().answerSignature(answerSignature).build()
        }

        fun withRung(rung: RecordsBase.LadderRung?): StudyItem {
            return copyBuilder().rung(rung).build()
        }

        fun withPhase(phase: RecordsBase.SchedulerPhase?): StudyItem {
            return copyBuilder().phase(phase).build()
        }

        fun withRungAndPhase(rung: RecordsBase.LadderRung?, phase: RecordsBase.SchedulerPhase?): StudyItem {
            return copyBuilder().rung(rung).phase(phase).build()
        }

        fun withLadderProgress(
            rung: RecordsBase.LadderRung?,
            phase: RecordsBase.SchedulerPhase?,
            stepIndex: Int,
            realPassStreak: Int,
            realAgainStreak: Int,
            lastRealReviewDueAtMillis: Long,
        ): StudyItem {
            return copyBuilder()
                .rung(rung)
                .phase(phase)
                .learningStep(stepIndex)
                .realPassStreak(realPassStreak)
                .realAgainStreak(realAgainStreak)
                .lastRealReviewDueAtMillis(lastRealReviewDueAtMillis)
                .build()
        }

        fun withHasSimilarKanji(hasSimilarKanji: Boolean): StudyItem {
            return copyBuilder().hasSimilarKanji(hasSimilarKanji).build()
        }

        /**
         * Snapshot of which conditional rungs this item can support, assembled
         * from its (never-persisted, annotated-on-read) availability flags.
         * Ladder-movement methods consume this instead of individual booleans.
         */
        fun rungAvailability(): RecordsBase.RungAvailability {
            return RecordsBase.RungAvailability.of(hasSimilarKanji, hasKanjiReading)
        }

        fun withHasKanjiReading(hasKanjiReading: Boolean): StudyItem {
            return copyBuilder().hasKanjiReading(hasKanjiReading).build()
        }

        fun withKanjiReadingMemory(memory: TaskMemory?): StudyItem {
            return copyBuilder().kanjiReadingMemory(memory).build()
        }

        fun withSimilarKanjiMemory(memory: TaskMemory?): StudyItem {
            return copyBuilder().similarKanjiMemory(memory).build()
        }

        fun memoryForTaskType(taskType: String?): TaskMemory {
            return when (taskType) {
                null -> kanjiMeaningMemory
                BridgeScheduler.TASK_WRITING_REMEDIATION,
                BridgeScheduler.TASK_WRITE_KANJI -> writingRemediationMemory
                BridgeScheduler.TASK_TYPING_MEANING,
                BridgeScheduler.TASK_TYPE_MEANING -> typingMeaningMemory
                BridgeScheduler.TASK_SIMILAR_KANJI -> similarKanjiMemory
                BridgeScheduler.TASK_MEANING_KANJI -> meaningKanjiMemory
                BridgeScheduler.TASK_WORD_READING -> wordReadingMemory
                BridgeScheduler.TASK_FONT_MEANING -> fontMeaningMemory
                BridgeScheduler.TASK_KANJI_READING -> kanjiReadingMemory
                else -> kanjiMeaningMemory
            }
        }

        fun memoryForRung(rung: RecordsBase.LadderRung?): TaskMemory {
            return when (rung) {
                null -> kanjiMeaningMemory
                RecordsBase.LadderRung.WRITE_KANJI -> writingRemediationMemory
                RecordsBase.LadderRung.TYPE_MEANING -> typingMeaningMemory
                RecordsBase.LadderRung.SIMILAR_KANJI -> similarKanjiMemory
                RecordsBase.LadderRung.MEANING_KANJI -> meaningKanjiMemory
                RecordsBase.LadderRung.FONT_MEANING -> fontMeaningMemory
                RecordsBase.LadderRung.WORD_READING -> wordReadingMemory
                RecordsBase.LadderRung.KANJI_MEANING -> kanjiMeaningMemory
                RecordsBase.LadderRung.KANJI_READING -> kanjiReadingMemory
            }
        }

        fun withTaskMemory(taskType: String?, memory: TaskMemory?): StudyItem {
            return when (taskType) {
                null -> copyBuilder().kanjiMeaningMemory(memory).build()
                BridgeScheduler.TASK_WRITING_REMEDIATION,
                BridgeScheduler.TASK_WRITE_KANJI -> copyBuilder().writingRemediationMemory(memory).build()
                BridgeScheduler.TASK_TYPING_MEANING,
                BridgeScheduler.TASK_TYPE_MEANING -> copyBuilder().typingMeaningMemory(memory).build()
                BridgeScheduler.TASK_SIMILAR_KANJI -> copyBuilder().similarKanjiMemory(memory).build()
                BridgeScheduler.TASK_MEANING_KANJI -> copyBuilder().meaningKanjiMemory(memory).build()
                BridgeScheduler.TASK_WORD_READING -> copyBuilder().wordReadingMemory(memory).build()
                BridgeScheduler.TASK_FONT_MEANING -> copyBuilder().fontMeaningMemory(memory).build()
                BridgeScheduler.TASK_KANJI_READING -> copyBuilder().kanjiReadingMemory(memory).build()
                else -> copyBuilder().kanjiMeaningMemory(memory).build()
            }
        }

        fun withTaskMemories(
            kanjiMemory: TaskMemory?,
            fontMemory: TaskMemory?,
            wordMemory: TaskMemory?,
            writingMemory: TaskMemory?,
        ): StudyItem {
            return copyBuilder()
                .kanjiMeaningMemory(kanjiMemory)
                .fontMeaningMemory(fontMemory)
                .wordReadingMemory(wordMemory)
                .writingRemediationMemory(writingMemory)
                .build()
        }

        fun withTaskMemories(
            typingMemory: TaskMemory?,
            kanjiMemory: TaskMemory?,
            fontMemory: TaskMemory?,
            wordMemory: TaskMemory?,
            writingMemory: TaskMemory?,
        ): StudyItem {
            return copyBuilder()
                .typingMeaningMemory(typingMemory)
                .kanjiMeaningMemory(kanjiMemory)
                .fontMeaningMemory(fontMemory)
                .wordReadingMemory(wordMemory)
                .writingRemediationMemory(writingMemory)
                .build()
        }

        fun copyBuilder(): StudyItemBuilder {
            return StudyItemBuilder(this)
        }

        class StudyItemBuilder internal constructor(src: StudyItem) {
            var kanji: String? = src.kanji
            var state: String? = src.state
            var dueAtMillis: Long = src.dueAtMillis
            var stability: Double = src.stability
            var difficulty: Double = src.difficulty
            var totalReviews: Int = src.totalReviews
            var lapses: Int = src.lapses
            var learningStep: Int = src.learningStep
            var writingLevel: Int = src.writingLevel
            var recognitionStage: Int = src.recognitionStage
            var consecutiveFailedRecognitionDays: Int = src.consecutiveFailedRecognitionDays
            var lastFailedRecognitionDayMillis: Long = src.lastFailedRecognitionDayMillis
            var writingRemediationPending: Boolean = src.writingRemediationPending
            var suppressedByTaskType: String? = src.suppressedByTaskType
            var suppressedAtMillis: Long = src.suppressedAtMillis
            var matureIntervalDays: Int = src.matureIntervalDays
            var answerSignature: String? = src.answerSignature
            var activeToken: String? = src.activeToken
            var createdAtMillis: Long = src.createdAtMillis
            var typingMeaningMemory: TaskMemory? = src.typingMeaningMemory
            var meaningKanjiMemory: TaskMemory? = src.meaningKanjiMemory
            var kanjiMeaningMemory: TaskMemory? = src.kanjiMeaningMemory
            var fontMeaningMemory: TaskMemory? = src.fontMeaningMemory
            var wordReadingMemory: TaskMemory? = src.wordReadingMemory
            var writingRemediationMemory: TaskMemory? = src.writingRemediationMemory
            var rung: RecordsBase.LadderRung? = src.rung
            var phase: RecordsBase.SchedulerPhase? = src.phase
            var realPassStreak: Int = src.realPassStreak
            var realAgainStreak: Int = src.realAgainStreak
            var lastRealReviewDueAtMillis: Long = src.lastRealReviewDueAtMillis
            var hasSimilarKanji: Boolean = src.hasSimilarKanji
            var similarKanjiMemory: TaskMemory? = src.similarKanjiMemory
            var hasKanjiReading: Boolean = src.hasKanjiReading
            var kanjiReadingMemory: TaskMemory? = src.kanjiReadingMemory
            var legacyFieldModified: Boolean = false
            var rungExplicitlySet: Boolean = false

            fun state(value: String?): StudyItemBuilder {
                this.state = value
                return this
            }

            fun dueAtMillis(value: Long): StudyItemBuilder {
                this.dueAtMillis = value
                return this
            }

            fun stability(value: Double): StudyItemBuilder {
                this.stability = value
                return this
            }

            fun difficulty(value: Double): StudyItemBuilder {
                this.difficulty = value
                return this
            }

            fun totalReviews(value: Int): StudyItemBuilder {
                this.totalReviews = value
                return this
            }

            fun lapses(value: Int): StudyItemBuilder {
                this.lapses = value
                return this
            }

            fun learningStep(value: Int): StudyItemBuilder {
                this.learningStep = value
                return this
            }

            fun writingLevel(value: Int): StudyItemBuilder {
                this.writingLevel = value
                return this
            }

            fun recognitionStage(value: Int): StudyItemBuilder {
                this.recognitionStage = value
                this.legacyFieldModified = true
                return this
            }

            fun consecutiveFailedRecognitionDays(value: Int): StudyItemBuilder {
                this.consecutiveFailedRecognitionDays = value
                return this
            }

            fun lastFailedRecognitionDayMillis(value: Long): StudyItemBuilder {
                this.lastFailedRecognitionDayMillis = value
                return this
            }

            fun writingRemediationPending(value: Boolean): StudyItemBuilder {
                this.writingRemediationPending = value
                this.legacyFieldModified = true
                return this
            }

            fun suppressedByTaskType(value: String?): StudyItemBuilder {
                this.suppressedByTaskType = value
                return this
            }

            fun suppressedAtMillis(value: Long): StudyItemBuilder {
                this.suppressedAtMillis = value
                return this
            }

            fun matureIntervalDays(value: Int): StudyItemBuilder {
                this.matureIntervalDays = value
                return this
            }

            fun answerSignature(value: String?): StudyItemBuilder {
                this.answerSignature = value
                return this
            }

            fun activeToken(value: String?): StudyItemBuilder {
                this.activeToken = value
                return this
            }

            fun createdAtMillis(value: Long): StudyItemBuilder {
                this.createdAtMillis = value
                return this
            }

            fun typingMeaningMemory(value: TaskMemory?): StudyItemBuilder {
                this.typingMeaningMemory = value
                return this
            }

            fun meaningKanjiMemory(value: TaskMemory?): StudyItemBuilder {
                this.meaningKanjiMemory = value
                return this
            }

            fun kanjiMeaningMemory(value: TaskMemory?): StudyItemBuilder {
                this.kanjiMeaningMemory = value
                return this
            }

            fun fontMeaningMemory(value: TaskMemory?): StudyItemBuilder {
                this.fontMeaningMemory = value
                return this
            }

            fun wordReadingMemory(value: TaskMemory?): StudyItemBuilder {
                this.wordReadingMemory = value
                return this
            }

            fun writingRemediationMemory(value: TaskMemory?): StudyItemBuilder {
                this.writingRemediationMemory = value
                return this
            }

            fun rung(value: RecordsBase.LadderRung?): StudyItemBuilder {
                this.rung = value
                this.rungExplicitlySet = true
                return this
            }

            fun phase(value: RecordsBase.SchedulerPhase?): StudyItemBuilder {
                this.phase = value
                return this
            }

            fun realPassStreak(value: Int): StudyItemBuilder {
                this.realPassStreak = value
                return this
            }

            fun realAgainStreak(value: Int): StudyItemBuilder {
                this.realAgainStreak = value
                return this
            }

            fun lastRealReviewDueAtMillis(value: Long): StudyItemBuilder {
                this.lastRealReviewDueAtMillis = value
                return this
            }

            fun hasSimilarKanji(value: Boolean): StudyItemBuilder {
                this.hasSimilarKanji = value
                return this
            }

            fun similarKanjiMemory(value: TaskMemory?): StudyItemBuilder {
                this.similarKanjiMemory = value
                return this
            }

            fun hasKanjiReading(value: Boolean): StudyItemBuilder {
                this.hasKanjiReading = value
                return this
            }

            fun kanjiReadingMemory(value: TaskMemory?): StudyItemBuilder {
                this.kanjiReadingMemory = value
                return this
            }

            fun build(): StudyItem {
                val effectiveRung = if (legacyFieldModified && !rungExplicitlySet) null else rung
                return StudyItem(
                    kanji,
                    state,
                    dueAtMillis,
                    stability,
                    difficulty,
                    totalReviews,
                    lapses,
                    learningStep,
                    writingLevel,
                    recognitionStage,
                    consecutiveFailedRecognitionDays,
                    lastFailedRecognitionDayMillis,
                    writingRemediationPending,
                    suppressedByTaskType,
                    suppressedAtMillis,
                    matureIntervalDays,
                    answerSignature,
                    activeToken,
                    createdAtMillis,
                    typingMeaningMemory,
                    meaningKanjiMemory,
                    kanjiMeaningMemory,
                    fontMeaningMemory,
                    wordReadingMemory,
                    writingRemediationMemory,
                    effectiveRung,
                    phase,
                    realPassStreak,
                    realAgainStreak,
                    lastRealReviewDueAtMillis,
                    hasSimilarKanji,
                    similarKanjiMemory,
                    hasKanjiReading,
                    kanjiReadingMemory,
                )
            }
        }

        private class StudyItemArgs {
            var lapses: Int = 0
            var learningStep: Int = 0
            var writingLevel: Int = 0
            var recognitionStage: Int = 0
            var consecutiveFailedRecognitionDays: Int = 0
            var lastFailedRecognitionDayMillis: Long = 0L
            var writingRemediationPending: Boolean = false
            var suppressedByTaskType: String? = null
            var suppressedAtMillis: Long = 0L
            var matureIntervalDays: Int = 0
            var answerSignature: String? = ""
            var activeToken: String? = null
            var createdAtMillis: Long = 0L
            var typingMeaningMemory: TaskMemory? = null
            var meaningKanjiMemory: TaskMemory? = null
            var kanjiMeaningMemory: TaskMemory? = null
            var fontMeaningMemory: TaskMemory? = null
            var wordReadingMemory: TaskMemory? = null
            var writingRemediationMemory: TaskMemory? = null
            var rung: RecordsBase.LadderRung? = null
            var phase: RecordsBase.SchedulerPhase? = null
            var realPassStreak: Int = 0
            var realAgainStreak: Int = -1
            var lastRealReviewDueAtMillis: Long = 0L
            var hasSimilarKanji: Boolean = false
            var similarKanjiMemory: TaskMemory? = null
            var hasKanjiReading: Boolean = false
            var kanjiReadingMemory: TaskMemory? = null

            companion object {
                fun from(
                    state: String?,
                    dueAtMillis: Long,
                    stability: Double,
                    difficulty: Double,
                    totalReviews: Int,
                    rest: Array<out Any?>,
                ): StudyItemArgs {
                    val args = rest.toStudyArgsArray()
                    requireArgCount(CONTEXT_STUDY_ITEM, args, 5, 9, 13, 17, 18, 19, 25, 26, 27, 28)
                    val result = StudyItemArgs()
                    result.lapses = intArg(args, 0, CONTEXT_STUDY_ITEM)
                    result.learningStep = intArg(args, 1, CONTEXT_STUDY_ITEM)
                    result.writingLevel = intArg(args, 2, CONTEXT_STUDY_ITEM)
                    var memoryStart = -1
                    if (args.size == 5) {
                        result.activeToken = stringArg(args, 3, CONTEXT_STUDY_ITEM)
                        result.createdAtMillis = longArg(args, 4, CONTEXT_STUDY_ITEM)
                    } else {
                        result.recognitionStage = intArg(args, 3, CONTEXT_STUDY_ITEM)
                        result.consecutiveFailedRecognitionDays = intArg(args, 4, CONTEXT_STUDY_ITEM)
                        result.lastFailedRecognitionDayMillis = longArg(args, 5, CONTEXT_STUDY_ITEM)
                        result.writingRemediationPending = booleanArg(args, 6, CONTEXT_STUDY_ITEM)
                        if (args.size == 9) {
                            result.activeToken = stringArg(args, 7, CONTEXT_STUDY_ITEM)
                            result.createdAtMillis = longArg(args, 8, CONTEXT_STUDY_ITEM)
                        } else {
                            result.suppressedByTaskType = stringArg(args, 7, CONTEXT_STUDY_ITEM)
                            result.suppressedAtMillis = longArg(args, 8, CONTEXT_STUDY_ITEM)
                            result.matureIntervalDays = intArg(args, 9, CONTEXT_STUDY_ITEM)
                            result.answerSignature = stringArg(args, 10, CONTEXT_STUDY_ITEM)
                            result.activeToken = stringArg(args, 11, CONTEXT_STUDY_ITEM)
                            result.createdAtMillis = longArg(args, 12, CONTEXT_STUDY_ITEM)
                            if (args.size > 13) {
                                memoryStart = 13
                            }
                        }
                    }
                    result.seedMemories(state, dueAtMillis, stability, difficulty, totalReviews)
                    if (memoryStart >= 0) {
                        result.applyMemories(args, memoryStart)
                    }
                    if (args.size == 25 || args.size == 26 || args.size == 27 || args.size == 28) {
                        // 25/26: the original 7 trailing state fields ending in
                        // similarKanjiMemory. 27/28 append 2 more
                        // (hasKanjiReading, kanjiReadingMemory) for the
                        // kanji_reading rung (Goal 78). stateStart depends on
                        // whether the meaning_kanji memory is present.
                        val stateStart = if (args.size == 25 || args.size == 27) 18 else 19
                        result.rung = arg(args, stateStart, CONTEXT_STUDY_ITEM) as? RecordsBase.LadderRung
                        result.phase = arg(args, stateStart + 1, CONTEXT_STUDY_ITEM) as? RecordsBase.SchedulerPhase
                        result.realPassStreak = intArg(args, stateStart + 2, CONTEXT_STUDY_ITEM)
                        result.realAgainStreak = intArg(args, stateStart + 3, CONTEXT_STUDY_ITEM)
                        result.lastRealReviewDueAtMillis = longArg(args, stateStart + 4, CONTEXT_STUDY_ITEM)
                        result.hasSimilarKanji = booleanArg(args, stateStart + 5, CONTEXT_STUDY_ITEM)
                        result.similarKanjiMemory = arg(args, stateStart + 6, CONTEXT_STUDY_ITEM) as? TaskMemory
                        if (args.size == 27 || args.size == 28) {
                            result.hasKanjiReading = booleanArg(args, stateStart + 7, CONTEXT_STUDY_ITEM)
                            result.kanjiReadingMemory = arg(args, stateStart + 8, CONTEXT_STUDY_ITEM) as? TaskMemory
                        }
                    }
                    return result
                }

                private fun StudyItemArgs.seedMemories(
                    state: String?,
                    dueAtMillis: Long,
                    stability: Double,
                    difficulty: Double,
                    totalReviews: Int,
                ) {
                    typingMeaningMemory = seedMemoryForStage(-1, this, state, dueAtMillis, stability, difficulty, totalReviews)
                    meaningKanjiMemory = TaskMemory.initial()
                    kanjiMeaningMemory = seedMemoryForStage(0, this, state, dueAtMillis, stability, difficulty, totalReviews)
                    fontMeaningMemory = seedMemoryForStage(1, this, state, dueAtMillis, stability, difficulty, totalReviews)
                    wordReadingMemory = seedMemoryForStage(2, this, state, dueAtMillis, stability, difficulty, totalReviews)
                    writingRemediationMemory = seedMemoryForWriting(this, state, dueAtMillis, stability, difficulty, totalReviews)
                    similarKanjiMemory = TaskMemory.initial()
                    kanjiReadingMemory = TaskMemory.initial()
                }

                private fun StudyItemArgs.applyMemories(rest: Array<Any?>, start: Int) {
                    if (rest.size == 17) {
                        kanjiMeaningMemory = arg(rest, start, CONTEXT_STUDY_ITEM) as? TaskMemory
                        fontMeaningMemory = arg(rest, start + 1, CONTEXT_STUDY_ITEM) as? TaskMemory
                        wordReadingMemory = arg(rest, start + 2, CONTEXT_STUDY_ITEM) as? TaskMemory
                        writingRemediationMemory = arg(rest, start + 3, CONTEXT_STUDY_ITEM) as? TaskMemory
                        return
                    }
                    typingMeaningMemory = arg(rest, start, CONTEXT_STUDY_ITEM) as? TaskMemory
                    if (rest.size == 19 || rest.size == 26 || rest.size == 28) {
                        meaningKanjiMemory = arg(rest, start + 1, CONTEXT_STUDY_ITEM) as? TaskMemory
                        kanjiMeaningMemory = arg(rest, start + 2, CONTEXT_STUDY_ITEM) as? TaskMemory
                        fontMeaningMemory = arg(rest, start + 3, CONTEXT_STUDY_ITEM) as? TaskMemory
                        wordReadingMemory = arg(rest, start + 4, CONTEXT_STUDY_ITEM) as? TaskMemory
                        writingRemediationMemory = arg(rest, start + 5, CONTEXT_STUDY_ITEM) as? TaskMemory
                        return
                    }
                    kanjiMeaningMemory = arg(rest, start + 1, CONTEXT_STUDY_ITEM) as? TaskMemory
                    fontMeaningMemory = arg(rest, start + 2, CONTEXT_STUDY_ITEM) as? TaskMemory
                    wordReadingMemory = arg(rest, start + 3, CONTEXT_STUDY_ITEM) as? TaskMemory
                    writingRemediationMemory = arg(rest, start + 4, CONTEXT_STUDY_ITEM) as? TaskMemory
                }

                private fun seedMemoryForStage(
                    memoryStage: Int,
                    args: StudyItemArgs,
                    state: String?,
                    dueAtMillis: Long,
                    stability: Double,
                    difficulty: Double,
                    totalReviews: Int,
                ): TaskMemory {
                    val safeStage = args.recognitionStage.coerceIn(-1, 2)
                    if (safeStage != memoryStage) {
                        return TaskMemory.initial()
                    }
                    return TaskMemory.fromStudyFields(
                        state,
                        dueAtMillis,
                        stability,
                        difficulty,
                        totalReviews,
                        args.lapses,
                        args.learningStep,
                        args.matureIntervalDays,
                    )
                }

                private fun seedMemoryForWriting(
                    args: StudyItemArgs,
                    state: String?,
                    dueAtMillis: Long,
                    stability: Double,
                    difficulty: Double,
                    totalReviews: Int,
                ): TaskMemory {
                    if (!args.writingRemediationPending) {
                        return TaskMemory.initial()
                    }
                    return TaskMemory.fromStudyFields(
                        state,
                        dueAtMillis,
                        stability,
                        difficulty,
                        totalReviews,
                        args.lapses,
                        args.learningStep,
                        args.matureIntervalDays,
                    )
                }
            }
        }

        private companion object {
            fun derivedRung(writingRemediationPending: Boolean, recognitionStage: Int): RecordsBase.LadderRung {
                if (writingRemediationPending) {
                    return RecordsBase.LadderRung.WRITE_KANJI
                }
                return when (recognitionStage.coerceIn(-1, 2)) {
                    -1 -> RecordsBase.LadderRung.TYPE_MEANING
                    1 -> RecordsBase.LadderRung.FONT_MEANING
                    2 -> RecordsBase.LadderRung.WORD_READING
                    else -> RecordsBase.LadderRung.KANJI_MEANING
                }
            }

            fun derivedPhase(
                state: String?,
                totalReviews: Int,
                writingRemediationPending: Boolean,
            ): RecordsBase.SchedulerPhase {
                if (state == LEARNING_REPEAT_REVIEW || state == "retired") {
                    return RecordsBase.SchedulerPhase.REVIEW
                }
                if (writingRemediationPending || totalReviews > 0) {
                    return RecordsBase.SchedulerPhase.RELEARNING
                }
                return RecordsBase.SchedulerPhase.NEW_LEARNING
            }
        }
    }

    class KanjiRecoveryTimeline(
        @JvmField val inventoryItem: KanjiInventoryItem?,
        @JvmField val currentRow: DashboardRow?,
        @JvmField val currentStudyItem: StudyItem?,
        events: List<KanjiTimelineEvent>,
    ) {
        @JvmField
        val events: List<KanjiTimelineEvent> = Collections.unmodifiableList(ArrayList(events))

        constructor(
            currentRow: DashboardRow?,
            currentStudyItem: StudyItem?,
            events: List<KanjiTimelineEvent>,
        ) : this(null, currentRow, currentStudyItem, events)
    }
}
