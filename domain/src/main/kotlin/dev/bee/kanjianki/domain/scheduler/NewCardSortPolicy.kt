package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow

class NewCardSortPolicy {
    fun compare(
        left: StudyDashboardRow?,
        right: StudyDashboardRow?,
        mode: NewCardSortMode = NewCardSortMode.default,
    ): Int {
        if (mode == NewCardSortMode.FREQUENCY) {
            return compareRank(left, right)
        }
        val primary = when (mode) {
            NewCardSortMode.FSRS_DIFFICULTY ->
                compareOptionalDescending(maxDifficulty(left), maxDifficulty(right))
            NewCardSortMode.RETRIEVABILITY_RISK ->
                compareOptionalAscending(minRetrievability(left), minRetrievability(right))
            NewCardSortMode.KANI_WEAKNESS -> compareWeakness(left, right)
            NewCardSortMode.FREQUENCY -> compareRank(left, right)
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
        left: StudyDashboardRow?,
        right: StudyDashboardRow?,
    ): Int {
        val weakness = rowWeakness(right).compareTo(rowWeakness(left))
        if (weakness != 0) {
            return weakness
        }
        return rowSuspendedExamples(right).compareTo(rowSuspendedExamples(left))
    }

    private fun compareRank(
        left: StudyDashboardRow?,
        right: StudyDashboardRow?,
    ): Int = rankSortValue(left).compareTo(rankSortValue(right))

    private fun compareOptionalDescending(
        left: Double?,
        right: Double?,
    ): Int = when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> right.compareTo(left)
    }

    private fun compareOptionalAscending(
        left: Double?,
        right: Double?,
    ): Int = when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        else -> left.compareTo(right)
    }

    private fun rankSortValue(row: StudyDashboardRow?): Int = row?.jitenRank ?: Int.MAX_VALUE

    private fun rowWeakness(row: StudyDashboardRow?): Int = row?.weaknessScore ?: 0

    private fun rowSuspendedExamples(row: StudyDashboardRow?): Int =
        row?.suspendedExampleCount ?: 0

    private fun rowKanji(row: StudyDashboardRow?): String = row?.kanji.orEmpty()

    private fun maxDifficulty(row: StudyDashboardRow?): Double? =
        row?.examples
            ?.mapNotNull { it.fsrsDifficulty?.takeIf { value -> value.isFiniteValue() } }
            ?.maxOrNull()

    private fun minRetrievability(row: StudyDashboardRow?): Double? =
        row?.examples
            ?.mapNotNull { normalizedRetrievability(it.fsrsRetrievability) }
            ?.minOrNull()

    private fun normalizedRetrievability(value: Double?): Double? = when {
        value == null || !value.isFiniteValue() || value < 0.0 -> null
        value > 1.0 && value <= 100.0 -> value / 100.0
        value > 1.0 -> null
        else -> value
    }

    private fun Double.isFiniteValue(): Boolean = !isNaN() && !isInfinite()
}
