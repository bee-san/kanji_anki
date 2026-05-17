package dev.bee.fsrs;

/**
 * Immutable FSRS memory state for a reviewed item.
 */
public record FsrsMemoryState(double stability, double difficulty) {
    public FsrsMemoryState {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            throw new IllegalArgumentException("stability must be finite and positive");
        }
        if (!Double.isFinite(difficulty) || difficulty < Fsrs.MIN_DIFFICULTY || difficulty > Fsrs.MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be finite and in [1, 10]");
        }
    }

    @Override
    public String toString() {
        return "FsrsMemoryState{stability=" + stability + ", difficulty=" + difficulty + '}';
    }
}
