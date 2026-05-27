package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

class NewCardSortPlanner {
    fun sortedRows(
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings?,
    ): List<RecordsImportModels.DashboardRow> = sortedRowsForSettings(rows, settings)

    fun compareRows(
        left: RecordsImportModels.DashboardRow?,
        right: RecordsImportModels.DashboardRow?,
        settings: RecordsSyncModels.Settings?,
    ): Int = compareRowsForSettings(left, right, settings)

    companion object {
        private const val BALANCED_WEIGHT_WEAKNESS = 0.30
        private const val BALANCED_WEIGHT_RETRIEVABILITY_RISK = 0.25
        private const val BALANCED_WEIGHT_DIFFICULTY = 0.20
        private const val BALANCED_WEIGHT_SUSPENDED_PRESSURE = 0.15
        private const val BALANCED_WEIGHT_INVERSE_FREQUENCY = 0.10

        @JvmStatic
        fun sortedRowsForSettings(
            rows: List<RecordsImportModels.DashboardRow>,
            settings: RecordsSyncModels.Settings?,
        ): List<RecordsImportModels.DashboardRow> {
            val mode = settings?.newCardSortMode ?: RecordsBase.DEFAULT_NEW_CARD_SORT_MODE
            if (RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY == mode) {
                return sortedBalancedRows(rows)
            }
            val out = ArrayList(rows)
            out.sortWith { left, right -> compareRowsForSettings(left, right, settings) }
            return out
        }

        @JvmStatic
        fun compareRowsForSettings(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
            settings: RecordsSyncModels.Settings?,
        ): Int {
            val mode = settings?.newCardSortMode ?: RecordsBase.DEFAULT_NEW_CARD_SORT_MODE
            if (RecordsBase.NEW_CARD_SORT_FREQUENCY == mode) {
                return compareRank(left, right)
            }
            if (RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY == mode) {
                return compareBalancedRows(left, right)
            }
            val primary = when (mode) {
                RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> compareOptionalDescending(maxDifficulty(left), maxDifficulty(right))
                RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> compareOptionalAscending(minRetrievability(left), minRetrievability(right))
                RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> compareWeakness(left, right)
                else -> compareRank(left, right)
            }
            if (primary != 0) {
                return primary
            }
            return compareRankThenKanji(left, right)
        }

        private fun sortedBalancedRows(rows: List<RecordsImportModels.DashboardRow>): List<RecordsImportModels.DashboardRow> {
            val scores = balancedScores(rows)
            val out = ArrayList(rows)
            out.sortWith { left, right -> compareBalancedRows(left, right, scores) }
            return out
        }

        private fun compareBalancedRows(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
        ): Int {
            val candidates = listOfNotNull(left, right)
            return compareBalancedRows(left, right, balancedScores(candidates))
        }

        private fun compareBalancedRows(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
            scores: Map<RecordsImportModels.DashboardRow, Double>,
        ): Int {
            val score = (scores[right] ?: 0.0).compareTo(scores[left] ?: 0.0)
            if (score != 0) {
                return score
            }
            return compareRankThenKanji(left, right)
        }

        private fun balancedScores(rows: List<RecordsImportModels.DashboardRow>): Map<RecordsImportModels.DashboardRow, Double> {
            val maxWeakness = rows.maxOfOrNull { rowWeakness(it) } ?: 0
            val maxDifficulty = rows.mapNotNull { maxDifficulty(it) }.maxOrNull() ?: 0.0
            val maxSuspended = rows.maxOfOrNull { rowSuspendedExamples(it) } ?: 0
            val finiteRanks = rows.mapNotNull { it.jitenRank?.takeIf { rank -> rank > 0 } }
            val minRank = finiteRanks.minOrNull()
            val maxRank = finiteRanks.maxOrNull()

            return rows.associateWith { row ->
                BALANCED_WEIGHT_WEAKNESS * normalizedPositive(rowWeakness(row), maxWeakness) +
                    BALANCED_WEIGHT_RETRIEVABILITY_RISK * retrievabilityRisk(row) +
                    BALANCED_WEIGHT_DIFFICULTY * normalizedPositive(maxDifficulty(row), maxDifficulty) +
                    BALANCED_WEIGHT_SUSPENDED_PRESSURE * normalizedPositive(rowSuspendedExamples(row), maxSuspended) +
                    BALANCED_WEIGHT_INVERSE_FREQUENCY * inverseRankScore(row.jitenRank, minRank, maxRank)
            }
        }

        private fun compareWeakness(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
        ): Int {
            val weakness = rowWeakness(right).compareTo(rowWeakness(left))
            if (weakness != 0) {
                return weakness
            }
            return rowSuspendedExamples(right).compareTo(rowSuspendedExamples(left))
        }

        private fun compareRankThenKanji(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
        ): Int {
            val rank = compareRank(left, right)
            if (rank != 0) {
                return rank
            }
            return rowKanji(left).compareTo(rowKanji(right))
        }

        private fun compareRank(
            left: RecordsImportModels.DashboardRow?,
            right: RecordsImportModels.DashboardRow?,
        ): Int {
            return rankSortValue(left).compareTo(rankSortValue(right))
        }

        private fun compareOptionalDescending(left: Double?, right: Double?): Int {
            if (left == null && right == null) {
                return 0
            }
            if (left == null) {
                return 1
            }
            if (right == null) {
                return -1
            }
            return right.compareTo(left)
        }

        private fun compareOptionalAscending(left: Double?, right: Double?): Int {
            if (left == null && right == null) {
                return 0
            }
            if (left == null) {
                return 1
            }
            if (right == null) {
                return -1
            }
            return left.compareTo(right)
        }

        private fun rankSortValue(row: RecordsImportModels.DashboardRow?): Int {
            return row?.jitenRank ?: Int.MAX_VALUE
        }

        private fun rowWeakness(row: RecordsImportModels.DashboardRow?): Int {
            return max(row?.weaknessScore ?: 0, 0)
        }

        private fun rowSuspendedExamples(row: RecordsImportModels.DashboardRow?): Int {
            return max(row?.suspendedExampleCount ?: 0, 0)
        }

        private fun rowKanji(row: RecordsImportModels.DashboardRow?): String {
            return row?.kanji ?: ""
        }

        private fun normalizedPositive(value: Int, maxValue: Int): Double {
            if (value <= 0 || maxValue <= 0) {
                return 0.0
            }
            return value.toDouble() / maxValue.toDouble()
        }

        private fun normalizedPositive(value: Double?, maxValue: Double): Double {
            if (value == null || value <= 0.0 || !value.isFinite() || maxValue <= 0.0 || !maxValue.isFinite()) {
                return 0.0
            }
            return value / maxValue
        }

        private fun retrievabilityRisk(row: RecordsImportModels.DashboardRow?): Double {
            val retrievability = minRetrievability(row) ?: return 0.0
            return 1.0 - retrievability.coerceIn(0.0, 1.0)
        }

        private fun inverseRankScore(rank: Int?, minRank: Int?, maxRank: Int?): Double {
            if (rank == null || rank <= 0 || minRank == null || maxRank == null) {
                return 0.0
            }
            if (minRank == maxRank) {
                return 0.0
            }
            return (maxRank - rank).toDouble() / (maxRank - minRank).toDouble()
        }

        private fun maxDifficulty(row: RecordsImportModels.DashboardRow?): Double? {
            if (row == null) {
                return null
            }
            var best: Double? = null
            for (example in row.examples) {
                val difficulty = example.fsrsDifficulty
                if (difficulty != null && difficulty.isFinite()) {
                    best = if (best == null) difficulty else max(best, difficulty)
                }
            }
            return best
        }

        private fun minRetrievability(row: RecordsImportModels.DashboardRow?): Double? {
            if (row == null) {
                return null
            }
            var lowest: Double? = null
            for (example in row.examples) {
                val normalized = normalizedRetrievability(example.fsrsRetrievability)
                if (normalized != null) {
                    lowest = if (lowest == null) normalized else min(lowest, normalized)
                }
            }
            return lowest
        }

        private fun normalizedRetrievability(value: Double?): Double? {
            if (value == null || !value.isFinite() || value < 0.0) {
                return null
            }
            if (value > 1.0 && value <= 100.0) {
                return value / 100.0
            }
            return if (value > 1.0) null else value
        }

    }
}
