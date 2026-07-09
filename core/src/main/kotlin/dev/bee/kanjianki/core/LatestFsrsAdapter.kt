package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs
import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating
import dev.bee.fsrs.FsrsReviewInput

internal class LatestFsrsAdapter(
    private val engine: FsrsEngine = FsrsEngine.latestDefault(),
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
        elapsedDays: Int,
        targetRetention: Double,
    ): KaniFsrsReviewResult {
        val output = engine.review(
            FsrsReviewInput(
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
    private fun promotionIntervalDays(stability: Double): Int =
        engine.nextIntervalDays(stability, PROMOTION_RETENTION, MAXIMUM_INTERVAL_DAYS)

    private fun FsrsMemoryState.toResult(intervalDays: Int, promotionIntervalDays: Int): KaniFsrsReviewResult =
        KaniFsrsReviewResult(
            stability,
            difficulty,
            intervalDays * KaniFsrsReviewResult.DAY_MILLIS,
            promotionIntervalDays * KaniFsrsReviewResult.DAY_MILLIS,
        )

    private fun String?.toFsrsRating(): FsrsRating = when (this) {
        StudyRatings.HARD -> FsrsRating.HARD
        StudyRatings.GOOD -> FsrsRating.GOOD
        StudyRatings.EASY -> FsrsRating.EASY
        else -> FsrsRating.AGAIN
    }

    private fun safeStability(stability: Double): Double {
        if (!stability.isFinite() || stability <= 0.0) {
            return Fsrs.STABILITY_MIN
        }
        return stability.coerceAtLeast(Fsrs.STABILITY_MIN)
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
        return difficulty.coerceIn(Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY)
    }

    private fun safeElapsedDays(elapsedDays: Int): Int = elapsedDays.coerceAtLeast(0)

    private companion object {
        private const val MAXIMUM_INTERVAL_DAYS = 36_500
        private const val DEFAULT_DIFFICULTY = 5.0
        private const val DEFAULT_RETENTION = 0.9

        /** Fixed retention used for retention-independent promotion intervals (Goal 64 / D4). */
        private const val PROMOTION_RETENTION = 0.9
    }
}
