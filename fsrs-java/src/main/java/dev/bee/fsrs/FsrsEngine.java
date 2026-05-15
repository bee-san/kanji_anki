package dev.bee.fsrs;

/**
 * Pure FSRS memory-math engine.
 */
public interface FsrsEngine {
    static FsrsEngine create(FsrsParameters parameters) {
        return new DefaultFsrsEngine(parameters);
    }

    static FsrsEngine latestDefault() {
        return create(FsrsParameters.latestDefault());
    }

    FsrsMemoryState initialState(FsrsRating firstRating);

    double retrievability(FsrsMemoryState state, int elapsedDays);

    FsrsMemoryState nextState(FsrsMemoryState previousState, FsrsRating rating, int elapsedDays);

    double shortTermStability(double stability, FsrsRating rating);

    int nextIntervalDays(double stability, double desiredRetention, int maximumInterval);

    FsrsReviewOutput review(FsrsReviewInput input);
}
