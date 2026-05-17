package dev.bee.fsrs;

/**
 * Input for one persisted FSRS review.
 */
public record FsrsReviewInput(
        FsrsMemoryState previousState,
        FsrsRating rating,
        int elapsedDays,
        double desiredRetention,
        int maximumInterval
) {
    public FsrsReviewInput {
        previousState = Fsrs.requireNonNull(previousState, "previousState");
        rating = Fsrs.requireNonNull(rating, "rating");
        Fsrs.validateElapsedDays(elapsedDays);
        Fsrs.validateDesiredRetention(desiredRetention);
        Fsrs.validateMaximumInterval(maximumInterval);
    }
}
