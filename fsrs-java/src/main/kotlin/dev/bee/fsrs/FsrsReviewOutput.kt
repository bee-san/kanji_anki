package dev.bee.fsrs

/**
 * Result of one FSRS review calculation.
 */
class FsrsReviewOutput(
    nextState: FsrsMemoryState?,
    private val retrievability: Double,
    private val nextIntervalDays: Int,
) {
    private val nextState: FsrsMemoryState = Fsrs.requireNonNull(nextState, "nextState")!!

    init {
        if (!retrievability.isFinite() || retrievability < 0.0 || retrievability > 1.0) {
            throw IllegalArgumentException("retrievability must be finite and in [0, 1]")
        }
        Fsrs.validateMaximumInterval(nextIntervalDays)
    }

    fun nextState(): FsrsMemoryState = nextState

    fun retrievability(): Double = retrievability

    fun nextIntervalDays(): Int = nextIntervalDays

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FsrsReviewOutput &&
            nextState == other.nextState &&
            retrievability == other.retrievability &&
            nextIntervalDays == other.nextIntervalDays

    override fun hashCode(): Int {
        var result = nextState.hashCode()
        result = 31 * result + retrievability.hashCode()
        result = 31 * result + nextIntervalDays
        return result
    }

    override fun toString(): String =
        "FsrsReviewOutput[nextState=$nextState, retrievability=$retrievability, " +
            "nextIntervalDays=$nextIntervalDays]"
}
