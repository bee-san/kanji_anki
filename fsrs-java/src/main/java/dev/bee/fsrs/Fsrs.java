package dev.bee.fsrs;

/**
 * Shared constants and small validation helpers for FSRS calculations.
 */
public final class Fsrs {
    public static final double STABILITY_MIN = 0.001;
    public static final double MIN_DIFFICULTY = 1.0;
    public static final double MAX_DIFFICULTY = 10.0;

    private Fsrs() {
    }

    static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    static void validateElapsedDays(int elapsedDays) {
        if (elapsedDays < 0) {
            throw new IllegalArgumentException("elapsedDays must be non-negative");
        }
    }

    static void validateDesiredRetention(double desiredRetention) {
        if (!Double.isFinite(desiredRetention) || desiredRetention <= 0.0 || desiredRetention >= 1.0) {
            throw new IllegalArgumentException("desiredRetention must be finite and in (0, 1)");
        }
    }

    static void validateMaximumInterval(int maximumInterval) {
        if (maximumInterval < 1) {
            throw new IllegalArgumentException("maximumInterval must be at least 1");
        }
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
