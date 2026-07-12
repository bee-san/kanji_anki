package dev.bee.kanjianki.core

/** Compatibility and selection boundary for routing-v2 study items. */
object AdaptiveStudyItemPolicy {
    const val ROUTING_VERSION: Int = 2

    @JvmStatic
    fun isAdaptive(item: RecordsStudyModels.StudyItem?): Boolean =
        item != null && item.routingVersion >= ROUTING_VERSION

    @JvmStatic
    fun routeState(item: RecordsStudyModels.StudyItem?): AdaptiveRouteState? =
        if (isAdaptive(item)) AdaptiveRouteStateCodec.decode(item?.adaptiveRouteStateJson) else null

    /**
     * Legacy learning/relearning finishes unchanged. The first transition that
     * returns to review converts the item to a core anchor and copies the just-
     * exercised memory into that core without deleting any legacy slot.
     */
    @JvmStatic
    fun canonicalizeAfterLegacyTransition(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        if (isAdaptive(item) || item.phase != RecordsBase.SchedulerPhase.REVIEW) return item
        val core = AdaptiveCorePolicy.coreForRung(item.rung) ?: CoreSkill.RECOGNITION
        val activeMemory = item.memoryForRung(item.rung)
        val ownerTask = AdaptiveCorePolicy.memoryOwnerTaskType(core)
        val state = AdaptiveRouteState(
            activeCore = core,
            recognitionReviewCount = if (core == CoreSkill.RECOGNITION) {
                activeMemory.totalReviews
            } else {
                item.kanjiMeaningMemory.totalReviews
            },
            contextualReadingReviewCount = if (core == CoreSkill.CONTEXTUAL_READING) {
                activeMemory.totalReviews
            } else {
                item.wordReadingMemory.totalReviews
            },
        )
        return coreSurface(item, core, activeMemory, state)
            .withTaskMemory(ownerTask, activeMemory)
    }

    /**
     * A malformed v2 payload must not trap an item in an unrouteable relearning
     * state. Restore the nearest core memory without calling FSRS.
     */
    @JvmStatic
    fun recoverMalformedRouteState(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        if (!isAdaptive(item) || routeState(item) != null) return item
        val core = AdaptiveCorePolicy.coreForRung(item.rung) ?: CoreSkill.RECOGNITION
        val memory = item.memoryForTaskType(AdaptiveCorePolicy.memoryOwnerTaskType(core))
        val state = AdaptiveRouteState(
            activeCore = core,
            recognitionReviewCount = item.kanjiMeaningMemory.totalReviews,
            contextualReadingReviewCount = item.wordReadingMemory.totalReviews,
        )
        return coreSurface(item, core, memory, state)
    }

    @JvmStatic
    fun taskTypeFor(
        item: RecordsStudyModels.StudyItem,
        ladder: RecordsBase.StudyLadderSettings?,
    ): String {
        if (!isAdaptive(item)) return StudyTaskTypes.forRung(item.rung)
        val recovered = recoverMalformedRouteState(item)
        val state = routeState(recovered) ?: return StudyTaskTypes.forRung(recovered.rung)
        state.activeRepairTask()?.let { return it }
        val forcePlainCore = state.revalidationPending
        val alternateEnabled = when (state.activeCore) {
            CoreSkill.RECOGNITION -> ladder?.isEnabled(RecordsBase.LadderRung.FONT_MEANING) != false
            CoreSkill.CONTEXTUAL_READING -> ladder?.isEnabled(RecordsBase.LadderRung.SENTENCE_READING) != false
        }
        val alternateAvailable = when (state.activeCore) {
            CoreSkill.RECOGNITION -> true
            CoreSkill.CONTEXTUAL_READING -> recovered.hasSentenceReading
        }
        // Presentation cadence belongs to the core skill, not to the raw FSRS
        // memory review count. Recognition memory is cloned when the card first
        // unlocks contextual reading; using that cloned count could make the
        // first contextual check a sentence variant instead of the required
        // plain-word check.
        val completedReviews = state.reviewCount(state.activeCore)
        return AdaptivePresentationPolicy.taskType(
            AdaptivePresentationPolicy.variant(
                state.activeCore,
                completedReviews,
                alternateEnabled = alternateEnabled && !forcePlainCore,
                alternateAvailable = alternateAvailable,
            ),
        )
    }

    @JvmStatic
    fun coreMemory(item: RecordsStudyModels.StudyItem, core: CoreSkill): RecordsStudyModels.TaskMemory =
        item.memoryForTaskType(AdaptiveCorePolicy.memoryOwnerTaskType(core))

    @JvmStatic
    fun isContextualComplete(item: RecordsStudyModels.StudyItem): Boolean {
        val state = routeState(item) ?: return false
        return state.activeCore == CoreSkill.CONTEXTUAL_READING &&
            !state.isRepairActive() &&
            !state.revalidationPending &&
            item.phase == RecordsBase.SchedulerPhase.REVIEW &&
            state.contextualReadingReviewCount > 0 &&
            item.wordReadingMemory.consecutivePasses > 0
    }

    private fun coreSurface(
        item: RecordsStudyModels.StudyItem,
        core: CoreSkill,
        memory: RecordsStudyModels.TaskMemory,
        state: AdaptiveRouteState,
    ): RecordsStudyModels.StudyItem {
        val anchor = AdaptiveCorePolicy.memoryOwnerRung(core)
        return item.copyBuilder()
            .state(StudyLadderRules.STATE_REVIEW)
            .dueAtMillis(memory.dueAtMillis)
            .stability(memory.stability)
            .difficulty(memory.difficulty)
            .learningStep(0)
            .matureIntervalDays(memory.matureIntervalDays)
            .rung(anchor)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .realPassStreak(0)
            .realAgainStreak(0)
            .recognitionStage(StudyLadderRules.rungToLegacyStage(anchor))
            .writingRemediationPending(false)
            .activeToken(null)
            .routingVersion(ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(state))
            .build()
    }
}
