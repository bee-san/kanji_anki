package dev.bee.kanjianki.core

internal interface KaniFsrsAdapter {
    fun initialReview(
        rating: String,
        currentStability: Double,
        currentDifficulty: Double,
        targetRetention: Double,
        isNewLearning: Boolean,
    ): KaniFsrsReviewResult

    fun review(
        stability: Double,
        difficulty: Double,
        rating: String,
        elapsedDays: Int,
        targetRetention: Double,
    ): KaniFsrsReviewResult
}
