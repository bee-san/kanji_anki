package dev.bee.kanjianki.core

internal class KaniFsrsReviewResult(
    @JvmField val stability: Double,
    @JvmField val difficulty: Double,
    @JvmField val intervalMillis: Long,
    /**
     * Retention-independent interval used for ladder promotion decisions:
     * the interval this memory state would schedule at a fixed 0.90 target
     * retention, so ladder progression speed does not silently track the
     * user's retention setting (Goal 64 / closed decision D4). Defaults to
     * [intervalMillis] for callers (e.g. test fakes) that inject a fixed
     * interval directly; the real adapter computes it at 0.90.
     */
    @JvmField val promotionIntervalMillis: Long = intervalMillis,
) {
    fun intervalDays(): Int = daysFromMillis(intervalMillis)

    fun promotionIntervalDays(): Int = daysFromMillis(promotionIntervalMillis)

    private fun daysFromMillis(millis: Long): Int {
        val safeInterval = maxOf(1L, millis)
        val days = ((safeInterval - 1L) / DAY_MILLIS) + 1L
        return if (days > Int.MAX_VALUE) Int.MAX_VALUE else days.toInt()
    }

    companion object {
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
