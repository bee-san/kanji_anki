package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

object SettingsInputRules {
    const val DEFAULT_STUDY_AHEAD_MINUTES: Int = 0
    const val MAX_STUDY_AHEAD_MINUTES: Int = 1440
    private const val MAX_RANK_SLIDER_PROGRESS = FrequencyRetentionRanges.MAX_RANK - 1

    @JvmStatic
    fun validImportThresholds(difficulty: Double, lapseThreshold: Int, minCards: Int): Boolean {
        val difficultyValid = difficulty >= 1.0 && difficulty <= 10.0
        val lapsesValid = lapseThreshold >= 1 && lapseThreshold <= 100
        val minCardsValid = minCards >= 1 && minCards <= 1000
        return difficultyValid && lapsesValid && minCardsValid
    }

    @JvmStatic
    fun hasSelectedImportSource(
        activeCards: Boolean,
        suspendedCards: Boolean,
        taggedCards: Boolean,
        weakCards: Boolean,
        browserQueryCards: Boolean,
        parsedTags: List<String>?,
        queryText: String?,
    ): Boolean {
        if (activeCards || suspendedCards || weakCards) {
            return true
        }
        if (taggedCards && parsedTags!!.isNotEmpty()) {
            return true
        }
        return browserQueryCards && queryText!!.isNotEmpty()
    }

    @JvmStatic
    fun rankSliderProgress(rank: Int): Int {
        return max(0, min(MAX_RANK_SLIDER_PROGRESS, rank - 1))
    }

    @JvmStatic
    fun rankFromSliderProgress(progress: Int): Int {
        return max(FrequencyRetentionRanges.MIN_RANK, min(FrequencyRetentionRanges.MAX_RANK, progress + 1))
    }

    @JvmStatic
    fun validRank(rank: Int): Boolean {
        return rank >= FrequencyRetentionRanges.MIN_RANK && rank <= FrequencyRetentionRanges.MAX_RANK
    }

    @JvmStatic
    fun normalizedRankRange(minRank: Int, maxRank: Int): RankRange {
        var normalizedMin = clampRank(minRank)
        var normalizedMax = clampRank(maxRank)
        if (normalizedMin > normalizedMax) {
            val swap = normalizedMin
            normalizedMin = normalizedMax
            normalizedMax = swap
        }
        return RankRange(normalizedMin, normalizedMax)
    }

    @JvmStatic
    fun retentionPercent(retention: Double): Int {
        return max(80, min(97, Math.round(retention * 100.0).toInt()))
    }

    @JvmStatic
    fun normalizeStudyAheadMinutes(minutes: Int): Int {
        if (minutes <= 0) {
            return 0
        }
        return min(minutes, MAX_STUDY_AHEAD_MINUTES)
    }

    private fun clampRank(rank: Int): Int {
        return max(FrequencyRetentionRanges.MIN_RANK, min(FrequencyRetentionRanges.MAX_RANK, rank))
    }

    data class RankRange(val minRank: Int, val maxRank: Int) {
        fun minRank(): Int = minRank

        fun maxRank(): Int = maxRank
    }
}
