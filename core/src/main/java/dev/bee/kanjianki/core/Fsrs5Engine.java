package dev.bee.kanjianki.core;

/**
 * Pure-Java implementation of the FSRS-5 (Free Spaced Repetition Scheduler v5) algorithm.
 * <p>
 * Reference: <a href="https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm">
 * FSRS Algorithm Specification</a>
 * <p>
 * This replaces the prior simplified multiplier-based model with the full FSRS-5
 * power-law forgetting curve and 19-weight parameter model.
 */
public final class Fsrs5Engine {

    /** FSRS-4.5+ forgetting curve decay constant. */
    public static final double DECAY = -0.5;
    /** FSRS-4.5+ factor ensuring R(S,S) = 0.9. Derived: (0.9)^(1/DECAY) - 1 = 19/81. */
    public static final double FACTOR = 19.0 / 81.0;

    /** Maximum stability in days (100 years) to prevent overflow. */
    private static final double MAX_STABILITY = 36500.0;
    /** Minimum stability to prevent degenerate intervals. */
    private static final double MIN_STABILITY = 0.01;

    private static final long DAY_MILLIS = 86_400_000L;

    // Rating constants matching BridgeScheduler wire format
    private static final int RATING_AGAIN = 1;
    private static final int RATING_HARD = 2;
    private static final int RATING_GOOD = 3;
    private static final int RATING_EASY = 4;

    private final double[] w;
    private final double targetRetention;

    /**
     * Creates an FSRS-5 engine with the given weights and target retention.
     *
     * @param weights 19 FSRS-5 parameters (w[0]–w[18])
     * @param targetRetention desired probability of recall at review time (e.g. 0.9)
     */
    public Fsrs5Engine(double[] weights, double targetRetention) {
        if (weights == null || weights.length < 19) {
            this.w = defaultWeights();
        } else {
            this.w = weights.clone();
        }
        this.targetRetention = Math.max(0.01, Math.min(0.99, targetRetention));
    }

    /** Creates an FSRS-5 engine with default weights and 0.9 target retention. */
    public Fsrs5Engine() {
        this(defaultWeights(), 0.9);
    }

    // ======================== PUBLIC API ========================

    /**
     * Computes the initial stability when a card is first reviewed.
     * S₀(G) = w[G-1]
     *
     * @param rating 1=Again, 2=Hard, 3=Good, 4=Easy
     */
    public double initialStability(int rating) {
        int idx = Math.max(0, Math.min(3, rating - 1));
        return clampStability(w[idx]);
    }

    /**
     * Computes the initial difficulty after the first rating.
     * D₀(G) = w4 - e^(w5 * (G-1)) + 1
     *
     * @param rating 1=Again, 2=Hard, 3=Good, 4=Easy
     */
    public double initialDifficulty(int rating) {
        return clampDifficulty(w[4] - Math.exp(w[5] * (rating - 1)) + 1);
    }

    /**
     * Updates difficulty after a review using FSRS-5 mean-reversion formula.
     * <p>
     * ΔD(G) = -w6 * (G - 3)
     * D' = D + ΔD * (10 - D) / 9  (linear damping)
     * D'' = w7 * D₀(4) + (1 - w7) * D'  (mean reversion toward D₀(4))
     *
     * @param currentDifficulty current D value
     * @param rating 1=Again, 2=Hard, 3=Good, 4=Easy
     */
    public double updateDifficulty(double currentDifficulty, int rating) {
        double deltaD = -w[6] * (rating - 3);
        double dPrime = currentDifficulty + deltaD * (10.0 - currentDifficulty) / 9.0;
        double d0Easy = initialDifficulty(RATING_EASY);
        double dFinal = w[7] * d0Easy + (1.0 - w[7]) * dPrime;
        return clampDifficulty(dFinal);
    }

    /**
     * Computes retrievability (probability of recall) after t days since last review.
     * R(t, S) = (1 + FACTOR * t/S)^DECAY
     *
     * @param elapsedDays days since last review
     * @param stability current stability in days
     */
    public double retrievability(double elapsedDays, double stability) {
        if (stability <= 0 || elapsedDays < 0) {
            return 1.0;
        }
        return Math.pow(1.0 + FACTOR * elapsedDays / stability, DECAY);
    }

    /**
     * Computes the next interval in milliseconds for the given stability and target retention.
     * I(r, S) = (S / FACTOR) * (r^(1/DECAY) - 1)
     *
     * @param stability current stability in days
     */
    public long nextIntervalMillis(double stability) {
        double cappedS = Math.min(stability, MAX_STABILITY);
        double intervalDays = (cappedS / FACTOR) * (Math.pow(targetRetention, 1.0 / DECAY) - 1.0);
        intervalDays = Math.max(1.0, intervalDays);
        return Math.round(intervalDays * DAY_MILLIS);
    }

    /**
     * Computes the next interval in whole days.
     */
    public int nextIntervalDays(double stability) {
        double cappedS = Math.min(stability, MAX_STABILITY);
        double intervalDays = (cappedS / FACTOR) * (Math.pow(targetRetention, 1.0 / DECAY) - 1.0);
        return Math.max(1, (int) Math.round(intervalDays));
    }

    /**
     * Computes new stability after a successful recall (Hard, Good, or Easy).
     * <p>
     * S'_r = S * (e^w8 * (11-D) * S^(-w9) * (e^(w10*(1-R)) - 1) * hardPenalty * easyBonus + 1)
     *
     * @param stability current stability
     * @param difficulty current difficulty
     * @param retrievability R at time of review
     * @param rating 2=Hard, 3=Good, 4=Easy
     */
    public double stabilityAfterRecall(double stability, double difficulty, double retrievability, int rating) {
        double hardPenalty = (rating == RATING_HARD) ? w[15] : 1.0;
        double easyBonus = (rating == RATING_EASY) ? w[16] : 1.0;

        double sIncrease = Math.exp(w[8])
                * (11.0 - difficulty)
                * Math.pow(stability, -w[9])
                * (Math.exp(w[10] * (1.0 - retrievability)) - 1.0)
                * hardPenalty
                * easyBonus;

        // SInc must be >= 1 for successful recall (stability never decreases on pass)
        double newStability = stability * (Math.max(0, sIncrease) + 1.0);
        return clampStability(newStability);
    }

    /**
     * Computes new stability after forgetting (rating = Again).
     * <p>
     * S'_f = w11 * D^(-w12) * ((S+1)^w13 - 1) * e^(w14*(1-R))
     *
     * @param stability current stability
     * @param difficulty current difficulty
     * @param retrievability R at time of review
     */
    public double stabilityAfterForgetting(double stability, double difficulty, double retrievability) {
        double newStability = w[11]
                * Math.pow(difficulty, -w[12])
                * (Math.pow(stability + 1.0, w[13]) - 1.0)
                * Math.exp(w[14] * (1.0 - retrievability));
        return clampStability(Math.min(newStability, stability));
    }

    /**
     * Computes new stability after a same-day review (review within the same day as the last).
     * S'(S,G) = S * e^(w17 * (G - 3 + w18))
     * <p>
     * The result is clamped to be >= S when G >= 3 (Good or Easy).
     *
     * @param stability current stability
     * @param rating 1=Again, 2=Hard, 3=Good, 4=Easy
     */
    public double stabilityAfterSameDayReview(double stability, int rating) {
        double sIncrease = Math.exp(w[17] * (rating - 3 + w[18]));
        // Ensure stability doesn't decrease for Good/Easy same-day reviews
        if (rating >= RATING_GOOD) {
            sIncrease = Math.max(1.0, sIncrease);
        }
        return clampStability(stability * sIncrease);
    }

    // ======================== CONVENIENCE METHODS ========================

    /**
     * Applies a full review and returns the new stability.
     * Determines whether to use recall or forget formula based on rating.
     *
     * @param stability current stability
     * @param difficulty current difficulty
     * @param elapsedDays days since last review
     * @param rating 1-4
     */
    public double applyReview(double stability, double difficulty, double elapsedDays, int rating) {
        double retrievability = retrievability(elapsedDays, stability);
        if (rating == RATING_AGAIN) {
            return stabilityAfterForgetting(stability, difficulty, retrievability);
        }
        return stabilityAfterRecall(stability, difficulty, retrievability, rating);
    }

    /**
     * Returns the elapsed days since a given due timestamp relative to now.
     */
    public static double elapsedDays(long dueAtMillis, long nowMillis) {
        long elapsed = nowMillis - dueAtMillis;
        if (elapsed <= 0) {
            return 0.0;
        }
        return (double) elapsed / DAY_MILLIS;
    }

    // ======================== RATING CONVERSION ========================

    /**
     * Converts the wire-format rating string to FSRS integer (1-4).
     */
    public static int ratingToInt(String rating) {
        if (rating == null) return RATING_AGAIN;
        return switch (rating) {
            case "again" -> RATING_AGAIN;
            case "hard" -> RATING_HARD;
            case "good" -> RATING_GOOD;
            case "easy" -> RATING_EASY;
            default -> RATING_AGAIN;
        };
    }

    // ======================== WEIGHTS ========================

    /**
     * Default FSRS-5 weights from the reference implementation.
     * <a href="https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm#fsrs-5">Source</a>
     */
    public static double[] defaultWeights() {
        return new double[]{
                0.40255,  // w0: S₀(Again)
                1.18385,  // w1: S₀(Hard)
                3.173,    // w2: S₀(Good)
                15.69105, // w3: S₀(Easy)
                7.1949,   // w4: D₀ base (D₀(1) = w4)
                0.5345,   // w5: D₀ exponential factor
                1.4604,   // w6: difficulty delta factor
                0.0046,   // w7: mean reversion weight
                1.54575,  // w8: SInc base (e^w8 in recall formula)
                0.1192,   // w9: stability power in recall (S^(-w9))
                1.01925,  // w10: retrievability factor in recall
                1.9395,   // w11: forget base multiplier
                0.11,     // w12: difficulty power in forget (D^(-w12))
                0.29605,  // w13: stability power in forget ((S+1)^w13)
                2.2698,   // w14: retrievability factor in forget
                0.2315,   // w15: hard penalty for recall
                2.9898,   // w16: easy bonus for recall
                0.51655,  // w17: same-day review factor
                0.6621    // w18: same-day review rating offset
        };
    }

    public double[] getWeights() {
        return w.clone();
    }

    public double getTargetRetention() {
        return targetRetention;
    }

    // ======================== HELPERS ========================

    private static double clampStability(double s) {
        return Math.max(MIN_STABILITY, Math.min(MAX_STABILITY, s));
    }

    private static double clampDifficulty(double d) {
        return Math.max(1.0, Math.min(10.0, d));
    }
}
