package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.HashSet

class SchedulerTimelineSimulatorTest {
    @Test
    fun newKanjiEntersKanjiMeaningMatchesGoldenTimeline() {
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = listOf(row("裂", 30)),
            startingItems = emptyList(),
            startMillis = START,
        )

        simulator.seedQueue()
        val next = simulator.nextSession()

        assertEquals("next", next.kind)
        assertEquals("裂", next.trace.selected!!.kanji)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, next.snapshot!!.rung)
        assertTrue(next.trace.selected!!.reasonCodes.contains("new_learning_unseen"))
        assertGolden("newKanjiEntersKanjiMeaning", simulator.renderText())
    }

    @Test
    fun reviewPassPromotesAfterLongFsrsIntervalMatchesGoldenTimeline() {
        val simulator = SchedulerTimelineSimulator(
            scheduler = schedulerWithReviewIntervalDays(22),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, START)),
            startMillis = START,
        )

        // First qualifying pass: the interval qualifies but the min-pass gate
        // (default 2) blocks promotion, so the rung holds.
        simulator.nextSession()
        val firstAnswer = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, firstAnswer.snapshot!!.rung)
        assertTrue(firstAnswer.trace.transition!!.reasonCodes.contains("promotion_blocked_min_passes"))

        // Second qualifying pass on a fresh due slot promotes.
        simulator.advanceTo(firstAnswer.snapshot!!.dueAtMillis)
        simulator.nextSession()
        val answer = simulator.answer("good")

        assertEquals("answer", answer.kind)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, answer.snapshot!!.rung)
        assertEquals("fsrs_interval_promotes", answer.trace.transition!!.movementReason)
        assertTrue(answer.trace.transition!!.reasonCodes.contains("review_pass_fsrs_interval"))
        assertTrue(answer.trace.transition!!.reasonCodes.contains("fsrs_interval_promotes"))
        assertGolden("reviewPassPromotesAfterLongFsrsInterval", simulator.renderText())
    }

    @Test
    fun promotionRequiresSecondRealDuePassMatchesGoldenTimeline() {
        // Anti-cascade (Goal 63): a mature card no longer climbs two rungs in two
        // reviews. Each rung requires two real-due passes; the first is blocked.
        val simulator = SchedulerTimelineSimulator(
            scheduler = schedulerWithReviewIntervalDays(22),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, START)),
            startMillis = START,
        )

        simulator.nextSession()
        val firstAnswer = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, firstAnswer.snapshot!!.rung)
        assertEquals(1, firstAnswer.snapshot!!.realPassStreak)
        assertEquals("rung_unchanged", firstAnswer.trace.transition!!.movementReason)
        assertTrue(firstAnswer.trace.transition!!.reasonCodes.contains("promotion_blocked_min_passes"))

        simulator.advanceTo(firstAnswer.snapshot!!.dueAtMillis)
        simulator.nextSession()
        val secondAnswer = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, secondAnswer.snapshot!!.rung)

        // A third pass on the newly promoted rung is again blocked: FONT_MEANING
        // does not immediately cascade up to WORD_READING on its first pass.
        simulator.advanceTo(secondAnswer.snapshot!!.dueAtMillis)
        simulator.nextSession()
        val thirdAnswer = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, thirdAnswer.snapshot!!.rung)
        assertTrue(thirdAnswer.trace.transition!!.reasonCodes.contains("promotion_blocked_min_passes"))

        assertGolden("promotionRequiresSecondRealDuePass", simulator.renderText())
    }

    @Test
    fun threeDueReviewAgainsDemoteMatchesGoldenTimeline() {
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, START)),
            startMillis = START,
            learningSettings = noRelearningSteps(),
        )

        simulator.nextSession()
        val firstAgain = simulator.answer("again")
        simulator.advanceTo(firstAgain.snapshot!!.dueAtMillis)
        simulator.nextSession()
        val secondAgain = simulator.answer("again")
        simulator.advanceTo(secondAgain.snapshot!!.dueAtMillis)
        simulator.nextSession()
        val finalAgain = simulator.answer("again")

        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, finalAgain.snapshot!!.rung)
        assertEquals(0, finalAgain.snapshot!!.realAgainStreak)
        assertEquals("again_streak_demotes", finalAgain.trace.transition!!.movementReason)
        assertTrue(finalAgain.trace.transition!!.reasonCodes.contains("review_again_lapse"))
        assertTrue(finalAgain.trace.transition!!.reasonCodes.contains("real_again_streak_threshold"))
        assertGolden("threeDueReviewAgainsDemote", simulator.renderText())
    }

    @Test
    fun similarKanjiSkippedWithoutContentMatchesGoldenTimeline() {
        // New default order (Goal 65): kanji_meaning demotes across the
        // content-less similar_kanji rung to meaning_kanji, recording the skip.
        val almostDemoting = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, START)
            .copyBuilder()
            .realAgainStreak(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK - 1)
            .build()
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(almostDemoting),
            startMillis = START,
        )

        simulator.nextSession()
        val answer = simulator.answer("again")

        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, answer.snapshot!!.rung)
        assertEquals("again_streak_demotes", answer.trace.transition!!.movementReason)
        assertTrue(answer.trace.transition!!.reasonCodes.contains("similar_kanji_unavailable"))
        assertGolden("similarKanjiSkippedWithoutContent", simulator.renderText())
    }

    @Test
    fun relearningBeatsSameFamilyReviewSiblingMatchesGoldenTimeline() {
        val relearning = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, START)
            .copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .state("learning")
            .build()
        val reviewSibling = reviewCard("裂", RecordsBase.LadderRung.FONT_MEANING, START)
            .copyBuilder()
            .activeToken("review-sibling")
            .build()
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(reviewSibling, relearning),
            startMillis = START,
        )

        val next = simulator.nextSession()

        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, next.trace.selected!!.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, next.trace.selected!!.rung)
        assertTrue(next.trace.skipped.first { it.rung == RecordsBase.LadderRung.FONT_MEANING }.reasonCodes.contains("same_family_hidden"))
        assertGolden("relearningBeatsSameFamilyReviewSibling", simulator.renderText())
    }

    @Test
    fun emptyQueueRendersNoSelectedTimeline() {
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = emptyList(),
            startingItems = emptyList(),
            startMillis = START,
        )

        val seed = simulator.seedQueue()
        val next = simulator.nextSession()

        assertEquals("seed", seed.kind)
        assertNull(seed.trace.selected)
        assertEquals("next", next.kind)
        assertNull(next.trace.selected)
        assertEquals(
            """
            T+00:00 seed admitted=none
            T+00:00 next selected=none
            """.trimIndent(),
            simulator.renderText().trimEnd(),
        )
    }

    @Test
    fun advanceEventsExposeCurrentItemsAndTimelineOffsets() {
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = listOf(row("裂", 30)),
            startingItems = emptyList(),
            startMillis = START,
        )

        simulator.seedQueue()
        val afterMinutes = simulator.advanceBy(90 * 60_000L)
        val afterDays = simulator.advanceTo(START + 2 * BridgeScheduler.DAY)

        assertEquals("advance", afterMinutes.kind)
        assertEquals(90 * 60_000L, afterMinutes.offsetMillis)
        assertEquals("advance", afterDays.kind)
        assertEquals(2 * BridgeScheduler.DAY, afterDays.offsetMillis)
        assertEquals(1, simulator.currentItems().size)
        assertEquals(3, simulator.events().size)
        assertTrue(simulator.renderText().contains("T+01:30 advance now=T+01:30"))
        assertTrue(simulator.renderText().contains("T+2d advance now=T+2d"))
    }

    private fun assertGolden(name: String, actual: String) {
        val resource = javaClass.getResource("/dev/bee/kanjianki/core/scheduler-goldens/$name.timeline.txt")
        assertNotNull("Missing scheduler golden resource $name", resource)
        val expected = resource!!.readText().trimEnd()
        assertEquals(expected, actual.trimEnd())
    }

    private fun row(kanji: String, score: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, if (score > 15) 1 else 0, 0, ArrayList<RecordsImportModels.Example>())
    }

    private fun item(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
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

    private fun noRelearningSteps(): RecordsSchedulerModels.LearningStepSettings {
        return RecordsSchedulerModels.LearningStepSettings(
            RecordsSchedulerModels.LearningStepSettings.defaultNewSteps(),
            emptyList(),
        )
    }

    private fun schedulerWithReviewIntervalDays(intervalDays: Long): BridgeScheduler {
        return BridgeScheduler(FixedIntervalFsrsAdapter(intervalDays * BridgeScheduler.DAY))
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

    private companion object {
        const val START = 1_000L
    }
}
