package dev.bee.fsrs;

/**
 * Immutable FSRS memory state for a reviewed item.
 */
public final class FsrsMemoryState {
    private final double stability;
    private final double difficulty;

    public FsrsMemoryState(double stability, double difficulty) {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            throw new IllegalArgumentException("stability must be finite and positive");
        }
        if (!Double.isFinite(difficulty) || difficulty < Fsrs.MIN_DIFFICULTY || difficulty > Fsrs.MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be finite and in [1, 10]");
        }
        this.stability = stability;
        this.difficulty = difficulty;
    }

    public double stability() {
        return stability;
    }

    public double difficulty() {
        return difficulty;
    }

    @Override
    public String toString() {
        return "FsrsMemoryState{stability=" + stability + ", difficulty=" + difficulty + '}';
    }
}
