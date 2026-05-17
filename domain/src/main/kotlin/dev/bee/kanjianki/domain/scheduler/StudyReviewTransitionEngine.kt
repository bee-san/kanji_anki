package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory
import kotlin.math.round

class StudyReviewTransitionEngine(
    private val tokenGuard: ReviewTokenGuard = ReviewTokenGuard(),
    private val ratingResolver: ReviewRatingResolver = ReviewRatingResolver(),
    private val memoryPolicy: ReviewMemoryPolicy = ReviewMemoryPolicy(),
    private val learningStepEngine: LearningStepEngine = LearningStepEngine(),
    private val fsrsScheduler: StudyFsrsScheduler = StudyFsrsScheduler(),
    private val ladderMovementEngine: LadderMovementEngine = LadderMovementEngine(),
) {
    fun apply(input: StudyReviewTransitionInput): StudyReviewTransitionResult {
        val tokenResult = tokenGuard.evaluate(
            ReviewTokenGuardInput(
                requestToken = input.request.token,
                activeToken = input.item.activeToken,
                consumedTokens = input.consumedTokens,
            ),
        )
        if (!tokenResult.accepted) {
            return StudyReviewTransitionResult(
                item = input.item,
                appliedRating = null,
                duplicate = true,
                message = tokenResult.message,
                consumedTokens = tokenResult.consumedTokens,
                rejectionReason = tokenResult.reason,
            )
        }

        val initialRung = input.ladderSettings.effectiveRung(
            current = input.item.rung,
            hasSimilarKanji = input.item.hasSimilarKanji,
        )
        val resolved = ratingResolver.resolve(input.request, initialRung)
        val activeMemory = memoryPolicy.activeTaskMemory(input.item, initialRung)
        val state = MutableReviewState.from(input.item, activeMemory, initialRung)

        when (input.item.phase) {
            StudyPhase.NEW_LEARNING,
            StudyPhase.RELEARNING -> applyLearningTransition(input, state, activeMemory, resolved.rating)
            StudyPhase.REVIEW -> applyReviewTransition(input, state, activeMemory, resolved.rating)
        }
        updateWritingLevel(initialRung, resolved, state)

        val updatedItem = updatedStudyItem(
            input = input,
            initialRung = initialRung,
            activeMemory = activeMemory,
            state = state,
            rating = resolved.rating,
        )
        return StudyReviewTransitionResult(
            item = updatedItem,
            appliedRating = resolved.rating,
            duplicate = false,
            message = "Review applied.",
            consumedTokens = tokenResult.consumedTokens,
            rejectionReason = null,
        )
    }

    private fun applyLearningTransition(
        input: StudyReviewTransitionInput,
        state: MutableReviewState,
        activeMemory: TaskMemory,
        rating: StudyRating,
    ) {
        when (
            val result = learningStepEngine.apply(
                LearningStepInput(
                    phase = input.item.phase,
                    currentStepIndex = activeMemory.learningStep,
                    rating = rating,
                    nowMillis = input.nowMillis,
                    settings = input.learningSettings,
                ),
            )
        ) {
            is LearningStepResult.Repeat -> {
                state.phase = result.phase
                state.schedulerState = StudyItemState.LEARNING
                state.stepIndex = result.stepIndex
                state.dueAtMillis = result.dueAtMillis
                state.scheduledIntervalDays = 0
            }
            LearningStepResult.Graduate -> graduateToReview(
                input = input,
                state = state,
                rating = rating,
                activeMemory = activeMemory,
                isNewLearning = input.item.phase == StudyPhase.NEW_LEARNING,
            )
        }
    }

    private fun applyReviewTransition(
        input: StudyReviewTransitionInput,
        state: MutableReviewState,
        activeMemory: TaskMemory,
        rating: StudyRating,
    ) {
        val elapsedDays = memoryPolicy.elapsedReviewDays(activeMemory, input.nowMillis)
        val fsrsResult = fsrsScheduler.review(
            StudyFsrsExistingReviewInput(
                stability = activeMemory.stability,
                difficulty = activeMemory.difficulty,
                rating = rating,
                elapsedDays = elapsedDays,
                targetRetention = input.targetRetention,
            ),
        )
        state.stability = fsrsResult.stability
        state.difficulty = fsrsResult.difficulty
        if (rating == StudyRating.AGAIN) {
            applyReviewAgain(input, state, fsrsResult)
        } else {
            applyReviewPass(input, state, rating, fsrsResult)
        }
    }

    private fun applyReviewAgain(
        input: StudyReviewTransitionInput,
        state: MutableReviewState,
        fsrsResult: StudyFsrsReviewResult,
    ) {
        state.lapses += 1
        state.taskLapses += 1
        when (
            val result = learningStepEngine.apply(
                LearningStepInput(
                    phase = StudyPhase.RELEARNING,
                    currentStepIndex = 0,
                    rating = StudyRating.AGAIN,
                    nowMillis = input.nowMillis,
                    settings = input.learningSettings,
                ),
            )
        ) {
            is LearningStepResult.Repeat -> {
                state.phase = StudyPhase.RELEARNING
                state.schedulerState = StudyItemState.LEARNING
                state.stepIndex = result.stepIndex
                state.dueAtMillis = result.dueAtMillis
                state.scheduledIntervalDays = 0
            }
            LearningStepResult.Graduate -> {
                state.phase = StudyPhase.REVIEW
                state.schedulerState = StudyItemState.REVIEW
                state.stepIndex = 0
                state.dueAtMillis = input.nowMillis + fsrsResult.intervalMillis
                state.scheduledIntervalDays = fsrsResult.intervalDays
            }
        }
        applyLadderMovement(input, state, StudyRating.AGAIN, fsrsResult.intervalMillis)
    }

    private fun applyReviewPass(
        input: StudyReviewTransitionInput,
        state: MutableReviewState,
        rating: StudyRating,
        fsrsResult: StudyFsrsReviewResult,
    ) {
        state.phase = StudyPhase.REVIEW
        state.schedulerState = StudyItemState.REVIEW
        state.stepIndex = 0
        state.dueAtMillis = input.nowMillis + fsrsResult.intervalMillis
        state.scheduledIntervalDays = fsrsResult.intervalDays
        applyLadderMovement(input, state, rating, fsrsResult.intervalMillis)
    }

    private fun graduateToReview(
        input: StudyReviewTransitionInput,
        state: MutableReviewState,
        rating: StudyRating,
        activeMemory: TaskMemory,
        isNewLearning: Boolean,
    ) {
        val fsrsResult = fsrsScheduler.initialReview(
            StudyFsrsInitialReviewInput(
                rating = rating,
                currentStability = activeMemory.stability,
                currentDifficulty = activeMemory.difficulty,
                targetRetention = input.targetRetention,
                isNewLearning = isNewLearning,
            ),
        )
        state.phase = StudyPhase.REVIEW
        state.schedulerState = StudyItemState.REVIEW
        state.stepIndex = 0
        state.stability = fsrsResult.stability
        state.difficulty = fsrsResult.difficulty
        state.dueAtMillis = input.nowMillis + fsrsResult.intervalMillis
        state.scheduledIntervalDays = fsrsResult.intervalDays
    }

    private fun applyLadderMovement(
        input: StudyReviewTransitionInput,
        state: MutableReviewState,
        rating: StudyRating,
        fsrsIntervalMillis: Long,
    ) {
        val movement = ladderMovementEngine.apply(
            LadderMovementInput(
                currentRung = state.rung,
                phase = StudyPhase.REVIEW,
                rating = rating,
                dueAtMillis = input.item.dueAtMillis,
                nowMillis = input.nowMillis,
                lastRealReviewDueAtMillis = state.lastRealReviewDueAtMillis,
                realPassStreak = state.realPassStreak,
                realAgainStreak = state.realAgainStreak,
                fsrsScheduledIntervalMillis = fsrsIntervalMillis,
                hasSimilarKanji = input.item.hasSimilarKanji,
                settings = input.ladderSettings,
            ),
        )
        state.rung = movement.rung
        state.realPassStreak = movement.realPassStreak
        state.realAgainStreak = movement.realAgainStreak
        state.lastRealReviewDueAtMillis = movement.lastRealReviewDueAtMillis
    }

    private fun updateWritingLevel(
        initialRung: StudyRung,
        resolved: ResolvedReviewRating,
        state: MutableReviewState,
    ) {
        if (initialRung != StudyRung.WRITE_KANJI) {
            return
        }
        if (resolved.failedWriting) {
            state.writingLevel = (state.writingLevel - 1).coerceAtLeast(0)
        } else if (resolved.cleanWritingPass) {
            state.writingLevel = (state.writingLevel + 1).coerceAtMost(MAX_WRITING_LEVEL)
        }
    }

    private fun updatedStudyItem(
        input: StudyReviewTransitionInput,
        initialRung: StudyRung,
        activeMemory: TaskMemory,
        state: MutableReviewState,
        rating: StudyRating,
    ): StudyQueueItem {
        val updatedMemory = TaskMemory.from(
            state = state.schedulerState.wireName,
            dueAtMillis = state.dueAtMillis,
            stability = state.stability.roundForStorage(),
            difficulty = state.difficulty.roundForStorage(),
            totalReviews = activeMemory.totalReviews + 1,
            lapses = state.taskLapses,
            learningStep = state.stepIndex,
            lastRating = rating.wireName,
            matureIntervalDays = state.scheduledIntervalDays,
            consecutivePasses = if (rating == StudyRating.AGAIN) 0 else state.realPassStreak,
            lastPassedDueAtMillis = if (rating == StudyRating.AGAIN) 0L else state.lastRealReviewDueAtMillis,
        )
        val reviewedTaskType = initialRung.wireName
        val activeTaskType = state.rung.wireName
        var memories = input.item.memories.withTaskMemory(reviewedTaskType, updatedMemory)
        if (activeTaskType != reviewedTaskType) {
            memories = memories.withTaskMemory(activeTaskType, updatedMemory)
        }
        return input.item.copy(
            state = state.schedulerState,
            dueAtMillis = state.dueAtMillis,
            stability = state.stability.roundForStorage(),
            difficulty = state.difficulty.roundForStorage(),
            totalReviews = state.totalReviews,
            lapses = state.lapses,
            learningStep = state.stepIndex,
            writingLevel = state.writingLevel,
            matureIntervalDays = state.scheduledIntervalDays,
            rung = state.rung,
            phase = state.phase,
            realPassStreak = state.realPassStreak,
            realAgainStreak = state.realAgainStreak,
            lastRealReviewDueAtMillis = state.lastRealReviewDueAtMillis,
            activeToken = null,
            memories = memories,
        )
    }

    private fun Double.roundForStorage(): Double =
        round(this * 100.0) / 100.0

    private data class MutableReviewState(
        var totalReviews: Int,
        var lapses: Int,
        var taskLapses: Int,
        var stepIndex: Int,
        var writingLevel: Int,
        var scheduledIntervalDays: Int,
        var realPassStreak: Int,
        var realAgainStreak: Int,
        var lastRealReviewDueAtMillis: Long,
        var dueAtMillis: Long,
        var rung: StudyRung,
        var phase: StudyPhase,
        var stability: Double,
        var difficulty: Double,
        var schedulerState: StudyItemState,
    ) {
        companion object {
            fun from(
                item: StudyQueueItem,
                activeMemory: TaskMemory,
                initialRung: StudyRung,
            ): MutableReviewState = MutableReviewState(
                totalReviews = item.totalReviews + 1,
                lapses = item.lapses,
                taskLapses = activeMemory.lapses,
                stepIndex = activeMemory.learningStep,
                writingLevel = item.writingLevel,
                scheduledIntervalDays = activeMemory.matureIntervalDays,
                realPassStreak = item.realPassStreak,
                realAgainStreak = item.realAgainStreak,
                lastRealReviewDueAtMillis = item.lastRealReviewDueAtMillis,
                dueAtMillis = item.dueAtMillis,
                rung = initialRung,
                phase = item.phase,
                stability = activeMemory.stability,
                difficulty = activeMemory.difficulty,
                schedulerState = item.state,
            )
        }
    }

    private companion object {
        const val MAX_WRITING_LEVEL = 3
    }
}

data class StudyReviewTransitionInput(
    val item: StudyQueueItem,
    val request: StudyReviewRequest,
    val nowMillis: Long,
    val consumedTokens: Set<String> = emptySet(),
    val targetRetention: Double = 0.90,
    val learningSettings: LearningStepSettings = LearningStepSettings.defaults,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
)

data class StudyReviewTransitionResult(
    val item: StudyQueueItem,
    val appliedRating: StudyRating?,
    val duplicate: Boolean,
    val message: String,
    val consumedTokens: Set<String>,
    val rejectionReason: ReviewTokenRejectionReason?,
)
