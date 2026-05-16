package dev.bee.kanjianki.core;

interface KaniFsrsAdapter {
    KaniFsrsReviewResult initialReview(
            String rating,
            double currentDifficulty,
            double targetRetention,
            boolean isNewLearning
    );

    KaniFsrsReviewResult review(
            double stability,
            double difficulty,
            String rating,
            int elapsedDays,
            double targetRetention
    );
}
