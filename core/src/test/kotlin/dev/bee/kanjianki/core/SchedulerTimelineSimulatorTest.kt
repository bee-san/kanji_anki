package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(next.trace.selected.reasonCodes.contains("new_learning_unseen"))
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
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(simulator.currentItems().single())!!.activeCore)

        // Second qualifying pass on a fresh due slot promotes.
        simulator.advanceTo(firstAnswer.snapshot.dueAtMillis)
        simulator.nextSession()
        val answer = simulator.answer("good")

        assertEquals("answer", answer.kind)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, answer.snapshot!!.rung)
        assertEquals("rung_unchanged", answer.trace.transition!!.movementReason)
        assertTrue(answer.trace.transition.reasonCodes.contains("review_pass_fsrs_interval"))
        assertTrue(answer.trace.transition.reasonCodes.contains("promotion_blocked_min_passes"))
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
        assertEquals(0, firstAnswer.snapshot.realPassStreak)
        assertEquals("rung_unchanged", firstAnswer.trace.transition!!.movementReason)
        assertTrue(firstAnswer.trace.transition.reasonCodes.contains("promotion_blocked_min_passes"))
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))

        simulator.advanceTo(firstAnswer.snapshot.dueAtMillis)
        simulator.nextSession()
        val secondAnswer = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, secondAnswer.snapshot!!.rung)
        assertEquals(1, secondAnswer.snapshot.realPassStreak)

        // The third real-due recognition pass is the second adaptive-core pass,
        // so it promotes to contextual reading instead of another legacy rung.
        simulator.advanceTo(secondAnswer.snapshot.dueAtMillis)
        simulator.nextSession()
        val thirdAnswer = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.WORD_READING, thirdAnswer.snapshot!!.rung)
        assertEquals(CoreSkill.CONTEXTUAL_READING, AdaptiveStudyItemPolicy.routeState(simulator.currentItems().single())!!.activeCore)

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

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, finalAgain.snapshot!!.rung)
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertTrue(AdaptiveStudyItemPolicy.routeState(simulator.currentItems().single())!!.isRepairActive())
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, finalAgain.snapshot.phase)
        assertGolden("threeDueReviewAgainsDemote", simulator.renderText())
    }

    @Test
    fun finalLegacyWritingReviewConvertsToRecognitionCoreMatchesGoldenTimeline() {
        // Lazy v31 conversion lets this final legacy writing review finish in
        // place, including its writing-level update, then owns all later due
        // reviews through the recognition core rather than a legacy rung.
        val writing = reviewCard("裂", RecordsBase.LadderRung.WRITE_KANJI, START)
            .copyBuilder()
            .writingLevel(1)
            .realPassStreak(1)
            .build()
        val simulator = SchedulerTimelineSimulator(
            scheduler = schedulerWithReviewIntervalDays(22),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(writing),
            startMillis = START,
        )

        simulator.nextSession()
        val close = simulator.answerWriting("hard", passed = true, clean = false, hintsUsed = 0)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, close.snapshot!!.rung)
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertEquals(1, simulator.currentItems().single().writingLevel)

        simulator.advanceTo(close.snapshot.dueAtMillis)
        simulator.nextSession()
        val corePass = simulator.answer("good")
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, corePass.snapshot!!.rung)
        assertEquals("rung_unchanged", corePass.trace.transition!!.movementReason)

        assertGolden("writeKanjiExitRequiresCleanWrites", simulator.renderText())
    }

    @Test
    fun demotionWithEmptyRelearningStepsMatchesGoldenTimeline() {
        // Goal 70: with empty relearning steps, a demoting third `again` whose
        // FSRS post-lapse interval is large (30d fake) is capped to now + 1 day
        // so the newly demoted, more-scaffolded rung is practiced soon.
        val almostDemoting = reviewCard("裂", RecordsBase.LadderRung.KANJI_MEANING, START)
            .copyBuilder()
            .realAgainStreak(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK - 1)
            .build()
        val simulator = SchedulerTimelineSimulator(
            scheduler = schedulerWithReviewIntervalDays(30),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(almostDemoting),
            startMillis = START,
            learningSettings = noRelearningSteps(),
        )

        simulator.nextSession()
        val answer = simulator.answer("again")

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, answer.snapshot!!.rung)
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(simulator.currentItems().single())!!.activeCore)
        assertTrue("Demoted first review capped to <= 1 day",
            answer.snapshot.dueAtMillis - START <= BridgeScheduler.DAY)
        assertGolden("demotionWithEmptyRelearningSteps", simulator.renderText())
    }

    @Test
    fun ceilingCardDemotesOneRungWhenColdMatchesGoldenTimeline() {
        // Goal 66 (rejected deep-demotion): pins the current single-step
        // demotion from the word_reading ceiling. A ceiling card that goes cold
        // demotes exactly one rung (to font_meaning) per fail-streak, not two.
        val almostDemoting = reviewCard("裂", RecordsBase.LadderRung.WORD_READING, START)
            .copyBuilder()
            .realAgainStreak(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK - 1)
            .build()
        val simulator = SchedulerTimelineSimulator(
            scheduler = BridgeScheduler(),
            rows = listOf(row("裂", 20)),
            startingItems = listOf(almostDemoting),
            startMillis = START,
            learningSettings = noRelearningSteps(),
        )

        simulator.nextSession()
        val answer = simulator.answer("again")

        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, answer.snapshot!!.rung)
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(simulator.currentItems().single())!!.activeCore)
        assertEquals("again_streak_demotes", answer.trace.transition!!.movementReason)
        assertGolden("ceilingCardDemotesOneRungWhenCold", simulator.renderText())
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
        assertFalse(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertEquals("again_streak_demotes", answer.trace.transition!!.movementReason)
        assertTrue(answer.trace.transition.reasonCodes.contains("similar_kanji_unavailable"))
        assertGolden("similarKanjiSkippedWithoutContent", simulator.renderText())
    }

    @Test
    fun kanjiReadingSkippedWithoutContentMatchesGoldenTimeline() {
        // Goal 78: a word_reading card without reading data demotes across the
        // content-less kanji_reading rung to font_meaning, recording the skip.
        val almostDemoting = reviewCard("裂", RecordsBase.LadderRung.WORD_READING, START)
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

        assertEquals(RecordsBase.LadderRung.FONT_MEANING, answer.snapshot!!.rung)
        assertFalse(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().single()))
        assertEquals("again_streak_demotes", answer.trace.transition!!.movementReason)
        assertTrue(answer.trace.transition.reasonCodes.contains("kanji_reading_unavailable"))
        assertGolden("kanjiReadingSkippedWithoutContent", simulator.renderText())
    }

    @Test
    fun kanjiReadingRungReachedWhenContentAvailable() {
        // With reading data, a word_reading fail-streak demotion lands on
        // kanji_reading (no skip), and a subsequent pass over the interval +
        // min-pass gates promotes back out to word_reading.
        val availability = RecordsBase.RungAvailability.of(false, true)
        val ladder = RecordsBase.StudyLadderSettings.defaults()
        val demoted = ladder.previousRung(RecordsBase.LadderRung.WORD_READING, availability)
        assertEquals(RecordsBase.LadderRung.KANJI_READING, demoted)
        val promoted = ladder.nextRung(RecordsBase.LadderRung.KANJI_READING, availability)
        assertEquals(RecordsBase.LadderRung.WORD_READING, promoted)
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
        assertFalse(AdaptiveStudyItemPolicy.isAdaptive(simulator.currentItems().first { it.phase == RecordsBase.SchedulerPhase.RELEARNING }))
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, next.trace.selected.rung)
        assertTrue(next.trace.skipped.first { it.rung == RecordsBase.LadderRung.FONT_MEANING }.reasonCodes.contains("same_family_hidden"))
        assertGolden("relearningBeatsSameFamilyReviewSibling", simulator.renderText())
    }

    @Test
    fun legacyLearningAndRelearningCanonicalizeOnlyWhenTheyGraduateToReview() {
        val newLearning = item("新").copyBuilder()
            .state("learning")
            .dueAtMillis(START)
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .build()
        val newSimulator = SchedulerTimelineSimulator(
            BridgeScheduler(), listOf(row("新", 20)), listOf(newLearning), START,
        )

        newSimulator.nextSession()
        val firstLearningStep = newSimulator.answer("good")
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, firstLearningStep.snapshot!!.phase)
        assertFalse(AdaptiveStudyItemPolicy.isAdaptive(newSimulator.currentItems().single()))
        newSimulator.advanceTo(firstLearningStep.snapshot.dueAtMillis)
        newSimulator.nextSession()
        val graduatedLearning = newSimulator.answer("good")
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, graduatedLearning.snapshot!!.phase)
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(newSimulator.currentItems().single()))

        val relearning = reviewCard("再", RecordsBase.LadderRung.FONT_MEANING, START)
            .copyBuilder()
            .state("learning")
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()
        val relearningSimulator = SchedulerTimelineSimulator(
            BridgeScheduler(), listOf(row("再", 20)), listOf(relearning), START,
        )

        relearningSimulator.nextSession()
        assertFalse(AdaptiveStudyItemPolicy.isAdaptive(relearningSimulator.currentItems().single()))
        val graduatedRelearning = relearningSimulator.answer("good")
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, graduatedRelearning.snapshot!!.phase)
        assertTrue(AdaptiveStudyItemPolicy.isAdaptive(relearningSimulator.currentItems().single()))
        assertEquals(
            CoreSkill.RECOGNITION,
            AdaptiveStudyItemPolicy.routeState(relearningSimulator.currentItems().single())!!.activeCore,
        )
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
            elapsedDays: Double,
            targetRetention: Double,
        ): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis)
        }
    }

    private companion object {
        const val START = 1_000L
    }
}
