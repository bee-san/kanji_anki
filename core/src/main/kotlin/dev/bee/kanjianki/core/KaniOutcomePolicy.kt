package dev.bee.kanjianki.core

import java.util.Collections

object KaniOutcomePolicy {
    @JvmStatic
    fun summarize(
        outcomeEvidence: List<OutcomeEvidence?>?,
        ladderHealth: LadderHealthPolicy.Metric?,
    ): OutcomeStats {
        val accumulator = OutcomeAccumulator()
        for (evidence in outcomeEvidence.orEmpty()) {
            if (evidence != null) {
                accumulator.add(evidence.kanji(), evidence.before(), evidence.after())
            }
        }
        accumulator.sort()
        val improvedCount = accumulator.improvements.size
        return OutcomeStats(
            WeakKanjiImprovedMetric(
                improvedCount,
                if (improvedCount == 0) 0.0 else accumulator.beforeWeaknessSum / improvedCount,
                if (improvedCount == 0) 0.0 else accumulator.afterWeaknessSum / improvedCount,
                topThreeImprovements(accumulator.improvements),
            ),
            MatureSupportGainedMetric(
                accumulator.supportGains.size,
                accumulator.matureSupportGainSum,
                accumulator.firstSupportCount,
                topThreeSupportGains(accumulator.supportGains),
            ),
            ladderHealth,
        )
    }

    private fun topThreeImprovements(improvements: List<KanjiImprovement>): List<KanjiImprovement> {
        return ArrayList(improvements.subList(0, minOf(3, improvements.size)))
    }

    private fun topThreeSupportGains(supportGains: List<KanjiSupportGain>): List<KanjiSupportGain> {
        return ArrayList(supportGains.subList(0, minOf(3, supportGains.size)))
    }

    class OutcomeStats(
        weakKanjiImproved: WeakKanjiImprovedMetric?,
        matureSupportGained: MatureSupportGainedMetric?,
        ladderHealth: LadderHealthPolicy.Metric?,
    ) {
        private val weakKanjiImproved = weakKanjiImproved ?: WeakKanjiImprovedMetric.empty()
        private val matureSupportGained = matureSupportGained ?: MatureSupportGainedMetric.empty()
        private val ladderHealth = ladderHealth ?: LadderHealthPolicy.Metric.empty()

        fun weakKanjiImproved(): WeakKanjiImprovedMetric = weakKanjiImproved

        fun matureSupportGained(): MatureSupportGainedMetric = matureSupportGained

        fun ladderHealth(): LadderHealthPolicy.Metric = ladderHealth

        override fun equals(other: Any?): Boolean {
            return other is OutcomeStats &&
                weakKanjiImproved == other.weakKanjiImproved &&
                matureSupportGained == other.matureSupportGained &&
                ladderHealth == other.ladderHealth
        }

        override fun hashCode(): Int {
            var result = weakKanjiImproved.hashCode()
            result = 31 * result + matureSupportGained.hashCode()
            result = 31 * result + ladderHealth.hashCode()
            return result
        }

        override fun toString(): String {
            return "OutcomeStats[weakKanjiImproved=$weakKanjiImproved, matureSupportGained=$matureSupportGained, ladderHealth=$ladderHealth]"
        }

        companion object {
            @JvmStatic
            fun empty(): OutcomeStats {
                return OutcomeStats(
                    WeakKanjiImprovedMetric.empty(),
                    MatureSupportGainedMetric.empty(),
                    LadderHealthPolicy.Metric.empty(),
                )
            }
        }
    }

    class WeakKanjiImprovedMetric(
        improvedCount: Int,
        averageBeforeWeakness: Double,
        averageAfterWeakness: Double,
        examples: List<KanjiImprovement>?,
    ) {
        private val improvedCount = maxOf(0, improvedCount)
        private val averageBeforeWeakness = maxOf(0.0, averageBeforeWeakness)
        private val averageAfterWeakness = maxOf(0.0, averageAfterWeakness)
        private val examples = Collections.unmodifiableList(ArrayList(examples.orEmpty()))

        fun improvedCount(): Int = improvedCount

        fun averageBeforeWeakness(): Double = averageBeforeWeakness

        fun averageAfterWeakness(): Double = averageAfterWeakness

        fun examples(): List<KanjiImprovement> = examples

        override fun equals(other: Any?): Boolean {
            return other is WeakKanjiImprovedMetric &&
                improvedCount == other.improvedCount &&
                averageBeforeWeakness == other.averageBeforeWeakness &&
                averageAfterWeakness == other.averageAfterWeakness &&
                examples == other.examples
        }

        override fun hashCode(): Int {
            var result = improvedCount
            result = 31 * result + averageBeforeWeakness.hashCode()
            result = 31 * result + averageAfterWeakness.hashCode()
            result = 31 * result + examples.hashCode()
            return result
        }

        override fun toString(): String {
            return "WeakKanjiImprovedMetric[improvedCount=$improvedCount, averageBeforeWeakness=$averageBeforeWeakness, averageAfterWeakness=$averageAfterWeakness, examples=$examples]"
        }

        companion object {
            @JvmStatic
            fun empty(): WeakKanjiImprovedMetric {
                return WeakKanjiImprovedMetric(0, 0.0, 0.0, emptyList())
            }
        }
    }

    class MatureSupportGainedMetric(
        gainedSupportCount: Int,
        matureSupportGained: Int,
        firstSupportCount: Int,
        examples: List<KanjiSupportGain>?,
    ) {
        constructor(
            gainedSupportCount: Int,
            firstSupportCount: Int,
            examples: List<KanjiSupportGain>?,
        ) : this(gainedSupportCount, gainedSupportCount, firstSupportCount, examples)

        private val gainedSupportCount = maxOf(0, gainedSupportCount)
        private val matureSupportGained = maxOf(0, matureSupportGained)
        private val firstSupportCount = maxOf(0, firstSupportCount)
        private val examples = Collections.unmodifiableList(ArrayList(examples.orEmpty()))

        fun gainedSupportCount(): Int = gainedSupportCount

        fun matureSupportGained(): Int = matureSupportGained

        fun firstSupportCount(): Int = firstSupportCount

        fun examples(): List<KanjiSupportGain> = examples

        override fun equals(other: Any?): Boolean {
            return other is MatureSupportGainedMetric &&
                gainedSupportCount == other.gainedSupportCount &&
                matureSupportGained == other.matureSupportGained &&
                firstSupportCount == other.firstSupportCount &&
                examples == other.examples
        }

        override fun hashCode(): Int {
            var result = gainedSupportCount
            result = 31 * result + matureSupportGained
            result = 31 * result + firstSupportCount
            result = 31 * result + examples.hashCode()
            return result
        }

        override fun toString(): String {
            return "MatureSupportGainedMetric[gainedSupportCount=$gainedSupportCount, matureSupportGained=$matureSupportGained, firstSupportCount=$firstSupportCount, examples=$examples]"
        }

        companion object {
            @JvmStatic
            fun empty(): MatureSupportGainedMetric {
                return MatureSupportGainedMetric(0, 0, 0, emptyList())
            }
        }
    }

    class KanjiImprovement(kanji: String?, beforeWeakness: Double, afterWeakness: Double) {
        private val kanji = kanji.orEmpty()
        private val beforeWeakness = maxOf(0.0, beforeWeakness)
        private val afterWeakness = maxOf(0.0, afterWeakness)

        fun kanji(): String = kanji

        fun beforeWeakness(): Double = beforeWeakness

        fun afterWeakness(): Double = afterWeakness

        override fun equals(other: Any?): Boolean {
            return other is KanjiImprovement &&
                kanji == other.kanji &&
                beforeWeakness == other.beforeWeakness &&
                afterWeakness == other.afterWeakness
        }

        override fun hashCode(): Int {
            var result = kanji.hashCode()
            result = 31 * result + beforeWeakness.hashCode()
            result = 31 * result + afterWeakness.hashCode()
            return result
        }

        override fun toString(): String {
            return "KanjiImprovement[kanji=$kanji, beforeWeakness=$beforeWeakness, afterWeakness=$afterWeakness]"
        }
    }

    class KanjiSupportGain(kanji: String?, beforeMatureSupport: Int, afterMatureSupport: Int) {
        private val kanji = kanji.orEmpty()
        private val beforeMatureSupport = maxOf(0, beforeMatureSupport)
        private val afterMatureSupport = maxOf(0, afterMatureSupport)

        fun kanji(): String = kanji

        fun beforeMatureSupport(): Int = beforeMatureSupport

        fun afterMatureSupport(): Int = afterMatureSupport

        override fun equals(other: Any?): Boolean {
            return other is KanjiSupportGain &&
                kanji == other.kanji &&
                beforeMatureSupport == other.beforeMatureSupport &&
                afterMatureSupport == other.afterMatureSupport
        }

        override fun hashCode(): Int {
            var result = kanji.hashCode()
            result = 31 * result + beforeMatureSupport
            result = 31 * result + afterMatureSupport
            return result
        }

        override fun toString(): String {
            return "KanjiSupportGain[kanji=$kanji, beforeMatureSupport=$beforeMatureSupport, afterMatureSupport=$afterMatureSupport]"
        }
    }

    class OutcomeSnapshot(weaknessScore: Int, matureSupportCount: Int) {
        private val weaknessScore = maxOf(0, weaknessScore)
        private val matureSupportCount = maxOf(0, matureSupportCount)

        fun weaknessScore(): Int = weaknessScore

        fun matureSupportCount(): Int = matureSupportCount

        override fun equals(other: Any?): Boolean {
            return other is OutcomeSnapshot &&
                weaknessScore == other.weaknessScore &&
                matureSupportCount == other.matureSupportCount
        }

        override fun hashCode(): Int {
            var result = weaknessScore
            result = 31 * result + matureSupportCount
            return result
        }

        override fun toString(): String {
            return "OutcomeSnapshot[weaknessScore=$weaknessScore, matureSupportCount=$matureSupportCount]"
        }
    }

    class OutcomeEvidence(kanji: String?, before: OutcomeSnapshot?, after: OutcomeSnapshot?) {
        private val kanji = kanji.orEmpty()
        private val before = before
        private val after = after

        fun kanji(): String = kanji

        fun before(): OutcomeSnapshot? = before

        fun after(): OutcomeSnapshot? = after

        override fun equals(other: Any?): Boolean {
            return other is OutcomeEvidence &&
                kanji == other.kanji &&
                before == other.before &&
                after == other.after
        }

        override fun hashCode(): Int {
            var result = kanji.hashCode()
            result = 31 * result + (before?.hashCode() ?: 0)
            result = 31 * result + (after?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "OutcomeEvidence[kanji=$kanji, before=$before, after=$after]"
        }
    }

    private class OutcomeAccumulator {
        val improvements: MutableList<KanjiImprovement> = ArrayList()
        val supportGains: MutableList<KanjiSupportGain> = ArrayList()
        var beforeWeaknessSum = 0.0
        var afterWeaknessSum = 0.0
        var matureSupportGainSum = 0
        var firstSupportCount = 0

        fun add(kanji: String, before: OutcomeSnapshot?, after: OutcomeSnapshot?) {
            if (before == null || after == null) {
                return
            }
            addImprovement(kanji, before, after)
            addSupportGain(kanji, before, after)
        }

        fun sort() {
            improvements.sortWith { left, right ->
                val dropCompare = java.lang.Double.compare(
                    right.beforeWeakness() - right.afterWeakness(),
                    left.beforeWeakness() - left.afterWeakness(),
                )
                if (dropCompare == 0) left.kanji().compareTo(right.kanji()) else dropCompare
            }
            supportGains.sortWith { left, right ->
                val gainCompare = Integer.compare(
                    right.afterMatureSupport() - right.beforeMatureSupport(),
                    left.afterMatureSupport() - left.beforeMatureSupport(),
                )
                if (gainCompare == 0) left.kanji().compareTo(right.kanji()) else gainCompare
            }
        }

        private fun addImprovement(kanji: String, before: OutcomeSnapshot, after: OutcomeSnapshot) {
            val weaknessDrop = before.weaknessScore() - after.weaknessScore()
            if (before.weaknessScore() <= 0 || weaknessDrop < 5) {
                return
            }
            val beforeWeakness = normalizedWeakness(before.weaknessScore())
            val afterWeakness = normalizedWeakness(after.weaknessScore())
            improvements.add(KanjiImprovement(kanji, beforeWeakness, afterWeakness))
            beforeWeaknessSum += beforeWeakness
            afterWeaknessSum += afterWeakness
        }

        private fun addSupportGain(kanji: String, before: OutcomeSnapshot, after: OutcomeSnapshot) {
            val supportGain = after.matureSupportCount() - before.matureSupportCount()
            if (supportGain <= 0) {
                return
            }
            supportGains.add(KanjiSupportGain(kanji, before.matureSupportCount(), after.matureSupportCount()))
            matureSupportGainSum = saturatingAddNonNegative(matureSupportGainSum, supportGain)
            if (before.matureSupportCount() == 0) {
                firstSupportCount = saturatingAddNonNegative(firstSupportCount, 1)
            }
        }

        private fun normalizedWeakness(weaknessScore: Int): Double {
            return maxOf(0, weaknessScore) / 100.0
        }
    }
}
