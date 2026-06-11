package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
