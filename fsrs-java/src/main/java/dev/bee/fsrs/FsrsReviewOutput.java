package dev.bee.fsrs;

/**
 * Result of one FSRS review calculation.
 */
public final class FsrsReviewOutput {
    private final FsrsMemoryState nextState;
    private final double retrievability;
    private final int nextIntervalDays;

    public FsrsReviewOutput(FsrsMemoryState nextState, double retrievability, int nextIntervalDays) {
        this.nextState = Fsrs.requireNonNull(nextState, "nextState");
        if (!Double.isFinite(retrievability) || retrievability < 0.0 || retrievability > 1.0) {
            throw new IllegalArgumentException("retrievability must be finite and in [0, 1]");
        }
        Fsrs.validateMaximumInterval(nextIntervalDays);
        this.retrievability = retrievability;
        this.nextIntervalDays = nextIntervalDays;
    }

    public FsrsMemoryState nextState() {
        return nextState;
    }

    public double retrievability() {
        return retrievability;
    }

    public int nextIntervalDays() {
        return nextIntervalDays;
    }
}
