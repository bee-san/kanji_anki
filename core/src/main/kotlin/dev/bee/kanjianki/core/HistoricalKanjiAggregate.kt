package dev.bee.kanjianki.core

class HistoricalKanjiAggregate(kanji: String?) {
    private val kanji: String = kanji ?: ""
    private var activeCards = 0
    private var suspendedCards = 0
    private var matureSupportCount = 0
    private var totalLapses = 0
    private var totalReps = 0
    private var intervalCount = 0
    private var intervalSum = 0.0
    private var stabilityCount = 0
    private var stabilitySum = 0.0
    private var difficultyCount = 0
    private var difficultySum = 0.0
    private var retrievabilityCount = 0
    private var retrievabilitySum = 0.0
    private var weaknessScore = 0
    private var reasonCode = ""
    private var activeExampleCount = 0
    private var suspendedExampleCount = 0

    fun add(card: RecordsSyncModels.Card, matureDays: Int) {
        addCard(
            card.intervalDays,
            card.reps,
            card.lapses,
            card.suspended,
            card.mature(matureDays),
            FsrsMemoryValues(card.fsrsStability, card.fsrsDifficulty, card.fsrsRetrievability),
        )
    }

    fun addCard(
        intervalDays: Int,
        reps: Int,
        lapses: Int,
        suspended: Boolean,
        mature: Boolean,
        fsrs: FsrsMemoryValues?,
    ) {
        val memory = fsrs ?: EMPTY_FSRS
        if (suspended) {
            suspendedCards++
        } else {
            activeCards++
        }
        if (mature) {
            matureSupportCount++
        }
        totalLapses += maxOf(0, lapses)
        totalReps += maxOf(0, reps)
        intervalSum += maxOf(0, intervalDays)
        intervalCount++
        if (memory.stability != null) {
            stabilitySum += memory.stability
            stabilityCount++
        }
        if (memory.difficulty != null) {
            difficultySum += memory.difficulty
            difficultyCount++
        }
        if (memory.retrievability != null) {
            retrievabilitySum += memory.retrievability
            retrievabilityCount++
        }
    }

    fun mergeDashboardEvidence(
        rowWeaknessScore: Int,
        rowReasonCode: String?,
        rowActiveExampleCount: Int,
        rowSuspendedExampleCount: Int,
        rowMatureSupportCount: Int,
    ) {
        weaknessScore = rowWeaknessScore
        reasonCode = rowReasonCode ?: ""
        activeExampleCount = maxOf(activeExampleCount, rowActiveExampleCount)
        suspendedExampleCount = maxOf(suspendedExampleCount, rowSuspendedExampleCount)
        matureSupportCount = maxOf(matureSupportCount, rowMatureSupportCount)
    }

    fun kanji(): String = kanji

    fun activeCards(): Int = activeCards

    fun suspendedCards(): Int = suspendedCards

    fun matureSupportCount(): Int = matureSupportCount

    fun totalLapses(): Int = totalLapses

    fun totalReps(): Int = totalReps

    fun averageIntervalDays(): Double {
        return if (intervalCount == 0) 0.0 else intervalSum / intervalCount
    }

    fun averageStability(): Double? {
        return if (stabilityCount == 0) null else stabilitySum / stabilityCount
    }

    fun averageDifficulty(): Double? {
        return if (difficultyCount == 0) null else difficultySum / difficultyCount
    }

    fun averageRetrievability(): Double? {
        return if (retrievabilityCount == 0) null else retrievabilitySum / retrievabilityCount
    }

    fun weaknessScore(): Int = weaknessScore

    fun reasonCode(): String = reasonCode

    fun activeExampleCount(): Int = activeExampleCount

    fun suspendedExampleCount(): Int = suspendedExampleCount

    fun impactMetricSnapshot(): KanjiImpactAnalyzer.MetricSnapshot {
        return KanjiImpactAnalyzer.MetricSnapshot(
            activeCards,
            suspendedCards,
            matureSupportCount,
            averageIntervalDays(),
            totalReps,
            totalLapses,
            averageStability(),
            averageDifficulty(),
            averageRetrievability(),
        )
    }

    @JvmRecord
    data class FsrsMemoryValues(
        val stability: Double?,
        val difficulty: Double?,
        val retrievability: Double?,
    )

    companion object {
        private val EMPTY_FSRS = FsrsMemoryValues(null, null, null)
    }
}
