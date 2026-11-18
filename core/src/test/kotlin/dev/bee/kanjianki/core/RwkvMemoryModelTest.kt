package dev.bee.kanjianki.core

import dev.bee.rwkv.RwkvArchitecture
import dev.bee.rwkv.RwkvCollectionState
import dev.bee.rwkv.RwkvOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RwkvMemoryModelTest {
    private val ladder = RecordsBase.StudyLadderSettings.defaults()
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val parameters = RecordsSchedulerModels.SchedulerParameters.defaults()
    private val steps = RecordsSchedulerModels.LearningStepSettings.defaults()

    @Test
    fun streamKeysAreStableAndFollowTheSignatureMeaning() {
        val ids = RwkvStreamKeys.idsFor("脱", CoreSkill.RECOGNITION, "脱|脱出|だっしゅつ|escape")
        // Pinned: persisted review rows and states are keyed by these values.
        assertEquals(ids, RwkvStreamKeys.idsFor("脱", CoreSkill.RECOGNITION, "脱|脱出|だっしゅつ|escape"))
        assertTrue(ids.card > 0 && ids.note > 0 && ids.deck > 0 && ids.preset > 0)
        val reshuffled = RwkvStreamKeys.idsFor("脱", CoreSkill.RECOGNITION, "脱|脱ぐ|ぬぐ|escape")
        assertEquals("same meaning keeps the card", ids.card, reshuffled.card)
        val changed = RwkvStreamKeys.idsFor("脱", CoreSkill.RECOGNITION, "脱|脱ぐ|ぬぐ|undress")
        assertNotEquals("a meaning change starts a new card", ids.card, changed.card)
        val contextual = RwkvStreamKeys.idsFor("脱", CoreSkill.CONTEXTUAL_READING, "脱|脱出|だっしゅつ|escape")
        assertNotEquals(ids.card, contextual.card)
        assertNotEquals(ids.deck, contextual.deck)
        assertEquals("cores of one kanji share the note", ids.note, contextual.note)
        assertEquals(ids.preset, contextual.preset)
        assertNotEquals(ids.note, RwkvStreamKeys.idsFor("着", CoreSkill.RECOGNITION, "").note)
        // Length-prefixing keeps part boundaries significant.
        assertNotEquals(RwkvStreamKeys.stableId("ab", "c"), RwkvStreamKeys.stableId("a", "bc"))
    }

    @Test
    fun modelDaysAreConsecutiveAcrossDaylightSavingChanges() {
        val hour = 60L * 60L * 1000L
        val day = 24L * hour
        val start = 1_710_000_000_000L
        val days = listOf(start, start + day - hour, start + 2 * day, start + 3 * day + hour)
            .map(RwkvDays::fromDayStart)
        assertEquals(listOf(days[0], days[0] + 1, days[0] + 2, days[0] + 3), days)
    }

    @Test
    fun ratingsMapToRwkvEases() {
        assertEquals(1, RwkvMemoryModel.ratingCode(StudyRatings.AGAIN))
        assertEquals(2, RwkvMemoryModel.ratingCode(StudyRatings.HARD))
        assertEquals(3, RwkvMemoryModel.ratingCode(StudyRatings.GOOD))
        assertEquals(4, RwkvMemoryModel.ratingCode(StudyRatings.EASY))
        assertEquals(1, RwkvMemoryModel.ratingCode("bogus"))
        assertEquals(1, RwkvMemoryModel.ratingCode(null))
    }

    @Test
    fun everyLegacyLearningAnswerFeedsTheModelOnceAndOnlyGraduationSchedulesFromIt() {
        val model = TestMemoryModels.rwkv()
        val scheduler = BridgeScheduler.withMemoryModel(model)
        val now = NOW
        var item = newItem("新")
        item = review(scheduler, item, StudyRatings.GOOD, now, "t1")
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, item.phase)
        assertEquals("a learning step keeps its configured delay", now + 10 * MINUTE, item.dueAtMillis)
        assertEquals(1, model.fedAnswers.size)
        assertEquals(MemoryAnswerKind.NEW_LEARNING_STEP.rwkvReviewKind, model.fedAnswers[0].answer.reviewKind)

        item = review(scheduler, item, StudyRatings.GOOD, now + 10 * MINUTE, "t2")
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, item.phase)
        assertEquals(2, model.fedAnswers.size)
        assertTrue("graduation schedules at least a day ahead", item.dueAtMillis >= now + 10 * MINUTE + DAY)
        assertTrue("stored stability is the model's S90", item.stability > 0.0)
        assertEquals(model.fedAnswers[0].ids, model.fedAnswers[1].ids)
    }

    @Test
    fun adaptiveRepairAppearancesNeverFeedTheModel() {
        val state = RwkvCollectionState()
        val model = TestMemoryModels.rwkv(state)
        val scheduler = BridgeScheduler.withMemoryModel(model)
        val route = AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION)
        var item = adaptiveItem(route)
        item = review(scheduler, item, StudyRatings.AGAIN, NOW, "fail", StudyTaskTypes.KANJI_MEANING)
        assertEquals(1, model.fedAnswers.size)
        val repairTask = AdaptiveStudyItemPolicy.routeState(item)!!.activeRepairTask()
        assertTrue("a core fail opens a repair", repairTask != null)
        val streamsBefore = state.streams.map { it.toMap() }
        item = review(scheduler, item, StudyRatings.GOOD, item.dueAtMillis, "repair", repairTask!!)
        assertEquals("repair is practice-only", 1, model.fedAnswers.size)
        assertEquals(streamsBefore, state.streams.map { it.toMap() })
    }

    @Test
    fun previewingAFirstGoodLeavesTheStateUntouched() {
        val state = RwkvCollectionState()
        val model = TestMemoryModels.rwkv(state)
        val request = MemoryReviewRequest(
            kanji = "脱",
            core = CoreSkill.CONTEXTUAL_READING,
            answerSignature = "",
            rating = StudyRatings.GOOD,
            kind = MemoryAnswerKind.NEW_GRADUATION,
            reviewedAtMillis = NOW,
            targetRetention = 0.9,
            previousStability = 3.0,
            previousDifficulty = 6.5,
            elapsedDays = 0,
        )
        val preview = model.previewFirstGood(request)
        assertTrue(preview.intervalDays() >= 1)
        assertEquals("difficulty passes through", 6.5, preview.difficulty, 0.0)
        assertTrue(model.fedAnswers.isEmpty())
        assertTrue(state.streams.all { it.isEmpty() })
        assertTrue(state.histories.isEmpty())
        assertEquals(0L, state.globals.reviewIndex)
    }

    @Test
    fun singlePassSimulationsWriteOnlyToTheirOverlay() {
        val base = RwkvCollectionState()
        val overlay = RwkvOverlayView(base)
        val model = RwkvMemoryModel(
            TestMemoryModels.rwkvEngine,
            overlay,
            { Math.floorDiv(it, DAY) },
            singlePass = true,
        )
        val result = model.review(
            MemoryReviewRequest("脱", CoreSkill.RECOGNITION, "", StudyRatings.GOOD, MemoryAnswerKind.REVIEW, NOW, 0.9, 1.0, 5.0, 0),
        )
        assertTrue(result.intervalDays() >= 1)
        assertTrue(result.promotionIntervalDays() >= 1)
        assertEquals(RwkvArchitecture.STREAMS, overlay.writtenStateCount())
        assertTrue(base.streams.all { it.isEmpty() })
        assertNull(base.histories[model.fedAnswers.single().ids.card])
    }

    @Test
    fun anUnboundSchedulerRefusesToApplyReviews() {
        try {
            review(BridgeScheduler(), newItem("新"), StudyRatings.GOOD, NOW, "t")
            fail("an unbound scheduler applied a review")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("withMemoryModel"))
        }
        try {
            UnboundMemoryModel.previewFirstGood(
                MemoryReviewRequest("新", CoreSkill.RECOGNITION, "", StudyRatings.GOOD, MemoryAnswerKind.NEW_GRADUATION, NOW, 0.9, 1.0, 5.0, 0),
            )
            fail("an unbound model previewed")
        } catch (_: IllegalStateException) {
        }
    }

    private fun review(
        scheduler: BridgeScheduler,
        item: RecordsStudyModels.StudyItem,
        rating: String,
        now: Long,
        token: String,
        taskType: String = StudyTaskTypes.KANJI_MEANING,
    ): RecordsStudyModels.StudyItem {
        val request = RecordsSchedulerModels.ReviewRequest(
            item.kanji, token, rating, false, false, false, false, 0, taskType, "", "",
        )
        return scheduler.applyReview(
            BridgeScheduler.ReviewApplication.builder(item.withToken(token), request, HashSet(), now)
                .parameters(parameters)
                .settings(settings)
                .learningSettings(steps)
                .ladder(ladder)
                .build(),
        ).item
    }

    private fun newItem(kanji: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, "new", NOW, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .build()

    private fun adaptiveItem(route: AdaptiveRouteState): RecordsStudyModels.StudyItem {
        val owner = AdaptiveCorePolicy.memoryOwnerTaskType(route.activeCore)
        val memory = RecordsStudyModels.TaskMemory(StudyLadderRules.STATE_REVIEW, NOW - 1L, 4.0, 5.0, 4, 0, 0, "good", 4)
        return RecordsStudyModels.StudyItem("脱", StudyLadderRules.STATE_REVIEW, NOW - 1L, 4.0, 5.0, 4, 0, 0, 1, null, NOW - 1000L)
            .copyBuilder()
            .rung(AdaptiveCorePolicy.memoryOwnerRung(route.activeCore))
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(4)
            .build()
            .withTaskMemory(owner, memory)
            .copyBuilder()
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
        const val DAY = 24L * 60L * MINUTE
    }
}
