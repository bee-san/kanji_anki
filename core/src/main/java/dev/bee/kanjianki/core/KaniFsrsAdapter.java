package dev.bee.kanjianki.core;

interface KaniFsrsAdapter {
    KaniFsrsReviewResult initialReview(String rating, double currentDifficulty, double targetRetention);

    KaniFsrsReviewResult review(
            double stability,
            double difficulty,
            String rating,
            long dueAtMillis,
            long nowMillis,
            double targetRetention
    );
}
