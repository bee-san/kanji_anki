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
        @JvmStatic
        fun sortedRowsForSettings(
            rows: List<RecordsImportModels.DashboardRow>,
            settings: RecordsSyncModels.Settings?,
        ): List<RecordsImportModels.DashboardRow> {
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
            val primary = when (mode) {
                RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> compareOptionalDescending(maxDifficulty(left), maxDifficulty(right))
                RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> compareOptionalAscending(minRetrievability(left), minRetrievability(right))
                RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> compareWeakness(left, right)
                else -> compareRank(left, right)
            }
            if (primary != 0) {
                return primary
            }
            val rank = compareRank(left, right)
            if (rank != 0) {
                return rank
            }
            return rowKanji(left).compareTo(rowKanji(right))
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
            return row?.weaknessScore ?: 0
        }

        private fun rowSuspendedExamples(row: RecordsImportModels.DashboardRow?): Int {
            return row?.suspendedExampleCount ?: 0
        }

        private fun rowKanji(row: RecordsImportModels.DashboardRow?): String {
            return row?.kanji ?: ""
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
