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
    public KaniFsrsReviewResult initialReview(String rating, double currentDifficulty, double targetRetention) {
        FsrsMemoryState state = engine.initialState(toRating(rating));
        int intervalDays = engine.nextIntervalDays(state.stability(), retention(targetRetention), MAXIMUM_INTERVAL_DAYS);
        return result(state, intervalDays);
    }

    @Override
    public KaniFsrsReviewResult review(
            double stability,
            double difficulty,
            String rating,
            long dueAtMillis,
            long nowMillis,
            double targetRetention
    ) {
        FsrsReviewOutput output = engine.review(new FsrsReviewInput(
                new FsrsMemoryState(Math.max(stability, Fsrs.STABILITY_MIN), clampDifficulty(difficulty)),
                toRating(rating),
                elapsedFullDays(dueAtMillis, nowMillis),
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

    private static int elapsedFullDays(long dueAtMillis, long nowMillis) {
        long elapsed = Math.max(0L, nowMillis - dueAtMillis);
        return (int) Math.min(Integer.MAX_VALUE, elapsed / BridgeScheduler.DAY);
    }

    private static double retention(double targetRetention) {
        return Math.max(0.01, Math.min(0.99, targetRetention));
    }

    private static double clampDifficulty(double difficulty) {
        return Math.max(Fsrs.MIN_DIFFICULTY, Math.min(Fsrs.MAX_DIFFICULTY, difficulty));
    }
}
