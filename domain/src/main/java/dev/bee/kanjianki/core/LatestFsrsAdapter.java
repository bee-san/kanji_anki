package dev.bee.kanjianki.core;

import dev.bee.fsrs.Fsrs;
import dev.bee.fsrs.FsrsEngine;
import dev.bee.fsrs.FsrsMemoryState;
import dev.bee.fsrs.FsrsRating;
import dev.bee.fsrs.FsrsReviewInput;
import dev.bee.fsrs.FsrsReviewOutput;

import java.util.Objects;

final class LatestFsrsAdapter implements KaniFsrsAdapter {
    private static final int MAXIMUM_INTERVAL_DAYS = 36_500;
    private static final double DEFAULT_DIFFICULTY = 5.0;
    private static final double DEFAULT_RETENTION = 0.9;

    private final FsrsEngine engine;

    LatestFsrsAdapter() {
        this(FsrsEngine.latestDefault());
    }

    LatestFsrsAdapter(FsrsEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    @Override
    public KaniFsrsReviewResult initialReview(
            String rating,
            double currentStability,
            double currentDifficulty,
            double targetRetention,
            boolean isNewLearning
    ) {
        FsrsRating fsrsRating = toRating(rating);
        FsrsMemoryState state = engine.initialState(fsrsRating);
        if (!isNewLearning) {
            state = new FsrsMemoryState(
                    safeStability(currentStability),
                    engine.nextDifficulty(safeDifficulty(currentDifficulty), fsrsRating)
            );
        }
        int intervalDays = engine.nextIntervalDays(state.stability(), safeRetention(targetRetention), MAXIMUM_INTERVAL_DAYS);
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
                new FsrsMemoryState(safeStability(stability), safeDifficulty(difficulty)),
                toRating(rating),
                elapsedDays,
                safeRetention(targetRetention),
                MAXIMUM_INTERVAL_DAYS
        ));
        return result(output.nextState(), output.nextIntervalDays());
    }

    private static KaniFsrsReviewResult result(FsrsMemoryState state, int intervalDays) {
        return new KaniFsrsReviewResult(state.stability(), state.difficulty(), intervalDays * KaniFsrsReviewResult.DAY_MILLIS);
    }

    private static FsrsRating toRating(String rating) {
        if (StudyRatings.HARD.equals(rating)) {
            return FsrsRating.HARD;
        }
        if (StudyRatings.GOOD.equals(rating)) {
            return FsrsRating.GOOD;
        }
        if (StudyRatings.EASY.equals(rating)) {
            return FsrsRating.EASY;
        }
        return FsrsRating.AGAIN;
    }

    private static double safeStability(double stability) {
        if (!Double.isFinite(stability) || stability <= 0.0) {
            return Fsrs.STABILITY_MIN;
        }
        return Math.max(stability, Fsrs.STABILITY_MIN);
    }

    private static double safeRetention(double targetRetention) {
        if (!Double.isFinite(targetRetention)) {
            return DEFAULT_RETENTION;
        }
        return Math.max(0.01, Math.min(0.99, targetRetention));
    }

    private static double safeDifficulty(double difficulty) {
        if (!Double.isFinite(difficulty)) {
            return DEFAULT_DIFFICULTY;
        }
        return Math.max(Fsrs.MIN_DIFFICULTY, Math.min(Fsrs.MAX_DIFFICULTY, difficulty));
    }
}
