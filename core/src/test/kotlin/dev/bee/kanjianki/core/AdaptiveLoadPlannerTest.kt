package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;

import org.junit.Assert.*;

class AdaptiveLoadPlannerTest {
    @Test
    fun defaultWorkloadProducesSmallParetoTarget() {
        val plan = plan(
                rows(10),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                20,
                1000L
        );

        assertEquals(20, plan.workloadPercent);
        assertTrue(plan.target >= 1);
        assertTrue(plan.target <= 5);
        assertEquals(plan.target, plan.newAdmissionLimit);
        assertFalse(plan.allKanjiMode);
    }

    @Test
    fun veryLowWorkloadProducesOneKanji() {
        val plan = plan(
                rows(5),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 0, 8, 0, 4, 0),
                5,
                emptySet(),
                0,
                1000L
        );

        assertEquals(0, plan.workloadPercent);
        assertEquals(1, plan.target);
        assertEquals(1, plan.newAdmissionLimit);
    }

    @Test
    fun allKanjiModeAdmitsAllCurrentCandidates() {
        val plan = plan(
                rows(8),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                2,
                emptySet(),
                100,
                1000L
        );

        assertTrue(plan.allKanjiMode);
        assertEquals(8, plan.target);
        assertEquals(8, plan.newAdmissionLimit);
        assertEquals(8, plan.focusKanji.size);
    }

    @Test
    fun emptyManualAllKanjiModeKeepsAllKanjiFlag() {
        val plan = plan(
                emptyList(),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                0,
                emptySet(),
                100,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertFalse(plan.autoMode);
        assertTrue(plan.allKanjiMode);
        assertEquals("No current problem kanji.", plan.status);
    }

    @Test
    fun highMissAndWritingFailureLowerTarget() {
        val steady = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 0, 0, 9, 1, 8, 0),
                5,
                emptySet(),
                50,
                1000L
        );
        val rough = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 5, 2, 3, 0, 8, 4),
                5,
                emptySet(),
                50,
                1000L
        );

        assertTrue(rough.target < steady.target);
    }

    @Test
    fun stableStreakWithLowMissesRaisesTargetSlightly() {
        val noStreak = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                0,
                emptySet(),
                50,
                1000L
        );
        val streak = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                4,
                emptySet(),
                50,
                1000L
        );

        assertEquals(noStreak.target + 1, streak.target);
    }

    @Test
    fun steadyStreakBoostRequiresLowHardAndWritingRates() {
        val boosted = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                4,
                emptySet(),
                50,
                1000L
        );
        val hardBlocked = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(4, 0, 2, 2, 0, 4, 0),
                4,
                emptySet(),
                50,
                1000L
        );
        val writingBlocked = plan(
                rows(20),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 1),
                4,
                emptySet(),
                50,
                1000L
        );

        assertEquals(8, boosted.target);
        assertEquals(6, hardBlocked.target);
        assertEquals(7, writingBlocked.target);
    }

    @Test
    fun overduePressurePreventsNewAdmissions() {
        val due = listOf(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L)
        );

        val plan = plan(
                rows(10),
                due,
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                20,
                1000L
        );

        assertEquals(0, plan.newAdmissionLimit);
        assertTrue(plan.focusKanji.contains("字0"));
        assertTrue(plan.focusKanji.contains("字1"));
        assertTrue(plan.focusKanji.contains("字2"));
    }

    @Test
    fun learningItemsCountAsRecoveryOnlyWhenDue() {
        val items = listOf(
                item("学", "learning", 999L, 0, 0),
                item("済", "retired", 0L, 10, 0),
                item("待", "review", 2000L, 10, 0)
        );

        val plan = plan(
                listOf(
                        row("学", 10, null, null, null, 3, 1),
                        row("済", 9, null, null, null, 3, 1),
                        row("待", 8, null, null, null, 3, 1)
                ),
                items,
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                0,
                setOf("済"),
                20,
                1000L
        );

        assertTrue(plan.focusKanji.contains("学"));
        assertEquals(2, plan.remaining);
    }

    @Test
    fun fsrsLowRetrievabilityOutranksOtherwiseSimilarKanji() {
        val weakFsrs = row("弱", 10, 0.30, 7.0, 3.0, 10, 2);
        val ordinary = row("普", 10, 0.95, 4.0, 30.0, 10, 2);

        val plan = plan(
                listOf(ordinary, weakFsrs),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("弱", plan.focusKanji.get(0));
    }

    @Test
    fun missingFsrsDataFallsBackToIntervalRepsAndLapses() {
        val shakyMature = row("揺", 10, null, null, null, 10, 1);
        val betterSupported = row("支", 10, null, null, null, 40, 12);

        val plan = plan(
                listOf(betterSupported, shakyMature),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("揺", plan.focusKanji.get(0));
    }

    @Test
    fun studiedKanjiReduceRemainingUnlessRecoveryIsStillDue() {
        val studied = HashSet<String>()
        studied.add("字0");

        val complete = plan(
                rows(1),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                studied,
                0,
                1000L
        );
        val dueAgain = plan(
                rows(1),
                listOf(reviewed("字0", 0L)),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                studied,
                0,
                1000L
        );

        assertEquals(0, complete.remaining);
        assertTrue(complete.focusComplete());
        assertEquals(1, dueAgain.remaining);
    }

    @Test
    fun learningCardWithFutureDueStillCountsAsRemaining() {
        // Simulates failing a card during study-ahead: the card enters
        // relearning with dueAtMillis in the future (e.g. +1 minute).
        // It should still count as "remaining" so the session does not
        // end prematurely with focusComplete().
        val now = 100_000L;
        val studied = HashSet<String>()
        studied.add("字0");

        // Card in learning state with due time in the future (relearning step)
        val learningFutureDue = item("字0", "learning", now + 60_000L, 1, 1);

        val plan = plan(
                rows(1),
                listOf(learningFutureDue),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                studied,
                0,
                now
        );

        assertEquals(1, plan.remaining);
        assertFalse(plan.focusComplete());
    }

    @Test
    fun autoWorkloadUsesPriorityMassConcentration() {
        val plan = plan(
                listOf(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1),
                        row("薄", 8, null, null, null, 3, 1)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.autoMode);
        assertEquals(2, plan.target);
        assertEquals(listOf("強", "重"), plan.focusKanji);
        assertTrue(plan.status.contains("concentrated"));
    }

    @Test
    fun autoWorkloadReportsConcentrationWhileWaitingForHistory() {
        val plan = plan(
                listOf(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1),
                        row("薄", 8, null, null, null, 3, 1)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.status.contains("concentrated"));
        assertTrue(plan.status.contains("starts small"));
    }

    @Test
    fun autoWorkloadOrdersConcentratedFocusByCompositePriorityScore() {
        val plan = plan(
                listOf(
                        row("査", 1, 0.75, null, null, 3, 1),
                        row("濃", 100, null, null, null, 3, 1),
                        row("濁", 90, null, null, null, 3, 1),
                        row("薄", 10, null, null, null, 3, 1)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(2, plan.target);
        assertEquals(listOf("濃", "濁"), plan.focusKanji);
    }

    @Test
    fun autoWorkloadFallsBackToSmallParetoFocusWhenCurveIsFlat() {
        val flat = listOf(
                row("字0", 40, null, null, null, 3, 1),
                row("字1", 38, null, null, null, 3, 1),
                row("字2", 36, null, null, null, 3, 1),
                row("字3", 34, null, null, null, 3, 1),
                row("字4", 32, null, null, null, 3, 1),
                row("字5", 30, null, null, null, 3, 1)
        );

        val plan = plan(
                flat,
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(AdaptiveLoadPlanner.targetCeiling(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT), plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    fun autoWorkloadFallsBackWhenPriorityScoresAreZero() {
        val plan = plan(
                listOf(
                        row("穏", 0, null, null, null, 45, 12),
                        row("静", 0, null, null, null, 45, 12)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals(2, plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    fun autoWorkloadIncludesDueRecoveryBeforeNewAdmissions() {
        val due = listOf(
                reviewed("復", 0L),
                reviewed("習", 0L)
        );

        val plan = plan(
                listOf(
                        row("復", 9, null, null, null, 3, 1),
                        row("習", 8, null, null, null, 3, 1),
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1)
                ),
                due,
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.focusKanji.contains("復"));
        assertTrue(plan.focusKanji.contains("習"));
        assertEquals(3, plan.newAdmissionLimit);
    }

    @Test
    fun autoWorkloadStillShrinksWhenRecentReviewsAreRough() {
        val steep = listOf(
                row("強", 42, null, null, null, 3, 1),
                row("重", 38, null, null, null, 3, 1),
                row("固", 36, null, null, null, 3, 1),
                row("軽", 10, null, null, null, 3, 1)
        );
        val steady = plan(
                steep,
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );
        val rough = plan(
                steep,
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 4, 2, 2, 0, 6, 3),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(rough.target < steady.target);
    }

    @Test
    fun manualModeStillHonorsAllKanjiOverride() {
        val plan = plan(
                rows(8),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                2,
                emptySet(),
                100,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertFalse(plan.autoMode);
        assertTrue(plan.allKanjiMode);
        assertEquals(8, plan.target);
    }

    @Test
    fun manualModeStatusCoversNoHistoryRecoveryAndFullRange() {
        val noHistory = plan(
                rows(4),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                20,
                1000L
        );
        val recovery = plan(
                rows(2),
                listOf(reviewed("字0", 0L), reviewed("字1", 0L)),
                RecordsSchedulerModels.ReviewStats(6, 0, 0, 6, 0, 4, 0),
                0,
                emptySet(),
                5,
                1000L
        );
        val fullRange = plan(
                rows(4),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                5,
                emptySet(),
                5,
                1000L
        );

        assertTrue(noHistory.status.contains("starts small"));
        assertTrue(recovery.status.contains("Due recovery"));
        assertTrue(fullRange.status.contains("full focus range"));
    }

    @Test
    fun nullInputsAndModeHelpersFallBackSafely() {
        val plan = plan(
                null,
                null,
                null,
                0,
                null,
                17,
                null,
                1000L,
                null
        );

        assertTrue(plan.autoMode);
        assertEquals(15, plan.workloadPercent);
        assertEquals(0, plan.target);
        assertEquals("No current problem kanji.", plan.status);
        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, AdaptiveLoadPlanner.normalizeWorkloadMode("unexpected"));
        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, AdaptiveLoadPlanner.normalizeWorkloadMode(AdaptiveLoadPlanner.MODE_MANUAL));
        assertEquals(AdaptiveLoadPlanner.WorkloadMode.AUTO, AdaptiveLoadPlanner.WorkloadMode.fromSetting("unexpected"));
        assertEquals(AdaptiveLoadPlanner.WorkloadMode.MANUAL, AdaptiveLoadPlanner.WorkloadMode.fromSetting(AdaptiveLoadPlanner.MODE_MANUAL));
        assertTrue(AdaptiveLoadPlanner.isAutoMode(null));
    }

    @Test
    fun workloadLabelsAndCeilingsCoverBoundaries() {
        assertEquals(0, AdaptiveLoadPlanner.snapWorkloadPercent(-5));
        assertEquals(95, AdaptiveLoadPlanner.snapWorkloadPercent(98));
        assertEquals(100, AdaptiveLoadPlanner.snapWorkloadPercent(100));
        assertEquals(Integer.MAX_VALUE, AdaptiveLoadPlanner.targetCeiling(100));
        assertEquals("Very little", AdaptiveLoadPlanner.workloadLabel(0));
        assertEquals("Pareto", AdaptiveLoadPlanner.workloadLabel(20));
        assertEquals("Balanced", AdaptiveLoadPlanner.workloadLabel(50));
        assertEquals("More", AdaptiveLoadPlanner.workloadLabel(95));
        assertEquals("All kanji", AdaptiveLoadPlanner.workloadLabel(100));
    }

    @Test
    fun autoModeCoversRecoveryAndSteadyStreakStatusBranches() {
        val steep = listOf(
                row("強", 42, null, null, null, 3, 1),
                row("重", 38, null, null, null, 3, 1),
                row("軽", 10, null, null, null, 3, 1)
        );
        val spreadSix = listOf(
                row("字0", 40, null, null, null, 3, 1),
                row("字1", 38, null, null, null, 3, 1),
                row("字2", 36, null, null, null, 3, 1),
                row("字3", 34, null, null, null, 3, 1),
                row("字4", 32, null, null, null, 3, 1),
                row("字5", 30, null, null, null, 3, 1)
        );
        val recoveryOnly = plan(
                steep,
                listOf(reviewed("強", 0L), reviewed("重", 0L), reviewed("軽", 0L)),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );
        val steady = plan(
                spreadSix,
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                5,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(recoveryOnly.status.contains("Due recovery"));
        assertTrue(steady.status.contains("steady streak"));
    }

    @Test
    fun autoWorkloadRespectsMaxItems() {
        val plan = plan(
                rows(12),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size);
        assertEquals(5, plan.newAdmissionLimit);
    }

    @Test
    fun dueRecoveryIsCappedByMaxItems() {
        val due = listOf(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L),
                reviewed("字3", 0L),
                reviewed("字4", 0L),
                reviewed("字5", 0L)
        );

        val plan = plan(
                rows(8),
                due,
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size);
        assertEquals(0, plan.newAdmissionLimit);
        assertFalse(plan.focusKanji.contains("字5"));
    }

    @Test
    fun manualAllKanjiModeIsLimitedByMaxItems() {
        val plan = plan(
                rows(8),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                100,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertFalse(plan.allKanjiMode);
        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size);
        assertTrue(plan.status.contains("capped"));
    }

    @Test
    fun manualAllKanjiModeWithMaxAboveRowsStaysUncapped() {
        val plan = plan(
                rows(4),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                100,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                20,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.allKanjiMode);
        assertEquals(4, plan.target);
        assertTrue(plan.status.contains("All current problem kanji"));
    }

    @Test
    fun fsrsRiskCoversInvalidPercentAndMatureStabilityBranches() {
        val percentRisk = row("百", 10, 75.0, 6.0, 2.0, 10, 5);
        val invalidRisk = row("無", 50, 101.0, null, null, 3, 1);
        val protectedMature = row("熟", 45, 0.95, 4.0, 50.0, 50, 10);

        val plan = plan(
                listOf(invalidRisk, protectedMature, percentRisk),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("百", plan.focusKanji.get(0));
    }

    @Test
    fun typedPlanRequestBuilderShapesPolicyAndSettings() {
        val settings = RecordsSyncModels.Settings.kikuDefaults();
        val request = AdaptiveLoadPlanner.PlanRequest.builder(
                        rows(2),
                        emptyList(),
                        RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                        0,
                        emptySet(),
                        AdaptiveLoadPlanner.WorkloadPolicy.of(AdaptiveLoadPlanner.WorkloadMode.MANUAL, 20, 1),
                        1000L
                )
                .settings(settings)
                .build();

        val manual = plan(request);
        val fromSettings = plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                rows(2),
                                emptyList(),
                                RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                                0,
                                emptySet(),
                                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(20, AdaptiveLoadPlanner.MODE_AUTO, 1),
                                1000L
                        )
                        .settings(settings)
                        .build()
        );

        assertFalse(manual.autoMode);
        assertEquals(1, manual.target);
        assertTrue(fromSettings.autoMode);
        assertEquals(1, fromSettings.target);
    }

    @Test
    fun nullPlanRequestFallsBackToEmptyDefaultPlan() {
        val plan = plan(null);

        assertFalse(plan.autoMode);
        assertEquals(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT, plan.workloadPercent);
        assertEquals(0, plan.target);
        assertTrue(plan.status.contains("No current problem"));
    }

    @Test
    fun targetAdjustmentCoversModerateMissesHardRateAndFutureLearning() {
        val futureLearning = item("字0", "learning", 5000L, 0, 0);
        val dueUnreviewedReview = item("字1", "review", 0L, 0, 0);
        val studied = HashSet<String>()
        studied.add("字0");

        val moderateMisses = plan(
                rows(10),
                listOf(futureLearning),
                RecordsSchedulerModels.ReviewStats(4, 1, 0, 3, 0, 4, 0),
                0,
                studied,
                20,
                1000L
        );
        val hardHeavy = plan(
                rows(10),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(4, 0, 2, 2, 0, 4, 0),
                0,
                emptySet(),
                20,
                1000L
        );
        val unreviewedReview = plan(
                rows(10),
                listOf(dueUnreviewedReview),
                RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                emptySet(),
                20,
                1000L
        );

        assertTrue(moderateMisses.target <= 3);
        assertTrue(hardHeavy.target <= 3);
        // The future-learning card is still actively in a learning cycle,
        // so it counts as remaining (session should not end prematurely).
        assertTrue(moderateMisses.remaining <= moderateMisses.target);
        assertFalse(unreviewedReview.focusKanji.isEmpty());
    }

    @Test
    fun autoNoHistoryWithoutDropUsesSmallStartStatus() {
        val plan = plan(
                listOf(
                        row("穏", 0, null, null, null, 45, 12),
                        row("静", 0, null, null, null, 45, 12)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.status.contains("Auto Pareto starts small"));
    }

    @Test
    fun fsrsRiskCoversNegativeRetrievabilityAndIntervalFallback() {
        val intervalFallback = row("間", 10, null, null, null, 3, 9);
        val negativeRetrievability = row("負", 50, -0.1, null, null, 30, 9);

        val plan = plan(
                listOf(negativeRetrievability, intervalFallback),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("間", plan.focusKanji.get(0));
    }

    @Test
    fun fsrsRiskDoesNotProtectImmatureHighStabilityExamples() {
        val immatureHighStability = row("未", 10, 0.95, null, 50.0, 3, 9);
        val protectedMature = row("熟", 10, 0.95, null, 50.0, 50, 9);

        val plan = plan(
                listOf(protectedMature, immatureHighStability),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("未", plan.focusKanji.get(0));
    }

    @Test
    fun autoParetoTreatsGentleDecayAsSpreadPriority() {
        val plan = plan(
                listOf(
                        row("十", 10, null, null, null, 3, 1),
                        row("七", 7, null, null, null, 3, 1),
                        row("一", 1, null, null, null, 3, 1)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals(3, plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    fun fsrsRiskProtectsOnlyVeryStableMatureExamples() {
        val matureWithoutProtection = row("並", 10, 0.95, 10.0, 30.0, 50, 9);
        val protectedMature = row("熟", 10, 0.95, 10.0, 90.0, 90, 9);

        val plan = plan(
                listOf(protectedMature, matureWithoutProtection),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals("並", plan.focusKanji.get(0));
    }

    @Test
    fun autoParetoConcentratesOnDominantHeadAboveWeakTail() {
        val plan = plan(
                listOf(
                        row("十", 10, null, null, null, 3, 1),
                        row("零", 0, null, null, null, 45, 12),
                        row("空", 0, null, null, null, 45, 12)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals(1, plan.target);
        assertEquals(listOf("十"), plan.focusKanji);
    }

    @Test
    fun recentReadingExposurePromotesFocusCandidate() {
        val exposed = ReadingExposureModels.ExposureIndex(
                listOf(ReadingExposureModels.KanjiStats("読", 60, 15, 25, 40, 1000L))
        );
        val plan = plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                listOf(
                                        row("未", 20, null, null, null, 3, 1),
                                        row("読", 5, null, null, null, 3, 1)
                                ),
                                emptyList(),
                                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                                0,
                                emptySet(),
                                AdaptiveLoadPlanner.WorkloadPolicy.of(AdaptiveLoadPlanner.WorkloadMode.MANUAL, 0, 2),
                                1000L
                        )
                        .settings(RecordsSyncModels.Settings.kikuDefaults())
                        .readingExposure(exposed)
                        .build()
        );

        assertEquals("読", plan.focusKanji.get(0));
    }

    @Test
    fun lightReadingExposureDoesNotOverrideHigherRiskCandidate() {
        val exposed = ReadingExposureModels.ExposureIndex(
                listOf(ReadingExposureModels.KanjiStats("読", 1, 1, 1, 1, 1000L))
        );
        val plan = plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                listOf(
                                        row("危", 5, 0.10, 8.0, 1.0, 3, 10),
                                        row("読", 5, null, null, null, 3, 1)
                                ),
                                emptyList(),
                                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                                0,
                                emptySet(),
                                AdaptiveLoadPlanner.WorkloadPolicy.of(AdaptiveLoadPlanner.WorkloadMode.MANUAL, 0, 1),
                                1000L
                        )
                        .settings(RecordsSyncModels.Settings.kikuDefaults())
                        .readingExposure(exposed)
                        .build()
        );

        assertEquals("危", plan.focusKanji.get(0));
    }

    @Test
    fun dueRecoveryStaysAheadOfReadingExposure() {
        val exposed = ReadingExposureModels.ExposureIndex(
                listOf(ReadingExposureModels.KanjiStats("読", 200, 60, 80, 120, 1000L))
        );
        val plan = plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                listOf(
                                        row("復", 1, null, null, null, 3, 1),
                                        row("読", 1, null, null, null, 3, 1)
                                ),
                                listOf(reviewed("復", 0L)),
                                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                                0,
                                emptySet(),
                                AdaptiveLoadPlanner.WorkloadPolicy.of(AdaptiveLoadPlanner.WorkloadMode.MANUAL, 0, 2),
                                1000L
                        )
                        .settings(RecordsSyncModels.Settings.kikuDefaults())
                        .readingExposure(exposed)
                        .build()
        );

        assertEquals("復", plan.focusKanji.get(0));
    }

    @Test
    fun frequencyValuePrefersCommonKanjiOnOtherwiseEqualEvidence() {
        val common = rowWithRank("常", 50, 10, 0);
        val rare = rowWithRank("稀", 2800, 10, 0);

        val plan = plan(
                listOf(rare, common),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("常", plan.focusKanji.get(0));
    }

    @Test
    fun concentratedFocusShrinksUnderRecentReviewStrain() {
        val plan = plan(
                listOf(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1),
                        row("薄", 8, null, null, null, 3, 1)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 4, 2, 2, 0, 6, 3),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(1, plan.target);
        assertTrue(plan.status.contains("concentrated"));
        assertTrue(plan.status.contains("review strain"));
    }

    @Test
    fun concentratedFocusGrowsOnSteadyStreak() {
        val plan = plan(
                listOf(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1),
                        row("薄", 8, null, null, null, 3, 1)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                5,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(3, plan.target);
        assertTrue(plan.status.contains("concentrated"));
        assertTrue(plan.status.contains("steady streak"));
    }

    @Test
    fun unknownRankEarnsNoFrequencyValue() {
        val ranked = rowWithRank("有", 900, 10, 0);
        val unranked = rowWithRank("無", null, 10, 0);
        val zeroRanked = rowWithRank("零", 0, 10, 0);

        val plan = plan(
                listOf(unranked, zeroRanked, ranked),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("有", plan.focusKanji.get(0));
    }

    @Test
    fun mostOverdueDueKanjiWinsFocusSlotOverFresherHigherRiskDue() {
        val dayMillis = 86_400_000L;
        val now = 30L * dayMillis;
        val longOverdueLowRisk = reviewed("古", now - 10L * dayMillis);
        val freshHighRisk = reviewed("新", now - 3_600_000L);

        val plan = plan(
                listOf(
                        rowWithRank("古", 900, 5, 0),
                        row("新", 5, 0.10, 8.0, 1.0, 3, 10)
                ),
                listOf(longOverdueLowRisk, freshHighRisk),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                1,
                now,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(listOf("古"), plan.focusKanji);
    }

    @Test
    fun dueBacklogOverflowIsSurfacedInStatus() {
        val due = listOf(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L),
                reviewed("字3", 0L),
                reviewed("字4", 0L),
                reviewed("字5", 0L)
        );

        val plan = plan(
                rows(8),
                due,
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.status.contains("1 due kanji waits beyond today's cap"));
        assertTrue(plan.status.contains("Continue all kanji"));
    }

    @Test
    fun dueBacklogOverflowUsesPluralCopy() {
        val due = listOf(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L)
        );

        val plan = plan(
                rows(4),
                due,
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                1,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.status.contains("2 due kanji wait beyond today's cap"));
    }

    @Test
    fun suspendedEvidenceIsCountedOnceThroughWeaknessScore() {
        // The analyzer already prices suspended examples into weaknessScore.
        // The planner must not add a second suspended-count bonus on top, so
        // a slightly weaker row with extra suspended examples cannot outrank
        // a clearly weaker row.
        val strongerWeakness = rowWithRank("重", 900, 24, 0);
        val extraSuspended = rowWithRank("軽", 900, 20, 2);

        val plan = plan(
                listOf(extraSuspended, strongerWeakness),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                emptySet(),
                0,
                1000L
        );

        assertEquals("重", plan.focusKanji.get(0));
    }

    @Test
    fun allZeroPriorityWithUnknownRanksFallsBackToSmallFocus() {
        val plan = plan(
                listOf(
                        rowWithRank("穏", null, 0, 0),
                        rowWithRank("静", null, 0, 0)
                ),
                emptyList(),
                RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals(2, plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    private fun planner(): AdaptiveLoadPlanner {
        return AdaptiveLoadPlanner();
    }

    private fun plan(request: AdaptiveLoadPlanner.PlanRequest?): RecordsSchedulerModels.AdaptiveLoadPlan {
        return planner().plan(request);
    }

    private fun plan(

            rows: kotlin.collections.List<RecordsImportModels.DashboardRow>?,

            items: kotlin.collections.List<RecordsStudyModels.StudyItem>?,

            recentStats: RecordsSchedulerModels.ReviewStats?,

            currentStreakDays: Int,

            studiedToday: kotlin.collections.Set<String>?,

            workloadPercent: Int,

            nowMillis: Long

    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, nowMillis, RecordsSyncModels.Settings.kikuDefaults());
    }

    private fun plan(

            rows: kotlin.collections.List<RecordsImportModels.DashboardRow>?,

            items: kotlin.collections.List<RecordsStudyModels.StudyItem>?,

            recentStats: RecordsSchedulerModels.ReviewStats?,

            currentStreakDays: Int,

            studiedToday: kotlin.collections.Set<String>?,

            workloadPercent: Int,

            nowMillis: Long,

            settings: RecordsSyncModels.Settings?

    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, AdaptiveLoadPlanner.WorkloadMode.MANUAL, nowMillis, settings);
    }

    private fun plan(

            rows: kotlin.collections.List<RecordsImportModels.DashboardRow>?,

            items: kotlin.collections.List<RecordsStudyModels.StudyItem>?,

            recentStats: RecordsSchedulerModels.ReviewStats?,

            currentStreakDays: Int,

            studiedToday: kotlin.collections.Set<String>?,

            workloadPercent: Int,

            workloadMode: AdaptiveLoadPlanner.WorkloadMode?,

            nowMillis: Long,

            settings: RecordsSyncModels.Settings?

    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, workloadMode, Integer.MAX_VALUE, nowMillis, settings);
    }

    private fun plan(

            rows: kotlin.collections.List<RecordsImportModels.DashboardRow>?,

            items: kotlin.collections.List<RecordsStudyModels.StudyItem>?,

            recentStats: RecordsSchedulerModels.ReviewStats?,

            currentStreakDays: Int,

            studiedToday: kotlin.collections.Set<String>?,

            workloadPercent: Int,

            workloadMode: AdaptiveLoadPlanner.WorkloadMode?,

            maxItems: Int,

            nowMillis: Long,

            settings: RecordsSyncModels.Settings?

    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return planner().plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                rows,
                                items,
                                recentStats,
                                currentStreakDays,
                                studiedToday,
                                AdaptiveLoadPlanner.WorkloadPolicy.of(workloadMode, workloadPercent, maxItems),
                                nowMillis
                        )
                        .settings(settings)
                        .build()
        );
    }

    private fun rows(

            count: Int

    ): kotlin.collections.List<RecordsImportModels.DashboardRow> {
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        for (i in 0 until count) {
            rows.add(row("字" + i, 20 - i, null, null, null, 3, 1));
        }
        return rows.toList();
    }

    private fun row(

            kanji: String,

            weakness: Int,

            retrievability: Double?,

            difficulty: Double?,

            stability: Double?,

            intervalDays: Int,

            reps: Int

    ): RecordsImportModels.DashboardRow {
        val example = RecordsImportModels.Example(
                "active",
                kanji[0].code.toLong(),
                kanji[0].code.toLong(),
                kanji + "語",
                "よみ",
                "meaning",
                kanji + "を見た。",
                intervalDays >= RecordsSyncModels.Settings.kikuDefaults().matureDays,
                0,
                intervalDays,
                reps,
                stability,
                difficulty,
                retrievability
        );
        return RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                weakness,
                "weak_support",
                "reason",
                1,
                0,
                if (intervalDays >= RecordsSyncModels.Settings.kikuDefaults().matureDays) 1 else 0,
                listOf(example)
        );
    }

    private fun rowWithRank(

            kanji: String,

            rank: Int?,

            weakness: Int,

            suspendedCount: Int

    ): RecordsImportModels.DashboardRow {
        val example = RecordsImportModels.Example(
                "active",
                kanji[0].code.toLong(),
                kanji[0].code.toLong(),
                kanji + "語",
                "よみ",
                "meaning",
                kanji + "を見た。",
                true,
                0,
                45,
                12,
                null,
                null,
                null
        );
        return RecordsImportModels.DashboardRow(
                kanji,
                rank,
                "meaning",
                "reading",
                "search",
                weakness,
                "weak_support",
                "reason",
                1,
                suspendedCount,
                1,
                listOf(example)
        );
    }

    private fun reviewed(

            kanji: String,

            dueAt: Long

    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", dueAt, 1.0, 5.0, 2, 0, 2, 1, null, 0L);
    }

    private fun item(

            kanji: String,

            state: String,

            dueAt: Long,

            totalReviews: Int,

            lapses: Int

    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, state, dueAt, 1.0, 5.0, totalReviews, lapses, 0, 1, null, 0L);
    }

    private fun settingsWithMatureSupport(

            matureSupportThreshold: Int

    ): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults();
        return RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays
        );
    }
}
