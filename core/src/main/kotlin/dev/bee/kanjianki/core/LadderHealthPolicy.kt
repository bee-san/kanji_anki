package dev.bee.kanjianki.core

import java.util.Collections
import java.util.LinkedHashMap

object LadderHealthPolicy {
    @JvmStatic
    fun summarize(
        items: List<ItemEvidence?>?,
        ladderPromotionIntervalDays: Int,
        ladderDemotionFailStreak: Int,
    ): Metric {
        val promotionDays = maxOf(1, ladderPromotionIntervalDays)
        val failStreak = maxOf(1, ladderDemotionFailStreak)
        val accumulator = Accumulator()
        for (item in items.orEmpty()) {
            accumulator.addItem(item, promotionDays, failStreak)
        }
        return accumulator.metric(promotionDays, failStreak)
    }

    @JvmStatic
    fun fromCounts(
        rungCounts: Map<out RecordsBase.LadderRung?, Int?>?,
        totalActiveItems: Int,
        ladderPromotionIntervalDays: Int,
        ladderDemotionFailStreak: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
        demotionReadyCount: Int,
    ): Metric {
        return Metric(
            rungCounts,
            totalActiveItems,
            ladderPromotionIntervalDays,
            ladderDemotionFailStreak,
            promotionReadyCount,
            demotionRiskCount,
            demotionReadyCount,
        )
    }

    @JvmStatic
    fun emptyRungDistribution(): MutableMap<RecordsBase.LadderRung, Int> {
        val out = LinkedHashMap<RecordsBase.LadderRung, Int>()
        for (rung in RecordsBase.LadderRung.values()) {
            out[rung] = 0
        }
        return out
    }

    class ItemEvidence(
        state: String?,
        rung: RecordsBase.LadderRung?,
        phase: RecordsBase.SchedulerPhase?,
        realPassStreak: Int,
        realAgainStreak: Int,
        matureIntervalDays: Int,
    ) {
        constructor(
            state: String?,
            rung: RecordsBase.LadderRung?,
            phase: RecordsBase.SchedulerPhase?,
            realPassStreak: Int,
            realAgainStreak: Int,
        ) : this(state, rung, phase, realPassStreak, realAgainStreak, 0)

        private val state = state.orEmpty()
        private val rung = rung ?: RecordsBase.LadderRung.KANJI_MEANING
        private val phase = phase ?: RecordsBase.SchedulerPhase.NEW_LEARNING
        private val realPassStreak = maxOf(0, realPassStreak)
        private val realAgainStreak = maxOf(0, realAgainStreak)
        private val matureIntervalDays = maxOf(0, matureIntervalDays)

        fun state(): String = state

        fun rung(): RecordsBase.LadderRung = rung

        fun phase(): RecordsBase.SchedulerPhase = phase

        fun realPassStreak(): Int = realPassStreak

        fun realAgainStreak(): Int = realAgainStreak

        fun matureIntervalDays(): Int = matureIntervalDays

        override fun equals(other: Any?): Boolean {
            return other is ItemEvidence &&
                state == other.state &&
                rung == other.rung &&
                phase == other.phase &&
                realPassStreak == other.realPassStreak &&
                realAgainStreak == other.realAgainStreak &&
                matureIntervalDays == other.matureIntervalDays
        }

        override fun hashCode(): Int {
            var result = state.hashCode()
            result = 31 * result + rung.hashCode()
            result = 31 * result + phase.hashCode()
            result = 31 * result + realPassStreak
            result = 31 * result + realAgainStreak
            result = 31 * result + matureIntervalDays
            return result
        }

        override fun toString(): String {
            return "ItemEvidence[state=$state, rung=$rung, phase=$phase, realPassStreak=$realPassStreak, realAgainStreak=$realAgainStreak, matureIntervalDays=$matureIntervalDays]"
        }
    }

    class Metric(
        rungCounts: Map<out RecordsBase.LadderRung?, Int?>?,
        totalActiveItems: Int,
        ladderPromotionIntervalDays: Int,
        ladderDemotionFailStreak: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
        demotionReadyCount: Int,
    ) {
        private val rungCounts: Map<RecordsBase.LadderRung, Int>
        private val totalActiveItems = maxOf(0, totalActiveItems)
        private val ladderPromotionIntervalDays = maxOf(1, ladderPromotionIntervalDays)
        private val ladderDemotionFailStreak = maxOf(1, ladderDemotionFailStreak)
        private val promotionReadyCount = maxOf(0, promotionReadyCount)
        private val demotionRiskCount = maxOf(0, demotionRiskCount)
        private val demotionReadyCount = maxOf(0, demotionReadyCount)

        init {
            val normalized = emptyRungDistribution()
            if (rungCounts != null) {
                for ((rung, count) in rungCounts) {
                    if (rung != null) {
                        normalized[rung] = maxOf(0, count ?: 0)
                    }
                }
            }
            this.rungCounts = Collections.unmodifiableMap(normalized)
        }

        fun rungCounts(): Map<RecordsBase.LadderRung, Int> = rungCounts

        fun totalActiveItems(): Int = totalActiveItems

        fun ladderPromotionIntervalDays(): Int = ladderPromotionIntervalDays

        fun ladderDemotionFailStreak(): Int = ladderDemotionFailStreak

        fun promotionReadyCount(): Int = promotionReadyCount

        fun demotionRiskCount(): Int = demotionRiskCount

        fun demotionReadyCount(): Int = demotionReadyCount

        fun countFor(rung: RecordsBase.LadderRung?): Int {
            return rungCounts[rung] ?: 0
        }

        override fun equals(other: Any?): Boolean {
            return other is Metric &&
                rungCounts == other.rungCounts &&
                totalActiveItems == other.totalActiveItems &&
                ladderPromotionIntervalDays == other.ladderPromotionIntervalDays &&
                ladderDemotionFailStreak == other.ladderDemotionFailStreak &&
                promotionReadyCount == other.promotionReadyCount &&
                demotionRiskCount == other.demotionRiskCount &&
                demotionReadyCount == other.demotionReadyCount
        }

        override fun hashCode(): Int {
            var result = rungCounts.hashCode()
            result = 31 * result + totalActiveItems
            result = 31 * result + ladderPromotionIntervalDays
            result = 31 * result + ladderDemotionFailStreak
            result = 31 * result + promotionReadyCount
            result = 31 * result + demotionRiskCount
            result = 31 * result + demotionReadyCount
            return result
        }

        override fun toString(): String {
            return "Metric[rungCounts=$rungCounts, totalActiveItems=$totalActiveItems, ladderPromotionIntervalDays=$ladderPromotionIntervalDays, ladderDemotionFailStreak=$ladderDemotionFailStreak, promotionReadyCount=$promotionReadyCount, demotionRiskCount=$demotionRiskCount, demotionReadyCount=$demotionReadyCount]"
        }

        companion object {
            @JvmStatic
            fun empty(): Metric {
                val defaults = RecordsSyncModels.Settings.kikuDefaults()
                return Metric(
                    emptyRungDistribution(),
                    0,
                    defaults.ladderPromotionIntervalDays,
                    defaults.ladderDemotionFailStreak,
                    0,
                    0,
                    0,
                )
            }
        }
    }

    private class Accumulator {
        private val distribution = emptyRungDistribution()
        private var total = 0
        private var promotionReady = 0
        private var demotionRisk = 0
        private var demotionReady = 0

        fun addItem(item: ItemEvidence?, promotionDays: Int, failStreak: Int) {
            if (item == null || StudyLadderRules.STATE_RETIRED == item.state()) {
                return
            }
            distribution[item.rung()] = distribution[item.rung()]!! + 1
            total++
            if (item.phase() == RecordsBase.SchedulerPhase.REVIEW) {
                recordReviewEvidence(item, promotionDays, failStreak)
            }
        }

        private fun recordReviewEvidence(item: ItemEvidence, promotionDays: Int, failStreak: Int) {
            if (item.matureIntervalDays() > promotionDays) {
                promotionReady++
            }
            if (item.realAgainStreak() > 0) {
                demotionRisk++
            }
            if (item.realAgainStreak() >= failStreak) {
                demotionReady++
            }
        }

        fun metric(promotionDays: Int, failStreak: Int): Metric {
            return Metric(distribution, total, promotionDays, failStreak, promotionReady, demotionRisk, demotionReady)
        }
    }
}
