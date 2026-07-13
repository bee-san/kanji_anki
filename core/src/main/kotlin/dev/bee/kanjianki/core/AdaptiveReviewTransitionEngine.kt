package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

/**
 * Routing-v2 transition engine. Only the two core memories call FSRS; repair
 * appearances mutate inline route state and due time only.
 */
internal class AdaptiveReviewTransitionEngine(private val fsrs: KaniFsrsAdapter) {
    data class Transition(
        val item: RecordsStudyModels.StudyItem,
        val appliedRating: String,
        val fsrsCalled: Boolean,
    )

    fun apply(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters,
        settings: RecordsSyncModels.Settings,
        learningSettings: RecordsSchedulerModels.LearningStepSettings,
        ladder: RecordsBase.StudyLadderSettings,
    ): Transition {
        val recovered = AdaptiveStudyItemPolicy.recoverMalformedRouteState(item)
        val route = AdaptiveStudyItemPolicy.routeState(recovered)
            ?: error("routing-v2 item could not recover adaptive state")
        return if (route.isRepairActive()) {
            applyRepair(recovered, request, route, nowMillis)
        } else {
            val rating = StudyRatings.normalize(request.rating)
            Transition(
                applyCore(recovered, request, route, nowMillis, parameters, settings, learningSettings, ladder),
                rating,
                true,
            )
        }
    }

    private fun applyCore(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        route: AdaptiveRouteState,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters,
        settings: RecordsSyncModels.Settings,
        learningSettings: RecordsSchedulerModels.LearningStepSettings,
        ladder: RecordsBase.StudyLadderSettings,
    ): RecordsStudyModels.StudyItem {
        val core = route.activeCore
        val ownerTask = AdaptiveCorePolicy.memoryOwnerTaskType(core)
        val beforeMemory = usableCoreMemory(item, core)
        val rating = StudyRatings.normalize(request.rating)
        val realDue = countsAsRealDue(item, nowMillis)
        val result = fsrs.review(
            beforeMemory.stability,
            beforeMemory.difficulty,
            rating,
            elapsedReviewDays(beforeMemory, nowMillis),
            parameters.targetRetention,
        )
        val nextTotalReviews = beforeMemory.totalReviews + 1
        val nextItemTotalReviews = item.totalReviews + 1
        val nextCoreReviewCount = route.reviewCount(core) + 1
        val evidence = evidenceFor(request, core)

        if (rating == StudyRatings.AGAIN) {
            val nextLapses = beforeMemory.lapses + 1
            val coreDue = nowMillis + result.intervalMillis
            val postLapseMemory = RecordsStudyModels.TaskMemory(
                StudyLadderRules.STATE_REVIEW,
                coreDue,
                result.stability,
                result.difficulty,
                nextTotalReviews,
                nextLapses,
                0,
                StudyRatings.AGAIN,
                result.intervalDays(),
                0,
                0L,
                nowMillis,
            )
            val recurrence = AdaptiveRepairPolicy.recordFailure(
                AdaptiveRepairPolicy.FailureRecurrence(route.recurringFailure, route.recurringFailureCount),
                evidence.failureKind ?: defaultFailure(core),
                realDueCoreOrRevalidation = realDue,
            )
            val repair = repairPlan(item, evidence, core, recurrence, settings, ladder)
            val schedule = AdaptiveRepairPolicy.schedule(repair.taskTypes, learningSettings.reviewStepsMinutes)
            val nextRoute = route.copy(
                recognitionReviewCount = if (core == CoreSkill.RECOGNITION) nextCoreReviewCount else route.recognitionReviewCount,
                contextualReadingReviewCount = if (core == CoreSkill.CONTEXTUAL_READING) nextCoreReviewCount else route.contextualReadingReviewCount,
                activeRepairTasks = schedule.taskTypes,
                repairTaskIndex = 0,
                repairStepMinutes = schedule.delayMinutes,
                repairDueAtMillis = if (schedule.taskTypes.isEmpty()) 0L else nowMillis + stepDelay(schedule.delayMinutes.first()),
                coreDueAtMillis = coreDue,
                recurringFailure = recurrence.kind,
                recurringFailureCount = recurrence.count,
                repairAttemptCount = 0,
                repairStartedAtMillis = nowMillis,
                revalidationPending = schedule.taskTypes.isEmpty(),
                answerEvidence = evidence,
            )
            val due = if (schedule.taskTypes.isEmpty()) {
                min(coreDue, nowMillis + StudyLadderRules.DAY)
            } else {
                nextRoute.repairDueAtMillis
            }
            val phase = if (schedule.taskTypes.isEmpty()) {
                RecordsBase.SchedulerPhase.REVIEW
            } else {
                RecordsBase.SchedulerPhase.RELEARNING
            }
            val persistedCoreMemory = if (schedule.taskTypes.isEmpty()) {
                postLapseMemory.withSchedule(due, scheduledIntervalDays(nowMillis, due))
            } else {
                postLapseMemory
            }
            return updateItem(
                item = item.withTaskMemory(ownerTask, persistedCoreMemory),
                memory = persistedCoreMemory,
                route = nextRoute,
                dueAtMillis = due,
                phase = phase,
                totalReviews = nextItemTotalReviews,
                lapses = item.lapses + 1,
                realPassStreak = 0,
                realAgainStreak = if (realDue) item.realAgainStreak + 1 else item.realAgainStreak,
                lastRealDue = if (realDue) item.dueAtMillis else item.lastRealReviewDueAtMillis,
                writingLevel = item.writingLevel,
            )
        }

        val passStreak = if (realDue) item.realPassStreak + 1 else item.realPassStreak
        var memory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            nowMillis + result.intervalMillis,
            result.stability,
            result.difficulty,
            nextTotalReviews,
            beforeMemory.lapses,
            0,
            rating,
            result.intervalDays(),
            passStreak,
            if (realDue) item.dueAtMillis else beforeMemory.lastPassedDueAtMillis,
            nowMillis,
        )
        var nextCore = core
        var nextPassStreak = passStreak
        var nextItem = item
        if (core == CoreSkill.RECOGNITION &&
            realDue &&
            result.promotionIntervalMillis > settings.ladderPromotionIntervalDays.toLong() * StudyLadderRules.DAY &&
            passStreak >= settings.ladderPromotionMinPasses
        ) {
            nextCore = CoreSkill.CONTEXTUAL_READING
            nextPassStreak = 0
            val capDays = max(1, settings.ladderPromotionIntervalDays / PROMOTION_REVALIDATION_DIVISOR)
            if (memory.matureIntervalDays > capDays) {
                memory = memory.withSchedule(nowMillis + capDays * StudyLadderRules.DAY, capDays)
            }
            nextItem = nextItem.withTaskMemory(AdaptiveCorePolicy.memoryOwnerTaskType(nextCore), memory)
        }
        nextItem = nextItem.withTaskMemory(ownerTask, memory)
        val nextRoute = route.copy(
            activeCore = nextCore,
            recognitionReviewCount = if (core == CoreSkill.RECOGNITION) nextCoreReviewCount else route.recognitionReviewCount,
            contextualReadingReviewCount = if (core == CoreSkill.CONTEXTUAL_READING) nextCoreReviewCount else route.contextualReadingReviewCount,
            activeRepairTasks = emptyList(),
            repairTaskIndex = 0,
            repairStepMinutes = emptyList(),
            repairDueAtMillis = 0L,
            coreDueAtMillis = 0L,
            recurringFailure = if (route.revalidationPending) null else route.recurringFailure,
            recurringFailureCount = if (route.revalidationPending) 0 else route.recurringFailureCount,
            repairAttemptCount = 0,
            repairStartedAtMillis = 0L,
            revalidationPending = false,
            answerEvidence = null,
        )
        return updateItem(
            item = nextItem,
            memory = memory,
            route = nextRoute,
            dueAtMillis = memory.dueAtMillis,
            phase = RecordsBase.SchedulerPhase.REVIEW,
            totalReviews = nextItemTotalReviews,
            lapses = item.lapses,
            realPassStreak = nextPassStreak,
            realAgainStreak = 0,
            lastRealDue = if (realDue) item.dueAtMillis else item.lastRealReviewDueAtMillis,
            writingLevel = item.writingLevel,
        )
    }

    private fun applyRepair(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        route: AdaptiveRouteState,
        nowMillis: Long,
    ): Transition {
        val taskType = route.activeRepairTask() ?: error("repair transition requires an active task")
        var writingLevel = item.writingLevel
        var rating = repairRating(request, taskType)
        if (taskType == StudyTaskTypes.WRITE_KANJI) {
            writingLevel = when {
                rating == StudyRatings.AGAIN -> max(0, writingLevel - 1)
                request.writingPassed && request.writingClean && request.hintsUsed <= 0 -> min(3, writingLevel + 1)
                else -> writingLevel
            }
            if (rating == StudyRatings.GOOD && writingLevel < WRITING_REPAIR_CLEAN_LEVEL) {
                rating = StudyRatings.HARD
            }
        }
        val nextIndex = AdaptiveRepairPolicy.nextTaskIndex(
            route.repairTaskIndex,
            route.activeRepairTasks.size,
            rating,
        )
        val coreMemory = AdaptiveStudyItemPolicy.coreMemory(item, route.activeCore)
        if (nextIndex >= route.activeRepairTasks.size) {
            val coreDue = route.coreDueAtMillis.takeIf { it > 0L } ?: coreMemory.dueAtMillis
            val validationDue = min(coreDue, nowMillis + StudyLadderRules.DAY)
            val validationMemory = coreMemory.withSchedule(
                validationDue,
                intervalDaysPreservingLastReview(coreMemory, validationDue),
            )
            val nextRoute = route.copy(
                activeRepairTasks = emptyList(),
                repairTaskIndex = 0,
                repairStepMinutes = emptyList(),
                repairDueAtMillis = 0L,
                revalidationPending = true,
                repairAttemptCount = route.repairAttemptCount + 1,
            )
            return Transition(updateItem(
                item.withTaskMemory(AdaptiveCorePolicy.memoryOwnerTaskType(route.activeCore), validationMemory),
                validationMemory,
                nextRoute,
                validationDue,
                RecordsBase.SchedulerPhase.REVIEW,
                item.totalReviews,
                item.lapses,
                item.realPassStreak,
                item.realAgainStreak,
                item.lastRealReviewDueAtMillis,
                writingLevel,
            ), rating, false)
        }
        val delay = route.repairStepMinutes.getOrElse(nextIndex) {
            route.repairStepMinutes.lastOrNull() ?: AdaptiveRepairPolicy.SYNTHETIC_REPAIR_STEP_MINUTES
        }
        val nextDue = nowMillis + stepDelay(delay)
        val nextRoute = route.copy(
            repairTaskIndex = nextIndex,
            repairDueAtMillis = nextDue,
            repairAttemptCount = route.repairAttemptCount + 1,
        )
        return Transition(updateItem(
            item,
            coreMemory,
            nextRoute,
            nextDue,
            RecordsBase.SchedulerPhase.RELEARNING,
            item.totalReviews,
            item.lapses,
            item.realPassStreak,
            item.realAgainStreak,
            item.lastRealReviewDueAtMillis,
            writingLevel,
        ), rating, false)
    }

    private fun repairPlan(
        item: RecordsStudyModels.StudyItem,
        evidence: AnswerEvidence,
        core: CoreSkill,
        recurrence: AdaptiveRepairPolicy.FailureRecurrence,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings,
    ): AdaptiveRepairPolicy.RepairPlan {
        val enabled = ladder.enabledRepairTaskTypes.toSet()
        val available = linkedSetOf(
            StudyTaskTypes.MEANING_KANJI,
            StudyTaskTypes.TYPE_MEANING,
            StudyTaskTypes.WRITE_KANJI,
        )
        if (item.hasSimilarKanji) available += StudyTaskTypes.SIMILAR_KANJI
        if (item.hasKanjiReading && evidence.correctAnswer.isNotBlank()) available += StudyTaskTypes.KANJI_READING
        if (item.hasReadingKanji && evidence.correctAnswer.isNotBlank()) available += StudyTaskTypes.READING_KANJI
        if (evidence.renderedReading.isNotBlank()) available += StudyTaskTypes.TYPE_READING
        return AdaptiveRepairPolicy.select(
            AdaptiveRepairPolicy.RepairRequest(
                coreSkill = core,
                failureKind = evidence.failureKind ?: defaultFailure(core),
                sameIssueCount = recurrence.count.coerceAtLeast(1),
                escalationThreshold = settings.ladderDemotionFailStreak,
                enabledTaskTypes = enabled,
                availableTaskTypes = available,
                priorityTaskTypes = ladder.repairTaskOrder,
            ),
        )
    }

    private fun evidenceFor(request: RecordsSchedulerModels.ReviewRequest, core: CoreSkill): AnswerEvidence {
        val decoded = AnswerEvidenceCodec.decode(request.answerEvidenceJson)
        val requestedFailure = decoded?.failureKind ?: FailureKind.fromWireName(request.failureCause)
        return (decoded ?: AnswerEvidence()).copy(
            coreSkill = core,
            failureKind = normalizedFailure(core, requestedFailure),
            evidenceSource = decoded?.evidenceSource ?: EvidenceSource.fromWireName(request.evidenceSource),
            selectedAnswer = decoded?.selectedAnswer?.ifEmpty { request.selectedAnswer } ?: request.selectedAnswer,
            correctAnswer = decoded?.correctAnswer?.ifEmpty { request.correctAnswer } ?: request.correctAnswer,
        )
    }

    private fun updateItem(
        item: RecordsStudyModels.StudyItem,
        memory: RecordsStudyModels.TaskMemory,
        route: AdaptiveRouteState,
        dueAtMillis: Long,
        phase: RecordsBase.SchedulerPhase,
        totalReviews: Int,
        lapses: Int,
        realPassStreak: Int,
        realAgainStreak: Int,
        lastRealDue: Long,
        writingLevel: Int,
    ): RecordsStudyModels.StudyItem {
        val anchor = AdaptiveCorePolicy.memoryOwnerRung(route.activeCore)
        return item.copyBuilder()
            .state(if (phase == RecordsBase.SchedulerPhase.RELEARNING) StudyLadderRules.STATE_LEARNING else StudyLadderRules.STATE_REVIEW)
            .dueAtMillis(dueAtMillis)
            .stability(memory.stability)
            .difficulty(memory.difficulty)
            .totalReviews(totalReviews)
            .lapses(lapses)
            .learningStep(if (phase == RecordsBase.SchedulerPhase.RELEARNING) route.repairTaskIndex else 0)
            .writingLevel(writingLevel)
            .recognitionStage(StudyLadderRules.rungToLegacyStage(anchor))
            .writingRemediationPending(false)
            .matureIntervalDays(if (phase == RecordsBase.SchedulerPhase.RELEARNING) 0 else memory.matureIntervalDays)
            .rung(anchor)
            .phase(phase)
            .realPassStreak(realPassStreak)
            .realAgainStreak(realAgainStreak)
            .lastRealReviewDueAtMillis(lastRealDue)
            .activeToken(null)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
    }

    private fun usableCoreMemory(item: RecordsStudyModels.StudyItem, core: CoreSkill): RecordsStudyModels.TaskMemory {
        val memory = AdaptiveStudyItemPolicy.coreMemory(item, core)
        if (memory.totalReviews > 0 || item.totalReviews <= 0) return memory
        return RecordsStudyModels.TaskMemory.fromStudyFields(
            item.state,
            item.dueAtMillis,
            item.stability,
            item.difficulty,
            item.totalReviews,
            item.lapses,
            item.learningStep,
            item.matureIntervalDays,
        )
    }

    private fun countsAsRealDue(item: RecordsStudyModels.StudyItem, nowMillis: Long): Boolean =
        item.dueAtMillis <= nowMillis &&
            (item.lastRealReviewDueAtMillis == 0L || item.lastRealReviewDueAtMillis != item.dueAtMillis)

    private fun elapsedReviewDays(memory: RecordsStudyModels.TaskMemory, nowMillis: Long): Int {
        val previousIntervalMillis = max(0L, memory.matureIntervalDays.toLong()) * StudyLadderRules.DAY
        val lastReviewAt = memory.lastReviewedAtMillis.takeIf { it > 0L }
            ?: max(0L, memory.dueAtMillis - previousIntervalMillis)
        return min(Int.MAX_VALUE.toLong(), max(0L, nowMillis - lastReviewAt) / StudyLadderRules.DAY).toInt()
    }

    private fun repairRating(request: RecordsSchedulerModels.ReviewRequest, taskType: String): String {
        if (taskType == StudyTaskTypes.WRITE_KANJI) {
            if (request.manualOverride) return StudyRatings.HARD
            if (request.writingRequired && !request.writingPassed) return StudyRatings.AGAIN
        }
        return StudyRatings.normalize(request.rating)
    }

    private fun defaultFailure(core: CoreSkill): FailureKind = when (core) {
        CoreSkill.RECOGNITION -> FailureKind.UNKNOWN
        CoreSkill.CONTEXTUAL_READING -> FailureKind.WRONG_READING
    }

    private fun normalizedFailure(core: CoreSkill, failure: FailureKind?): FailureKind {
        val compatible = when (core) {
            CoreSkill.RECOGNITION -> failure in setOf(
                FailureKind.MEANING_UNKNOWN,
                FailureKind.VISUAL_CONFUSION,
                FailureKind.WRITING_SHAPE,
                FailureKind.UNKNOWN,
            )
            CoreSkill.CONTEXTUAL_READING -> failure in setOf(
                FailureKind.WRONG_READING,
                FailureKind.HOMOPHONE_CONFUSION,
                FailureKind.UNKNOWN,
            )
        }
        return if (compatible) failure!! else defaultFailure(core)
    }

    private fun RecordsStudyModels.TaskMemory.withSchedule(
        dueAtMillis: Long,
        intervalDays: Int,
    ): RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
        state,
        dueAtMillis,
        stability,
        difficulty,
        totalReviews,
        lapses,
        learningStep,
        lastRating,
        intervalDays,
        consecutivePasses,
        lastPassedDueAtMillis,
        lastReviewedAtMillis,
    )

    private fun scheduledIntervalDays(nowMillis: Long, dueAtMillis: Long): Int {
        val intervalMillis = max(1L, dueAtMillis - nowMillis)
        return min(Int.MAX_VALUE.toLong(), (intervalMillis + StudyLadderRules.DAY - 1L) / StudyLadderRules.DAY).toInt()
    }

    private fun intervalDaysPreservingLastReview(
        memory: RecordsStudyModels.TaskMemory,
        dueAtMillis: Long,
    ): Int {
        val priorIntervalMillis = max(0L, memory.matureIntervalDays.toLong()) * StudyLadderRules.DAY
        val lastReviewAt = memory.lastReviewedAtMillis.takeIf { it > 0L }
            ?: max(0L, memory.dueAtMillis - priorIntervalMillis)
        return scheduledIntervalDays(lastReviewAt, dueAtMillis)
    }

    private fun stepDelay(minutes: Int): Long = StudyLadderRules.stepDelayMillis(minutes.coerceAtLeast(1))

    private companion object {
        const val PROMOTION_REVALIDATION_DIVISOR = 3
        const val WRITING_REPAIR_CLEAN_LEVEL = 2
    }
}
