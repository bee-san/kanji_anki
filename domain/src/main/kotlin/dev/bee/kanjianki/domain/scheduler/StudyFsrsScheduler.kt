package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.fsrs.FsrsMemory
import dev.bee.kanjianki.fsrs.FsrsReviewRating
import dev.bee.kanjianki.fsrs.FsrsReviewRequest
import dev.bee.kanjianki.fsrs.FsrsSchedulingBounds
import dev.bee.kanjianki.fsrs.JavaBackedKaniFsrsEngine
import dev.bee.kanjianki.fsrs.KaniFsrsEngine
import kotlin.math.max
import kotlin.math.min

class StudyFsrsScheduler(
    private val engine: KaniFsrsEngine = JavaBackedKaniFsrsEngine(),
) {
    fun initialReview(input: StudyFsrsInitialReviewInput): StudyFsrsReviewResult {
        val rating = input.rating.toFsrsRating()
        val nextMemory = if (input.isNewLearning) {
            engine.initialState(rating)
        } else {
            FsrsMemory(
                stability = input.currentStability.clampStability(),
                difficulty = engine.nextDifficulty(
                    currentDifficulty = input.currentDifficulty.clampDifficulty(),
                    rating = rating,
                ),
            )
        }
        val intervalDays = engine.nextIntervalDays(
            stability = nextMemory.stability,
            desiredRetention = input.targetRetention.clampRetention(),
            maximumIntervalDays = FsrsSchedulingBounds.MAXIMUM_INTERVAL_DAYS,
        )
        return StudyFsrsReviewResult(
            stability = nextMemory.stability,
            difficulty = nextMemory.difficulty,
            intervalDays = intervalDays,
        )
    }

    fun review(input: StudyFsrsExistingReviewInput): StudyFsrsReviewResult {
        val schedule = engine.review(
            FsrsReviewRequest(
                previousMemory = FsrsMemory(
                    stability = input.stability.clampStability(),
                    difficulty = input.difficulty.clampDifficulty(),
                ),
                rating = input.rating.toFsrsRating(),
                elapsedDays = input.elapsedDays,
                desiredRetention = input.targetRetention.clampRetention(),
                maximumIntervalDays = FsrsSchedulingBounds.MAXIMUM_INTERVAL_DAYS,
            ),
        )
        return StudyFsrsReviewResult(
            stability = schedule.nextMemory.stability,
            difficulty = schedule.nextMemory.difficulty,
            intervalDays = schedule.nextIntervalDays,
        )
    }

    private fun StudyRating.toFsrsRating(): FsrsReviewRating = when (this) {
        StudyRating.AGAIN -> FsrsReviewRating.AGAIN
        StudyRating.HARD -> FsrsReviewRating.HARD
        StudyRating.GOOD -> FsrsReviewRating.GOOD
        StudyRating.EASY -> FsrsReviewRating.EASY
    }

    private fun Double.clampStability(): Double =
        max(this, FsrsSchedulingBounds.STABILITY_MINIMUM)

    private fun Double.clampDifficulty(): Double =
        max(FsrsSchedulingBounds.MIN_DIFFICULTY, min(FsrsSchedulingBounds.MAX_DIFFICULTY, this))

    private fun Double.clampRetention(): Double =
        max(FsrsSchedulingBounds.MIN_DESIRED_RETENTION, min(FsrsSchedulingBounds.MAX_DESIRED_RETENTION, this))
}

data class StudyFsrsInitialReviewInput(
    val rating: StudyRating,
    val currentStability: Double,
    val currentDifficulty: Double,
    val targetRetention: Double,
    val isNewLearning: Boolean,
)

data class StudyFsrsExistingReviewInput(
    val stability: Double,
    val difficulty: Double,
    val rating: StudyRating,
    val elapsedDays: Int,
    val targetRetention: Double,
) {
    init {
        require(elapsedDays >= 0) { "elapsedDays must not be negative" }
    }
}

data class StudyFsrsReviewResult(
    val stability: Double,
    val difficulty: Double,
    val intervalDays: Int,
) {
    init {
        require(intervalDays >= 1) { "intervalDays must be positive" }
    }

    val intervalMillis: Long
        get() = intervalDays.toLong() * DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
