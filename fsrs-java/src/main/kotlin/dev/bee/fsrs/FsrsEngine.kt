package dev.bee.fsrs

/**
 * Pure FSRS memory-math engine.
 */
interface FsrsEngine {
    fun initialState(firstRating: FsrsRating?): FsrsMemoryState

    fun retrievability(state: FsrsMemoryState?, elapsedDays: Int): Double

    fun nextDifficulty(currentDifficulty: Double, rating: FsrsRating?): Double {
        val resolvedRating = Fsrs.requireNonNull(rating, "rating")
        return nextState(FsrsMemoryState(1.0, currentDifficulty), resolvedRating, 0).difficulty
    }

    fun nextState(previousState: FsrsMemoryState?, rating: FsrsRating?, elapsedDays: Int): FsrsMemoryState

    fun shortTermStability(stability: Double, rating: FsrsRating?): Double

    fun nextIntervalDays(stability: Double, desiredRetention: Double, maximumInterval: Int): Int

    fun review(input: FsrsReviewInput?): FsrsReviewOutput

    companion object {
        @JvmStatic
        fun create(parameters: FsrsParameters?): FsrsEngine = DefaultFsrsEngine(parameters)

        @JvmStatic
        fun latestDefault(): FsrsEngine = create(FsrsParameters.latestDefault())
    }
}
