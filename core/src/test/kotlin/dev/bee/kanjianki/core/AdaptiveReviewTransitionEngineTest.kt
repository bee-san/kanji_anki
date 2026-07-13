package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveReviewTransitionEngineTest {
    private val ladder = RecordsBase.StudyLadderSettings.defaults()
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val parameters = RecordsSchedulerModels.SchedulerParameters.defaults()
    private val steps = RecordsSchedulerModels.LearningStepSettings.defaults()

    @Test
    fun legacyReviewCanonicalizesToCoreAndPreservesOldMemory() {
        val fontMemory = memory(totalReviews = 4, dueAt = NOW + StudyLadderRules.DAY)
        val legacy = baseItem(RecordsBase.LadderRung.FONT_MEANING)
            .withTaskMemory(StudyTaskTypes.FONT_MEANING, fontMemory)

        val converted = AdaptiveStudyItemPolicy.canonicalizeAfterLegacyTransition(legacy)

        assertEquals(AdaptiveStudyItemPolicy.ROUTING_VERSION, converted.routingVersion)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, converted.rung)
        assertEquals(fontMemory.encode(), converted.fontMeaningMemory.encode())
        assertEquals(fontMemory.encode(), converted.kanjiMeaningMemory.encode())
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(converted)!!.activeCore)
    }

    @Test
    fun coreFailCallsFsrsOnceAndStartsInlineRepair() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val item = adaptiveItem(
            AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION),
            hasSimilarKanji = true,
        )

        val transition = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        val route = AdaptiveStudyItemPolicy.routeState(transition.item)!!

        assertEquals(1, adapter.reviewCalls)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, transition.item.phase)
        assertEquals(StudyTaskTypes.SIMILAR_KANJI, route.activeRepairTask())
        assertEquals(1, transition.item.kanjiMeaningMemory.lapses)
        assertEquals(1, transition.item.lapses)
        assertEquals(NOW + 10 * 60_000L, transition.item.dueAtMillis)
    }

    @Test
    fun repairPassIsPracticeOnlyAndSchedulesCoreRevalidation() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val coreDue = NOW + 5 * StudyLadderRules.DAY
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI),
            repairStepMinutes = listOf(10),
            repairDueAtMillis = NOW,
            coreDueAtMillis = coreDue,
            recurringFailure = FailureKind.VISUAL_CONFUSION,
            recurringFailureCount = 1,
        )
        val item = adaptiveItem(route, hasSimilarKanji = true)
            .copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .state(StudyLadderRules.STATE_LEARNING)
            .dueAtMillis(NOW)
            .build()
        val reviewsBefore = item.totalReviews
        val lapsesBefore = item.lapses

        val transition = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("good", StudyTaskTypes.SIMILAR_KANJI, null),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        val afterRoute = AdaptiveStudyItemPolicy.routeState(transition.item)!!

        assertEquals(0, adapter.reviewCalls)
        assertEquals(reviewsBefore, transition.item.totalReviews)
        assertEquals(lapsesBefore, transition.item.lapses)
        assertTrue(afterRoute.revalidationPending)
        assertFalse(afterRoute.isRepairActive())
        assertEquals(NOW + StudyLadderRules.DAY, transition.item.dueAtMillis)
    }

    @Test
    fun sameIssueThresholdAddsEscalatedRepairWithoutExtraFsrsCalls() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            recurringFailure = FailureKind.VISUAL_CONFUSION,
            recurringFailureCount = settings.ladderDemotionFailStreak - 1,
            revalidationPending = true,
        )
        val item = adaptiveItem(route, hasSimilarKanji = true)

        val transition = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        val tasks = AdaptiveStudyItemPolicy.routeState(transition.item)!!.activeRepairTasks

        assertEquals(1, adapter.reviewCalls)
        assertEquals(listOf(StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.WRITE_KANJI), tasks)
    }

    @Test
    fun recognitionPromotesToContextualCoreButContextNeverDemotes() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30)
        val item = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION))
            .copyBuilder()
            .realPassStreak(settings.ladderPromotionMinPasses - 1)
            .build()

        val promoted = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("good", StudyTaskTypes.KANJI_MEANING, null),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item

        assertEquals(CoreSkill.CONTEXTUAL_READING, AdaptiveStudyItemPolicy.routeState(promoted)!!.activeCore)
        assertEquals(RecordsBase.LadderRung.WORD_READING, promoted.rung)
        assertEquals(0, promoted.realPassStreak)
        assertEquals(0, AdaptiveStudyItemPolicy.routeState(promoted)!!.contextualReadingReviewCount)
        assertEquals(7, promoted.wordReadingMemory.matureIntervalDays)
        assertEquals(NOW + 7 * StudyLadderRules.DAY, promoted.wordReadingMemory.dueAtMillis)
        assertEquals(StudyTaskTypes.WORD_READING, AdaptiveStudyItemPolicy.taskTypeFor(promoted, ladder))
    }

    @Test
    fun contextualVariantCountStartsAtZeroThenAdvancesIndependentlyOfClonedMemory() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30)
        val recognition = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION))
            .copyBuilder()
            .realPassStreak(settings.ladderPromotionMinPasses - 1)
            .hasSentenceReading(true)
            .build()
        val promoted = AdaptiveReviewTransitionEngine(adapter).apply(
            recognition,
            request("good", StudyTaskTypes.KANJI_MEANING, null),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item.copyBuilder().dueAtMillis(NOW).activeToken("token").build()

        val contextualPass = AdaptiveReviewTransitionEngine(adapter).apply(
            promoted,
            request("good", StudyTaskTypes.WORD_READING, null),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item

        assertEquals(1, AdaptiveStudyItemPolicy.routeState(contextualPass)!!.contextualReadingReviewCount)
        assertEquals(StudyTaskTypes.SENTENCE_READING, AdaptiveStudyItemPolicy.taskTypeFor(contextualPass, ladder))
    }

    @Test
    fun earlyCoreFailureDoesNotAdvanceRealDueRecurrenceOrStreak() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            recognitionReviewCount = 4,
            recurringFailure = FailureKind.VISUAL_CONFUSION,
            recurringFailureCount = 2,
        )
        val early = adaptiveItem(route, hasSimilarKanji = true)
            .copyBuilder()
            .dueAtMillis(NOW + 60_000L)
            .realAgainStreak(2)
            .build()

        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            early,
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.MEANING_UNKNOWN),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed)!!

        assertEquals(1, adapter.reviewCalls)
        assertEquals(FailureKind.VISUAL_CONFUSION, failedRoute.recurringFailure)
        assertEquals(2, failedRoute.recurringFailureCount)
        assertEquals(2, failed.realAgainStreak)
        assertEquals(5, failedRoute.recognitionReviewCount)
    }

    @Test
    fun cappedRevalidationStoresTheActualScheduledInterval() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION), hasSimilarKanji = true),
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val repairDue = failed.copyBuilder().dueAtMillis(NOW).activeToken("token").build()
        val revalidation = AdaptiveReviewTransitionEngine(adapter).apply(
            repairDue,
            request("good", StudyTaskTypes.SIMILAR_KANJI, null),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item

        assertEquals(1, revalidation.kanjiMeaningMemory.matureIntervalDays)
        assertEquals(NOW + StudyLadderRules.DAY, revalidation.kanjiMeaningMemory.dueAtMillis)
    }

    @Test
    fun longRepairKeepsTheCoreLapseAsTheIntervalAnchor() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION), hasSimilarKanji = true),
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val twoDaysLater = NOW + 2 * StudyLadderRules.DAY
        val delayedRepair = failed.copyBuilder().dueAtMillis(twoDaysLater).activeToken("token").build()

        val revalidation = AdaptiveReviewTransitionEngine(adapter).apply(
            delayedRepair,
            request("good", StudyTaskTypes.SIMILAR_KANJI, null),
            twoDaysLater,
            parameters,
            settings,
            steps,
            ladder,
        ).item

        assertEquals(NOW + 3 * StudyLadderRules.DAY, revalidation.kanjiMeaningMemory.dueAtMillis)
        assertEquals(3, revalidation.kanjiMeaningMemory.matureIntervalDays)
    }

    @Test
    fun revalidationUsesExactCoreReviewTimeWhenItsIntervalRoundsUp() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION), hasSimilarKanji = true),
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        assertEquals(NOW, failed.kanjiMeaningMemory.lastReviewedAtMillis)

        val repairAt = NOW + 2 * StudyLadderRules.DAY + StudyLadderRules.DAY / 2
        val delayedRepair = failed.copyBuilder().dueAtMillis(repairAt).activeToken("repair-token").build()
        val revalidation = AdaptiveReviewTransitionEngine(adapter).apply(
            delayedRepair,
            request("good", StudyTaskTypes.SIMILAR_KANJI, null),
            repairAt,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val revalidationAt = repairAt + StudyLadderRules.DAY
        assertEquals(revalidationAt, revalidation.dueAtMillis)
        assertEquals(4, revalidation.kanjiMeaningMemory.matureIntervalDays)
        assertEquals(NOW, revalidation.kanjiMeaningMemory.lastReviewedAtMillis)

        val reviewed = AdaptiveReviewTransitionEngine(adapter).apply(
            revalidation.copyBuilder().activeToken("revalidate-token").build(),
            request("good", StudyTaskTypes.KANJI_MEANING, null),
            revalidationAt,
            parameters,
            settings,
            steps,
            ladder,
        ).item

        assertEquals(3, adapter.elapsedDays)
        assertEquals(revalidationAt, reviewed.kanjiMeaningMemory.lastReviewedAtMillis)
    }

    @Test
    fun disabledTypeReadingIsNotSelectedAsReadingRepairFallback() {
        val disabled = ladder.withRepairTaskEnabled(StudyTaskTypes.TYPE_READING, false)
        val item = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING))

        val failed = AdaptiveReviewTransitionEngine(CountingAdapter(5, 5)).apply(
            item,
            request("again", StudyTaskTypes.WORD_READING, FailureKind.WRONG_READING),
            NOW,
            parameters,
            settings,
            steps,
            disabled,
        ).item

        val route = AdaptiveStudyItemPolicy.routeState(failed)!!
        assertTrue(route.activeRepairTasks.isEmpty())
        assertTrue(route.revalidationPending)
    }

    @Test
    fun malformedRouteRecoveryKeepsPersistedRevisionForReviewCas() {
        val malformed = baseItem(RecordsBase.LadderRung.WORD_READING)
            .copyBuilder()
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson("not-json")
            .schedulerRevision(8L)
            .build()

        val recovered = AdaptiveStudyItemPolicy.recoverMalformedRouteState(malformed)

        assertEquals(8L, recovered.schedulerRevision)
        assertEquals(CoreSkill.CONTEXTUAL_READING, AdaptiveStudyItemPolicy.routeState(recovered)!!.activeCore)
    }

    @Test
    fun unknownPersistedRepairRecoversToUnchangedCoreMemory() {
        val coreMemory = memory(totalReviews = 7, dueAt = NOW + 3 * StudyLadderRules.DAY)
        val malformed = baseItem(RecordsBase.LadderRung.WORD_READING)
            .withTaskMemory(StudyTaskTypes.WORD_READING, coreMemory)
            .copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .state(StudyLadderRules.STATE_LEARNING)
            .dueAtMillis(NOW + 10 * 60_000L)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(
                "{\"v\":1,\"c\":\"contextual_reading\",\"t\":[\"future_repair\"]," +
                    "\"d\":${NOW + 10 * 60_000L},\"o\":${coreMemory.dueAtMillis}}",
            )
            .schedulerRevision(8L)
            .activeToken("stale-repair-token")
            .build()

        assertNull(AdaptiveStudyItemPolicy.routeState(malformed))

        val recovered = AdaptiveStudyItemPolicy.recoverMalformedRouteState(malformed)
        val route = AdaptiveStudyItemPolicy.routeState(recovered)!!

        assertEquals(8L, recovered.schedulerRevision)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, recovered.phase)
        assertEquals(StudyLadderRules.STATE_REVIEW, recovered.state)
        assertEquals(coreMemory.encode(), recovered.wordReadingMemory.encode())
        assertEquals(coreMemory.dueAtMillis, recovered.dueAtMillis)
        assertEquals(CoreSkill.CONTEXTUAL_READING, route.activeCore)
        assertFalse(route.isRepairActive())
        assertEquals(StudyTaskTypes.WORD_READING, AdaptiveStudyItemPolicy.taskTypeFor(recovered, ladder))
        assertNull(recovered.activeToken)
    }

    private fun adaptiveItem(
        route: AdaptiveRouteState,
        hasSimilarKanji: Boolean = false,
    ): RecordsStudyModels.StudyItem {
        val owner = AdaptiveCorePolicy.memoryOwnerTaskType(route.activeCore)
        return baseItem(AdaptiveCorePolicy.memoryOwnerRung(route.activeCore))
            .withTaskMemory(owner, memory(totalReviews = 4, dueAt = NOW - 1L))
            .copyBuilder()
            .hasSimilarKanji(hasSimilarKanji)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .activeToken("token")
            .build()
    }

    private fun baseItem(rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem("脱", StudyLadderRules.STATE_REVIEW, NOW - 1L, 4.0, 5.0, 4, 0, 0, 1, null, NOW - 1000L)
            .copyBuilder()
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(4)
            .build()

    private fun memory(totalReviews: Int, dueAt: Long) = RecordsStudyModels.TaskMemory(
        StudyLadderRules.STATE_REVIEW,
        dueAt,
        4.0,
        5.0,
        totalReviews,
        0,
        0,
        "good",
        4,
        0,
        0L,
    )

    private fun request(
        rating: String,
        taskType: String,
        failure: FailureKind?,
    ): RecordsSchedulerModels.ReviewRequest {
        val evidence = AnswerEvidence(
            coreSkill = AdaptiveCorePolicy.coreForTaskType(taskType),
            failureKind = failure,
            evidenceSource = if (failure == null) null else EvidenceSource.SELF_REPORT,
            renderedExpression = "脱出",
            renderedReading = "だっしゅつ",
        )
        return RecordsSchedulerModels.ReviewRequest(
            "脱", "token", rating, false, false,
            false, false, 0, taskType, "", "",
        ).withEvidence(
            RecordsSchedulerModels.ReviewRequest.ReviewEvidence(
                evidence.coreSkill?.wireName(),
                evidence.failureKind?.wireName(),
                evidence.evidenceSource?.wireName(),
                evidence.selectedAnswer,
                evidence.correctAnswer,
                AnswerEvidenceCodec.encode(evidence),
            ),
        )
    }

    private class CountingAdapter(
        private val intervalDays: Int,
        private val promotionDays: Int,
    ) : KaniFsrsAdapter {
        var reviewCalls = 0
        var elapsedDays = -1

        override fun initialReview(
            rating: String?,
            currentStability: Double,
            currentDifficulty: Double,
            targetRetention: Double,
            isNewLearning: Boolean,
        ): KaniFsrsReviewResult = KaniFsrsReviewResult(currentStability, currentDifficulty, StudyLadderRules.DAY)

        override fun review(
            stability: Double,
            difficulty: Double,
            rating: String?,
            elapsedDays: Int,
            targetRetention: Double,
        ): KaniFsrsReviewResult {
            reviewCalls++
            this.elapsedDays = elapsedDays
            return KaniFsrsReviewResult(
                stability + 1.0,
                difficulty,
                intervalDays * StudyLadderRules.DAY,
                promotionDays * StudyLadderRules.DAY,
            )
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
