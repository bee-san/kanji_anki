package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.HashSet
import java.util.Locale

class SchedulerDecisionTraceTest {
    @Test
    fun traceNextSessionForNewKanjiExplainsSelectedKanjiMeaning() {
        val scheduler = BridgeScheduler()
        val now = 1_000L
        val item = itemAtRung("裂", RecordsBase.LadderRung.KANJI_MEANING)
        val trace = scheduler.debugTraceNextSession(listOf(item), listOf(row("裂", 30)), now)

        assertEquals("next_session", trace.operation)
        assertEquals(now, trace.nowMillis)
        assertNotNull(trace.selected)
        assertEquals("裂", trace.selected!!.kanji)
        assertEquals("kanji_meaning", trace.selected.taskType)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, trace.selected.rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, trace.selected.phase)
        assertEquals(now, trace.selected.dueAtMillis)
        assertTrue(trace.selected.reasonCodes.contains("new_learning_unseen"))
        assertTrue(trace.selected.reasonCodes.contains("selected_best_candidate"))
        assertTrue(SchedulerTraceFormatter.userExplanation(trace).contains("裂"))
        assertTrue(SchedulerTraceFormatter.developerExplanation(trace).contains("new_learning_unseen"))
    }

    @Test
    fun traceNextSessionExplainsRelearningBeatsSameFamilyReviewSibling() {
        val scheduler = BridgeScheduler()
        val now = 1_000L
        val relearning = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, now)
            .copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .state("learning")
            .build()
        val reviewSibling = reviewCard("裂", RecordsBase.LadderRung.FONT_MEANING, now)
            .copyBuilder()
            .activeToken("review-sibling")
            .build()
        val trace = scheduler.debugTraceNextSession(listOf(reviewSibling, relearning), listOf(row("裂", 20)), now)

        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, trace.selected!!.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, trace.selected.rung)
        val hidden = trace.skipped.first { it.rung == RecordsBase.LadderRung.FONT_MEANING }
        assertTrue(hidden.reasonCodes.contains("same_family_hidden"))
        assertTrue(hidden.reasonCodes.contains("same_family_lower_priority"))
    }

    @Test
    fun traceApplyReviewCapturesFsrsPromotionAfterLongInterval() {
        val scheduler = schedulerWithReviewIntervalDays(22)
        // Prime one prior real-due pass so this pass clears the default
        // two-pass promotion gate (Goal 63).
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
            .copyBuilder().realPassStreak(1).build()
            .withToken("promote")
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, passRequest("裂", "promote"), HashSet(), 1_000L).build()
        )

        assertFalse(traced.result.duplicate)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.result.item.rung)
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(traced.result.item)!!.activeCore)
        assertEquals(7, traced.result.item.fontMeaningMemory.matureIntervalDays)
        assertEquals("apply_review", traced.trace.operation)
        assertEquals("good", traced.trace.transition!!.rating)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.trace.transition.beforeRung)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, traced.trace.transition.afterRung)
        assertEquals("fsrs_interval_promotes", traced.trace.transition.movementReason)
        assertTrue(traced.trace.transition.reasonCodes.contains("review_pass_fsrs_interval"))
        assertTrue(traced.trace.transition.reasonCodes.contains("fsrs_interval_promotes"))
        assertEquals(1, traced.trace.fsrsCalls.size)
        assertEquals("review", traced.trace.fsrsCalls[0].callType)
        assertEquals("good", traced.trace.fsrsCalls[0].rating)
        // The promoted rung's first review is capped at promotionDays / 3.
        assertEquals(7, traced.trace.fsrsCalls[0].outputIntervalDays)
    }

    @Test
    fun traceApplyReviewCapturesThreeDueAgainsDemotion() {
        val scheduler = BridgeScheduler()
        val consumed = HashSet<String>()
        var item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var now = 1_000L
        for (i in 0 until 2) {
            val result = scheduler.applyReview(item.withToken("again-$i"), failRequest("裂", "again-$i"), consumed, now)
            item = result.item
            now = maxOf(item.dueAtMillis, now + 86_400_000L)
            item = item.copyBuilder().dueAtMillis(now - 60_000L).phase(RecordsBase.SchedulerPhase.REVIEW).state("review").build()
        }

        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item.withToken("again-final"), failRequest("裂", "again-final"), consumed, now).build()
        )

        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, traced.result.item.rung)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.trace.transition!!.beforeRung)
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, traced.trace.transition.afterRung)
        assertEquals("again_streak_demotes", traced.trace.transition.movementReason)
        assertTrue(traced.trace.transition.reasonCodes.contains("review_again_lapse"))
        assertTrue(traced.trace.transition.reasonCodes.contains("real_again_streak_threshold"))
        assertEquals(1, traced.trace.fsrsCalls.size)
        assertEquals("again", traced.trace.fsrsCalls[0].rating)
    }

    @Test
    fun traceApplyReviewCapturesSimilarKanjiSkipWhenUnavailable() {
        val scheduler = schedulerWithReviewIntervalDays(22)
        // New default order (Goal 65): promotion from meaning_kanji crosses the
        // similar_kanji rung, which has no content for this card, so the trace
        // must record the skip.
        val item = reviewCard("裂", RecordsBase.LadderRung.MEANING_KANJI, 0L)
            .copyBuilder().realPassStreak(1).build()
            .withHasSimilarKanji(false)
            .withToken("similar-skip")
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, passRequest("裂", "similar-skip"), HashSet(), 1_000L).build()
        )

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.result.item.rung)
        assertTrue(traced.trace.transition!!.reasonCodes.contains("fsrs_interval_promotes"))
        assertTrue(traced.trace.transition.reasonCodes.contains("similar_kanji_unavailable"))
    }

    @Test
    fun traceApplyReviewOmitsSimilarKanjiSkipWhenPromotionDoesNotCrossIt() {
        val scheduler = schedulerWithReviewIntervalDays(22)
        // type_meaning -> meaning_kanji never crosses similar_kanji, so no
        // skip reason may be reported even though the card lacks similar data.
        val item = reviewCard("裂", RecordsBase.LadderRung.TYPE_MEANING, 0L)
            .copyBuilder().realPassStreak(1).build()
            .withHasSimilarKanji(false)
            .withToken("similar-not-crossed")
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, passRequest("裂", "similar-not-crossed"), HashSet(), 1_000L).build()
        )

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.result.item.rung)
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(traced.result.item)!!.activeCore)
        assertTrue(traced.result.item.meaningKanjiMemory.totalReviews > 0)
        assertTrue(traced.trace.transition!!.reasonCodes.contains("fsrs_interval_promotes"))
        assertFalse(traced.trace.transition.reasonCodes.contains("similar_kanji_unavailable"))
    }

    @Test
    fun adaptiveTraceKeepsMandatoryCoreAnchorWhenLegacyBitIsDisabled() {
        val scheduler = schedulerWithReviewIntervalDays(10)
        val route = AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION, recognitionReviewCount = 1)
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
            .copyBuilder()
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .activeToken("adaptive")
            .build()
        val ladder = RecordsBase.StudyLadderSettings.defaults()
            .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)

        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(
                item,
                passRequest("裂", "adaptive"),
                HashSet(),
                1_000L,
            ).ladder(ladder).build(),
        )

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.trace.transition!!.beforeRung)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, traced.trace.transition.afterRung)
    }

    @Test
    fun traceApplyReviewDuplicateTokenDoesNotCallFsrs() {
        val scheduler = schedulerWithReviewIntervalDays(22)
        val item = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("dupe")
        val consumed = HashSet<String>()
        consumed.add("dupe")
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, passRequest("裂", "dupe"), consumed, 1_000L).build()
        )

        assertTrue(traced.result.duplicate)
        assertTrue(traced.trace.transition!!.reasonCodes.contains("duplicate_token"))
        assertTrue(traced.trace.fsrsCalls.isEmpty())
    }

    @Test
    fun traceApplyReviewLearningStepDoesNotInventFsrsCall() {
        val scheduler = schedulerWithReviewIntervalDays(22)
        val item = itemAtRung("裂", RecordsBase.LadderRung.KANJI_MEANING).withToken("learning-hard")
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, hardRequest("裂", "learning-hard"), HashSet(), 1_000L).build()
        )

        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, traced.result.item.phase)
        assertTrue(traced.trace.transition!!.reasonCodes.contains("new_learning_step"))
        assertTrue(traced.trace.fsrsCalls.isEmpty())
        assertTrue(SchedulerTraceFormatter.userExplanation(traced.trace).contains("hard"))
    }

    @Test
    fun traceFormattersExplainNullReadySkippedTransitionAndFsrsDetails() {
        assertEquals("No scheduler trace is available.", SchedulerTraceFormatter.userExplanation(null))
        assertEquals("scheduler_trace unavailable", SchedulerTraceFormatter.developerExplanation(null))

        val emptyTrace = SchedulerDecisionTrace("next_session", 1_000L, null, null, null, null, null)
        assertEquals("No study card is ready.", SchedulerTraceFormatter.userExplanation(emptyTrace))

        val skipped = SchedulerDecisionTraceCandidate(
            "裂",
            "kanji_meaning",
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.SchedulerPhase.NEW_LEARNING,
            1_000L,
            listOf("same_family_hidden"),
            "裂\u0000signature",
            42,
        )
        val transition = SchedulerReviewTransitionTrace(
            "good",
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.LadderRung.FONT_MEANING,
            "fsrs_interval_promotes",
            listOf("review_pass_fsrs_interval", "fsrs_interval_promotes"),
        )
        val fsrs = SchedulerFsrsCallTrace("review", "good", 22)
        val trace = SchedulerDecisionTrace("apply_review", 2_000L, null, null, listOf(skipped), transition, listOf(fsrs))

        val developer = SchedulerTraceFormatter.developerExplanation(trace)
        assertTrue(developer.contains("skipped=裂:same_family_hidden"))
        assertTrue(developer.contains("transition=kanji_meaning->font_meaning:fsrs_interval_promotes"))
        assertTrue(developer.contains("fsrs=review:good:22"))
        assertTrue(SchedulerTraceFormatter.userExplanation(trace).contains("kanji_meaning -> font_meaning"))
    }

    @Test
    fun userTraceFormatterTranslatesJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("スケジューラーの記録はありません。", SchedulerTraceFormatter.userExplanation(null))

            val emptyTrace = SchedulerDecisionTrace("next_session", 1_000L, null, null, null, null, null)
            assertEquals("学習できるカードはありません。", SchedulerTraceFormatter.userExplanation(emptyTrace))

            val selected = SchedulerDecisionTraceCandidate(
                "裂",
                "kanji_meaning",
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.SchedulerPhase.NEW_LEARNING,
                1_000L,
                listOf("due_now"),
                "裂\u0000signature",
                42,
            )
            val selectedTrace = SchedulerDecisionTrace("next_session", 1_000L, selected, listOf(selected), null, null, null)
            assertEquals(
                "裂をkanji_meaning用に選びました（new_learning）。",
                SchedulerTraceFormatter.userExplanation(selectedTrace),
            )

            val transition = SchedulerReviewTransitionTrace(
                "good",
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.LadderRung.FONT_MEANING,
                "fsrs_interval_promotes",
                listOf("review_pass_fsrs_interval"),
            )
            val reviewTrace = SchedulerDecisionTrace("apply_review", 2_000L, null, null, null, transition, null)
            assertEquals(
                "goodを適用: kanji_meaning → font_meaning。",
                SchedulerTraceFormatter.userExplanation(reviewTrace),
            )
        }
    }

    private fun item(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
    }

    private fun itemAtRung(kanji: String, rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem {
        return item(kanji).withRungAndPhase(rung, RecordsBase.SchedulerPhase.NEW_LEARNING)
    }

    private fun reviewCard(kanji: String, rung: RecordsBase.LadderRung, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return item(kanji).copyBuilder()
            .state("review")
            .dueAtMillis(dueAtMillis)
            .stability(2.0)
            .difficulty(4.0)
            .totalReviews(4)
            .learningStep(0)
            .matureIntervalDays(21)
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()
    }

    private fun row(kanji: String, score: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, if (score > 15) 1 else 0, 0, ArrayList<RecordsImportModels.Example>())
    }

    private fun passRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, false, false, 0)
    }

    private fun failRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "again", false, false, false, 0)
    }

    private fun hardRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "hard", false, false, false, 0)
    }

    private fun schedulerWithReviewIntervalDays(intervalDays: Long): BridgeScheduler {
        return BridgeScheduler(FixedIntervalFsrsAdapter(intervalDays * BridgeScheduler.DAY))
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private class FixedIntervalFsrsAdapter(private val reviewIntervalMillis: Long) : KaniFsrsAdapter {
        override fun initialReview(
            rating: String?,
            currentStability: Double,
            currentDifficulty: Double,
            targetRetention: Double,
            isNewLearning: Boolean,
        ): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(currentStability, currentDifficulty, BridgeScheduler.DAY)
        }

        override fun review(
            stability: Double,
            difficulty: Double,
            rating: String?,
            elapsedDays: Int,
            targetRetention: Double,
        ): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis)
        }
    }
}
