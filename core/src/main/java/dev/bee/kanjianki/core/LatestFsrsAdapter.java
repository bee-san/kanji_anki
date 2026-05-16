package dev.bee.kanjianki.core;

import dev.bee.fsrs.Fsrs;
import dev.bee.fsrs.FsrsEngine;
import dev.bee.fsrs.FsrsMemoryState;
import dev.bee.fsrs.FsrsRating;
import dev.bee.fsrs.FsrsReviewInput;
import dev.bee.fsrs.FsrsReviewOutput;

final class LatestFsrsAdapter implements KaniFsrsAdapter {
    private static final int MAXIMUM_INTERVAL_DAYS = 36_500;

    private final FsrsEngine engine;

    LatestFsrsAdapter() {
        this(FsrsEngine.latestDefault());
    }

    LatestFsrsAdapter(FsrsEngine engine) {
        this.engine = engine;
    }

    @Override
    public KaniFsrsReviewResult initialReview(
            String rating,
            double currentDifficulty,
            double targetRetention,
            boolean isNewLearning
    ) {
        FsrsRating fsrsRating = toRating(rating);
        FsrsMemoryState state = engine.initialState(fsrsRating);
        if (!isNewLearning) {
            state = new FsrsMemoryState(
                    state.stability(),
                    engine.nextDifficulty(clampDifficulty(currentDifficulty), fsrsRating)
            );
        }
        int intervalDays = engine.nextIntervalDays(state.stability(), retention(targetRetention), MAXIMUM_INTERVAL_DAYS);
        return result(state, intervalDays);
    }

    @Override
    public KaniFsrsReviewResult review(
            double stability,
            double difficulty,
            String rating,
            int elapsedDays,
            double targetRetention
    ) {
        FsrsReviewOutput output = engine.review(new FsrsReviewInput(
                new FsrsMemoryState(Math.max(stability, Fsrs.STABILITY_MIN), clampDifficulty(difficulty)),
                toRating(rating),
                elapsedDays,
                retention(targetRetention),
                MAXIMUM_INTERVAL_DAYS
        ));
        return result(output.nextState(), output.nextIntervalDays());
    }

    private static KaniFsrsReviewResult result(FsrsMemoryState state, int intervalDays) {
        return new KaniFsrsReviewResult(state.stability(), state.difficulty(), intervalDays * BridgeScheduler.DAY);
    }

    private static FsrsRating toRating(String rating) {
        if (BridgeScheduler.RATING_HARD.equals(rating)) {
            return FsrsRating.HARD;
        }
        if (BridgeScheduler.RATING_GOOD.equals(rating)) {
            return FsrsRating.GOOD;
        }
        if (BridgeScheduler.RATING_EASY.equals(rating)) {
            return FsrsRating.EASY;
        }
        return FsrsRating.AGAIN;
    }

    private static double retention(double targetRetention) {
        return Math.max(0.01, Math.min(0.99, targetRetention));
    }

    private static double clampDifficulty(double difficulty) {
        return Math.max(Fsrs.MIN_DIFFICULTY, Math.min(Fsrs.MAX_DIFFICULTY, difficulty));
    }
}
