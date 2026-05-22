package dev.bee.kanjianki.core

internal class KaniFsrsReviewResult(
    @JvmField val stability: Double,
    @JvmField val difficulty: Double,
    @JvmField val intervalMillis: Long,
) {
    fun intervalDays(): Int {
        val safeInterval = maxOf(1L, intervalMillis)
        val days = ((safeInterval - 1L) / DAY_MILLIS) + 1L
        return if (days > Int.MAX_VALUE) Int.MAX_VALUE else days.toInt()
    }

    companion object {
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
