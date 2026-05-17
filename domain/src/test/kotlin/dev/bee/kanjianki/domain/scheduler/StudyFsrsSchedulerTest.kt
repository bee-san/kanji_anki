package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.fsrs.FsrsMemory
import dev.bee.kanjianki.fsrs.FsrsReviewRating
import dev.bee.kanjianki.fsrs.FsrsReviewRequest
import dev.bee.kanjianki.fsrs.FsrsReviewSchedule
import dev.bee.kanjianki.fsrs.FsrsSchedulingBounds
import dev.bee.kanjianki.fsrs.KaniFsrsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyFsrsSchedulerTest {
    private val engine = RecordingFsrsEngine()
    private val scheduler = StudyFsrsScheduler(engine)

    @Test
    fun initialNewLearningUsesFreshFsrsStateAndClampsRetention() {
        engine.initialMemory = FsrsMemory(stability = 2.5, difficulty = 4.5)
        engine.nextIntervalDays = 9

        val result = scheduler.initialReview(
            StudyFsrsInitialReviewInput(
                rating = StudyRating.EASY,
                currentStability = -1.0,
                currentDifficulty = 20.0,
                targetRetention = 1.5,
                isNewLearning = true,
            ),
        )

        assertEquals(FsrsReviewRating.EASY, engine.initialRating)
        assertNull(engine.nextDifficultyInput)
        assertEquals(2.5, engine.nextIntervalStability, 0.0)
        assertEquals(FsrsSchedulingBounds.MAX_DESIRED_RETENTION, engine.nextIntervalRetention, 0.0)
        assertEquals(FsrsSchedulingBounds.MAXIMUM_INTERVAL_DAYS, engine.nextIntervalMaximum)
        assertEquals(2.5, result.stability, 0.0)
        assertEquals(4.5, result.difficulty, 0.0)
        assertEquals(9, result.intervalDays)
        assertEquals(9L * DAY_MILLIS, result.intervalMillis)
    }

    @Test
    fun initialRelearningGraduationPreservesCurrentMemoryWithClamps() {
        engine.nextDifficultyResult = 6.25
        engine.nextIntervalDays = 11

        val result = scheduler.initialReview(
            StudyFsrsInitialReviewInput(
                rating = StudyRating.HARD,
                currentStability = -3.0,
                currentDifficulty = 99.0,
                targetRetention = -1.0,
                isNewLearning = false,
            ),
        )

        assertNull(engine.initialRating)
        assertEquals(
            NextDifficultyInput(
                currentDifficulty = FsrsSchedulingBounds.MAX_DIFFICULTY,
                rating = FsrsReviewRating.HARD,
            ),
            engine.nextDifficultyInput,
        )
        assertEquals(FsrsSchedulingBounds.STABILITY_MINIMUM, engine.nextIntervalStability, 0.0)
        assertEquals(FsrsSchedulingBounds.MIN_DESIRED_RETENTION, engine.nextIntervalRetention, 0.0)
        assertEquals(FsrsSchedulingBounds.STABILITY_MINIMUM, result.stability, 0.0)
        assertEquals(6.25, result.difficulty, 0.0)
        assertEquals(11, result.intervalDays)
    }

    @Test
    fun existingReviewSendsClampedStateIntoFsrsReview() {
        engine.reviewSchedule = FsrsReviewSchedule(
            nextMemory = FsrsMemory(stability = 7.0, difficulty = 3.0),
            retrievability = 0.42,
            nextIntervalDays = 13,
        )

        val result = scheduler.review(
            StudyFsrsExistingReviewInput(
                stability = -4.0,
                difficulty = -8.0,
                rating = StudyRating.AGAIN,
                elapsedDays = 5,
                targetRetention = 2.0,
            ),
        )

        assertEquals(
            FsrsReviewRequest(
                previousMemory = FsrsMemory(
                    stability = FsrsSchedulingBounds.STABILITY_MINIMUM,
                    difficulty = FsrsSchedulingBounds.MIN_DIFFICULTY,
                ),
                rating = FsrsReviewRating.AGAIN,
                elapsedDays = 5,
                desiredRetention = FsrsSchedulingBounds.MAX_DESIRED_RETENTION,
                maximumIntervalDays = FsrsSchedulingBounds.MAXIMUM_INTERVAL_DAYS,
            ),
            engine.reviewRequest,
        )
        assertEquals(7.0, result.stability, 0.0)
        assertEquals(3.0, result.difficulty, 0.0)
        assertEquals(13, result.intervalDays)
    }

    @Test
    fun rejectsNegativeElapsedDaysAndNonPositiveIntervals() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyFsrsExistingReviewInput(
                stability = 1.0,
                difficulty = 5.0,
                rating = StudyRating.GOOD,
                elapsedDays = -1,
                targetRetention = 0.9,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            StudyFsrsReviewResult(stability = 1.0, difficulty = 5.0, intervalDays = 0)
        }
    }

    private class RecordingFsrsEngine : KaniFsrsEngine {
        var initialMemory: FsrsMemory = FsrsMemory(stability = 1.0, difficulty = 5.0)
        var initialRating: FsrsReviewRating? = null
        var nextDifficultyInput: NextDifficultyInput? = null
        var nextDifficultyResult: Double = 5.0
        var nextIntervalStability: Double = 0.0
        var nextIntervalRetention: Double = 0.0
        var nextIntervalMaximum: Int = 0
        var nextIntervalDays: Int = 1
        var reviewRequest: FsrsReviewRequest? = null
        var reviewSchedule: FsrsReviewSchedule = FsrsReviewSchedule(
            nextMemory = FsrsMemory(stability = 1.0, difficulty = 5.0),
            retrievability = 1.0,
            nextIntervalDays = 1,
        )

        override fun initialState(firstRating: FsrsReviewRating): FsrsMemory {
            initialRating = firstRating
            return initialMemory
        }

        override fun review(request: FsrsReviewRequest): FsrsReviewSchedule {
            reviewRequest = request
            return reviewSchedule
        }

        override fun nextDifficulty(
            currentDifficulty: Double,
            rating: FsrsReviewRating,
        ): Double {
            nextDifficultyInput = NextDifficultyInput(currentDifficulty, rating)
            return nextDifficultyResult
        }

        override fun nextIntervalDays(
            stability: Double,
            desiredRetention: Double,
            maximumIntervalDays: Int,
        ): Int {
            nextIntervalStability = stability
            nextIntervalRetention = desiredRetention
            nextIntervalMaximum = maximumIntervalDays
            return nextIntervalDays
        }
    }

    private data class NextDifficultyInput(
        val currentDifficulty: Double,
        val rating: FsrsReviewRating,
    )

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
