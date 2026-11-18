package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs
import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating
import dev.bee.fsrs.FsrsReviewInput

/**
 * Test double: the FSRS-6 memory model Kani shipped before RWKV (the former
 * `LatestFsrsAdapter`), behind [KaniMemoryModel]. Policy tests (ladder,
 * repair, promotion gates) use it because its intervals are closed-form and
 * well understood; production schedules with [RwkvMemoryModel].
 *
 * Learning steps are observed without changing memory, a new-card graduation
 * starts from `initialState(rating)`, and a relearning graduation keeps the
 * post-lapse stability and applies `nextDifficulty` once more.
 */
internal class FsrsTestMemoryModel(
    private val engine: FsrsEngine = FsrsEngine.latestDefault(),
) : KaniMemoryModel {
    override fun review(request: MemoryReviewRequest): MemoryReviewResult =
        when (request.kind) {
            MemoryAnswerKind.NEW_LEARNING_STEP,
            MemoryAnswerKind.RELEARNING_STEP,
            -> MemoryReviewResult(request.previousStability, request.previousDifficulty, 0L)
            MemoryAnswerKind.NEW_GRADUATION -> initialReview(request, request.rating, isNewLearning = true)
            MemoryAnswerKind.RELEARNING_GRADUATION -> initialReview(request, request.rating, isNewLearning = false)
            MemoryAnswerKind.REVIEW -> reviewState(request)
        }

    override fun previewFirstGood(request: MemoryReviewRequest): MemoryReviewResult =
        initialReview(request, StudyRatings.GOOD, isNewLearning = true)

    private fun initialReview(request: MemoryReviewRequest, rating: String, isNewLearning: Boolean): MemoryReviewResult {
        val fsrsRating = rating.toFsrsRating()
        val state = if (isNewLearning) {
            engine.initialState(fsrsRating)
        } else {
            FsrsMemoryState(
                safeStability(request.previousStability),
                engine.nextDifficulty(safeDifficulty(request.previousDifficulty), fsrsRating),
            )
        }
        val intervalDays = engine.nextIntervalDays(
            state.stability,
            safeRetention(request.targetRetention),
            MAXIMUM_INTERVAL_DAYS,
        )
        return state.toResult(intervalDays, promotionIntervalDays(state.stability))
    }

    private fun reviewState(request: MemoryReviewRequest): MemoryReviewResult {
        val output = engine.review(
            FsrsReviewInput(
                FsrsMemoryState(safeStability(request.previousStability), safeDifficulty(request.previousDifficulty)),
                request.rating.toFsrsRating(),
                request.elapsedDays.coerceAtLeast(0),
                safeRetention(request.targetRetention),
                MAXIMUM_INTERVAL_DAYS,
            ),
        )
        val nextState = output.nextState!!
        return nextState.toResult(output.nextIntervalDays, promotionIntervalDays(nextState.stability))
    }

    private fun promotionIntervalDays(stability: Double): Int =
        engine.nextIntervalDays(stability, PROMOTION_RETENTION, MAXIMUM_INTERVAL_DAYS)

    private fun FsrsMemoryState.toResult(intervalDays: Int, promotionIntervalDays: Int): MemoryReviewResult =
        MemoryReviewResult(
            stability,
            difficulty,
            intervalDays * MemoryReviewResult.DAY_MILLIS,
            promotionIntervalDays * MemoryReviewResult.DAY_MILLIS,
        )

    private fun String?.toFsrsRating(): FsrsRating = when (this) {
        StudyRatings.HARD -> FsrsRating.HARD
        StudyRatings.GOOD -> FsrsRating.GOOD
        StudyRatings.EASY -> FsrsRating.EASY
        else -> FsrsRating.AGAIN
    }

    private fun safeStability(stability: Double): Double =
        if (!stability.isFinite() || stability <= 0.0) Fsrs.STABILITY_MIN else stability.coerceAtLeast(Fsrs.STABILITY_MIN)

    private fun safeRetention(targetRetention: Double): Double =
        if (targetRetention.isNaN()) 0.9 else targetRetention.coerceIn(0.01, 0.99)

    private fun safeDifficulty(difficulty: Double): Double =
        if (difficulty.isNaN()) 5.0 else difficulty.coerceIn(Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY)

    private companion object {
        const val MAXIMUM_INTERVAL_DAYS = 36_500
        const val PROMOTION_RETENTION = 0.9
    }
}
