package dev.bee.fsrs;

final class DefaultFsrsEngine implements FsrsEngine {
    private static final String PARAM_RATING = "rating";

    private final FsrsParameters parameters;

    DefaultFsrsEngine(FsrsParameters parameters) {
        this.parameters = Fsrs.requireNonNull(parameters, "parameters");
    }

    @Override
    public FsrsMemoryState initialState(FsrsRating firstRating) {
        Fsrs.requireNonNull(firstRating, "firstRating");
        return new FsrsMemoryState(initialStability(firstRating), initialDifficulty(firstRating, true));
    }

    @Override
    public double retrievability(FsrsMemoryState state, int elapsedDays) {
        Fsrs.requireNonNull(state, "state");
        Fsrs.validateElapsedDays(elapsedDays);
        double stability = Math.max(state.stability(), Fsrs.STABILITY_MIN);
        return Math.pow(1.0 + parameters.factor() * elapsedDays / stability, parameters.decay());
    }

    @Override
    public FsrsMemoryState nextState(FsrsMemoryState previousState, FsrsRating rating, int elapsedDays) {
        Fsrs.requireNonNull(previousState, "previousState");
        Fsrs.requireNonNull(rating, PARAM_RATING);
        Fsrs.validateElapsedDays(elapsedDays);
        validateDifficulty(previousState.difficulty());

        double retrievability = retrievability(previousState, elapsedDays);
        double nextDifficulty = nextDifficulty(previousState.difficulty(), rating);
        double nextStability;
        if (rating == FsrsRating.AGAIN) {
            nextStability = nextForgetStability(nextDifficulty, previousState.stability(), retrievability);
        } else if (elapsedDays == 0) {
            nextStability = shortTermStability(previousState.stability(), rating);
        } else {
            nextStability = nextRecallStability(nextDifficulty, previousState.stability(), retrievability, rating);
        }
        return new FsrsMemoryState(nextStability, nextDifficulty);
    }

    @Override
    public double nextDifficulty(double currentDifficulty, FsrsRating rating) {
        validateDifficulty(currentDifficulty);
        Fsrs.requireNonNull(rating, PARAM_RATING);
        double deltaDifficulty = -(parameters.difficultyDeltaScale() * (rating.value() - 3.0));
        double linearDamping = (10.0 - currentDifficulty) * deltaDifficulty / 9.0;
        double easyInitialDifficulty = initialDifficulty(FsrsRating.EASY, false);
        double meanReversion = parameters.difficultyMeanReversionWeight();
        double next = meanReversion * easyInitialDifficulty
                + (1.0 - meanReversion) * (currentDifficulty + linearDamping);
        return Fsrs.clamp(next, Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY);
    }

    @Override
    public double shortTermStability(double stability, FsrsRating rating) {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            throw new IllegalArgumentException("stability must be finite and positive");
        }
        Fsrs.requireNonNull(rating, PARAM_RATING);
        double increase = Math.exp(parameters.shortTermBase() * (rating.value() - 3.0 + parameters.shortTermRatingOffset()))
                * Math.pow(stability, -parameters.shortTermStabilityDecay());
        if (rating == FsrsRating.GOOD || rating == FsrsRating.EASY) {
            increase = Math.max(increase, 1.0);
        }
        return Math.max(stability * increase, Fsrs.STABILITY_MIN);
    }

    @Override
    public int nextIntervalDays(double stability, double desiredRetention, int maximumInterval) {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            throw new IllegalArgumentException("stability must be finite and positive");
        }
        Fsrs.validateDesiredRetention(desiredRetention);
        Fsrs.validateMaximumInterval(maximumInterval);
        double interval = (stability / parameters.factor())
                * (Math.pow(desiredRetention, 1.0 / parameters.decay()) - 1.0);
        int rounded = (int) Math.round(interval);
        return Math.min(Math.max(rounded, 1), maximumInterval);
    }

    @Override
    public FsrsReviewOutput review(FsrsReviewInput input) {
        Fsrs.requireNonNull(input, "input");
        double retrievability = retrievability(input.previousState(), input.elapsedDays());
        FsrsMemoryState nextState = nextState(input.previousState(), input.rating(), input.elapsedDays());
        int interval = nextIntervalDays(nextState.stability(), input.desiredRetention(), input.maximumInterval());
        return new FsrsReviewOutput(nextState, retrievability, interval);
    }

    private double initialStability(FsrsRating rating) {
        return Math.max(parameters.initialStability(rating), Fsrs.STABILITY_MIN);
    }

    private double initialDifficulty(FsrsRating rating, boolean clamp) {
        double difficulty = parameters.initialDifficultyBase()
                - Math.exp(parameters.initialDifficultyExponent() * (rating.value() - 1.0))
                + 1.0;
        return clamp ? Fsrs.clamp(difficulty, Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY) : difficulty;
    }

    private double nextRecallStability(
            double difficulty,
            double stability,
            double retrievability,
            FsrsRating rating
    ) {
        double hardPenalty = rating == FsrsRating.HARD ? parameters.hardPenalty() : 1.0;
        double easyBonus = rating == FsrsRating.EASY ? parameters.easyBonus() : 1.0;
        double next = stability * (
                1.0
                        + Math.exp(parameters.recallStabilityBase())
                        * (11.0 - difficulty)
                        * Math.pow(stability, -parameters.recallStabilityStabilityDecay())
                        * (Math.exp((1.0 - retrievability) * parameters.recallStabilityRetrievabilitySensitivity()) - 1.0)
                        * hardPenalty
                        * easyBonus
        );
        return Math.max(next, Fsrs.STABILITY_MIN);
    }

    private double nextForgetStability(double difficulty, double stability, double retrievability) {
        double longTermForget = parameters.forgetStabilityBase()
                * Math.pow(difficulty, -parameters.forgetStabilityDifficultyDecay())
                * (Math.pow(stability + 1.0, parameters.forgetStabilityStabilityGrowth()) - 1.0)
                * Math.exp((1.0 - retrievability) * parameters.forgetStabilityRetrievabilitySensitivity());
        double shortTermForgetCap = stability / Math.exp(parameters.shortTermBase() * parameters.shortTermRatingOffset());
        return Math.max(Math.min(longTermForget, shortTermForgetCap), Fsrs.STABILITY_MIN);
    }

    private static void validateDifficulty(double difficulty) {
        if (!Double.isFinite(difficulty) || difficulty < Fsrs.MIN_DIFFICULTY || difficulty > Fsrs.MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be finite and in [1, 10]");
        }
    }
}
