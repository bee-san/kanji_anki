package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStudyPlannerTest {
    @Test
    fun defaultManualWorkloadProducesSmallParetoTarget() {
        val plan = plan(
            rows = rows(10),
            stats = AdaptiveReviewStats(),
            workloadPercent = 20,
        )

        assertEquals(20, plan.workloadPercent)
        assertTrue(plan.targetCount in 1..5)
        assertEquals(plan.targetCount, plan.newAdmissionLimit)
        assertFalse(plan.allKanjiMode)
    }

    @Test
    fun veryLowWorkloadProducesOneKanji() {
        val plan = plan(
            rows = rows(5),
            stats = AdaptiveReviewStats(total = 8, good = 8, writingRequired = 4),
            currentStreakDays = 5,
            workloadPercent = 0,
        )

        assertEquals(0, plan.workloadPercent)
        assertEquals(1, plan.targetCount)
        assertEquals(1, plan.newAdmissionLimit)
    }

    @Test
    fun allKanjiModeAdmitsAllCurrentCandidates() {
        val plan = plan(
            rows = rows(8),
            stats = AdaptiveReviewStats(total = 2, good = 2, writingRequired = 2),
            currentStreakDays = 2,
            workloadPercent = 100,
        )

        assertTrue(plan.allKanjiMode)
        assertEquals(8, plan.targetCount)
        assertEquals(8, plan.newAdmissionLimit)
        assertEquals(8, plan.focusKanji.size)
    }

    @Test
    fun emptyManualAllKanjiModeKeepsAllKanjiFlag() {
        val plan = plan(
            rows = emptyList(),
            stats = AdaptiveReviewStats(total = 2, good = 2, writingRequired = 2),
            workloadPercent = 100,
        )

        assertFalse(plan.autoMode)
        assertTrue(plan.allKanjiMode)
        assertEquals("No current problem kanji.", plan.status)
    }

    @Test
    fun highMissAndWritingFailureLowerTarget() {
        val steady = plan(
            rows = rows(20),
            stats = AdaptiveReviewStats(total = 10, good = 9, easy = 1, writingRequired = 8),
            currentStreakDays = 5,
            workloadPercent = 50,
        )
        val rough = plan(
            rows = rows(20),
            stats = AdaptiveReviewStats(total = 10, again = 5, hard = 2, good = 3, writingRequired = 8, writingFailed = 4),
            currentStreakDays = 5,
            workloadPercent = 50,
        )

        assertTrue(rough.targetCount < steady.targetCount)
    }

    @Test
    fun stableStreakWithLowMissesRaisesTargetSlightly() {
        val noStreak = plan(
            rows = rows(20),
            stats = AdaptiveReviewStats(total = 10, hard = 1, good = 8, easy = 1, writingRequired = 8),
            workloadPercent = 50,
        )
        val streak = plan(
            rows = rows(20),
            stats = AdaptiveReviewStats(total = 10, hard = 1, good = 8, easy = 1, writingRequired = 8),
            currentStreakDays = 4,
            workloadPercent = 50,
        )

        assertEquals(noStreak.targetCount + 1, streak.targetCount)
    }

    @Test
    fun overduePressurePreventsNewAdmissions() {
        val due = listOf(
            reviewed("字0", dueAtMillis = 0L),
            reviewed("字1", dueAtMillis = 0L),
            reviewed("字2", dueAtMillis = 0L),
        )

        val plan = plan(
            rows = rows(10),
            items = due,
            stats = AdaptiveReviewStats(),
            workloadPercent = 20,
        )

        assertEquals(0, plan.newAdmissionLimit)
        assertTrue(plan.focusKanji.contains("字0"))
        assertTrue(plan.focusKanji.contains("字1"))
        assertTrue(plan.focusKanji.contains("字2"))
    }

    @Test
    fun learningCardWithFutureDueStillCountsAsRemaining() {
        val now = 100_000L
        val learningFutureDue = item(
            kanji = "字0",
            state = StudyItemState.LEARNING,
            dueAtMillis = now + 60_000L,
            totalReviews = 1,
        )

        val plan = plan(
            rows = rows(1),
            items = listOf(learningFutureDue),
            studiedToday = setOf("字0"),
            workloadPercent = 0,
            nowMillis = now,
        )

        assertEquals(1, plan.remainingCount)
        assertFalse(plan.focusComplete())
    }

    @Test
    fun fsrsLowRetrievabilityOutranksOtherwiseSimilarKanji() {
        val weakFsrs = row("弱", weakness = 10, retrievability = 0.30, difficulty = 7.0, stability = 3.0, intervalDays = 10, reps = 2)
        val ordinary = row("普", weakness = 10, retrievability = 0.95, difficulty = 4.0, stability = 30.0, intervalDays = 10, reps = 2)

        val plan = plan(
            rows = listOf(ordinary, weakFsrs),
            workloadPercent = 0,
        )

        assertEquals("弱", plan.focusKanji.first())
    }

    @Test
    fun missingFsrsDataFallsBackToIntervalRepsAndLapses() {
        val shakyMature = row("揺", weakness = 10, intervalDays = 10, reps = 1)
        val betterSupported = row("支", weakness = 10, intervalDays = 40, reps = 12)

        val plan = plan(
            rows = listOf(betterSupported, shakyMature),
            workloadPercent = 0,
        )

        assertEquals("揺", plan.focusKanji.first())
    }

    @Test
    fun studiedKanjiReduceRemainingUnlessRecoveryIsStillDue() {
        val complete = plan(
            rows = rows(1),
            studiedToday = setOf("字0"),
            workloadPercent = 0,
        )
        val dueAgain = plan(
            rows = rows(1),
            items = listOf(reviewed("字0", dueAtMillis = 0L)),
            studiedToday = setOf("字0"),
            workloadPercent = 0,
        )

        assertEquals(0, complete.remainingCount)
        assertTrue(complete.focusComplete())
        assertEquals(1, dueAgain.remainingCount)
    }

    @Test
    fun autoWorkloadUsesFirstMajorParetoDropOff() {
        val plan = plan(
            rows = listOf(
                row("強", weakness = 42),
                row("重", weakness = 38),
                row("軽", weakness = 10),
                row("薄", weakness = 8),
            ),
            stats = AdaptiveReviewStats(total = 8, hard = 1, good = 7, writingRequired = 6),
            currentStreakDays = 1,
            workloadPercent = 20,
            mode = AdaptiveWorkloadMode.AUTO,
        )

        assertTrue(plan.autoMode)
        assertEquals(2, plan.targetCount)
        assertEquals(listOf("強", "重"), plan.focusKanji)
        assertTrue(plan.status.contains("drop-off"))
    }

    @Test
    fun autoWorkloadOrdersDropOffByCompositePriorityScore() {
        val plan = plan(
            rows = listOf(
                row("査", weakness = 1, retrievability = 0.75),
                row("濃", weakness = 100),
                row("濁", weakness = 90),
                row("薄", weakness = 10),
            ),
            stats = AdaptiveReviewStats(total = 8, hard = 1, good = 7, writingRequired = 6),
            currentStreakDays = 1,
            workloadPercent = 20,
            mode = AdaptiveWorkloadMode.AUTO,
        )

        assertEquals(2, plan.targetCount)
        assertEquals(listOf("濃", "濁"), plan.focusKanji)
    }

    @Test
    fun autoWorkloadRespectsMaxItems() {
        val plan = plan(
            rows = rows(12),
            stats = AdaptiveReviewStats(total = 8, hard = 1, good = 7, writingRequired = 6),
            currentStreakDays = 1,
            workloadPercent = 20,
            mode = AdaptiveWorkloadMode.AUTO,
            maxItems = 5,
        )

        assertEquals(5, plan.targetCount)
        assertEquals(5, plan.focusKanji.size)
        assertEquals(5, plan.newAdmissionLimit)
    }

    @Test
    fun dueRecoveryIsCappedByMaxItems() {
        val due = (0..5).map { index -> reviewed("字$index", dueAtMillis = 0L) }

        val plan = plan(
            rows = rows(8),
            items = due,
            stats = AdaptiveReviewStats(total = 8, hard = 1, good = 7, writingRequired = 6),
            currentStreakDays = 1,
            workloadPercent = 20,
            mode = AdaptiveWorkloadMode.AUTO,
            maxItems = 5,
        )

        assertEquals(5, plan.targetCount)
        assertEquals(5, plan.focusKanji.size)
        assertEquals(0, plan.newAdmissionLimit)
        assertFalse(plan.focusKanji.contains("字5"))
    }

    @Test
    fun manualAllKanjiModeIsLimitedByMaxItems() {
        val plan = plan(
            rows = rows(8),
            stats = AdaptiveReviewStats(total = 8, hard = 1, good = 7, writingRequired = 6),
            currentStreakDays = 1,
            workloadPercent = 100,
            maxItems = 5,
        )

        assertFalse(plan.allKanjiMode)
        assertEquals(5, plan.targetCount)
        assertEquals(5, plan.focusKanji.size)
        assertTrue(plan.status.contains("capped"))
    }

    @Test
    fun workloadLabelsAndCeilingsCoverBoundaries() {
        assertEquals(0, AdaptiveStudyPlanner.snapWorkloadPercent(-5))
        assertEquals(95, AdaptiveStudyPlanner.snapWorkloadPercent(98))
        assertEquals(100, AdaptiveStudyPlanner.snapWorkloadPercent(100))
        assertEquals(Int.MAX_VALUE, AdaptiveStudyPlanner.targetCeiling(100))
        assertEquals("Very little", AdaptiveStudyPlanner.workloadLabel(0))
        assertEquals("Pareto", AdaptiveStudyPlanner.workloadLabel(20))
        assertEquals("Balanced", AdaptiveStudyPlanner.workloadLabel(50))
        assertEquals("More", AdaptiveStudyPlanner.workloadLabel(95))
        assertEquals("All kanji", AdaptiveStudyPlanner.workloadLabel(100))
    }

    @Test
    fun nullPlanRequestFallsBackToEmptyManualDefaultPlan() {
        val plan = AdaptiveStudyPlanner().plan(null)

        assertFalse(plan.autoMode)
        assertEquals(AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT, plan.workloadPercent)
        assertEquals(0, plan.targetCount)
        assertTrue(plan.status.contains("No current problem"))
    }

    @Test
    fun fsrsRiskCoversPercentInvalidAndMatureStabilityBranches() {
        val percentRisk = row("百", weakness = 10, retrievability = 75.0, difficulty = 6.0, stability = 2.0, intervalDays = 10, reps = 5)
        val invalidRisk = row("無", weakness = 50, retrievability = 101.0, intervalDays = 3, reps = 1)
        val protectedMature = row("熟", weakness = 45, retrievability = 0.95, difficulty = 4.0, stability = 50.0, intervalDays = 50, reps = 10)

        val plan = plan(
            rows = listOf(invalidRisk, protectedMature, percentRisk),
            workloadPercent = 0,
        )

        assertEquals("百", plan.focusKanji.first())
    }

    private fun plan(
        rows: List<StudyDashboardRow>,
        items: List<StudyQueueItem> = emptyList(),
        stats: AdaptiveReviewStats = AdaptiveReviewStats(),
        currentStreakDays: Int = 0,
        studiedToday: Set<String> = emptySet(),
        workloadPercent: Int,
        mode: AdaptiveWorkloadMode = AdaptiveWorkloadMode.MANUAL,
        maxItems: Int = Int.MAX_VALUE,
        nowMillis: Long = 1_000L,
        settings: AdaptiveStudySettings = AdaptiveStudySettings(),
    ): AdaptiveStudyPlan = AdaptiveStudyPlanner().plan(
        AdaptiveStudyPlanRequest(
            rows = rows,
            items = items,
            recentStats = stats,
            currentStreakDays = currentStreakDays,
            studiedToday = studiedToday,
            workloadPolicy = AdaptiveWorkloadPolicy.of(mode, workloadPercent, maxItems),
            nowMillis = nowMillis,
            settings = settings,
        ),
    )

    private fun rows(count: Int): List<StudyDashboardRow> =
        (0 until count).map { index ->
            row("字$index", weakness = 20 - index, intervalDays = 3, reps = 1)
        }

    private fun row(
        kanji: String,
        weakness: Int,
        retrievability: Double? = null,
        difficulty: Double? = null,
        stability: Double? = null,
        intervalDays: Int = 3,
        reps: Int = 1,
    ): StudyDashboardRow {
        val mature = intervalDays >= AdaptiveStudySettings().matureDays
        return StudyDashboardRow(
            kanji = kanji,
            jitenRank = 900,
            primaryMeaning = "meaning",
            reading = "reading",
            browserSearch = "search",
            weaknessScore = weakness,
            reasonCode = "weak_support",
            reasonText = "reason",
            activeExampleCount = 1,
            suspendedExampleCount = 0,
            matureSupportCount = if (mature) 1 else 0,
            examples = listOf(
                StudyExample(
                    sourceType = "active",
                    expression = "${kanji}語",
                    reading = "よみ",
                    meaning = "meaning",
                    fsrsDifficulty = difficulty,
                    fsrsRetrievability = retrievability,
                    mature = mature,
                    intervalDays = intervalDays,
                    reps = reps,
                    fsrsStability = stability,
                ),
            ),
        )
    }

    private fun reviewed(
        kanji: String,
        dueAtMillis: Long,
    ): StudyQueueItem = item(
        kanji = kanji,
        state = StudyItemState.REVIEW,
        dueAtMillis = dueAtMillis,
        totalReviews = 2,
        writingLevel = 1,
    )

    private fun item(
        kanji: String,
        state: StudyItemState,
        dueAtMillis: Long,
        totalReviews: Int,
        lapses: Int = 0,
        writingLevel: Int = 1,
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = dueAtMillis,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = totalReviews,
        lapses = lapses,
        learningStep = 0,
        writingLevel = writingLevel,
    )
}
