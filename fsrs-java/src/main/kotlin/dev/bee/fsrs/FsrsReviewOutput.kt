package dev.bee.fsrs

/**
 * Result of one FSRS review calculation.
 */
@JvmRecord
data class FsrsReviewOutput(
    val nextState: FsrsMemoryState?,
    val retrievability: Double,
    val nextIntervalDays: Int,
) {
    init {
        Fsrs.requireNonNull(nextState, "nextState")
        require(retrievability.isFinite() && retrievability >= 0.0 && retrievability <= 1.0) {
            "retrievability must be finite and in [0, 1]"
        }
        Fsrs.validateMaximumInterval(nextIntervalDays)
    }
}
