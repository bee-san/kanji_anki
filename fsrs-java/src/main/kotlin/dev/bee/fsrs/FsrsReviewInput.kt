package dev.bee.fsrs

/**
 * Input for one persisted FSRS review.
 */
class FsrsReviewInput(
    previousState: FsrsMemoryState?,
    rating: FsrsRating?,
    private val elapsedDays: Int,
    private val desiredRetention: Double,
    private val maximumInterval: Int,
) {
    private val previousState: FsrsMemoryState = Fsrs.requireNonNull(previousState, "previousState")!!
    private val rating: FsrsRating = Fsrs.requireNonNull(rating, "rating")!!

    init {
        Fsrs.validateElapsedDays(elapsedDays)
        Fsrs.validateDesiredRetention(desiredRetention)
        Fsrs.validateMaximumInterval(maximumInterval)
    }

    fun previousState(): FsrsMemoryState = previousState

    fun rating(): FsrsRating = rating

    fun elapsedDays(): Int = elapsedDays

    fun desiredRetention(): Double = desiredRetention

    fun maximumInterval(): Int = maximumInterval

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FsrsReviewInput &&
            previousState == other.previousState &&
            rating == other.rating &&
            elapsedDays == other.elapsedDays &&
            desiredRetention == other.desiredRetention &&
            maximumInterval == other.maximumInterval

    override fun hashCode(): Int {
        var result = previousState.hashCode()
        result = 31 * result + rating.hashCode()
        result = 31 * result + elapsedDays
        result = 31 * result + desiredRetention.hashCode()
        result = 31 * result + maximumInterval
        return result
    }

    override fun toString(): String =
        "FsrsReviewInput[previousState=$previousState, rating=$rating, elapsedDays=$elapsedDays, " +
            "desiredRetention=$desiredRetention, maximumInterval=$maximumInterval]"
}
