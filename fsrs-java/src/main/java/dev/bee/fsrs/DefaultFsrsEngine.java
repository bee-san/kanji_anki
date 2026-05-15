package dev.bee.fsrs;

final class DefaultFsrsEngine implements FsrsEngine {
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
        Fsrs.requireNonNull(rating, "rating");
        Fsrs.validateElapsedDays(elapsedDays);
        validateDifficulty(previousState.difficulty());

        double retrievability = retrievability(previousState, elapsedDays);
        double nextDifficulty = nextDifficulty(previousState.difficulty(), rating);
        double nextStability = rating == FsrsRating.AGAIN
                ? nextForgetStability(nextDifficulty, previousState.stability(), retrievability)
                : nextRecallStability(nextDifficulty, previousState.stability(), retrievability, rating);
        return new FsrsMemoryState(nextStability, nextDifficulty);
    }

    @Override
    public double shortTermStability(double stability, FsrsRating rating) {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            throw new IllegalArgumentException("stability must be finite and positive");
        }
        Fsrs.requireNonNull(rating, "rating");
        double increase = Math.exp(parameters.get(17) * (rating.value() - 3.0 + parameters.get(18)))
                * Math.pow(stability, -parameters.get(19));
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
        return Math.max(parameters.get(rating.value() - 1), Fsrs.STABILITY_MIN);
    }

    private double initialDifficulty(FsrsRating rating, boolean clamp) {
        double difficulty = parameters.get(4) - Math.exp(parameters.get(5) * (rating.value() - 1.0)) + 1.0;
        return clamp ? Fsrs.clamp(difficulty, Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY) : difficulty;
    }

    private double nextDifficulty(double currentDifficulty, FsrsRating rating) {
        double deltaDifficulty = -(parameters.get(6) * (rating.value() - 3.0));
        double linearDamping = (10.0 - currentDifficulty) * deltaDifficulty / 9.0;
        double easyInitialDifficulty = initialDifficulty(FsrsRating.EASY, false);
        double next = parameters.get(7) * easyInitialDifficulty
                + (1.0 - parameters.get(7)) * (currentDifficulty + linearDamping);
        return Fsrs.clamp(next, Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY);
    }

    private double nextRecallStability(
            double difficulty,
            double stability,
            double retrievability,
            FsrsRating rating
    ) {
        double hardPenalty = rating == FsrsRating.HARD ? parameters.get(15) : 1.0;
        double easyBonus = rating == FsrsRating.EASY ? parameters.get(16) : 1.0;
        double next = stability * (
                1.0
                        + Math.exp(parameters.get(8))
                        * (11.0 - difficulty)
                        * Math.pow(stability, -parameters.get(9))
                        * (Math.exp((1.0 - retrievability) * parameters.get(10)) - 1.0)
                        * hardPenalty
                        * easyBonus
        );
        return Math.max(next, Fsrs.STABILITY_MIN);
    }

    private double nextForgetStability(double difficulty, double stability, double retrievability) {
        double longTermForget = parameters.get(11)
                * Math.pow(difficulty, -parameters.get(12))
                * (Math.pow(stability + 1.0, parameters.get(13)) - 1.0)
                * Math.exp((1.0 - retrievability) * parameters.get(14));
        double shortTermForgetCap = stability / Math.exp(parameters.get(17) * parameters.get(18));
        return Math.max(Math.min(longTermForget, shortTermForgetCap), Fsrs.STABILITY_MIN);
    }

    private static void validateDifficulty(double difficulty) {
        if (!Double.isFinite(difficulty) || difficulty < Fsrs.MIN_DIFFICULTY || difficulty > Fsrs.MAX_DIFFICULTY) {
            throw new IllegalArgumentException("difficulty must be finite and in [1, 10]");
        }
    }
}
