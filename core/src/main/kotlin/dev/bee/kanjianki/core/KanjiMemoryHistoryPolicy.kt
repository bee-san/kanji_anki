package dev.bee.kanjianki.core

import java.util.Calendar

object KanjiMemoryHistoryPolicy {
    @JvmStatic
    fun build(
        rows: List<MemoryHistoryRow>?,
    ): MemoryHistoryResult {
        val usable = rows.orEmpty().filter { it.stability.isFinite() && it.stability > 0.0 }
        if (usable.size < 2) return MemoryHistoryResult.EMPTY
        val points = usable.map { row ->
            MemoryHistoryPoint(
                dayLabel(row.reviewedAtMillis),
                row.stability.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat(),
            )
        }
        val failCount = usable.count { it.rating == "again" || it.rating == "hard" }
        return MemoryHistoryResult(
            points = points,
            caption = "${usable.size} reviews, $failCount misses",
        )
    }

    private fun dayLabel(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "$month/$day"
    }

    class MemoryHistoryRow(
        @JvmField val reviewedAtMillis: Long,
        @JvmField val rating: String,
        @JvmField val stability: Double,
    )

    class MemoryHistoryPoint(
        @JvmField val dayLabel: String,
        @JvmField val stability: Float,
    )

    class MemoryHistoryResult(
        @JvmField val points: List<MemoryHistoryPoint>,
        @JvmField val caption: String,
    ) {
        fun isEmpty(): Boolean = points.isEmpty()

        companion object {
            @JvmField
            val EMPTY = MemoryHistoryResult(emptyList(), "")
        }
    }
}
