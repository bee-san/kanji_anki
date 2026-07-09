package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

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
        // Update the writing level before the ladder transition so the
        // write_kanji promotion gate (Goal 67) can see the current attempt's
        // effect. The reorder is behavior-neutral for every non-writing path;
        // only the write_kanji promotion condition reads writingLevel.
        updateWritingLevel(context, state)
        applyLadderTransition(context, state)
        val result = RecordsSchedulerModels.ReviewResult(updatedStudyItem(context, state), context.rating, false, "Review applied.")
        // Consume the idempotency token only after the review has fully applied, so a
        // failure mid-apply leaves the token unconsumed and the retry is not rejected as
        // a duplicate while the item was never updated.
        application.consumedTokens.add(application.request.token)
        return result
    }

    fun debugTraceApplyReview(application: BridgeScheduler.ReviewApplication): SchedulerTracedReviewResult {
        val ladder = StudyLadderRules.safeLadder(application.ladder)
        val beforeRung = ladder.effectiveRung(application.item.rung, application.item.hasSimilarKanji)
        val beforePhase = application.item.phase
        val duplicateReason = duplicateReason(application.item, application.request, application.consumedTokens)
        val result = applyReview(application)
        val reasonCodes = transitionReasonCodes(application, result, beforeRung, beforePhase, duplicateReason, ladder)
        val movementReason = movementReason(beforeRung, result.item.rung, result.appliedRating, duplicateReason, ladder)
        val transition = SchedulerReviewTransitionTrace(
            result.appliedRating,
            beforeRung,
            result.item.rung,
            movementReason,
            reasonCodes,
        )
        val fsrsCalls = fsrsCallTrace(beforePhase, result, duplicateReason)
        val trace = SchedulerDecisionTrace("apply_review", application.nowMillis, null, emptyList(), emptyList(), transition, fsrsCalls)
        return SchedulerTracedReviewResult(result, trace)
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

    private fun duplicateReason(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: Set<String>,
    ): String? {
        if (consumedTokens.contains(request.token)) {
            return "duplicate_token"
        }
        if (!item.activeToken.isNullOrEmpty() && item.activeToken != request.token) {
            return "token_mismatch"
        }
        return null
    }

    private fun transitionReasonCodes(
        application: BridgeScheduler.ReviewApplication,
        result: RecordsSchedulerModels.ReviewResult,
        beforeRung: RecordsBase.LadderRung,
        beforePhase: RecordsBase.SchedulerPhase,
        duplicateReason: String?,
        ladder: RecordsBase.StudyLadderSettings,
    ): List<String> {
        val reasons = ArrayList<String>()
        if (duplicateReason != null) {
            reasons.add(duplicateReason)
            return reasons
        }
        when (beforePhase) {
            RecordsBase.SchedulerPhase.REVIEW -> {
                if (StudyRatings.AGAIN == result.appliedRating) {
                    reasons.add("review_again_lapse")
                } else {
                    reasons.add("review_pass_fsrs_interval")
                }
            }
            RecordsBase.SchedulerPhase.NEW_LEARNING -> reasons.add("new_learning_step")
            RecordsBase.SchedulerPhase.RELEARNING -> reasons.add("relearning_step")
        }
        val beforeRank = ladder.rankForRung(beforeRung)
        val afterRank = ladder.rankForRung(result.item.rung)
        if (afterRank > beforeRank) {
            reasons.add("fsrs_interval_promotes")
            if (skipsSimilarRungWithoutContent(application.item.hasSimilarKanji, ladder, beforeRank, afterRank)) {
                reasons.add("similar_kanji_unavailable")
            }
        } else if (afterRank == beforeRank &&
            beforePhase == RecordsBase.SchedulerPhase.REVIEW &&
            StudyRatings.AGAIN != result.appliedRating &&
            intervalQualifiesForPromotion(application, result)
        ) {
            if (beforeRung == RecordsBase.LadderRung.WRITE_KANJI &&
                result.item.writingLevel < WRITE_KANJI_PROMOTION_MIN_LEVEL
            ) {
                reasons.add("promotion_blocked_writing_level")
            }
            if (promotionBlockedByMinPasses(application, result)) {
                reasons.add("promotion_blocked_min_passes")
            }
        } else if (afterRank < beforeRank && StudyRatings.AGAIN == result.appliedRating) {
            reasons.add("real_again_streak_threshold")
            if (skipsSimilarRungWithoutContent(application.item.hasSimilarKanji, ladder, beforeRank, afterRank)) {
                reasons.add("similar_kanji_unavailable")
            }
        }
        return reasons
    }

    private fun intervalQualifiesForPromotion(
        application: BridgeScheduler.ReviewApplication,
        result: RecordsSchedulerModels.ReviewResult,
    ): Boolean {
        // Diagnostic approximation for trace reason codes: the persisted
        // matureIntervalDays is the scheduled interval (equal to the
        // promotion-strength interval at the default 0.90 retention).
        val settings = application.settings ?: RecordsSyncModels.Settings.kikuDefaults()
        return result.item.matureIntervalDays > max(1, settings.ladderPromotionIntervalDays)
    }

    private fun promotionBlockedByMinPasses(
        application: BridgeScheduler.ReviewApplication,
        result: RecordsSchedulerModels.ReviewResult,
    ): Boolean {
        val settings = application.settings ?: RecordsSyncModels.Settings.kikuDefaults()
        return result.item.realPassStreak < settings.ladderPromotionMinPasses
    }

    private fun skipsSimilarRungWithoutContent(
        hasSimilarKanji: Boolean,
        ladder: RecordsBase.StudyLadderSettings,
        beforeRank: Int,
        afterRank: Int,
    ): Boolean {
        val similarRank = ladder.rankForRung(RecordsBase.LadderRung.SIMILAR_KANJI)
        return !hasSimilarKanji &&
            ladder.isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI) &&
            min(beforeRank, afterRank) < similarRank &&
            similarRank < max(beforeRank, afterRank)
    }

    private fun movementReason(
        beforeRung: RecordsBase.LadderRung,
        afterRung: RecordsBase.LadderRung,
        rating: String,
        duplicateReason: String?,
        ladder: RecordsBase.StudyLadderSettings,
    ): String {
        if (duplicateReason != null) {
            return "duplicate"
        }
        val beforeRank = ladder.rankForRung(beforeRung)
        val afterRank = ladder.rankForRung(afterRung)
        if (afterRank > beforeRank) {
            return "fsrs_interval_promotes"
        }
        if (afterRank < beforeRank && StudyRatings.AGAIN == rating) {
            return "again_streak_demotes"
        }
        if (afterRank < beforeRank) {
            return "rung_demotes"
        }
        return "rung_unchanged"
    }

    private fun fsrsCallTrace(
        beforePhase: RecordsBase.SchedulerPhase,
        result: RecordsSchedulerModels.ReviewResult,
        duplicateReason: String?,
    ): List<SchedulerFsrsCallTrace> {
        if (duplicateReason != null) {
            return emptyList()
        }
        if (beforePhase == RecordsBase.SchedulerPhase.REVIEW) {
            return listOf(SchedulerFsrsCallTrace("review", result.appliedRating, result.item.matureIntervalDays))
        }
        if (!calledInitialReviewFsrs(beforePhase, result)) {
            return emptyList()
        }
        val callType = "initial_review"
        return listOf(SchedulerFsrsCallTrace(callType, result.appliedRating, result.item.matureIntervalDays))
    }

    private fun calledInitialReviewFsrs(
        beforePhase: RecordsBase.SchedulerPhase,
        result: RecordsSchedulerModels.ReviewResult,
    ): Boolean {
        if (result.item.phase != RecordsBase.SchedulerPhase.REVIEW) {
            return false
        }
        return beforePhase == RecordsBase.SchedulerPhase.NEW_LEARNING || StudyRatings.AGAIN != result.appliedRating
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
        if (steps.isEmpty()) {
            applyEmptyLearningStepsTransition(context, state, isNewLearning)
            return
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

    private fun applyEmptyLearningStepsTransition(context: ReviewContext, state: ReviewState, isNewLearning: Boolean) {
        // With no steps configured, every rating leaves the learning phase and
        // reschedules from the FSRS memory state. For a relearning card this
        // is the post-lapse memory state, matching Anki's FSRS behavior; the
        // scheduler must never fall back to a fixed one-day interval.
        graduateToReview(context, state, isNewLearning)
    }

    private fun applyLearningHard(
        context: ReviewContext,
        state: ReviewState,
        steps: List<Int>,
        isNewLearning: Boolean
    ) {
        val idx = max(0, state.stepIndex)
        if (idx >= steps.size) {
            // The configured steps shrank while this card sat mid-learning.
            // Anki graduates a card whose step index is past the last step;
            // repeating a clamped step would trap it in learning until a
            // Good/Easy answer.
            graduateToReview(context, state, isNewLearning)
            return
        }
        if (idx == 0 && steps.size >= 2) {
            // Anki semantics: Hard on the first step waits the midpoint of the first two
            // steps, sitting strictly between Again (returns to step 0) and Good
            // (advances to step 1). Using the plain midpoint keeps that true even for
            // descending steps like [10, 5], where max(step0, midpoint) would collapse
            // Hard onto the Again delay.
            val avg = (StudyLadderRules.stepDelayMillis(steps[0]) + StudyLadderRules.stepDelayMillis(steps[1])) / 2L
            state.due = context.nowMillis + avg
        } else if (idx == 0) {
            // Anki semantics: with a single learning step, Hard waits 1.5x the
            // step delay so it sits strictly between Again and Good.
            state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps[0]) * 3L / 2L
        } else {
            state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps[idx])
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
            // No relearning steps: skip practice and reschedule straight from
            // the FSRS post-lapse memory state, matching Anki's FSRS behavior.
            state.phase = RecordsBase.SchedulerPhase.REVIEW
            state.due = context.nowMillis + result.intervalMillis
            state.schedulerState = StudyLadderRules.STATE_REVIEW
            state.scheduledIntervalDays = result.intervalDays()
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
            state.lastFailedRealReviewDueAtMillis = context.item.dueAtMillis
            if (state.realAgainStreak >= context.settings.ladderDemotionFailStreak) {
                val demoted = StudyLadderRules.demoteRung(state.rung, context.item.hasSimilarKanji, context.ladder)
                // Only reset the fail streak when a demotion actually moved the rung. At
                // the WRITE_KANJI floor demoteRung returns the same rung, so keeping the
                // streak lets chronically-failing floor cards keep reporting in
                // LadderHealthPolicy instead of silently resetting to zero.
                if (demoted != state.rung) {
                    state.rung = demoted
                    state.realAgainStreak = 0
                    state.realPassStreak = 0
                    if (relearning.isEmpty()) {
                        capDemotedRungFirstReview(context, state)
                    }
                }
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
            // A single qualifying interval is thin evidence that a distinct rung
            // skill is retired: after the 7-day promotion cap a promoted rung
            // inherits cloned stability above the threshold, so its very first
            // pass would re-promote. Require `ladderPromotionMinPasses` real-due
            // passes on the current rung so each skill earns at least that many
            // due-review credits before the ladder removes its practice.
            if (promotesByMemoryStrength(result, context.settings.ladderPromotionIntervalDays) &&
                state.realPassStreak >= context.settings.ladderPromotionMinPasses &&
                writingRungPromotionAllowed(state)
            ) {
                val promoted = StudyLadderRules.promoteRung(state.rung, context.item.hasSimilarKanji, context.ladder)
                if (promoted != state.rung) {
                    capPromotedRungFirstReview(context, state)
                }
                state.rung = promoted
                state.realPassStreak = 0
                state.realAgainStreak = 0
            }
        }
    }

    /**
     * A card may only leave the write_kanji rung once it has demonstrated
     * clean, hint-free handwriting: `writingLevel` rises only on clean
     * hint-free passes (`updateWritingLevel`) and falls on failures, so
     * requiring `writingLevel >= WRITE_KANJI_PROMOTION_MIN_LEVEL` (2) blocks a
     * chain of messy `CLOSE`/"Save hard" passes from promoting production out
     * of the writing rung without a clean write (Goal 67). Non-writing rungs
     * are unaffected.
     */
    private fun writingRungPromotionAllowed(state: ReviewState): Boolean {
        if (state.rung != RecordsBase.LadderRung.WRITE_KANJI) {
            return true
        }
        return state.writingLevel >= WRITE_KANJI_PROMOTION_MIN_LEVEL
    }

    /**
     * A promotion unlocks a different study skill, so its first test should
     * not wait out the full promotion-sized FSRS interval. Cap the first
     * review of the newly promoted rung at one third of the promotion
     * threshold (7 days at the 21-day default) while keeping the cloned FSRS
     * memory state intact.
     */
    private fun capPromotedRungFirstReview(context: ReviewContext, state: ReviewState) {
        val capDays = max(1, context.settings.ladderPromotionIntervalDays / PROMOTED_RUNG_FIRST_REVIEW_DIVISOR)
        if (state.scheduledIntervalDays > capDays) {
            state.scheduledIntervalDays = capDays
            state.due = context.nowMillis + capDays * StudyLadderRules.DAY
        }
    }

    /**
     * Demotion moves the card to a more-scaffolded rung, which doc §7.1 sells
     * as "the easier skill is practiced immediately" via the ~10-minute
     * relearning due. With an empty relearning list the lapse reschedules from
     * the FSRS post-lapse interval instead (days out), so the promise silently
     * depended on configuration. Mirror the promotion cap on the demotion side
     * (Goal 70): when a demotion actually moves the rung and no relearning step
     * will deliver the near-term practice, cap the first review of the newly
     * demoted rung at one day. With relearning steps configured the ~10-minute
     * step already delivers the promise and this cap is not applied.
     */
    private fun capDemotedRungFirstReview(context: ReviewContext, state: ReviewState) {
        val capMillis = context.nowMillis + DEMOTED_RUNG_FIRST_REVIEW_CAP_DAYS * StudyLadderRules.DAY
        if (state.due > capMillis) {
            state.due = capMillis
            state.scheduledIntervalDays = min(state.scheduledIntervalDays, DEMOTED_RUNG_FIRST_REVIEW_CAP_DAYS)
        }
    }

    /**
     * Promotion keys off the memory strength expressed as a fixed-0.90
     * retention interval, not the user-facing scheduled interval, so the
     * retention setting does not silently tune ladder progression speed
     * (Goal 64 / closed decision D4). At the 0.90 default this is identical
     * to the scheduled interval.
     */
    private fun promotesByMemoryStrength(result: KaniFsrsReviewResult, promotionDays: Int): Boolean {
        return result.promotionIntervalMillis > max(1, promotionDays).toLong() * StudyLadderRules.DAY
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
            state.stability,
            state.difficulty,
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
            .stability(state.stability)
            .difficulty(state.difficulty)
            .totalReviews(state.total)
            .lapses(state.lapses)
            .learningStep(state.stepIndex)
            .writingLevel(state.writingLevel)
            .recognitionStage(StudyLadderRules.rungToLegacyStage(state.rung))
            .writingRemediationPending(state.rung == RecordsBase.LadderRung.WRITE_KANJI)
            .consecutiveFailedRecognitionDays(state.realAgainStreak)
            .lastFailedRecognitionDayMillis(state.lastFailedRealReviewDueAtMillis)
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
        var lastFailedRealReviewDueAtMillis: Long = 0L
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
                state.lastFailedRealReviewDueAtMillis = context.item.lastFailedRecognitionDayMillis
                state.stability = context.previousTaskMemory.stability
                state.difficulty = context.previousTaskMemory.difficulty
                state.schedulerState = context.item.state
                return state
            }
        }
    }

    companion object {
        /**
         * Divisor applied to `ladder_promotion_interval_days` to derive the
         * first-review cap for a freshly promoted rung.
         */
        private const val PROMOTED_RUNG_FIRST_REVIEW_DIVISOR = 3

        /**
         * Minimum `writingLevel` (net clean, hint-free passes, 0-3) required to
         * promote off the write_kanji rung (Goal 67).
         */
        private const val WRITE_KANJI_PROMOTION_MIN_LEVEL = 2

        /**
         * Cap (in days) applied to a demoted rung's first review when the
         * relearning list is empty, so the more-scaffolded skill is still
         * practiced soon (Goal 70).
         */
        private const val DEMOTED_RUNG_FIRST_REVIEW_CAP_DAYS = 1
    }
}
