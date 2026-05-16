package dev.bee.kanjianki.core;

final class Fsrs5Adapter implements KaniFsrsAdapter {
    @Override
    public KaniFsrsReviewResult initialReview(
            String rating,
            double currentDifficulty,
            double targetRetention,
            boolean isNewLearning
    ) {
        int fsrsRating = Fsrs5Engine.ratingToInt(rating);
        Fsrs5Engine engine = new Fsrs5Engine(null, targetRetention);
        double stability = engine.initialStability(fsrsRating);
        double difficulty = engine.updateDifficulty(currentDifficulty, fsrsRating);
        return new KaniFsrsReviewResult(stability, difficulty, engine.nextIntervalMillis(stability));
    }

    @Override
    public KaniFsrsReviewResult review(
            double stability,
            double difficulty,
            String rating,
            int elapsedDays,
            double targetRetention
    ) {
        int fsrsRating = Fsrs5Engine.ratingToInt(rating);
        Fsrs5Engine engine = new Fsrs5Engine(null, targetRetention);
        double nextDifficulty = engine.updateDifficulty(difficulty, fsrsRating);
        double retrievability = engine.retrievability(elapsedDays, stability);
        double nextStability = fsrsRating == Fsrs5Engine.ratingToInt(BridgeScheduler.RATING_AGAIN)
                ? engine.stabilityAfterForgetting(stability, nextDifficulty, retrievability)
                : engine.stabilityAfterRecall(stability, nextDifficulty, retrievability, fsrsRating);
        return new KaniFsrsReviewResult(nextStability, nextDifficulty, engine.nextIntervalMillis(nextStability));
    }
}
