package dev.bee.kanjianki.core

internal interface KaniFsrsAdapter {
    fun initialReview(
        rating: String?,
        currentStability: Double,
        currentDifficulty: Double,
        targetRetention: Double,
        isNewLearning: Boolean,
    ): KaniFsrsReviewResult

    /**
     * [elapsedDays] is fractional; see [FsrsElapsedTime]. FSRS-7 gives a same-day
     * review a real retrievability instead of collapsing every one onto t = 0,
     * so flooring here would hand the engine an FSRS-6-shaped question.
     */
    fun review(
        stability: Double,
        difficulty: Double,
        rating: String?,
        elapsedDays: Double,
        targetRetention: Double,
    ): KaniFsrsReviewResult
}
