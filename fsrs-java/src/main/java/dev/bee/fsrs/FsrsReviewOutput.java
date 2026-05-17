package dev.bee.fsrs;

/**
 * Result of one FSRS review calculation.
 */
public record FsrsReviewOutput(FsrsMemoryState nextState, double retrievability, int nextIntervalDays) {
    public FsrsReviewOutput {
        nextState = Fsrs.requireNonNull(nextState, "nextState");
        if (!Double.isFinite(retrievability) || retrievability < 0.0 || retrievability > 1.0) {
            throw new IllegalArgumentException("retrievability must be finite and in [0, 1]");
        }
        Fsrs.validateMaximumInterval(nextIntervalDays);
    }
}
