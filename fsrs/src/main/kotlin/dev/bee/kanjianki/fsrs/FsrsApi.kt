package dev.bee.kanjianki.fsrs

import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsParameters
import dev.bee.fsrs.FsrsRating
import dev.bee.fsrs.FsrsReviewInput

enum class FsrsReviewRating(val upstreamValue: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
}

data class FsrsMemory(
    val stability: Double,
    val difficulty: Double,
)

data class FsrsReviewRequest(
    val previousMemory: FsrsMemory,
    val rating: FsrsReviewRating,
    val elapsedDays: Int,
    val desiredRetention: Double,
    val maximumIntervalDays: Int = 36_500,
)

data class FsrsReviewSchedule(
    val nextMemory: FsrsMemory,
    val retrievability: Double,
    val nextIntervalDays: Int,
)

interface KaniFsrsEngine {
    fun initialState(firstRating: FsrsReviewRating): FsrsMemory

    fun review(request: FsrsReviewRequest): FsrsReviewSchedule

    fun nextDifficulty(
        currentDifficulty: Double,
        rating: FsrsReviewRating,
    ): Double

    fun nextIntervalDays(
        stability: Double,
        desiredRetention: Double,
        maximumIntervalDays: Int = 36_500,
    ): Int
}

object FsrsProvenance {
    const val upstreamRepository: String = FsrsAlgorithmInfo.UPSTREAM_REPOSITORY
    const val upstreamRelease: String = FsrsAlgorithmInfo.UPSTREAM_RELEASE
    const val upstreamCommit: String = FsrsAlgorithmInfo.UPSTREAM_COMMIT
    const val upstreamSchedulerBlob: String = FsrsAlgorithmInfo.UPSTREAM_SCHEDULER_BLOB
    const val algorithmLabel: String = FsrsAlgorithmInfo.ALGORITHM_LABEL
    const val parameterCount: Int = FsrsAlgorithmInfo.PARAMETER_COUNT
}

class JavaBackedKaniFsrsEngine(
    private val delegate: FsrsEngine = FsrsEngine.create(FsrsParameters.latestDefault()),
) : KaniFsrsEngine {
    override fun initialState(firstRating: FsrsReviewRating): FsrsMemory =
        delegate.initialState(firstRating.toJava()).toKani()

    override fun review(request: FsrsReviewRequest): FsrsReviewSchedule {
        val output = delegate.review(
            FsrsReviewInput(
                FsrsMemoryState(request.previousMemory.stability, request.previousMemory.difficulty),
                request.rating.toJava(),
                request.elapsedDays,
                request.desiredRetention,
                request.maximumIntervalDays,
            ),
        )
        return FsrsReviewSchedule(
            nextMemory = output.nextState().toKani(),
            retrievability = output.retrievability(),
            nextIntervalDays = output.nextIntervalDays(),
        )
    }

    override fun nextDifficulty(
        currentDifficulty: Double,
        rating: FsrsReviewRating,
    ): Double = delegate.nextDifficulty(currentDifficulty, rating.toJava())

    override fun nextIntervalDays(
        stability: Double,
        desiredRetention: Double,
        maximumIntervalDays: Int,
    ): Int = delegate.nextIntervalDays(stability, desiredRetention, maximumIntervalDays)

    private fun FsrsReviewRating.toJava(): FsrsRating = when (this) {
        FsrsReviewRating.AGAIN -> FsrsRating.AGAIN
        FsrsReviewRating.HARD -> FsrsRating.HARD
        FsrsReviewRating.GOOD -> FsrsRating.GOOD
        FsrsReviewRating.EASY -> FsrsRating.EASY
    }

    private fun FsrsMemoryState.toKani(): FsrsMemory =
        FsrsMemory(stability = stability(), difficulty = difficulty())
}
