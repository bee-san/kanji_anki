package dev.bee.kanjianki.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class KanjiImpactAnalyzer {
    fun analyze(histories: List<KanjiHistory?>?): Report {
        if (histories.isNullOrEmpty()) {
            return Report(0, 0, 0, emptyList())
        }
        val rows = ArrayList<Row>()
        var helped = 0
        var notHelping = 0
        var needsMoreCards = 0
        for (history in histories) {
            if (history == null || history.kanji.isEmpty()) {
                continue
            }
            val row = rowFor(history)
            rows.add(row)
            when (row.bucket) {
                BUCKET_HELPED -> helped++
                BUCKET_NEEDS_MORE_CARDS -> needsMoreCards++
                else -> notHelping++
            }
        }
        rows.sortWith(
            compareBy<Row> { bucketRank(it.bucket) }
                .thenBy { -abs(it.retentionDelta) }
                .thenBy { it.kanji }
        )
        return Report(helped, notHelping, needsMoreCards, rows)
    }

    private fun rowFor(history: KanjiHistory): Row {
        val baseline = firstNonNull(history.sameCardBaseline, history.baseline)
        val current = firstNonNull(history.sameCardCurrent, history.current)
        val bucket = bucketFor(history, baseline, current)
        val baselineDifficulty = baseline?.difficultyScore() ?: 0.0
        val currentDifficulty = current?.difficultyScore() ?: 0.0
        val baselineRetention = baseline?.retentionScore() ?: 0.0
        val currentRetention = current?.retentionScore() ?: 0.0
        val baselineMature = history.baseline?.matureCards ?: 0
        val currentMature = history.current?.matureCards ?: 0
        return Row.create(
            kanji = history.kanji,
            bucket = bucket,
            baselineDifficulty = baselineDifficulty,
            currentDifficulty = currentDifficulty,
            baselineRetention = baselineRetention,
            currentRetention = currentRetention,
            baselineMatureCards = baselineMature,
            currentMatureCards = currentMature,
            sameCardCount = history.commonCards,
            newCardCount = history.newCards,
            currentCardCount = history.current?.totalCards() ?: 0,
            reviewCount = history.reviewCount,
            advice = adviceFor(bucket)
        )
    }

    private fun bucketFor(history: KanjiHistory, baseline: MetricSnapshot?, current: MetricSnapshot?): String {
        if (history.current == null ||
            history.current.totalCards() < 2 ||
            history.commonCards <= 0 ||
            history.reviewCount < MIN_REVIEWS_TO_JUDGE ||
            baseline == null
        ) {
            return BUCKET_NEEDS_MORE_CARDS
        }
        val retentionDelta = current!!.retentionScore() - baseline.retentionScore()
        val difficultyDelta = current.difficultyScore() - baseline.difficultyScore()
        val sameMatureDelta = current.matureCards - baseline.matureCards
        val helped = retentionDelta >= RETENTION_HELP_THRESHOLD ||
            difficultyDelta <= DIFFICULTY_HELP_THRESHOLD ||
            sameMatureDelta > 0
        return if (helped) BUCKET_HELPED else BUCKET_NOT_HELPING
    }

    class Report(
        helpedCount: Int,
        notHelpingCount: Int,
        needsMoreCardsCount: Int,
        rows: List<Row?>?
    ) {
        @JvmField
        val helpedCount: Int = max(0, helpedCount)

        @JvmField
        val notHelpingCount: Int = max(0, notHelpingCount)

        @JvmField
        val needsMoreCardsCount: Int = max(0, needsMoreCardsCount)

        @JvmField
        val rows: List<Row> = rows
            ?.filterNotNull()
            ?.toList()
            ?.let { java.util.Collections.unmodifiableList(it) }
            ?: emptyList()

        fun empty(): Boolean = helpedCount == 0 && notHelpingCount == 0 && needsMoreCardsCount == 0
    }

    class Row private constructor(
        kanji: String,
        bucket: String,
        baselineDifficulty: Double,
        currentDifficulty: Double,
        baselineRetention: Double,
        currentRetention: Double,
        baselineMatureCards: Int,
        currentMatureCards: Int,
        sameCardCount: Int,
        newCardCount: Int,
        currentCardCount: Int,
        reviewCount: Int,
        advice: String
    ) {
        @JvmField
        val kanji: String = kanji

        @JvmField
        val bucket: String = bucket

        @JvmField
        val baselineDifficulty: Double = baselineDifficulty

        @JvmField
        val currentDifficulty: Double = currentDifficulty

        @JvmField
        val baselineRetention: Double = clamp(baselineRetention, 0.0, 1.0)

        @JvmField
        val currentRetention: Double = clamp(currentRetention, 0.0, 1.0)

        @JvmField
        val difficultyDelta: Double = currentDifficulty - baselineDifficulty

        @JvmField
        val retentionDelta: Double = currentRetention - baselineRetention

        @JvmField
        val baselineMatureCards: Int = max(0, baselineMatureCards)

        @JvmField
        val currentMatureCards: Int = max(0, currentMatureCards)

        @JvmField
        val sameCardCount: Int = max(0, sameCardCount)

        @JvmField
        val newCardCount: Int = max(0, newCardCount)

        @JvmField
        val currentCardCount: Int = max(0, currentCardCount)

        @JvmField
        val reviewCount: Int = max(0, reviewCount)

        @JvmField
        val advice: String = advice

        fun summary(): String = String.format(
            Locale.ROOT,
            "%s: difficulty %.1f -> %.1f, retention %d%% -> %d%%, mature cards %d -> %d",
            kanji,
            baselineDifficulty,
            currentDifficulty,
            (baselineRetention * 100.0).roundToInt(),
            (currentRetention * 100.0).roundToInt(),
            baselineMatureCards,
            currentMatureCards
        )

        companion object {
            fun create(
                kanji: String,
                bucket: String,
                baselineDifficulty: Double,
                currentDifficulty: Double,
                baselineRetention: Double,
                currentRetention: Double,
                baselineMatureCards: Int,
                currentMatureCards: Int,
                sameCardCount: Int,
                newCardCount: Int,
                currentCardCount: Int,
                reviewCount: Int,
                advice: String
            ): Row {
                return Row(
                    kanji,
                    bucket,
                    baselineDifficulty,
                    currentDifficulty,
                    baselineRetention,
                    currentRetention,
                    baselineMatureCards,
                    currentMatureCards,
                    sameCardCount,
                    newCardCount,
                    currentCardCount,
                    reviewCount,
                    advice
                )
            }
        }
    }

    class KanjiHistory(
        kanji: String?,
        @JvmField val baseline: MetricSnapshot?,
        @JvmField val current: MetricSnapshot?,
        @JvmField val sameCardBaseline: MetricSnapshot?,
        @JvmField val sameCardCurrent: MetricSnapshot?,
        counts: IntArray?
    ) {
        @JvmField
        val kanji: String = kanji ?: ""

        @JvmField
        val commonCards: Int = max(0, countAt(counts, 0))

        @JvmField
        val newCards: Int = max(0, countAt(counts, 1))

        @JvmField
        val reviewCount: Int = max(0, countAt(counts, 2))

        constructor(
            kanji: String?,
            baseline: MetricSnapshot?,
            current: MetricSnapshot?,
            sameCardBaseline: MetricSnapshot?,
            sameCardCurrent: MetricSnapshot?
        ) : this(kanji, baseline, current, sameCardBaseline, sameCardCurrent, null)

        constructor(
            kanji: String?,
            baseline: MetricSnapshot?,
            current: MetricSnapshot?,
            sameCardBaseline: MetricSnapshot?,
            sameCardCurrent: MetricSnapshot?,
            commonCards: Int
        ) : this(kanji, baseline, current, sameCardBaseline, sameCardCurrent, intArrayOf(commonCards))

        constructor(
            kanji: String?,
            baseline: MetricSnapshot?,
            current: MetricSnapshot?,
            sameCardBaseline: MetricSnapshot?,
            sameCardCurrent: MetricSnapshot?,
            commonCards: Int,
            newCards: Int
        ) : this(kanji, baseline, current, sameCardBaseline, sameCardCurrent, intArrayOf(commonCards, newCards))

        constructor(
            kanji: String?,
            baseline: MetricSnapshot?,
            current: MetricSnapshot?,
            sameCardBaseline: MetricSnapshot?,
            sameCardCurrent: MetricSnapshot?,
            commonCards: Int,
            newCards: Int,
            reviewCount: Int
        ) : this(
            kanji,
            baseline,
            current,
            sameCardBaseline,
            sameCardCurrent,
            intArrayOf(commonCards, newCards, reviewCount)
        )

        companion object {
            private fun countAt(counts: IntArray?, index: Int): Int {
                return if (counts == null || counts.size <= index) 0 else counts[index]
            }
        }
    }

    class MetricSnapshot(
        activeCards: Int,
        suspendedCards: Int,
        matureCards: Int,
        averageIntervalDays: Double,
        reps: Int,
        lapses: Int,
        fsrsValues: Array<Double?>?
    ) {
        @JvmField
        val activeCards: Int = max(0, activeCards)

        @JvmField
        val suspendedCards: Int = max(0, suspendedCards)

        @JvmField
        val matureCards: Int = max(0, matureCards)

        @JvmField
        val averageIntervalDays: Double = finiteOrZero(averageIntervalDays).coerceAtLeast(0.0)

        @JvmField
        val reps: Int = max(0, reps)

        @JvmField
        val lapses: Int = max(0, lapses)

        @JvmField
        val fsrsStability: Double? = fsrsAt(fsrsValues, 0)

        @JvmField
        val fsrsDifficulty: Double? = fsrsAt(fsrsValues, 1)

        @JvmField
        val fsrsRetrievability: Double? = fsrsAt(fsrsValues, 2)

        constructor(
            activeCards: Int,
            suspendedCards: Int,
            matureCards: Int,
            averageIntervalDays: Double,
            reps: Int,
            lapses: Int
        ) : this(activeCards, suspendedCards, matureCards, averageIntervalDays, reps, lapses, null as Array<Double?>?)

        constructor(
            activeCards: Int,
            suspendedCards: Int,
            matureCards: Int,
            averageIntervalDays: Double,
            reps: Int,
            lapses: Int,
            fsrsStability: Double?
        ) : this(
            activeCards,
            suspendedCards,
            matureCards,
            averageIntervalDays,
            reps,
            lapses,
            arrayOf(fsrsStability)
        )

        constructor(
            activeCards: Int,
            suspendedCards: Int,
            matureCards: Int,
            averageIntervalDays: Double,
            reps: Int,
            lapses: Int,
            fsrsStability: Double?,
            fsrsDifficulty: Double?
        ) : this(
            activeCards,
            suspendedCards,
            matureCards,
            averageIntervalDays,
            reps,
            lapses,
            arrayOf(fsrsStability, fsrsDifficulty)
        )

        constructor(
            activeCards: Int,
            suspendedCards: Int,
            matureCards: Int,
            averageIntervalDays: Double,
            reps: Int,
            lapses: Int,
            fsrsStability: Double?,
            fsrsDifficulty: Double?,
            fsrsRetrievability: Double?
        ) : this(
            activeCards,
            suspendedCards,
            matureCards,
            averageIntervalDays,
            reps,
            lapses,
            arrayOf(fsrsStability, fsrsDifficulty, fsrsRetrievability)
        )

        fun totalCards(): Int = saturatingAddNonNegative(activeCards, suspendedCards)

        fun retentionScore(): Double {
            if (fsrsRetrievability != null) {
                return normalizeRetention(fsrsRetrievability)
            }
            if (reps > 0) {
                return clamp((reps - min(reps, lapses)).toDouble() / reps.toDouble(), 0.0, 1.0)
            }
            if (matureCards > 0) {
                return 0.88
            }
            return 0.50
        }

        fun difficultyScore(): Double {
            if (fsrsDifficulty != null) {
                return clamp(fsrsDifficulty, 1.0, 10.0)
            }
            val lapseRate = if (reps == 0) 0.0 else lapses.toDouble() / reps.toDouble()
            val matureRatio = if (totalCards() == 0) 0.0 else matureCards.toDouble() / totalCards().toDouble()
            return clamp(5.0 + lapseRate * 5.0 - matureRatio * 1.5, 1.0, 10.0)
        }

        companion object {
            private fun normalizeRetention(value: Double): Double {
                val normalized = if (value > 1.0) value / 100.0 else value
                return clamp(normalized, 0.0, 1.0)
            }

            private fun fsrsAt(values: Array<Double?>?, index: Int): Double? {
                return if (values == null || values.size <= index) {
                    null
                } else {
                    values[index]?.takeIf { it.isFinite() }
                }
            }
        }
    }

    companion object {
        const val BUCKET_HELPED: String = "helped"
        const val BUCKET_NOT_HELPING: String = "not_helping_yet"
        const val BUCKET_NEEDS_MORE_CARDS: String = "needs_more_cards"

        private const val RETENTION_HELP_THRESHOLD = 0.08
        private const val DIFFICULTY_HELP_THRESHOLD = -0.30
        private const val MIN_REVIEWS_TO_JUDGE = 3

        @JvmStatic
        fun notHelpingRows(report: Report?): List<Row> {
            if (report == null) {
                return emptyList()
            }
            return report.rows
                .filter { it.bucket == BUCKET_NOT_HELPING }
                .let { java.util.Collections.unmodifiableList(it) }
        }

        private fun firstNonNull(first: MetricSnapshot?, second: MetricSnapshot?): MetricSnapshot? {
            return first ?: second
        }

        private fun bucketRank(bucket: String): Int {
            if (bucket == BUCKET_HELPED) {
                return 0
            }
            if (bucket == BUCKET_NOT_HELPING) {
                return 1
            }
            return 2
        }

        private fun adviceFor(bucket: String): String {
            if (bucket == BUCKET_HELPED) {
                return "Kani appears to be helping this kanji."
            }
            if (bucket == BUCKET_NOT_HELPING) {
                return "Kani is not moving the needle yet."
            }
            return "Immerse and mine more flashcards for this kanji before judging Kani."
        }

        private fun finiteOrZero(value: Double): Double = if (value.isFinite()) value else 0.0

        private fun clamp(value: Double, min: Double, max: Double): Double =
            max(min, min(max, finiteOrZero(value)))
    }
}
