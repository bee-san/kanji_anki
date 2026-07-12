package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.AdaptiveStudyHealthPolicy
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.KaniOutcomePolicy
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LadderHealthPolicy
import dev.bee.kanjianki.core.RecentMistakePolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyImpactPolicy
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import java.util.Collections

class StudyStatsStore private constructor(private val queries: StudyStatsQueries) {
    internal constructor(store: LocalStore) : this(StudyStatsQueries(store))

    internal constructor(store: LocalStore, db: SQLiteDatabase) : this(StudyStatsQueries(store, db))

    fun studyTaskTimeStats(nowMillis: Long): StudyTaskTimeStats {
        return queries.studyTaskTimeStats(nowMillis)
    }

    fun recentMistakes(limit: Int): List<RecentMistake> {
        return queries.recentMistakes(limit)
    }

    fun studyStreak(nowMillis: Long): StudyStreak {
        return queries.studyStreak(nowMillis)
    }

    fun studyImpactStats(): StudyImpactStats {
        return queries.studyImpactStats()
    }

    fun kaniOutcomeStats(): KaniOutcomeStats {
        return queries.kaniOutcomeStats()
    }

    fun kanjiRepairEvidence(): List<KanjiRepairEvidence> {
        return queries.kanjiRepairEvidenceInputs().map { repairEvidence(KanjiRepairEvidencePolicy.summarize(it)) }
    }

    fun kanjiRepairEvidenceCohortStats(): RepairEvidenceCohortStats {
        return repairEvidenceCohortStats(kanjiRepairEvidence())
    }

    fun retiredRepairsLast30Days(nowMillis: Long): Int {
        return queries.retiredKanjiCountSince(nowMillis - RETIRED_STAT_WINDOW_MILLIS)
    }

    fun reviewStatsSince(sinceMillis: Long): RecordsSchedulerModels.ReviewStats {
        return queries.reviewStatsSince(sinceMillis)
    }

    fun studiedKanjiSince(sinceMillis: Long): Set<String> {
        return queries.studiedKanjiSince(sinceMillis)
    }

    class StudyStreak(
        @JvmField val currentDays: Int,
        @JvmField val bestDays: Int,
        @JvmField val studiedToday: Boolean,
        @JvmField val reviewsToday: Int,
        @JvmField val lastStudyAtMillis: Long,
    )

    class StudyImpactStats(
        totalReviews: Int,
        distinctReviewedKanji: Int,
        writingRequired: Int,
        writingPassed: Int,
        writingFailed: Int,
        manualOverrides: Int,
    ) {
        @JvmField val totalReviews: Int
        @JvmField val distinctReviewedKanji: Int
        @JvmField val writingRequired: Int
        @JvmField val writingPassed: Int
        @JvmField val writingFailed: Int
        @JvmField val manualOverrides: Int

        init {
            val impact = StudyImpactPolicy.summarize(
                totalReviews,
                distinctReviewedKanji,
                writingRequired,
                writingPassed,
                writingFailed,
                manualOverrides
            )
            this.totalReviews = impact.totalReviews()
            this.distinctReviewedKanji = impact.distinctReviewedKanji()
            this.writingRequired = impact.writingRequired()
            this.writingPassed = impact.writingPassed()
            this.writingFailed = impact.writingFailed()
            this.manualOverrides = impact.manualOverrides()
        }
    }

    class StudyTaskTimeStats(todayMillis: Long, lastSevenDaysMillis: Long, answeredTasks: Int) {
        @JvmField val todayMillis: Long
        @JvmField val lastSevenDaysMillis: Long
        @JvmField val answeredTasks: Int

        init {
            val summary = StudyTaskTimingPolicy.summarize(todayMillis, lastSevenDaysMillis, answeredTasks)
            this.todayMillis = summary.todayMillis
            this.lastSevenDaysMillis = summary.lastSevenDaysMillis
            this.answeredTasks = summary.answeredTasks
        }

        fun averageMillisPerTask(): Long {
            return StudyTaskTimingPolicy.summarize(todayMillis, lastSevenDaysMillis, answeredTasks)
                .averageMillisPerTask()
        }
    }

    class KaniOutcomeStats {
        @JvmField val weakKanjiImproved: WeakKanjiImprovedMetric
        @JvmField val matureSupportGained: MatureSupportGainedMetric
        @JvmField val ladderHealth: LadderHealthMetric
        @JvmField val adaptiveHealth: AdaptiveHealthMetric

        constructor(weakKanjiImproved: WeakKanjiImprovedMetric?, matureSupportGained: MatureSupportGainedMetric?) : this(
            weakKanjiImproved,
            matureSupportGained,
            LadderHealthMetric.empty(),
            AdaptiveHealthMetric.empty(),
        )

        constructor(
            weakKanjiImproved: WeakKanjiImprovedMetric?,
            matureSupportGained: MatureSupportGainedMetric?,
            ladderHealth: LadderHealthMetric?,
        ) : this(weakKanjiImproved, matureSupportGained, ladderHealth, AdaptiveHealthMetric.empty())

        constructor(
            weakKanjiImproved: WeakKanjiImprovedMetric?,
            matureSupportGained: MatureSupportGainedMetric?,
            ladderHealth: LadderHealthMetric?,
            adaptiveHealth: AdaptiveHealthMetric?,
        ) {
            this.weakKanjiImproved = weakKanjiImproved ?: WeakKanjiImprovedMetric.empty()
            this.matureSupportGained = matureSupportGained ?: MatureSupportGainedMetric.empty()
            this.ladderHealth = ladderHealth ?: LadderHealthMetric.empty()
            this.adaptiveHealth = adaptiveHealth ?: AdaptiveHealthMetric.empty()
        }

        companion object {
            @JvmStatic
            fun empty(): KaniOutcomeStats {
                return KaniOutcomeStats(
                    WeakKanjiImprovedMetric.empty(),
                    MatureSupportGainedMetric.empty(),
                    LadderHealthMetric.empty(),
                    AdaptiveHealthMetric.empty(),
                )
            }
        }
    }

    class AdaptiveHealthMetric(
        coreCounts: Map<CoreSkill, Int>?,
        activeRepairsByTask: Map<String, Int>?,
        activeRepairsByFailure: Map<FailureKind, Int>?,
        totalAdaptiveItems: Int,
        contextualCompleteCount: Int,
        activeRepairCount: Int,
        revalidationPendingCount: Int,
        recentCoreMissCount: Int,
        escalationRiskCount: Int,
        stuckRepairCount: Int,
        malformedStateCount: Int,
    ) {
        @JvmField val coreCounts: Map<CoreSkill, Int> = immutableCoreCounts(coreCounts)
        @JvmField val activeRepairsByTask: Map<String, Int> = immutableStringCounts(activeRepairsByTask)
        @JvmField val activeRepairsByFailure: Map<FailureKind, Int> = immutableFailureCounts(activeRepairsByFailure)
        @JvmField val totalAdaptiveItems: Int = totalAdaptiveItems.coerceAtLeast(0)
        @JvmField val contextualCompleteCount: Int = contextualCompleteCount.coerceAtLeast(0)
        @JvmField val activeRepairCount: Int = activeRepairCount.coerceAtLeast(0)
        @JvmField val revalidationPendingCount: Int = revalidationPendingCount.coerceAtLeast(0)
        @JvmField val recentCoreMissCount: Int = recentCoreMissCount.coerceAtLeast(0)
        @JvmField val escalationRiskCount: Int = escalationRiskCount.coerceAtLeast(0)
        @JvmField val stuckRepairCount: Int = stuckRepairCount.coerceAtLeast(0)
        @JvmField val malformedStateCount: Int = malformedStateCount.coerceAtLeast(0)

        fun countFor(skill: CoreSkill?): Int = coreCounts[skill] ?: 0

        fun repairCountFor(taskType: String?): Int = activeRepairsByTask[taskType] ?: 0

        fun failureCountFor(kind: FailureKind?): Int = activeRepairsByFailure[kind] ?: 0

        companion object {
            @JvmStatic
            fun empty(): AdaptiveHealthMetric = fromCore(AdaptiveStudyHealthPolicy.Metric.empty())

            internal fun fromCore(metric: AdaptiveStudyHealthPolicy.Metric): AdaptiveHealthMetric = AdaptiveHealthMetric(
                metric.coreCounts,
                metric.activeRepairsByTask,
                metric.activeRepairsByFailure,
                metric.totalAdaptiveItems,
                metric.contextualCompleteCount,
                metric.activeRepairCount,
                metric.revalidationPendingCount,
                metric.recentCoreMissCount,
                metric.escalationRiskCount,
                metric.stuckRepairCount,
                metric.malformedStateCount,
            )

            private fun immutableCoreCounts(counts: Map<CoreSkill, Int>?): Map<CoreSkill, Int> {
                val normalized = linkedMapOf<CoreSkill, Int>()
                CoreSkill.entries.forEach { normalized[it] = (counts?.get(it) ?: 0).coerceAtLeast(0) }
                return Collections.unmodifiableMap(normalized)
            }

            private fun immutableFailureCounts(counts: Map<FailureKind, Int>?): Map<FailureKind, Int> {
                val normalized = linkedMapOf<FailureKind, Int>()
                FailureKind.entries.forEach { normalized[it] = (counts?.get(it) ?: 0).coerceAtLeast(0) }
                return Collections.unmodifiableMap(normalized)
            }

            private fun immutableStringCounts(counts: Map<String, Int>?): Map<String, Int> {
                val normalized = linkedMapOf<String, Int>()
                counts.orEmpty().entries
                    .filter { it.key.isNotBlank() && it.value > 0 }
                    .sortedBy { it.key }
                    .forEach { normalized[it.key] = it.value }
                return Collections.unmodifiableMap(normalized)
            }
        }
    }

    class WeakKanjiImprovedMetric(
        improvedCount: Int,
        averageBeforeWeakness: Double,
        averageAfterWeakness: Double,
        examples: List<KanjiImprovement>?,
    ) {
        @JvmField val improvedCount: Int = improvedCount.coerceAtLeast(0)
        @JvmField val averageBeforeWeakness: Double = averageBeforeWeakness.coerceAtLeast(0.0)
        @JvmField val averageAfterWeakness: Double = averageAfterWeakness.coerceAtLeast(0.0)
        @JvmField val examples: List<KanjiImprovement> = Collections.unmodifiableList(ArrayList(examples ?: emptyList()))

        companion object {
            @JvmStatic
            fun empty(): WeakKanjiImprovedMetric {
                return WeakKanjiImprovedMetric(0, 0.0, 0.0, emptyList())
            }
        }
    }

    class KanjiImprovement(kanji: String?, beforeWeakness: Double, afterWeakness: Double) {
        @JvmField val kanji: String = kanji ?: ""
        @JvmField val beforeWeakness: Double = beforeWeakness.coerceAtLeast(0.0)
        @JvmField val afterWeakness: Double = afterWeakness.coerceAtLeast(0.0)
    }

    class MatureSupportGainedMetric {
        @JvmField val gainedSupportCount: Int
        @JvmField val matureSupportGained: Int
        @JvmField val firstSupportCount: Int
        @JvmField val examples: List<KanjiSupportGain>

        constructor(gainedSupportCount: Int, firstSupportCount: Int, examples: List<KanjiSupportGain>?) : this(
            gainedSupportCount,
            gainedSupportCount,
            firstSupportCount,
            examples
        )

        constructor(
            gainedSupportCount: Int,
            matureSupportGained: Int,
            firstSupportCount: Int,
            examples: List<KanjiSupportGain>?,
        ) {
            this.gainedSupportCount = gainedSupportCount.coerceAtLeast(0)
            this.matureSupportGained = matureSupportGained.coerceAtLeast(0)
            this.firstSupportCount = firstSupportCount.coerceAtLeast(0)
            this.examples = Collections.unmodifiableList(ArrayList(examples ?: emptyList()))
        }

        companion object {
            @JvmStatic
            fun empty(): MatureSupportGainedMetric {
                return MatureSupportGainedMetric(0, 0, 0, emptyList())
            }
        }
    }

    class KanjiSupportGain(kanji: String?, beforeMatureSupport: Int, afterMatureSupport: Int) {
        @JvmField val kanji: String = kanji ?: ""
        @JvmField val beforeMatureSupport: Int = beforeMatureSupport.coerceAtLeast(0)
        @JvmField val afterMatureSupport: Int = afterMatureSupport.coerceAtLeast(0)
    }

    class LadderHealthMetric {
        @JvmField val rungCounts: Map<RecordsBase.LadderRung, Int>
        @JvmField val totalActiveItems: Int
        @JvmField val realDueReviewsToMove: Int
        @JvmField val ladderPromotionIntervalDays: Int
        @JvmField val ladderDemotionFailStreak: Int
        @JvmField val promotionReadyCount: Int
        @JvmField val demotionRiskCount: Int
        @JvmField val demotionReadyCount: Int
        @JvmField val stuckCount: Int

        constructor(
            rungCounts: Map<out RecordsBase.LadderRung?, Int?>?,
            totalActiveItems: Int,
            realDueReviewsToMove: Int,
            promotionReadyCount: Int,
            demotionRiskCount: Int,
            demotionReadyCount: Int,
        ) : this(
            rungCounts,
            totalActiveItems,
            RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
            realDueReviewsToMove,
            promotionReadyCount,
            demotionRiskCount,
            demotionReadyCount,
            0
        )

        constructor(
            rungCounts: Map<out RecordsBase.LadderRung?, Int?>?,
            totalActiveItems: Int,
            ladderPromotionIntervalDays: Int,
            ladderDemotionFailStreak: Int,
            promotionReadyCount: Int,
            demotionRiskCount: Int,
            demotionReadyCount: Int,
        ) : this(
            rungCounts,
            totalActiveItems,
            ladderPromotionIntervalDays,
            ladderDemotionFailStreak,
            promotionReadyCount,
            demotionRiskCount,
            demotionReadyCount,
            0
        )

        constructor(
            rungCounts: Map<out RecordsBase.LadderRung?, Int?>?,
            totalActiveItems: Int,
            ladderPromotionIntervalDays: Int,
            ladderDemotionFailStreak: Int,
            promotionReadyCount: Int,
            demotionRiskCount: Int,
            demotionReadyCount: Int,
            stuckCount: Int,
        ) {
            val normalized = emptyRungDistribution().toMutableMap()
            if (rungCounts != null) {
                for (entry in rungCounts.entries) {
                    val rung = entry.key
                    if (rung != null) {
                        normalized[rung] = (entry.value ?: 0).coerceAtLeast(0)
                    }
                }
            }
            this.rungCounts = Collections.unmodifiableMap(normalized)
            this.totalActiveItems = totalActiveItems.coerceAtLeast(0)
            this.ladderPromotionIntervalDays = ladderPromotionIntervalDays.coerceAtLeast(1)
            this.ladderDemotionFailStreak = ladderDemotionFailStreak.coerceAtLeast(1)
            this.realDueReviewsToMove = this.ladderDemotionFailStreak
            this.promotionReadyCount = promotionReadyCount.coerceAtLeast(0)
            this.demotionRiskCount = demotionRiskCount.coerceAtLeast(0)
            this.demotionReadyCount = demotionReadyCount.coerceAtLeast(0)
            this.stuckCount = stuckCount.coerceAtLeast(0)
        }

        fun countFor(rung: RecordsBase.LadderRung?): Int {
            return rungCounts[rung] ?: 0
        }

        companion object {
            @JvmStatic
            fun empty(): LadderHealthMetric {
                val defaults = RecordsSyncModels.Settings.kikuDefaults()
                return LadderHealthMetric(
                    emptyRungDistribution(),
                    0,
                    defaults.ladderPromotionIntervalDays,
                    defaults.ladderDemotionFailStreak,
                    0,
                    0,
                    0,
                    0
                )
            }
        }
    }

    class RecentMistake(kanji: String?, rating: String?, reviewedAtMillis: Long) {
        @JvmField val kanji: String
        @JvmField val rating: String
        @JvmField val reviewedAtMillis: Long

        init {
            val mistake = RecentMistakePolicy.mistake(kanji, rating, reviewedAtMillis)
            this.kanji = mistake.kanji()
            this.rating = mistake.rating()
            this.reviewedAtMillis = mistake.reviewedAtMillis()
        }
    }

    class OutcomeSnapshot(weaknessScore: Int, matureSupportCount: Int) {
        @JvmField val weaknessScore: Int = weaknessScore.coerceAtLeast(0)
        @JvmField val matureSupportCount: Int = matureSupportCount.coerceAtLeast(0)

        fun weaknessScore(): Int = weaknessScore

        fun matureSupportCount(): Int = matureSupportCount
    }

    class OutcomeEvidence(kanji: String?, @JvmField val before: OutcomeSnapshot?, @JvmField val after: OutcomeSnapshot?) {
        @JvmField val kanji: String = kanji ?: ""

        fun kanji(): String = kanji

        fun before(): OutcomeSnapshot? = before

        fun after(): OutcomeSnapshot? = after
    }

    class KanjiRepairEvidence(evidence: KanjiRepairEvidencePolicy.Evidence) {
        @JvmField val kanji: String
        @JvmField val status: KanjiRepairEvidencePolicy.Status
        @JvmField val reason: String
        @JvmField val explanation: String
        @JvmField val beforeWeakness: Int?
        @JvmField val afterWeakness: Int?
        @JvmField val beforeMatureSupport: Int?
        @JvmField val afterMatureSupport: Int?
        @JvmField val kaniReviews: Int
        @JvmField val writingFailures: Int
        @JvmField val lastMistakeAtMillis: Long
        @JvmField val lastSyncAtMillis: Long
        @JvmField val confidence: Double
        @JvmField val confidenceReason: String

        init {
            this.kanji = evidence.kanji()
            this.status = evidence.status()
            this.reason = evidence.reason()
            this.explanation = evidence.explanation()
            this.beforeWeakness = evidence.beforeWeakness()
            this.afterWeakness = evidence.afterWeakness()
            this.beforeMatureSupport = evidence.beforeMatureSupport()
            this.afterMatureSupport = evidence.afterMatureSupport()
            this.kaniReviews = evidence.kaniReviews()
            this.writingFailures = evidence.writingFailures()
            this.lastMistakeAtMillis = evidence.lastMistakeAtMillis()
            this.lastSyncAtMillis = evidence.lastSyncAtMillis()
            this.confidence = evidence.confidence()
            this.confidenceReason = evidence.confidenceReason()
        }

    }

    class RepairEvidenceCohortStats(
        @JvmField val totalCount: Int,
        @JvmField val improvingCount: Int,
        @JvmField val stableCount: Int,
        @JvmField val regressingCount: Int,
        @JvmField val insufficientEvidenceCount: Int,
        @JvmField val highConfidenceCount: Int,
        @JvmField val mediumConfidenceCount: Int,
        @JvmField val lowConfidenceCount: Int,
        @JvmField val examples: List<KanjiRepairEvidence>,
        @JvmField val retiredLast30Days: Int = 0,
    ) {
        companion object {
            private const val HIGH_CONFIDENCE_THRESHOLD = 0.75
            private const val LOW_CONFIDENCE_THRESHOLD = 0.50

            @JvmStatic
            fun empty(): RepairEvidenceCohortStats {
                return RepairEvidenceCohortStats(0, 0, 0, 0, 0, 0, 0, 0, emptyList())
            }

            @JvmStatic
            @JvmOverloads
            fun from(evidence: List<KanjiRepairEvidence?>?, retiredLast30Days: Int = 0): RepairEvidenceCohortStats {
                val safeRetiredLast30Days = retiredLast30Days.coerceAtLeast(0)
                val safeEvidence: MutableList<KanjiRepairEvidence> = ArrayList()
                for (item in evidence.orEmpty()) {
                    if (item != null) {
                        safeEvidence.add(item)
                    }
                }
                if (safeEvidence.isEmpty()) {
                    return RepairEvidenceCohortStats(0, 0, 0, 0, 0, 0, 0, 0, emptyList(), safeRetiredLast30Days)
                }

                val sortedExamples = safeEvidence.sortedWith(
                    compareBy<KanjiRepairEvidence> { statusPriority(it.status) }
                        .thenBy { if (it.status == KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE) it.reason else "" }
                        .thenByDescending { it.confidence }
                        .thenBy { it.kanji }
                )

                var improvingCount = 0
                var stableCount = 0
                var regressingCount = 0
                var insufficientEvidenceCount = 0
                var highConfidenceCount = 0
                var lowConfidenceCount = 0

                for (item in safeEvidence) {
                    when (item.status) {
                        KanjiRepairEvidencePolicy.Status.IMPROVING -> improvingCount += 1
                        KanjiRepairEvidencePolicy.Status.STABLE -> stableCount += 1
                        KanjiRepairEvidencePolicy.Status.REGRESSING -> regressingCount += 1
                        KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE -> insufficientEvidenceCount += 1
                    }
                    when {
                        item.confidence >= HIGH_CONFIDENCE_THRESHOLD -> highConfidenceCount += 1
                        item.confidence < LOW_CONFIDENCE_THRESHOLD -> lowConfidenceCount += 1
                    }
                }

                val totalCount = safeEvidence.size
                val mediumConfidenceCount = totalCount - highConfidenceCount - lowConfidenceCount

                return RepairEvidenceCohortStats(
                    totalCount,
                    improvingCount,
                    stableCount,
                    regressingCount,
                    insufficientEvidenceCount,
                    highConfidenceCount,
                    mediumConfidenceCount,
                    lowConfidenceCount,
                    sortedExamples,
                    safeRetiredLast30Days,
                )
            }

            private fun statusPriority(status: KanjiRepairEvidencePolicy.Status): Int {
                return when (status) {
                    KanjiRepairEvidencePolicy.Status.REGRESSING -> 0
                    KanjiRepairEvidencePolicy.Status.IMPROVING -> 1
                    KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE -> 2
                    KanjiRepairEvidencePolicy.Status.STABLE -> 3
                }
            }
        }
    }

    class LadderItemEvidence {
        @JvmField val state: String
        @JvmField val rung: RecordsBase.LadderRung
        @JvmField val phase: RecordsBase.SchedulerPhase
        @JvmField val realPassStreak: Int
        @JvmField val realAgainStreak: Int
        @JvmField val matureIntervalDays: Int
        @JvmField val hasSimilarKanji: Boolean

        constructor(
            state: String?,
            rung: RecordsBase.LadderRung?,
            phase: RecordsBase.SchedulerPhase?,
            realPassStreak: Int,
            realAgainStreak: Int,
        ) : this(state, rung, phase, realPassStreak, realAgainStreak, 0, false)

        constructor(
            state: String?,
            rung: RecordsBase.LadderRung?,
            phase: RecordsBase.SchedulerPhase?,
            realPassStreak: Int,
            realAgainStreak: Int,
            matureIntervalDays: Int,
        ) : this(state, rung, phase, realPassStreak, realAgainStreak, matureIntervalDays, false)

        constructor(
            state: String?,
            rung: RecordsBase.LadderRung?,
            phase: RecordsBase.SchedulerPhase?,
            realPassStreak: Int,
            realAgainStreak: Int,
            matureIntervalDays: Int,
            hasSimilarKanji: Boolean,
        ) {
            this.state = state ?: ""
            this.rung = rung ?: RecordsBase.LadderRung.KANJI_MEANING
            this.phase = phase ?: RecordsBase.SchedulerPhase.NEW_LEARNING
            this.realPassStreak = realPassStreak.coerceAtLeast(0)
            this.realAgainStreak = realAgainStreak.coerceAtLeast(0)
            this.matureIntervalDays = matureIntervalDays.coerceAtLeast(0)
            this.hasSimilarKanji = hasSimilarKanji
        }

        fun state(): String = state

        fun rung(): RecordsBase.LadderRung = rung

        fun phase(): RecordsBase.SchedulerPhase = phase

        fun realPassStreak(): Int = realPassStreak

        fun realAgainStreak(): Int = realAgainStreak

        fun matureIntervalDays(): Int = matureIntervalDays

        fun hasSimilarKanji(): Boolean = hasSimilarKanji
    }

    class AdaptiveItemEvidence(
        state: String?,
        phase: RecordsBase.SchedulerPhase?,
        routingVersion: Int,
        adaptiveRouteStateJson: String?,
        contextualReadingConsecutivePasses: Int,
    ) {
        @JvmField val state: String = state.orEmpty()
        @JvmField val phase: RecordsBase.SchedulerPhase = phase ?: RecordsBase.SchedulerPhase.NEW_LEARNING
        @JvmField val routingVersion: Int = routingVersion.coerceAtLeast(0)
        @JvmField val adaptiveRouteStateJson: String = adaptiveRouteStateJson.orEmpty()
        @JvmField val contextualReadingConsecutivePasses: Int = contextualReadingConsecutivePasses.coerceAtLeast(0)
    }

    companion object {
        private const val RETIRED_STAT_WINDOW_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

        @JvmStatic
        fun calculateKaniOutcomeStats(
            outcomeEvidence: List<OutcomeEvidence?>?,
            ladderItems: List<LadderItemEvidence?>?,
            realDueReviewsToMove: Int,
        ): KaniOutcomeStats {
            return calculateKaniOutcomeStats(
                outcomeEvidence,
                ladderHealth(safeList(ladderItems), RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS, realDueReviewsToMove)
            )
        }

        @JvmStatic
        fun calculateKaniOutcomeStats(
            outcomeEvidence: List<OutcomeEvidence?>?,
            ladderItems: List<LadderItemEvidence?>?,
            adaptiveItems: List<AdaptiveItemEvidence?>?,
            ladderPromotionIntervalDays: Int,
            ladderDemotionFailStreak: Int,
        ): KaniOutcomeStats {
            val legacy = calculateKaniOutcomeStats(
                outcomeEvidence,
                ladderItems,
                ladderPromotionIntervalDays,
                ladderDemotionFailStreak,
            )
            return KaniOutcomeStats(
                legacy.weakKanjiImproved,
                legacy.matureSupportGained,
                legacy.ladderHealth,
                adaptiveHealth(adaptiveItems, ladderDemotionFailStreak),
            )
        }

        @JvmStatic
        fun calculateKaniOutcomeStats(
            outcomeEvidence: List<OutcomeEvidence?>?,
            ladderItems: List<LadderItemEvidence?>?,
            ladderPromotionIntervalDays: Int,
            ladderDemotionFailStreak: Int,
        ): KaniOutcomeStats {
            return calculateKaniOutcomeStats(
                outcomeEvidence,
                ladderHealth(safeList(ladderItems), ladderPromotionIntervalDays, ladderDemotionFailStreak)
            )
        }

        @JvmStatic
        fun repairEvidence(evidence: KanjiRepairEvidencePolicy.Evidence): KanjiRepairEvidence {
            return KanjiRepairEvidence(evidence)
        }

        @JvmStatic
        @JvmOverloads
        fun repairEvidenceCohortStats(
            evidence: List<KanjiRepairEvidence?>?,
            retiredLast30Days: Int = 0,
        ): RepairEvidenceCohortStats {
            return RepairEvidenceCohortStats.from(evidence, retiredLast30Days)
        }

        private fun calculateKaniOutcomeStats(
            outcomeEvidence: List<OutcomeEvidence?>?,
            ladderHealth: LadderHealthMetric,
        ): KaniOutcomeStats {
            return toAppOutcomeStats(
                KaniOutcomePolicy.summarize(toCoreOutcomeEvidence(outcomeEvidence), toCoreMetric(ladderHealth))
            )
        }

        @JvmStatic
        fun ladderHealth(
            items: List<LadderItemEvidence?>?,
            ladderPromotionIntervalDays: Int,
            ladderDemotionFailStreak: Int,
        ): LadderHealthMetric {
            return toAppMetric(
                LadderHealthPolicy.summarize(
                    toCoreLadderItems(items),
                    ladderPromotionIntervalDays,
                    ladderDemotionFailStreak
                )
            )
        }

        @JvmStatic
        fun adaptiveHealth(
            items: List<AdaptiveItemEvidence?>?,
            escalationThreshold: Int,
        ): AdaptiveHealthMetric = AdaptiveHealthMetric.fromCore(
            AdaptiveStudyHealthPolicy.summarize(
                items.orEmpty().map { item ->
                    item?.let {
                        AdaptiveStudyHealthPolicy.ItemEvidence(
                            it.state,
                            it.phase,
                            it.routingVersion,
                            it.adaptiveRouteStateJson,
                            it.contextualReadingConsecutivePasses,
                        )
                    }
                },
                escalationThreshold,
            ),
        )

        private fun emptyRungDistribution(): Map<RecordsBase.LadderRung, Int> {
            return LadderHealthPolicy.emptyRungDistribution()
        }

        private fun toCoreLadderItems(items: List<LadderItemEvidence?>?): List<LadderHealthPolicy.ItemEvidence?> {
            val out: MutableList<LadderHealthPolicy.ItemEvidence?> = ArrayList()
            for (item in safeList(items)) {
                out.add(
                    if (item == null) {
                        null
                    } else {
                        LadderHealthPolicy.ItemEvidence(
                            item.state,
                            item.rung,
                            item.phase,
                            item.realPassStreak,
                            item.realAgainStreak,
                            item.matureIntervalDays,
                            item.hasSimilarKanji
                        )
                    }
                )
            }
            return out
        }

        private fun toAppMetric(metric: LadderHealthPolicy.Metric?): LadderHealthMetric {
            val safeMetric = metric ?: LadderHealthPolicy.Metric.empty()
            return LadderHealthMetric(
                safeMetric.rungCounts(),
                safeMetric.totalActiveItems(),
                safeMetric.ladderPromotionIntervalDays(),
                safeMetric.ladderDemotionFailStreak(),
                safeMetric.promotionReadyCount(),
                safeMetric.demotionRiskCount(),
                safeMetric.demotionReadyCount(),
                safeMetric.stuckCount()
            )
        }

        private fun toAppOutcomeStats(stats: KaniOutcomePolicy.OutcomeStats?): KaniOutcomeStats {
            val safeStats = stats ?: KaniOutcomePolicy.OutcomeStats.empty()
            return KaniOutcomeStats(
                toAppWeakMetric(safeStats.weakKanjiImproved()),
                toAppSupportMetric(safeStats.matureSupportGained()),
                toAppMetric(safeStats.ladderHealth())
            )
        }

        private fun toAppWeakMetric(metric: KaniOutcomePolicy.WeakKanjiImprovedMetric?): WeakKanjiImprovedMetric {
            val safeMetric = metric ?: KaniOutcomePolicy.WeakKanjiImprovedMetric.empty()
            val examples: MutableList<KanjiImprovement> = ArrayList()
            for (example in safeMetric.examples()) {
                examples.add(KanjiImprovement(example.kanji(), example.beforeWeakness(), example.afterWeakness()))
            }
            return WeakKanjiImprovedMetric(
                safeMetric.improvedCount(),
                safeMetric.averageBeforeWeakness(),
                safeMetric.averageAfterWeakness(),
                examples
            )
        }

        private fun toAppSupportMetric(metric: KaniOutcomePolicy.MatureSupportGainedMetric?): MatureSupportGainedMetric {
            val safeMetric = metric ?: KaniOutcomePolicy.MatureSupportGainedMetric.empty()
            val examples: MutableList<KanjiSupportGain> = ArrayList()
            for (example in safeMetric.examples()) {
                examples.add(
                    KanjiSupportGain(
                        example.kanji(),
                        example.beforeMatureSupport(),
                        example.afterMatureSupport()
                    )
                )
            }
            return MatureSupportGainedMetric(
                safeMetric.gainedSupportCount(),
                safeMetric.matureSupportGained(),
                safeMetric.firstSupportCount(),
                examples
            )
        }

        private fun toCoreOutcomeEvidence(evidence: List<OutcomeEvidence?>?): List<KaniOutcomePolicy.OutcomeEvidence?> {
            val out: MutableList<KaniOutcomePolicy.OutcomeEvidence?> = ArrayList()
            for (item in safeList(evidence)) {
                out.add(
                    if (item == null) {
                        null
                    } else {
                        KaniOutcomePolicy.OutcomeEvidence(
                            item.kanji,
                            toCoreSnapshot(item.before),
                            toCoreSnapshot(item.after)
                        )
                    }
                )
            }
            return out
        }

        private fun toCoreSnapshot(snapshot: OutcomeSnapshot?): KaniOutcomePolicy.OutcomeSnapshot? {
            return if (snapshot == null) {
                null
            } else {
                KaniOutcomePolicy.OutcomeSnapshot(snapshot.weaknessScore, snapshot.matureSupportCount)
            }
        }

        private fun toCoreMetric(metric: LadderHealthMetric?): LadderHealthPolicy.Metric {
            if (metric == null) {
                return LadderHealthPolicy.Metric.empty()
            }
            return LadderHealthPolicy.fromCounts(
                metric.rungCounts,
                metric.totalActiveItems,
                metric.ladderPromotionIntervalDays,
                metric.ladderDemotionFailStreak,
                metric.promotionReadyCount,
                metric.demotionRiskCount,
                metric.demotionReadyCount,
                metric.stuckCount
            )
        }

        private fun <T> safeList(value: List<T?>?): List<T?> {
            return value ?: emptyList()
        }
    }
}
