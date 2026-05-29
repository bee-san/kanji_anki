package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

internal class ReviewTransitionEngine(private val fsrsAdapter: KaniFsrsAdapter) {
    fun applyReview(application: BridgeScheduler.ReviewApplication): RecordsSchedulerModels.ReviewResult {
        val resolvedParameters = application.parameters ?: RecordsSchedulerModels.SchedulerParameters.defaults()
        val resolvedSettings = application.settings ?: RecordsSyncModels.Settings.kikuDefaults()
        val resolvedSteps = application.learningSettings ?: RecordsSchedulerModels.LearningStepSettings.defaults()
        val resolvedLadder = StudyLadderRules.safeLadder(application.ladder)
        val duplicate = duplicateReviewResult(application.item, application.request, application.consumedTokens)
        if (duplicate != null) {
            return duplicate
        }
        application.consumedTokens.add(application.request.token)
        val context = ReviewContext.from(
            application.item,
            application.request,
            resolvedParameters,
            resolvedSettings,
            resolvedSteps,
            resolvedLadder,
            application.nowMillis
        )
        val state = ReviewState.from(context)
        applyLadderTransition(context, state)
        updateWritingLevel(context, state)
        return RecordsSchedulerModels.ReviewResult(updatedStudyItem(context, state), context.rating, false, "Review applied.")
    }

    private fun duplicateReviewResult(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: Set<String>
    ): RecordsSchedulerModels.ReviewResult? {
        if (consumedTokens.contains(request.token)) {
            return RecordsSchedulerModels.ReviewResult(item, "duplicate", true, "Review token already consumed.")
        }
        if (!item.activeToken.isNullOrEmpty() && item.activeToken != request.token) {
            return RecordsSchedulerModels.ReviewResult(item, "duplicate", true, "Review token does not match the active session.")
        }
        return null
    }

    private fun applyLadderTransition(context: ReviewContext, state: ReviewState) {
        when (context.phase) {
            RecordsBase.SchedulerPhase.NEW_LEARNING -> applyLearningTransition(context, state, true)
            RecordsBase.SchedulerPhase.RELEARNING -> applyLearningTransition(context, state, false)
            RecordsBase.SchedulerPhase.REVIEW -> applyReviewTransition(context, state)
        }
    }

    private fun applyLearningTransition(context: ReviewContext, state: ReviewState, isNewLearning: Boolean) {
        val steps = if (isNewLearning) {
            context.learningSettings.newStepsMinutes
        } else {
            context.learningSettings.reviewStepsMinutes
        }
        when (context.rating) {
            StudyRatings.AGAIN -> {
                state.stepIndex = 0
                state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps[0])
                state.phase = if (isNewLearning) RecordsBase.SchedulerPhase.NEW_LEARNING else RecordsBase.SchedulerPhase.RELEARNING
                state.schedulerState = StudyLadderRules.STATE_LEARNING
            }
            StudyRatings.HARD -> applyLearningHard(context, state, steps, isNewLearning)
            StudyRatings.EASY -> graduateToReview(context, state, isNewLearning)
            StudyRatings.GOOD -> applyLearningGood(context, state, steps, isNewLearning)
            else -> applyLearningGood(context, state, steps, isNewLearning)
        }
    }

    private fun applyLearningHard(
        context: ReviewContext,
        state: ReviewState,
        steps: List<Int>,
        isNewLearning: Boolean
    ) {
        val idx = max(0, state.stepIndex)
        if (idx == 0 && steps.size >= 2) {
            val avg = (StudyLadderRules.stepDelayMillis(steps[0]) + StudyLadderRules.stepDelayMillis(steps[1])) / 2L
            state.due = context.nowMillis + max(StudyLadderRules.stepDelayMillis(steps[0]), avg)
        } else {
            val safeIdx = min(idx, steps.size - 1)
            state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps[safeIdx])
        }
        state.stepIndex = idx
        state.phase = if (isNewLearning) RecordsBase.SchedulerPhase.NEW_LEARNING else RecordsBase.SchedulerPhase.RELEARNING
        state.schedulerState = StudyLadderRules.STATE_LEARNING
    }

    private fun applyLearningGood(
        context: ReviewContext,
        state: ReviewState,
        steps: List<Int>,
        isNewLearning: Boolean
    ) {
        val nextIdx = state.stepIndex + 1
        if (nextIdx >= steps.size) {
            graduateToReview(context, state, isNewLearning)
            return
        }
        state.stepIndex = nextIdx
        state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps[nextIdx])
        state.phase = if (isNewLearning) RecordsBase.SchedulerPhase.NEW_LEARNING else RecordsBase.SchedulerPhase.RELEARNING
        state.schedulerState = StudyLadderRules.STATE_LEARNING
    }

    private fun graduateToReview(context: ReviewContext, state: ReviewState, isNewLearning: Boolean) {
        state.stepIndex = 0
        val result = fsrsAdapter.initialReview(
            context.rating,
            state.stability,
            state.difficulty,
            context.parameters.targetRetention,
            isNewLearning
        )
        state.stability = result.stability
        state.difficulty = result.difficulty
        state.scheduledIntervalDays = result.intervalDays()
        state.due = context.nowMillis + result.intervalMillis
        state.phase = RecordsBase.SchedulerPhase.REVIEW
        state.schedulerState = StudyLadderRules.STATE_REVIEW
    }

    private fun applyReviewTransition(context: ReviewContext, state: ReviewState) {
        when (context.rating) {
            StudyRatings.AGAIN -> applyReviewAgain(context, state)
            StudyRatings.HARD -> applyReviewPass(context, state)
            StudyRatings.EASY -> applyReviewPass(context, state)
            StudyRatings.GOOD -> applyReviewPass(context, state)
            else -> applyReviewPass(context, state)
        }
    }

    private fun applyReviewAgain(context: ReviewContext, state: ReviewState) {
        state.lapses++
        state.taskLapses++
        val result = fsrsAdapter.review(
            state.stability,
            state.difficulty,
            StudyRatings.AGAIN,
            context.elapsedReviewDays,
            context.parameters.targetRetention
        )
        state.stability = result.stability
        state.difficulty = result.difficulty

        val relearning = context.learningSettings.reviewStepsMinutes
        state.stepIndex = 0
        if (relearning.isEmpty()) {
            state.phase = RecordsBase.SchedulerPhase.REVIEW
            state.due = context.nowMillis + StudyLadderRules.DAY
            state.schedulerState = StudyLadderRules.STATE_REVIEW
            state.scheduledIntervalDays = 1
        } else {
            state.phase = RecordsBase.SchedulerPhase.RELEARNING
            state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(relearning[0])
            state.schedulerState = StudyLadderRules.STATE_LEARNING
            state.scheduledIntervalDays = 0
        }

        if (countsAsRealDue(context, state)) {
            state.realPassStreak = 0
            state.realAgainStreak++
            state.lastRealReviewDueAtMillis = context.item.dueAtMillis
            if (state.realAgainStreak >= context.settings.ladderDemotionFailStreak) {
                state.rung = StudyLadderRules.demoteRung(state.rung, context.item.hasSimilarKanji, context.ladder)
                state.realAgainStreak = 0
                state.realPassStreak = 0
            }
        }
    }

    private fun applyReviewPass(context: ReviewContext, state: ReviewState) {
        val result = fsrsAdapter.review(
            state.stability,
            state.difficulty,
            context.rating,
            context.elapsedReviewDays,
            context.parameters.targetRetention
        )
        state.stability = result.stability
        state.difficulty = result.difficulty
        state.scheduledIntervalDays = result.intervalDays()
        state.due = context.nowMillis + result.intervalMillis
        state.phase = RecordsBase.SchedulerPhase.REVIEW
        state.schedulerState = StudyLadderRules.STATE_REVIEW
        state.stepIndex = 0

        if (countsAsRealDue(context, state)) {
            state.realAgainStreak = 0
            state.realPassStreak++
            state.lastRealReviewDueAtMillis = context.item.dueAtMillis
            if (promotesByFsrsInterval(result, context.settings.ladderPromotionIntervalDays)) {
                state.rung = StudyLadderRules.promoteRung(state.rung, context.item.hasSimilarKanji, context.ladder)
                state.realPassStreak = 0
                state.realAgainStreak = 0
            }
        }
    }

    private fun promotesByFsrsInterval(result: KaniFsrsReviewResult, promotionDays: Int): Boolean {
        return result.intervalMillis > max(1, promotionDays).toLong() * StudyLadderRules.DAY
    }

    private fun countsAsRealDue(context: ReviewContext, state: ReviewState): Boolean {
        val currentDueSlot = context.item.dueAtMillis
        if (currentDueSlot > context.nowMillis) {
            return false
        }
        return state.lastRealReviewDueAtMillis == 0L || state.lastRealReviewDueAtMillis != currentDueSlot
    }

    private fun updateWritingLevel(context: ReviewContext, state: ReviewState) {
        if (context.rung != RecordsBase.LadderRung.WRITE_KANJI) {
            return
        }
        if (context.failedWriting) {
            state.writingLevel = max(0, state.writingLevel - 1)
        } else if (context.cleanWritingPass) {
            state.writingLevel = min(3, state.writingLevel + 1)
        }
    }

    private fun updatedStudyItem(context: ReviewContext, state: ReviewState): RecordsStudyModels.StudyItem {
        val updatedMemory = RecordsStudyModels.TaskMemory(
            state.schedulerState,
            state.due,
            roundScore(state.stability),
            roundScore(state.difficulty),
            state.taskTotal,
            state.taskLapses,
            state.stepIndex,
            context.rating,
            state.scheduledIntervalDays,
            taskMemoryConsecutivePasses(context, state),
            taskMemoryLastPassedDueAt(context, state)
        )
        val base = context.item.copyBuilder()
            .state(state.schedulerState)
            .dueAtMillis(state.due)
            .stability(roundScore(state.stability))
            .difficulty(roundScore(state.difficulty))
            .totalReviews(state.total)
            .lapses(state.lapses)
            .learningStep(state.stepIndex)
            .writingLevel(state.writingLevel)
            .recognitionStage(StudyLadderRules.rungToLegacyStage(state.rung))
            .writingRemediationPending(state.rung == RecordsBase.LadderRung.WRITE_KANJI)
            .consecutiveFailedRecognitionDays(state.realAgainStreak)
            .lastFailedRecognitionDayMillis(state.lastRealReviewDueAtMillis)
            .matureIntervalDays(state.scheduledIntervalDays)
            .rung(state.rung)
            .phase(state.phase)
            .realPassStreak(state.realPassStreak)
            .realAgainStreak(state.realAgainStreak)
            .lastRealReviewDueAtMillis(state.lastRealReviewDueAtMillis)
            .activeToken(null)
            .build()
        var updated = base.withTaskMemory(context.reviewedTaskType, updatedMemory)
        val activeTaskType = state.rung.wireName()
        if (activeTaskType != context.reviewedTaskType) {
            updated = updated.withTaskMemory(activeTaskType, updatedMemory)
        }
        return updated
    }

    private fun taskMemoryConsecutivePasses(context: ReviewContext, state: ReviewState): Int {
        return if (StudyRatings.AGAIN == context.rating) 0 else state.realPassStreak
    }

    private fun taskMemoryLastPassedDueAt(context: ReviewContext, state: ReviewState): Long {
        return if (StudyRatings.AGAIN == context.rating) 0L else state.lastRealReviewDueAtMillis
    }

    private class ReviewContext {
        lateinit var item: RecordsStudyModels.StudyItem
        lateinit var request: RecordsSchedulerModels.ReviewRequest
        lateinit var parameters: RecordsSchedulerModels.SchedulerParameters
        lateinit var settings: RecordsSyncModels.Settings
        lateinit var learningSettings: RecordsSchedulerModels.LearningStepSettings
        lateinit var ladder: RecordsBase.StudyLadderSettings
        lateinit var previousTaskMemory: RecordsStudyModels.TaskMemory
        lateinit var rung: RecordsBase.LadderRung
        lateinit var phase: RecordsBase.SchedulerPhase
        var nowMillis: Long = 0L
        var elapsedReviewDays: Int = 0
        lateinit var rating: String
        lateinit var reviewedTaskType: String
        var cleanWritingPass: Boolean = false
        var failedWriting: Boolean = false

        private fun elapsedReviewDays(): Int {
            val previousIntervalMillis = max(0L, previousTaskMemory.matureIntervalDays.toLong()) * StudyLadderRules.DAY
            val lastReviewAtMillis = max(0L, previousTaskMemory.dueAtMillis - previousIntervalMillis)
            val elapsedMillis = max(0L, nowMillis - lastReviewAtMillis)
            return min(Int.MAX_VALUE.toLong(), elapsedMillis / StudyLadderRules.DAY).toInt()
        }

        private fun activeTaskMemory(): RecordsStudyModels.TaskMemory {
            val memory = item.memoryForRung(rung)
            if (memory.totalReviews > 0 || item.totalReviews <= 0) {
                return memory
            }
            return RecordsStudyModels.TaskMemory.fromStudyFields(
                item.state,
                item.dueAtMillis,
                item.stability,
                item.difficulty,
                item.totalReviews,
                item.lapses,
                item.learningStep,
                item.matureIntervalDays
            )
        }

        companion object {
            fun from(
                item: RecordsStudyModels.StudyItem,
                request: RecordsSchedulerModels.ReviewRequest,
                parameters: RecordsSchedulerModels.SchedulerParameters,
                settings: RecordsSyncModels.Settings,
                learningSettings: RecordsSchedulerModels.LearningStepSettings,
                ladder: RecordsBase.StudyLadderSettings,
                nowMillis: Long
            ): ReviewContext {
                val context = ReviewContext()
                context.item = item
                context.request = request
                context.parameters = parameters
                context.settings = settings
                context.learningSettings = learningSettings
                context.ladder = StudyLadderRules.safeLadder(ladder)
                context.nowMillis = nowMillis
                context.rung = context.ladder.effectiveRung(item.rung, item.hasSimilarKanji)
                context.phase = item.phase
                context.reviewedTaskType = context.rung.wireName()
                context.previousTaskMemory = context.activeTaskMemory()
                context.elapsedReviewDays = context.elapsedReviewDays()
                context.rating = resolveRating(request, context.rung)
                val writingRung = context.rung == RecordsBase.LadderRung.WRITE_KANJI
                val writingReviewCanMoveHelp = writingRung && request.writingRequired && !request.manualOverride
                context.cleanWritingPass = writingReviewCanMoveHelp &&
                    request.writingPassed &&
                    request.writingClean &&
                    request.hintsUsed <= 0
                context.failedWriting = writingReviewCanMoveHelp && !request.writingPassed
                return context
            }

            private fun resolveRating(
                request: RecordsSchedulerModels.ReviewRequest,
                rung: RecordsBase.LadderRung
            ): String {
                if (rung == RecordsBase.LadderRung.WRITE_KANJI) {
                    if (request.manualOverride) {
                        return StudyRatings.HARD
                    }
                    if (request.writingRequired && !request.writingPassed) {
                        return StudyRatings.AGAIN
                    }
                } else if (request.writingRequired && !request.writingPassed && !request.manualOverride) {
                    return StudyRatings.AGAIN
                }
                return StudyRatings.normalize(request.rating)
            }
        }
    }

    private class ReviewState {
        var total: Int = 0
        var lapses: Int = 0
        var taskTotal: Int = 0
        var taskLapses: Int = 0
        var stepIndex: Int = 0
        var writingLevel: Int = 0
        var scheduledIntervalDays: Int = 0
        var realPassStreak: Int = 0
        var realAgainStreak: Int = 0
        var lastRealReviewDueAtMillis: Long = 0L
        var due: Long = 0L
        lateinit var rung: RecordsBase.LadderRung
        lateinit var phase: RecordsBase.SchedulerPhase
        var stability: Double = 0.0
        var difficulty: Double = 0.0
        lateinit var schedulerState: String

        companion object {
            fun from(context: ReviewContext): ReviewState {
                val state = ReviewState()
                state.total = context.item.totalReviews + 1
                state.lapses = context.item.lapses
                state.taskTotal = context.previousTaskMemory.totalReviews + 1
                state.taskLapses = context.previousTaskMemory.lapses
                state.stepIndex = context.previousTaskMemory.learningStep
                state.writingLevel = context.item.writingLevel
                state.rung = context.rung
                state.phase = context.phase
                state.realPassStreak = context.item.realPassStreak
                state.realAgainStreak = context.item.realAgainStreak
                state.lastRealReviewDueAtMillis = context.item.lastRealReviewDueAtMillis
                state.stability = context.previousTaskMemory.stability
                state.difficulty = context.previousTaskMemory.difficulty
                state.schedulerState = context.item.state
                return state
            }
        }
    }

    companion object {
        private fun roundScore(value: Double): Double = round(value * 100.0) / 100.0
    }
}
