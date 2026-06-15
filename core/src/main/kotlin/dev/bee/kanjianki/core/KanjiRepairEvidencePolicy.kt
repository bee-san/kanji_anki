package dev.bee.kanjianki.core

import kotlin.math.max

object KanjiRepairEvidencePolicy {
    private const val WEAKNESS_DELTA_THRESHOLD = 5
    private const val MIN_REVIEWS_TO_JUDGE = 3
    private const val MIN_POST_REVIEW_SAMPLES = 2

    enum class Status {
        IMPROVING,
        REGRESSING,
        STABLE,
        INSUFFICIENT_EVIDENCE,
    }

    @JvmStatic
    fun summarize(input: Input?): Evidence {
        val safe = input ?: Input()
        val kanji = safe.kanji()
        val before = safe.before()
        val after = safe.after()
        val reviews = safe.kaniReviews()
        val samples = safe.postReviewSamples()
        val writingFailures = safe.writingFailures()
        val lastMistakeAtMillis = safe.lastMistakeAtMillis()
        val lastReviewAtMillis = safe.lastReviewAtMillis()
        val lastSyncAtMillis = safe.lastSyncAtMillis()

        if (reviews <= 0) {
            return insufficient(
                kanji = kanji,
                beforeWeakness = before?.weaknessScore(),
                afterWeakness = after?.weaknessScore(),
                beforeSupport = before?.matureSupportCount(),
                afterSupport = after?.matureSupportCount(),
                reviews = reviews,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "no_kani_reviews",
                explanation = "No Kani reviews recorded for this kanji yet.",
                confidence = 0.05,
                confidenceReason = "No Kani reviews yet, so confidence stays very low.",
            )
        }

        if (before == null) {
            return insufficient(
                kanji = kanji,
                beforeWeakness = null,
                afterWeakness = after?.weaknessScore(),
                beforeSupport = null,
                afterSupport = after?.matureSupportCount(),
                reviews = reviews,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "no_baseline_sync",
                explanation = "Need a baseline AnkiDroid sync before judging this kanji.",
                confidence = 0.10,
                confidenceReason = "Need a baseline AnkiDroid sync before judging this kanji, so confidence stays low.",
            )
        }

        if (after == null) {
            return insufficient(
                kanji = kanji,
                beforeWeakness = before.weaknessScore(),
                afterWeakness = null,
                beforeSupport = before.matureSupportCount(),
                afterSupport = null,
                reviews = reviews,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "no_post_review_sync",
                explanation = "Study recorded; waiting for a later AnkiDroid sync.",
                confidence = 0.10,
                confidenceReason = "A later AnkiDroid sync is still missing, so confidence stays low.",
            )
        }

        if (lastSyncAtMillis <= lastReviewAtMillis) {
            return insufficient(
                kanji = kanji,
                beforeWeakness = before.weaknessScore(),
                afterWeakness = after.weaknessScore(),
                beforeSupport = before.matureSupportCount(),
                afterSupport = after.matureSupportCount(),
                reviews = reviews,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "no_sync_since_review",
                explanation = "No sync has landed since the latest Kani review.",
                confidence = 0.10,
                confidenceReason = "No sync has landed since the latest Kani review, so confidence stays low.",
            )
        }

        val beforeWeakness = before.weaknessScore()
        val afterWeakness = after.weaknessScore()
        val beforeSupport = before.matureSupportCount()
        val afterSupport = after.matureSupportCount()

        val weaknessDelta = afterWeakness - beforeWeakness
        val supportDelta = afterSupport - beforeSupport
        val weaknessImproved = weaknessDelta <= -WEAKNESS_DELTA_THRESHOLD
        val weaknessRegressed = weaknessDelta >= WEAKNESS_DELTA_THRESHOLD
        val supportImproved = supportDelta > 0
        val supportRegressed = supportDelta < 0
        val strongDelta = weaknessImproved || weaknessRegressed || supportImproved || supportRegressed
        val enoughSamples = reviews >= MIN_REVIEWS_TO_JUDGE && samples >= MIN_POST_REVIEW_SAMPLES
        val recentFailureSignal = writingFailures > 0 || lastMistakeAtMillis > lastReviewAtMillis

        if (!enoughSamples && !strongDelta) {
            return insufficient(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "too_few_post_review_samples",
                explanation = "Too few post-review samples to judge confidently.",
                confidence = 0.20,
                confidenceReason = "$reviews Kani reviews and $samples post-review samples are not enough to compare safely.",
            )
        }

        return when {
            weaknessImproved -> improving(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                samples = samples,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "improved_weakness_after_reviews",
                explanation = "After Kani reviews, AnkiDroid weakness moved $beforeWeakness → $afterWeakness.",
                confidence = if (enoughSamples) 0.84 else 0.64,
                confidenceReason = "Weakness moved $beforeWeakness → $afterWeakness after $reviews Kani reviews and $samples post-review samples.",
            )

            weaknessRegressed -> regressing(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                samples = samples,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "regressing_weakness_after_reviews",
                explanation = if (recentFailureSignal) {
                    "After Kani reviews, AnkiDroid weakness moved $beforeWeakness → $afterWeakness and mistakes are still showing up."
                } else {
                    "After Kani reviews, AnkiDroid weakness moved $beforeWeakness → $afterWeakness."
                },
                confidence = if (enoughSamples) 0.79 else 0.58,
                confidenceReason = "Weakness moved $beforeWeakness → $afterWeakness after $reviews Kani reviews and $samples post-review samples.",
            )

            supportImproved -> improving(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                samples = samples,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "improved_support_after_reviews",
                explanation = "After Kani reviews, mature AnkiDroid support moved $beforeSupport → $afterSupport.",
                confidence = if (enoughSamples) 0.84 else 0.64,
                confidenceReason = "Mature support moved $beforeSupport → $afterSupport after $reviews Kani reviews and $samples post-review samples.",
            )

            supportRegressed -> regressing(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                samples = samples,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "regressing_support_after_reviews",
                explanation = if (recentFailureSignal) {
                    "After Kani reviews, mature AnkiDroid support moved $beforeSupport → $afterSupport and mistakes are still showing up."
                } else {
                    "After Kani reviews, mature AnkiDroid support moved $beforeSupport → $afterSupport."
                },
                confidence = if (enoughSamples) 0.79 else 0.58,
                confidenceReason = "Mature support moved $beforeSupport → $afterSupport after $reviews Kani reviews and $samples post-review samples.",
            )

            recentFailureSignal -> stable(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                samples = samples,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "still_failing_after_reviews",
                explanation = "Still seeing mistakes/writing failures after Kani reviews, but the sync evidence has not moved enough to call a regression yet.",
                confidence = 0.44,
                confidenceReason = "$reviews Kani reviews and $samples post-review samples are enough to compare, but recent mistakes/writing failures are still showing up.",
            )

            else -> stable(
                kanji = kanji,
                beforeWeakness = beforeWeakness,
                afterWeakness = afterWeakness,
                beforeSupport = beforeSupport,
                afterSupport = afterSupport,
                reviews = reviews,
                samples = samples,
                writingFailures = writingFailures,
                lastMistakeAtMillis = lastMistakeAtMillis,
                lastSyncAtMillis = lastSyncAtMillis,
                reason = "stable_after_reviews",
                explanation = "Evidence is about unchanged after Kani reviews.",
                confidence = 0.62,
                confidenceReason = "$reviews Kani reviews and $samples post-review samples are enough to compare, but the before/after evidence stayed close together.",
            )
        }
    }

    private fun insufficient(
        kanji: String,
        beforeWeakness: Int?,
        afterWeakness: Int?,
        beforeSupport: Int?,
        afterSupport: Int?,
        reviews: Int,
        writingFailures: Int,
        lastMistakeAtMillis: Long,
        lastSyncAtMillis: Long,
        reason: String,
        explanation: String,
        confidence: Double,
        confidenceReason: String,
    ): Evidence {
        return Evidence(
            kanjiArg = kanji,
            statusArg = Status.INSUFFICIENT_EVIDENCE,
            reasonArg = reason,
            explanationArg = explanation,
            beforeWeaknessArg = beforeWeakness,
            afterWeaknessArg = afterWeakness,
            beforeMatureSupportArg = beforeSupport,
            afterMatureSupportArg = afterSupport,
            kaniReviewsArg = reviews,
            writingFailuresArg = writingFailures,
            lastMistakeAtMillisArg = lastMistakeAtMillis,
            lastSyncAtMillisArg = lastSyncAtMillis,
            confidenceArg = confidence,
            confidenceReasonArg = confidenceReason,
        )
    }

    private fun improving(
        kanji: String,
        beforeWeakness: Int,
        afterWeakness: Int,
        beforeSupport: Int,
        afterSupport: Int,
        reviews: Int,
        samples: Int,
        writingFailures: Int,
        lastMistakeAtMillis: Long,
        lastSyncAtMillis: Long,
        reason: String,
        explanation: String,
        confidence: Double,
        confidenceReason: String,
    ): Evidence {
        return Evidence(
            kanjiArg = kanji,
            statusArg = Status.IMPROVING,
            reasonArg = reason,
            explanationArg = explanation,
            beforeWeaknessArg = beforeWeakness,
            afterWeaknessArg = afterWeakness,
            beforeMatureSupportArg = beforeSupport,
            afterMatureSupportArg = afterSupport,
            kaniReviewsArg = reviews,
            writingFailuresArg = writingFailures,
            lastMistakeAtMillisArg = lastMistakeAtMillis,
            lastSyncAtMillisArg = lastSyncAtMillis,
            confidenceArg = confidence,
            confidenceReasonArg = confidenceReason,
        )
    }

    private fun regressing(
        kanji: String,
        beforeWeakness: Int,
        afterWeakness: Int,
        beforeSupport: Int,
        afterSupport: Int,
        reviews: Int,
        samples: Int,
        writingFailures: Int,
        lastMistakeAtMillis: Long,
        lastSyncAtMillis: Long,
        reason: String,
        explanation: String,
        confidence: Double,
        confidenceReason: String,
    ): Evidence {
        return Evidence(
            kanjiArg = kanji,
            statusArg = Status.REGRESSING,
            reasonArg = reason,
            explanationArg = explanation,
            beforeWeaknessArg = beforeWeakness,
            afterWeaknessArg = afterWeakness,
            beforeMatureSupportArg = beforeSupport,
            afterMatureSupportArg = afterSupport,
            kaniReviewsArg = reviews,
            writingFailuresArg = writingFailures,
            lastMistakeAtMillisArg = lastMistakeAtMillis,
            lastSyncAtMillisArg = lastSyncAtMillis,
            confidenceArg = confidence,
            confidenceReasonArg = confidenceReason,
        )
    }

    private fun stable(
        kanji: String,
        beforeWeakness: Int,
        afterWeakness: Int,
        beforeSupport: Int,
        afterSupport: Int,
        reviews: Int,
        samples: Int,
        writingFailures: Int,
        lastMistakeAtMillis: Long,
        lastSyncAtMillis: Long,
        reason: String,
        explanation: String,
        confidence: Double,
        confidenceReason: String,
    ): Evidence {
        return Evidence(
            kanjiArg = kanji,
            statusArg = Status.STABLE,
            reasonArg = reason,
            explanationArg = explanation,
            beforeWeaknessArg = beforeWeakness,
            afterWeaknessArg = afterWeakness,
            beforeMatureSupportArg = beforeSupport,
            afterMatureSupportArg = afterSupport,
            kaniReviewsArg = reviews,
            writingFailuresArg = writingFailures,
            lastMistakeAtMillisArg = lastMistakeAtMillis,
            lastSyncAtMillisArg = lastSyncAtMillis,
            confidenceArg = confidence,
            confidenceReasonArg = confidenceReason,
        )
    }

    class Input(
        kanjiArg: String? = null,
        beforeArg: Snapshot? = null,
        afterArg: Snapshot? = null,
        kaniReviewsArg: Int = 0,
        postReviewSamplesArg: Int = 0,
        writingFailuresArg: Int = 0,
        lastMistakeAtMillisArg: Long = 0L,
        firstReviewAtMillisArg: Long = 0L,
        lastReviewAtMillisArg: Long = 0L,
        lastSyncAtMillisArg: Long = 0L,
        ladderArg: Ladder? = null,
    ) {
        private val kanji = TextUtil.normalizeJapanese(kanjiArg)
        private val before = beforeArg
        private val after = afterArg
        private val kaniReviews = max(0, kaniReviewsArg)
        private val postReviewSamples = max(0, postReviewSamplesArg)
        private val writingFailures = max(0, writingFailuresArg)
        private val lastMistakeAtMillis = max(0L, lastMistakeAtMillisArg)
        private val firstReviewAtMillis = max(0L, firstReviewAtMillisArg)
        private val lastReviewAtMillis = max(0L, lastReviewAtMillisArg)
        private val lastSyncAtMillis = max(0L, lastSyncAtMillisArg)
        private val ladder = ladderArg

        fun kanji(): String = kanji

        fun before(): Snapshot? = before

        fun after(): Snapshot? = after

        fun kaniReviews(): Int = kaniReviews

        fun postReviewSamples(): Int = postReviewSamples

        fun writingFailures(): Int = writingFailures

        fun lastMistakeAtMillis(): Long = lastMistakeAtMillis

        fun firstReviewAtMillis(): Long = firstReviewAtMillis

        fun lastReviewAtMillis(): Long = lastReviewAtMillis

        fun lastSyncAtMillis(): Long = lastSyncAtMillis

        fun ladder(): Ladder? = ladder
    }

    class Snapshot(
        weaknessScoreArg: Int = 0,
        matureSupportCountArg: Int = 0,
        sampledAtMillisArg: Long = 0L,
        activeExampleCountArg: Int = 0,
        suspendedExampleCountArg: Int = 0,
        reasonCodeArg: String? = null,
    ) {
        private val weaknessScore = max(0, weaknessScoreArg)
        private val matureSupportCount = max(0, matureSupportCountArg)
        private val sampledAtMillis = max(0L, sampledAtMillisArg)
        private val activeExampleCount = max(0, activeExampleCountArg)
        private val suspendedExampleCount = max(0, suspendedExampleCountArg)
        private val reasonCode = reasonCodeArg?.trim()?.takeIf { it.isNotEmpty() }

        fun weaknessScore(): Int = weaknessScore

        fun matureSupportCount(): Int = matureSupportCount

        fun sampledAtMillis(): Long = sampledAtMillis

        fun activeExampleCount(): Int = activeExampleCount

        fun suspendedExampleCount(): Int = suspendedExampleCount

        fun reasonCode(): String? = reasonCode
    }

    class Ladder(
        rungArg: RecordsBase.LadderRung? = null,
        phaseArg: RecordsBase.SchedulerPhase? = null,
        realPassStreakArg: Int = 0,
        realAgainStreakArg: Int = 0,
        matureIntervalDaysArg: Int = 0,
    ) {
        private val rung = rungArg ?: RecordsBase.LadderRung.KANJI_MEANING
        private val phase = phaseArg ?: RecordsBase.SchedulerPhase.NEW_LEARNING
        private val realPassStreak = max(0, realPassStreakArg)
        private val realAgainStreak = max(0, realAgainStreakArg)
        private val matureIntervalDays = max(0, matureIntervalDaysArg)

        fun rung(): RecordsBase.LadderRung = rung

        fun phase(): RecordsBase.SchedulerPhase = phase

        fun realPassStreak(): Int = realPassStreak

        fun realAgainStreak(): Int = realAgainStreak

        fun matureIntervalDays(): Int = matureIntervalDays
    }

    class Evidence(
        kanjiArg: String? = null,
        statusArg: Status = Status.INSUFFICIENT_EVIDENCE,
        reasonArg: String? = null,
        explanationArg: String? = null,
        beforeWeaknessArg: Int? = null,
        afterWeaknessArg: Int? = null,
        beforeMatureSupportArg: Int? = null,
        afterMatureSupportArg: Int? = null,
        kaniReviewsArg: Int = 0,
        writingFailuresArg: Int = 0,
        lastMistakeAtMillisArg: Long = 0L,
        lastSyncAtMillisArg: Long = 0L,
        confidenceArg: Double = 0.0,
        confidenceReasonArg: String? = null,
    ) {
        private val kanji = TextUtil.normalizeJapanese(kanjiArg)
        private val status = statusArg
        private val reason = reasonArg?.trim().orEmpty()
        private val explanation = explanationArg?.trim().orEmpty()
        private val beforeWeakness = normalizeNullableInt(beforeWeaknessArg)
        private val afterWeakness = normalizeNullableInt(afterWeaknessArg)
        private val beforeMatureSupport = normalizeNullableInt(beforeMatureSupportArg)
        private val afterMatureSupport = normalizeNullableInt(afterMatureSupportArg)
        private val kaniReviews = max(0, kaniReviewsArg)
        private val writingFailures = max(0, writingFailuresArg)
        private val lastMistakeAtMillis = max(0L, lastMistakeAtMillisArg)
        private val lastSyncAtMillis = max(0L, lastSyncAtMillisArg)
        private val confidence = normalizeConfidence(confidenceArg)
        private val confidenceReason = confidenceReasonArg?.trim().orEmpty()

        fun kanji(): String = kanji

        fun status(): Status = status

        fun reason(): String = reason

        fun explanation(): String = explanation

        fun beforeWeakness(): Int? = beforeWeakness

        fun afterWeakness(): Int? = afterWeakness

        fun beforeMatureSupport(): Int? = beforeMatureSupport

        fun afterMatureSupport(): Int? = afterMatureSupport

        fun kaniReviews(): Int = kaniReviews

        fun writingFailures(): Int = writingFailures

        fun lastMistakeAtMillis(): Long = lastMistakeAtMillis

        fun lastSyncAtMillis(): Long = lastSyncAtMillis

        fun confidence(): Double = confidence

        fun confidenceReason(): String = confidenceReason
    }

    private fun normalizeNullableInt(value: Int?): Int? {
        return value?.let { max(0, it) }
    }

    private fun normalizeConfidence(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) {
            return 0.0
        }
        return when {
            value < 0.0 -> 0.0
            value > 1.0 -> 1.0
            else -> value
        }
    }
}
