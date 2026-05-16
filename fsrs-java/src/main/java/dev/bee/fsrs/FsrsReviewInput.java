package dev.bee.fsrs;

/**
 * Input for one persisted FSRS review.
 */
public final class FsrsReviewInput {
    private final FsrsMemoryState previousState;
    private final FsrsRating rating;
    private final int elapsedDays;
    private final double desiredRetention;
    private final int maximumInterval;

    public FsrsReviewInput(
            FsrsMemoryState previousState,
            FsrsRating rating,
            int elapsedDays,
            double desiredRetention,
            int maximumInterval
    ) {
        this.previousState = Fsrs.requireNonNull(previousState, "previousState");
        this.rating = Fsrs.requireNonNull(rating, "rating");
        Fsrs.validateElapsedDays(elapsedDays);
        Fsrs.validateDesiredRetention(desiredRetention);
        Fsrs.validateMaximumInterval(maximumInterval);
        this.elapsedDays = elapsedDays;
        this.desiredRetention = desiredRetention;
        this.maximumInterval = maximumInterval;
    }

    public FsrsMemoryState previousState() {
        return previousState;
    }

    public FsrsRating rating() {
        return rating;
    }

    public int elapsedDays() {
        return elapsedDays;
    }

    public double desiredRetention() {
        return desiredRetention;
    }

    public int maximumInterval() {
        return maximumInterval;
    }
}
