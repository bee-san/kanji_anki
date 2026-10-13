package dev.bee.fsrs

/**
 * Input for one persisted FSRS review.
 */
@JvmRecord
data class FsrsReviewInput(
    val previousState: FsrsMemoryState?,
    val rating: FsrsRating?,
    val elapsedDays: Int,
    val desiredRetention: Double,
    val maximumInterval: Int,
) {
    init {
        Fsrs.requireNonNull(previousState, "previousState")
        Fsrs.requireNonNull(rating, "rating")
        Fsrs.validateElapsedDays(elapsedDays)
        Fsrs.validateDesiredRetention(desiredRetention)
        Fsrs.validateMaximumInterval(maximumInterval)
    }
}
