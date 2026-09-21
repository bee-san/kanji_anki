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
        // The contextual core starts from FSRS's initial state for a first Good
        // (the fake adapter reports a one-day initial interval), not from a clone
        // of recognition's 30-day memory.
        assertEquals(1, adapter.initialReviewCalls)
        assertEquals(0, promoted.wordReadingMemory.totalReviews)
        assertEquals(0, promoted.wordReadingMemory.consecutivePasses)
        assertEquals(1, promoted.wordReadingMemory.matureIntervalDays)
        assertEquals(NOW + StudyLadderRules.DAY, promoted.wordReadingMemory.dueAtMillis)
        assertEquals(NOW + StudyLadderRules.DAY, promoted.dueAtMillis)
        // Recognition keeps its real, uncapped FSRS schedule.
        assertEquals(30, promoted.kanjiMeaningMemory.matureIntervalDays)
        assertEquals(5, promoted.kanjiMeaningMemory.totalReviews)
        assertEquals(StudyTaskTypes.WORD_READING, AdaptiveStudyItemPolicy.taskTypeFor(promoted, ladder))
    }

    @Test
    fun promotionCapsTheSeededContextualFirstCheck() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30, initialIntervalDays = 40)
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

        val capDays = settings.ladderPromotionIntervalDays / 3
        assertEquals(capDays, promoted.wordReadingMemory.matureIntervalDays)
        assertEquals(NOW + capDays * StudyLadderRules.DAY, promoted.wordReadingMemory.dueAtMillis)
        // Difficulty is the kanji's learned difficulty; stability is the fresh seed.
        assertEquals(5.0, promoted.wordReadingMemory.difficulty, 0.0)
        assertEquals(CountingAdapter.INITIAL_STABILITY, promoted.wordReadingMemory.stability, 0.0)
    }

    @Test
    fun revalidationPassDoesNotCountTowardPromotion() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30)
        val route = AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION, revalidationPending = true)
        val item = adaptiveItem(route)
            .copyBuilder()
            .realPassStreak(settings.ladderPromotionMinPasses - 1)
            .build()

        val passed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("good", StudyTaskTypes.KANJI_MEANING, null),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val passedRoute = AdaptiveStudyItemPolicy.routeState(passed)!!

        assertEquals(1, adapter.reviewCalls)
        assertFalse(passedRoute.revalidationPending)
        assertEquals(CoreSkill.RECOGNITION, passedRoute.activeCore)
        assertEquals(settings.ladderPromotionMinPasses - 1, passed.realPassStreak)
        assertEquals(settings.ladderPromotionMinPasses - 1, passed.kanjiMeaningMemory.consecutivePasses)
        // The due slot was still consumed.
        assertEquals(item.dueAtMillis, passed.lastRealReviewDueAtMillis)
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

    @Test
    fun corePassSaturatesReviewCountersAndDueTime() {
        val now = Long.MAX_VALUE - 1L
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            contextualReadingReviewCount = Int.MAX_VALUE,
        )
        val item = adaptiveItem(route)
            .withTaskMemory(StudyTaskTypes.WORD_READING, saturatedMemory(now - 1L))
            .copyBuilder()
            .dueAtMillis(now - 1L)
            .totalReviews(Int.MAX_VALUE)
            .lapses(Int.MAX_VALUE)
            .realPassStreak(Int.MAX_VALUE)
            .build()
        val recognitionMemory = item.kanjiMeaningMemory
        val adapter = CountingAdapter(5, 5)

        val reviewed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("good", StudyTaskTypes.WORD_READING, null),
            now,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val reviewedRoute = AdaptiveStudyItemPolicy.routeState(reviewed)!!

        assertEquals(1, adapter.reviewCalls)
        assertEquals(recognitionMemory, reviewed.kanjiMeaningMemory)
        assertEquals(Long.MAX_VALUE, reviewed.dueAtMillis)
        assertEquals(Long.MAX_VALUE, reviewed.wordReadingMemory.dueAtMillis)
        assertEquals(Int.MAX_VALUE, reviewed.totalReviews)
        assertEquals(Int.MAX_VALUE, reviewed.wordReadingMemory.totalReviews)
        assertEquals(Int.MAX_VALUE, reviewed.lapses)
        assertEquals(Int.MAX_VALUE, reviewed.wordReadingMemory.lapses)
        assertEquals(Int.MAX_VALUE, reviewed.realPassStreak)
        assertEquals(0, reviewedRoute.recognitionReviewCount)
        assertEquals(Int.MAX_VALUE, reviewedRoute.contextualReadingReviewCount)
    }

    @Test
    fun coreFailSaturatesLapseRecurrenceAndRepairDueTime() {
        val now = Long.MAX_VALUE - 1L
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            recognitionReviewCount = Int.MAX_VALUE,
            recurringFailure = FailureKind.VISUAL_CONFUSION,
            recurringFailureCount = Int.MAX_VALUE,
            revalidationPending = true,
        )
        val item = adaptiveItem(route, hasSimilarKanji = true)
            .withTaskMemory(StudyTaskTypes.KANJI_MEANING, saturatedMemory(now - 1L))
            .copyBuilder()
            .dueAtMillis(now - 1L)
            .totalReviews(Int.MAX_VALUE)
            .lapses(Int.MAX_VALUE)
            .realAgainStreak(Int.MAX_VALUE)
            .build()
        val contextualMemory = item.wordReadingMemory
        val adapter = CountingAdapter(5, 5)

        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
            now,
            parameters,
            settings,
            steps,
            ladder,
        ).item
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed)!!

        assertEquals(1, adapter.reviewCalls)
        assertEquals(contextualMemory, failed.wordReadingMemory)
        assertEquals(Long.MAX_VALUE, failed.dueAtMillis)
        assertEquals(Long.MAX_VALUE, failedRoute.repairDueAtMillis)
        assertEquals(Long.MAX_VALUE, failedRoute.coreDueAtMillis)
        assertEquals(Int.MAX_VALUE, failed.totalReviews)
        assertEquals(Int.MAX_VALUE, failed.kanjiMeaningMemory.totalReviews)
        assertEquals(Int.MAX_VALUE, failed.lapses)
        assertEquals(Int.MAX_VALUE, failed.kanjiMeaningMemory.lapses)
        assertEquals(Int.MAX_VALUE, failed.realAgainStreak)
        assertEquals(Int.MAX_VALUE, failedRoute.recognitionReviewCount)
        assertEquals(0, failedRoute.contextualReadingReviewCount)
        assertEquals(Int.MAX_VALUE, failedRoute.recurringFailureCount)
    }

    @Test
    fun repairAdvanceSaturatesAttemptCounterAndExitsWhenExhausted() {
        val now = Long.MAX_VALUE - 1L
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.WRITE_KANJI),
            repairTaskIndex = 0,
            repairStepMinutes = listOf(10, 20),
            repairDueAtMillis = now - 1L,
            coreDueAtMillis = Long.MAX_VALUE,
            repairAttemptCount = Int.MAX_VALUE,
        )
        val item = adaptiveItem(route, hasSimilarKanji = true)
            .copyBuilder()
            .state(StudyLadderRules.STATE_LEARNING)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .dueAtMillis(now - 1L)
            .build()

        val advanced = AdaptiveReviewTransitionEngine(CountingAdapter(5, 5)).apply(
            item,
            request("good", StudyTaskTypes.SIMILAR_KANJI, null),
            now,
            parameters,
            settings,
            steps,
            ladder,
        )
        val advancedRoute = AdaptiveStudyItemPolicy.routeState(advanced.item)!!

        // A saturated attempt counter is far past the exhaustion limit: the
        // episode exits to revalidation and the counter stays saturated.
        assertFalse(advanced.fsrsCalled)
        assertFalse(advancedRoute.isRepairActive())
        assertTrue(advancedRoute.revalidationPending)
        assertEquals(Int.MAX_VALUE, advancedRoute.repairAttemptCount)
        assertEquals(Long.MAX_VALUE, advanced.item.dueAtMillis)
    }

    @Test
    fun hardLoopOnWritingRepairExitsToRevalidationAfterMaxAttempts() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        val coreDue = NOW + 5 * StudyLadderRules.DAY
        var item = adaptiveItem(
            AdaptiveRouteState(
                activeCore = CoreSkill.RECOGNITION,
                activeRepairTasks = listOf(StudyTaskTypes.WRITE_KANJI),
                repairStepMinutes = listOf(10),
                repairDueAtMillis = NOW,
                coreDueAtMillis = coreDue,
            ),
        ).copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .state(StudyLadderRules.STATE_LEARNING)
            .dueAtMillis(NOW)
            .build()

        // Messy writes never raise writingLevel, so Good is downgraded to Hard and
        // the appearance repeats. Before the exit rule this loop had no end.
        var appearances = 0
        while (AdaptiveStudyItemPolicy.routeState(item)!!.isRepairActive()) {
            appearances++
            assertTrue("repair must exit within the attempt limit", appearances <= AdaptiveRepairPolicy.MAX_REPAIR_ATTEMPTS)
            val transition = engine.apply(
                item.copyBuilder().dueAtMillis(NOW).activeToken("token").build(),
                request("good", StudyTaskTypes.WRITE_KANJI, null),
                NOW, parameters, settings, steps, ladder,
            )
            assertFalse(transition.fsrsCalled)
            item = transition.item
        }
        val route = AdaptiveStudyItemPolicy.routeState(item)!!

        assertEquals(AdaptiveRepairPolicy.MAX_REPAIR_ATTEMPTS, appearances)
        assertEquals(AdaptiveRepairPolicy.MAX_REPAIR_ATTEMPTS, route.repairAttemptCount)
        assertTrue(route.revalidationPending)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, item.phase)
        assertEquals(NOW + StudyLadderRules.DAY, item.dueAtMillis)
        assertEquals(0, adapter.reviewCalls)
    }

    @Test
    fun repairBelowTheAttemptLimitStillAdvancesNormally() {
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.WRITE_KANJI),
            repairStepMinutes = listOf(10, 20),
            repairDueAtMillis = NOW,
            coreDueAtMillis = NOW + 5 * StudyLadderRules.DAY,
            repairAttemptCount = AdaptiveRepairPolicy.MAX_REPAIR_ATTEMPTS - 2,
        )
        val item = adaptiveItem(route, hasSimilarKanji = true)
            .copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .state(StudyLadderRules.STATE_LEARNING)
            .dueAtMillis(NOW)
            .build()

        val advanced = AdaptiveReviewTransitionEngine(CountingAdapter(5, 5)).apply(
            item,
            request("good", StudyTaskTypes.SIMILAR_KANJI, null),
            NOW, parameters, settings, steps, ladder,
        )
        val advancedRoute = AdaptiveStudyItemPolicy.routeState(advanced.item)!!

        assertTrue(advancedRoute.isRepairActive())
        assertEquals(1, advancedRoute.repairTaskIndex)
        assertEquals(AdaptiveRepairPolicy.MAX_REPAIR_ATTEMPTS - 1, advancedRoute.repairAttemptCount)
        assertEquals(NOW + 20 * 60_000L, advanced.item.dueAtMillis)
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

    private fun saturatedMemory(dueAt: Long) = RecordsStudyModels.TaskMemory.fromFields(
        RecordsStudyModels.TaskMemory.Fields(
            state = StudyLadderRules.STATE_REVIEW,
            dueAtMillis = dueAt,
            stability = 4.0,
            difficulty = 5.0,
            totalReviews = Int.MAX_VALUE,
            lapses = Int.MAX_VALUE,
            learningStep = 0,
            lastRating = StudyRatings.GOOD,
            matureIntervalDays = 4,
            consecutivePasses = Int.MAX_VALUE,
            lastReviewedAtMillis = dueAt - StudyLadderRules.DAY,
        ),
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
        private val initialIntervalDays: Int = 1,
    ) : KaniFsrsAdapter {
        var reviewCalls = 0
        var initialReviewCalls = 0
        var elapsedDays = -1

        override fun initialReview(
            rating: String?,
            currentStability: Double,
            currentDifficulty: Double,
            targetRetention: Double,
            isNewLearning: Boolean,
        ): KaniFsrsReviewResult {
            initialReviewCalls++
            val stability = if (isNewLearning) INITIAL_STABILITY else currentStability
            return KaniFsrsReviewResult(stability, currentDifficulty, initialIntervalDays * StudyLadderRules.DAY)
        }

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

        companion object {
            const val INITIAL_STABILITY = 2.5
        }
    }

    // --- Golden life-of-kanji timelines (Goal 141) ---

    @Test
    fun goldenHappyPath_newItemToReadingCeiling() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30)
        val engine = AdaptiveReviewTransitionEngine(adapter)

        // Step 1: New item starts at recognition core, phase=review
        var item = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION))
            .copyBuilder().realPassStreak(0).build()
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(item)!!.activeCore)

        // Step 2: Pass recognition until promotion threshold (min passes - 1 already done)
        item = item.copyBuilder().realPassStreak(settings.ladderPromotionMinPasses - 1).build()
        val promoted = engine.apply(item, request("good", StudyTaskTypes.KANJI_MEANING, null), NOW, parameters, settings, steps, ladder)
        val promotedRoute = AdaptiveStudyItemPolicy.routeState(promoted.item)!!

        // Step 3: Should promote to contextual reading core
        assertEquals(CoreSkill.CONTEXTUAL_READING, promotedRoute.activeCore)
        assertEquals(RecordsBase.LadderRung.WORD_READING, promoted.item.rung)
        assertEquals(0, promoted.item.realPassStreak)

        // Step 4: Pass reading core
        val readingItem = promoted.item.copyBuilder().dueAtMillis(NOW).activeToken("t2").build()
        val readingPass = engine.apply(readingItem, request("good", StudyTaskTypes.WORD_READING, null), NOW, parameters, settings, steps, ladder)

        assertEquals(CoreSkill.CONTEXTUAL_READING, AdaptiveStudyItemPolicy.routeState(readingPass.item)!!.activeCore)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, readingPass.item.phase)
        assertTrue(readingPass.item.dueAtMillis > NOW)
    }

    @Test
    fun goldenRecognitionLapse_repairAndRevalidation() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)

        // Mature item on recognition core fails
        val item = adaptiveItem(
            AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION, recognitionReviewCount = 10),
            hasSimilarKanji = true,
        ).copyBuilder().matureIntervalDays(21).build()

        val failed = engine.apply(item, request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION), NOW, parameters, settings, steps, ladder)
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed.item)!!

        // Should enter relearning + repair
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, failed.item.phase)
        assertEquals(StudyTaskTypes.SIMILAR_KANJI, failedRoute.activeRepairTask())
        assertEquals(1, failed.item.kanjiMeaningMemory.lapses)

        // Pass repair → practice-only, revalidation pending
        val repairItem = failed.item.copyBuilder().dueAtMillis(NOW).activeToken("t-repair").build()
        val repairPass = engine.apply(repairItem, request("good", StudyTaskTypes.SIMILAR_KANJI, null), NOW, parameters, settings, steps, ladder)
        val afterRepair = AdaptiveStudyItemPolicy.routeState(repairPass.item)!!

        assertTrue(afterRepair.revalidationPending)
        assertFalse(afterRepair.isRepairActive())

        // Revalidation pass → core memory restored
        val revalItem = repairPass.item.copyBuilder().dueAtMillis(NOW).activeToken("t-reval").build()
        val revalPass = engine.apply(revalItem, request("good", StudyTaskTypes.KANJI_MEANING, null), NOW, parameters, settings, steps, ladder)
        val afterReval = AdaptiveStudyItemPolicy.routeState(revalPass.item)!!

        assertFalse(afterReval.revalidationPending)
        assertFalse(afterReval.isRepairActive())
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, revalPass.item.phase)
    }

    @Test
    fun goldenReadingLapse_repairAndRevalidation() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)

        // Item at contextual reading core fails
        val route = AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING, contextualReadingReviewCount = 5)
        val item = adaptiveItem(route)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .hasKanjiReading(true)
            .matureIntervalDays(21)
            .build()

        val failed = engine.apply(item, request("again", StudyTaskTypes.WORD_READING, FailureKind.WRONG_READING), NOW, parameters, settings, steps, ladder)
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed.item)!!

        // Should enter relearning with reading repair
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, failed.item.phase)
        assertTrue(failedRoute.isRepairActive())
        assertEquals(1, failed.item.wordReadingMemory.lapses)

        // Pass repair → revalidation
        val repairItem = failed.item.copyBuilder().dueAtMillis(NOW).activeToken("t-rr").build()
        val repairPass = engine.apply(repairItem, request("good", failedRoute.activeRepairTask()!!, null), NOW, parameters, settings, steps, ladder)
        val afterRepair = AdaptiveStudyItemPolicy.routeState(repairPass.item)!!

        assertTrue(afterRepair.revalidationPending)
        assertFalse(afterRepair.isRepairActive())

        // Revalidation pass on reading core
        val revalItem = repairPass.item.copyBuilder().dueAtMillis(NOW).activeToken("t-rv").build()
        val revalPass = engine.apply(revalItem, request("good", StudyTaskTypes.WORD_READING, null), NOW, parameters, settings, steps, ladder)
        val afterReval = AdaptiveStudyItemPolicy.routeState(revalPass.item)!!

        assertFalse(afterReval.revalidationPending)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, revalPass.item.phase)
        assertEquals(CoreSkill.CONTEXTUAL_READING, afterReval.activeCore)
    }

    @Test
    fun goldenStuckRepairEscalation() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)

        // Item with revalidation pending fails the core again → escalation threshold
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            recurringFailure = FailureKind.VISUAL_CONFUSION,
            recurringFailureCount = settings.ladderDemotionFailStreak - 1,
            revalidationPending = true,
        )
        val item = adaptiveItem(route, hasSimilarKanji = true)

        // Fail the core task (not repair) → should hit the escalation threshold
        val failed = engine.apply(item, request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION), NOW, parameters, settings, steps, ladder)
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed.item)!!

        // Escalation adds write_kanji to the repair chain per existing test pattern
        assertTrue(failedRoute.activeRepairTasks.contains(StudyTaskTypes.WRITE_KANJI))
        assertTrue(failedRoute.recurringFailureCount >= settings.ladderDemotionFailStreak)
        assertTrue(failedRoute.isRepairActive())
    }

    @Test
    fun goldenChronicSameCauseAcrossRevalidationPassesReachesEscalation() {
        // Interval stays below the promotion threshold, so a revalidation pass
        // does not resolve the same-cause history.
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        var item = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION), hasSimilarKanji = true)

        for (cycle in 1..settings.ladderDemotionFailStreak) {
            // Each cycle happens on a fresh due slot weeks later, so every core
            // failure is a real-due failure.
            val now = NOW + cycle * 30L * StudyLadderRules.DAY
            val failed = engine.apply(
                item.copyBuilder().dueAtMillis(now).activeToken("token").build(),
                request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.VISUAL_CONFUSION),
                now, parameters, settings, steps, ladder,
            ).item
            val failedRoute = AdaptiveStudyItemPolicy.routeState(failed)!!
            assertEquals(cycle, failedRoute.recurringFailureCount)
            if (cycle == settings.ladderDemotionFailStreak) {
                // Third same-cause real-due failure: the full escalation chain is required.
                assertEquals(listOf(StudyTaskTypes.SIMILAR_KANJI, StudyTaskTypes.WRITE_KANJI), failedRoute.activeRepairTasks)
                return
            }
            assertEquals(listOf(StudyTaskTypes.SIMILAR_KANJI), failedRoute.activeRepairTasks)
            val repaired = engine.apply(
                failed.copyBuilder().dueAtMillis(now).activeToken("token").build(),
                request("good", StudyTaskTypes.SIMILAR_KANJI, null),
                now, parameters, settings, steps, ladder,
            ).item
            assertTrue(AdaptiveStudyItemPolicy.routeState(repaired)!!.revalidationPending)
            val revalidationAt = now + StudyLadderRules.DAY
            item = engine.apply(
                repaired.copyBuilder().dueAtMillis(revalidationAt).activeToken("token").build(),
                request("good", StudyTaskTypes.KANJI_MEANING, null),
                revalidationAt, parameters, settings, steps, ladder,
            ).item
            val passedRoute = AdaptiveStudyItemPolicy.routeState(item)!!
            assertFalse(passedRoute.revalidationPending)
            // History survives the pass.
            assertEquals(FailureKind.VISUAL_CONFUSION, passedRoute.recurringFailure)
            assertEquals(cycle, passedRoute.recurringFailureCount)
        }
        throw AssertionError("escalation threshold was never reached")
    }

    @Test
    fun goldenPromotionStrengthPassResolvesSameCauseHistory() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30)
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            contextualReadingReviewCount = 6,
            recurringFailure = FailureKind.WRONG_READING,
            recurringFailureCount = 2,
        )
        val item = adaptiveItem(route)

        val passed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("good", StudyTaskTypes.WORD_READING, null),
            NOW, parameters, settings, steps, ladder,
        ).item
        val passedRoute = AdaptiveStudyItemPolicy.routeState(passed)!!

        assertNull(passedRoute.recurringFailure)
        assertEquals(0, passedRoute.recurringFailureCount)
    }

    @Test
    fun studyAheadPassKeepsSameCauseHistoryEvenWithStrongMemory() {
        val adapter = CountingAdapter(intervalDays = 30, promotionDays = 30)
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            recurringFailure = FailureKind.MEANING_UNKNOWN,
            recurringFailureCount = 1,
        )
        val early = adaptiveItem(route).copyBuilder().dueAtMillis(NOW + 60_000L).build()

        val passed = AdaptiveReviewTransitionEngine(adapter).apply(
            early,
            request("good", StudyTaskTypes.KANJI_MEANING, null),
            NOW, parameters, settings, steps, ladder,
        ).item
        val passedRoute = AdaptiveStudyItemPolicy.routeState(passed)!!

        assertEquals(FailureKind.MEANING_UNKNOWN, passedRoute.recurringFailure)
        assertEquals(1, passedRoute.recurringFailureCount)
    }

    @Test
    fun shapeCauseOnContextualCoreRoutesToRecognitionRepairWithoutDemoting() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val route = AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING, contextualReadingReviewCount = 6)
        val item = adaptiveItem(route, hasSimilarKanji = true)
            .copyBuilder()
            .hasKanjiReading(true)
            .build()

        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("again", StudyTaskTypes.WORD_READING, FailureKind.VISUAL_CONFUSION),
            NOW, parameters, settings, steps, ladder,
        ).item
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed)!!

        assertEquals(1, adapter.reviewCalls)
        assertEquals(CoreSkill.CONTEXTUAL_READING, failedRoute.activeCore)
        assertEquals(RecordsBase.LadderRung.WORD_READING, failed.rung)
        assertEquals(FailureKind.VISUAL_CONFUSION, failedRoute.recurringFailure)
        assertEquals(FailureKind.VISUAL_CONFUSION, failedRoute.answerEvidence?.failureKind)
        assertEquals(StudyTaskTypes.SIMILAR_KANJI, failedRoute.activeRepairTask())
        assertEquals(1, failed.wordReadingMemory.lapses)
    }

    @Test
    fun meaningCauseOnContextualCoreRoutesToMeaningRepair() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val item = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING, contextualReadingReviewCount = 6))

        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("again", StudyTaskTypes.WORD_READING, FailureKind.MEANING_UNKNOWN),
            NOW, parameters, settings, steps, ladder,
        ).item
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed)!!

        assertEquals(FailureKind.MEANING_UNKNOWN, failedRoute.recurringFailure)
        assertEquals(StudyTaskTypes.MEANING_KANJI, failedRoute.activeRepairTask())
    }

    @Test
    fun readingCauseOnRecognitionCoreStillCollapsesToUnknown() {
        val adapter = CountingAdapter(intervalDays = 5, promotionDays = 5)
        val item = adaptiveItem(AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION))

        val failed = AdaptiveReviewTransitionEngine(adapter).apply(
            item,
            request("again", StudyTaskTypes.KANJI_MEANING, FailureKind.WRONG_READING),
            NOW, parameters, settings, steps, ladder,
        ).item
        val failedRoute = AdaptiveStudyItemPolicy.routeState(failed)!!

        assertEquals(FailureKind.UNKNOWN, failedRoute.recurringFailure)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
