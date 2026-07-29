package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7
import dev.bee.fsrs.Fsrs7Engine
import dev.bee.fsrs.Fsrs7ReviewInput
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating

/**
 * Kani's single boundary onto the FSRS engine, now FSRS-7.
 *
 * The engine changed; the memory state did not. [FsrsMemoryState] is shared by
 * both engines — the same `(stability, difficulty)` pair, stability in days and
 * difficulty on the same 1..10 scale — so every persisted `study_items` row and
 * every state seeded from AnkiDroid during admission stays readable. What changes
 * is the mathematics applied to it: intervals move because FSRS-7's forgetting
 * curve is two blended power laws rather than one, which is the point of adopting
 * it.
 *
 * Elapsed time is fractional days here. FSRS-6 took whole days, so a review 23
 * hours after the previous one was indistinguishable from one answered in the same
 * second. Rounding at this boundary would discard exactly the resolution the
 * revision provides while still changing every interval — the worst of both.
 */
internal class LatestFsrsAdapter(
    private val engine: Fsrs7Engine = Fsrs7Engine.latestDefault(),
) : KaniFsrsAdapter {
    override fun initialReview(
        rating: String?,
        currentStability: Double,
        currentDifficulty: Double,
        targetRetention: Double,
        isNewLearning: Boolean,
    ): KaniFsrsReviewResult {
        val fsrsRating = rating.toFsrsRating()
        val state = if (isNewLearning) {
            engine.initialState(fsrsRating)
        } else {
            FsrsMemoryState(
                safeStability(currentStability),
                engine.nextDifficulty(safeDifficulty(currentDifficulty), fsrsRating),
            )
        }
        val intervalDays = engine.nextIntervalDays(
            state.stability,
            safeRetention(targetRetention),
            MAXIMUM_INTERVAL_DAYS,
        )
        return state.toResult(intervalDays, promotionIntervalDays(state.stability))
    }

    override fun review(
        stability: Double,
        difficulty: Double,
        rating: String?,
        elapsedDays: Double,
        targetRetention: Double,
    ): KaniFsrsReviewResult {
        val output = engine.review(
            Fsrs7ReviewInput(
                FsrsMemoryState(safeStability(stability), safeDifficulty(difficulty)),
                rating.toFsrsRating(),
                safeElapsedDays(elapsedDays),
                safeRetention(targetRetention),
                MAXIMUM_INTERVAL_DAYS,
            ),
        )
        val nextState = output.nextState!!
        return nextState.toResult(output.nextIntervalDays, promotionIntervalDays(nextState.stability))
    }

    /**
     * The interval this stability would schedule at a fixed 0.90 target
     * retention. Ladder promotion keys off this value so progression speed
     * is decoupled from the user's retention setting (Goal 64 / D4).
     */
    private fun promotionIntervalDays(stability: Double): Double =
        engine.nextIntervalDays(stability, PROMOTION_RETENTION, MAXIMUM_INTERVAL_DAYS)

    private fun FsrsMemoryState.toResult(
        intervalDays: Double,
        promotionIntervalDays: Double,
    ): KaniFsrsReviewResult =
        KaniFsrsReviewResult(
            stability,
            difficulty,
            FsrsElapsedTime.daysToMillis(atLeastOneDay(intervalDays)),
            FsrsElapsedTime.daysToMillis(atLeastOneDay(promotionIntervalDays)),
        )

    /**
     * Floor a *scheduled* interval at one day. Deliberate, and not a hedge against
     * FSRS-7.
     *
     * FSRS-6's `nextIntervalDays` rounded and clamped to `[1, max]`, so this floor
     * used to live inside the engine. FSRS-7 removes it, correctly: the engine should
     * answer "when does retrievability reach the target" without knowing what the
     * caller will do with sub-day answers. Kani's answer is that its `review` phase is
     * a day-granularity long-term queue, and sub-day repetition is already modelled by
     * learning/relearning steps — which are explicitly practice-only and do not move
     * the ladder.
     *
     * Without this, a lapsed card's post-lapse stability of a few thousandths of a day
     * schedules it seconds out. It comes due again in the same session, and because
     * ladder movement keys off the persisted FSRS due time rather than the calendar
     * day, a card can accumulate real-due fails and demote a rung in seconds. Anki
     * floors review intervals the same way, for the same reason.
     *
     * The floor applies to the interval leaving the adapter, never to the elapsed time
     * entering it: sub-day *inputs* are what FSRS-7 improves for Kani and are passed
     * through untouched.
     */
    private fun atLeastOneDay(intervalDays: Double): Double = intervalDays.coerceAtLeast(1.0)

    private fun String?.toFsrsRating(): FsrsRating = when (this) {
        StudyRatings.HARD -> FsrsRating.HARD
        StudyRatings.GOOD -> FsrsRating.GOOD
        StudyRatings.EASY -> FsrsRating.EASY
        else -> FsrsRating.AGAIN
    }

    /**
     * FSRS-7's stability floor is 0.0001 days rather than FSRS-6's 0.001: upstream
     * runs this model with sub-day scheduling enabled, so the tighter floor is part
     * of the algorithm and not a tuning choice.
     */
    private fun safeStability(stability: Double): Double {
        if (!stability.isFinite() || stability <= 0.0) {
            return Fsrs7.STABILITY_MIN
        }
        return stability.coerceAtLeast(Fsrs7.STABILITY_MIN)
    }

    private fun safeRetention(targetRetention: Double): Double {
        if (targetRetention.isNaN()) {
            return DEFAULT_RETENTION
        }
        return targetRetention.coerceIn(0.01, 0.99)
    }

    private fun safeDifficulty(difficulty: Double): Double {
        if (difficulty.isNaN()) {
            return DEFAULT_DIFFICULTY
        }
        return difficulty.coerceIn(Fsrs7.MIN_DIFFICULTY, Fsrs7.MAX_DIFFICULTY)
    }

    /**
     * Clocks move backwards — NTP corrections, restored backups, timezone-naive edits
     * — and a negative or non-finite elapsed count makes the engine throw, which at
     * this boundary means a lost review rather than a scheduled one. Clamped to zero
     * and treated as an immediate re-review, which under FSRS-7 is a meaningful
     * short-term case rather than a degenerate one.
     */
    private fun safeElapsedDays(elapsedDays: Double): Double {
        if (!elapsedDays.isFinite()) {
            return 0.0
        }
        return elapsedDays.coerceAtLeast(0.0)
    }

    private companion object {
        private const val MAXIMUM_INTERVAL_DAYS = 36_500.0
        private const val DEFAULT_DIFFICULTY = 5.0
        private const val DEFAULT_RETENTION = 0.9

        /** Fixed retention used for retention-independent promotion intervals (Goal 64 / D4). */
        private const val PROMOTION_RETENTION = 0.9
    }
}
