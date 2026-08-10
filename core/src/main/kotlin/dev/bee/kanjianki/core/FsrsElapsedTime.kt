package dev.bee.kanjianki.core

/**
 * The one definition of "how long since this memory was last reviewed".
 *
 * FSRS-7 takes fractional days, so this returns a `Double`. FSRS-6 took whole
 * days and this computation used to floor, which meant a card reviewed 23 hours
 * after the last one had "elapsed 0 days" — indistinguishable from a card
 * answered twice in the same second. Flooring here would hand an FSRS-7 engine
 * an FSRS-6-shaped question and discard the sub-day resolution the revision
 * exists to provide, at the boundary rather than in the engine.
 *
 * This lived in three places before FSRS-7: `ReviewTransitionEngine`'s review
 * context, `AdaptiveReviewTransitionEngine`, and `FsrsTrainingDataQueries`, whose
 * copy carried the comment "exact mirror of ReviewContext.elapsedReviewDays()".
 * A mirror maintained by comment is one edit away from being a lie, and the
 * fitter silently training on a different elapsed time than the scheduler
 * schedules with is close to undetectable — the loss would just be slightly
 * wrong. One function, three callers.
 */
object FsrsElapsedTime {
    private const val DAY_MILLIS = 86_400_000.0

    /**
     * Fractional days between a memory's last review and [nowMillis].
     *
     * When the memory has no recorded review timestamp — pre-DB31 rows, and state
     * seeded from AnkiDroid during admission — the last review is inferred by
     * walking back from the due date by the previously scheduled interval, which
     * is the best available estimate and what the integer version did.
     */
    @JvmStatic
    fun elapsedDays(
        nowMillis: Long,
        lastReviewedAtMillis: Long,
        dueAtMillis: Long,
        previousIntervalDays: Int,
    ): Double {
        val previousIntervalMillis = previousIntervalDays.toLong().coerceAtLeast(0L) * StudyLadderRules.DAY
        val resolvedLastReview = lastReviewedAtMillis.takeIf { it > 0L }
            ?: saturatingSubtract(dueAtMillis, previousIntervalMillis).coerceAtLeast(0L)
        return millisToDays(nonNegativeDifference(nowMillis, resolvedLastReview))
    }

    /** Fractional days for an already-computed non-negative duration. */
    @JvmStatic
    fun millisToDays(millis: Long): Double = millis.coerceAtLeast(0L).toDouble() / DAY_MILLIS

    /**
     * A fractional day count as whole milliseconds, which is the resolution the
     * database stores due times at.
     *
     * Rounded rather than truncated, and rounded *here*, so the due time held in
     * memory is exactly the one persistence will read back: a due instant that
     * shifted on reload would make an equality check against a reloaded item fail
     * for no visible reason.
     */
    @JvmStatic
    fun daysToMillis(days: Double): Long {
        if (!days.isFinite() || days <= 0.0) {
            return 0L
        }
        val millis = days * DAY_MILLIS
        if (millis >= Long.MAX_VALUE.toDouble()) {
            return Long.MAX_VALUE
        }
        return Math.round(millis)
    }
}
