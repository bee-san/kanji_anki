package dev.bee.fsrs;

import java.util.Arrays;

/**
 * Immutable FSRS parameter set.
 */
public final class FsrsParameters {
    public static final int PARAMETER_COUNT = 21;

    private static final double[] LATEST_DEFAULT_TEMPLATE = {
            0.212, 1.2931, 2.3065, 8.2956, 6.4133,
            0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483,
            0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
            0.1542
    };

    private final double[] values;

    private FsrsParameters(double[] values) {
        this.values = values;
    }

    public static FsrsParameters latestDefault() {
        return of(LATEST_DEFAULT_TEMPLATE);
    }

    public static double[] latestDefaultValues() {
        return LATEST_DEFAULT_TEMPLATE.clone();
    }

    public static FsrsParameters of(double[] values) {
        if (values == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
        if (values.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("FSRS requires exactly " + PARAMETER_COUNT + " parameters");
        }
        double[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            if (!Double.isFinite(copy[i])) {
                throw new IllegalArgumentException("parameter " + i + " must be finite");
            }
        }
        if (copy[20] <= 0.0) {
            throw new IllegalArgumentException("decay magnitude parameter must be positive");
        }
        return new FsrsParameters(copy);
    }

    public double get(int index) {
        return values[index];
    }

    double initialStability(FsrsRating rating) {
        return get(rating.value() - 1);
    }

    double initialDifficultyBase() {
        return values[4];
    }

    double initialDifficultyExponent() {
        return values[5];
    }

    double difficultyDeltaScale() {
        return values[6];
    }

    double difficultyMeanReversionWeight() {
        return values[7];
    }

    double recallStabilityBase() {
        return values[8];
    }

    double recallStabilityStabilityDecay() {
        return values[9];
    }

    double recallStabilityRetrievabilitySensitivity() {
        return values[10];
    }

    double forgetStabilityBase() {
        return values[11];
    }

    double forgetStabilityDifficultyDecay() {
        return values[12];
    }

    double forgetStabilityStabilityGrowth() {
        return values[13];
    }

    double forgetStabilityRetrievabilitySensitivity() {
        return values[14];
    }

    double hardPenalty() {
        return values[15];
    }

    double easyBonus() {
        return values[16];
    }

    double shortTermBase() {
        return values[17];
    }

    double shortTermRatingOffset() {
        return values[18];
    }

    double shortTermStabilityDecay() {
        return values[19];
    }

    public double[] toArray() {
        return values.clone();
    }

    public double decayMagnitude() {
        return values[20];
    }

    public double decay() {
        return -values[20];
    }

    public double factor() {
        return Math.pow(0.9, 1.0 / decay()) - 1.0;
    }

    @Override
    public String toString() {
        return "FsrsParameters" + Arrays.toString(values);
    }
}
