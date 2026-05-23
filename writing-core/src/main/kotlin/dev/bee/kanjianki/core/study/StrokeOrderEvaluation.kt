package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class StrokeOrderEvaluation(
    expectedCount: Int,
    attemptedCount: Int,
    orderedMatchCount: Int,
    missingStrokeIds: List<String>?,
    extraStrokeIds: List<String>?,
    duplicateStrokeIds: List<String>?,
    outOfPositionStrokeIds: List<String>?,
    score: Double,
) {
    private val expectedCount: Int = maxOf(0, expectedCount)
    private val attemptedCount: Int = maxOf(0, attemptedCount)
    private val orderedMatchCount: Int = maxOf(0, orderedMatchCount)
    private val missingStrokeIds: List<String> = copy(missingStrokeIds)
    private val extraStrokeIds: List<String> = copy(extraStrokeIds)
    private val duplicateStrokeIds: List<String> = copy(duplicateStrokeIds)
    private val outOfPositionStrokeIds: List<String> = copy(outOfPositionStrokeIds)
    private val score: Double = clamp(score)

    fun expectedCount(): Int = expectedCount

    fun attemptedCount(): Int = attemptedCount

    fun orderedMatchCount(): Int = orderedMatchCount

    fun missingStrokeIds(): List<String> = missingStrokeIds

    fun extraStrokeIds(): List<String> = extraStrokeIds

    fun duplicateStrokeIds(): List<String> = duplicateStrokeIds

    fun outOfPositionStrokeIds(): List<String> = outOfPositionStrokeIds

    fun score(): Double = score

    fun complete(): Boolean {
        return expectedCount > 0 &&
            attemptedCount == expectedCount &&
            missingStrokeIds.isEmpty() &&
            extraStrokeIds.isEmpty() &&
            duplicateStrokeIds.isEmpty()
    }

    fun exactOrder(): Boolean = complete() && orderedMatchCount == expectedCount && outOfPositionStrokeIds.isEmpty()

    fun passed(): Boolean = exactOrder()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is StrokeOrderEvaluation) {
            return false
        }
        return expectedCount == other.expectedCount &&
            attemptedCount == other.attemptedCount &&
            orderedMatchCount == other.orderedMatchCount &&
            missingStrokeIds == other.missingStrokeIds &&
            extraStrokeIds == other.extraStrokeIds &&
            duplicateStrokeIds == other.duplicateStrokeIds &&
            outOfPositionStrokeIds == other.outOfPositionStrokeIds &&
            score == other.score
    }

    override fun hashCode(): Int {
        var result = expectedCount
        result = 31 * result + attemptedCount
        result = 31 * result + orderedMatchCount
        result = 31 * result + missingStrokeIds.hashCode()
        result = 31 * result + extraStrokeIds.hashCode()
        result = 31 * result + duplicateStrokeIds.hashCode()
        result = 31 * result + outOfPositionStrokeIds.hashCode()
        result = 31 * result + score.hashCode()
        return result
    }

    override fun toString(): String {
        return "StrokeOrderEvaluation[expectedCount=$expectedCount, attemptedCount=$attemptedCount, " +
            "orderedMatchCount=$orderedMatchCount, missingStrokeIds=$missingStrokeIds, " +
            "extraStrokeIds=$extraStrokeIds, duplicateStrokeIds=$duplicateStrokeIds, " +
            "outOfPositionStrokeIds=$outOfPositionStrokeIds, score=$score]"
    }

    companion object {
        private fun copy(values: List<String>?): List<String> {
            return Collections.unmodifiableList(if (values == null) ArrayList() else ArrayList(values))
        }

        private fun clamp(value: Double): Double = maxOf(0.0, minOf(1.0, value))
    }
}
