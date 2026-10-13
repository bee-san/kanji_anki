package dev.bee.fsrs

/**
 * Input for one FSRS-7 review.
 *
 * Separate from [FsrsReviewInput] rather than a widening of it, because
 * [elapsedDays] and [maximumIntervalDays] are fractional here. Reusing the FSRS-6
 * type with `Double` fields would change the meaning of stored FSRS-6 rows, and
 * the whole reason both engines exist side by side is that an old row must stay
 * interpretable.
 */
@JvmRecord
data class Fsrs7ReviewInput(
    val previousState: FsrsMemoryState?,
    val rating: FsrsRating?,
    /** Fractional days since the previous review. Ten minutes is 0.00694. */
    val elapsedDays: Double,
    val desiredRetention: Double,
    /** Cap on the returned interval, in fractional days. */
    val maximumIntervalDays: Double,
) {
    init {
        Fsrs.requireNonNull(previousState, "previousState")
        Fsrs.requireNonNull(rating, "rating")
        Fsrs7.validateElapsedDays(elapsedDays)
        Fsrs7.validateDesiredRetention(desiredRetention)
        Fsrs7.validateMaximumInterval(maximumIntervalDays)
    }
}

/** Result of one FSRS-7 review calculation. */
@JvmRecord
data class Fsrs7ReviewOutput(
    val nextState: FsrsMemoryState?,
    val retrievability: Double,
    /** Fractional days until the item is due. */
    val nextIntervalDays: Double,
) {
    init {
        Fsrs.requireNonNull(nextState, "nextState")
        require(retrievability.isFinite() && retrievability >= 0.0 && retrievability <= 1.0) {
            "retrievability must be finite and in [0, 1]"
        }
        Fsrs7.validateMaximumInterval(nextIntervalDays)
    }
}
