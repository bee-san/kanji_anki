package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdaptiveLoadPlannerTest {
    @Test
    public void defaultWorkloadProducesSmallParetoTarget() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(10),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
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
    public void veryLowWorkloadProducesOneKanji() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(5),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 0, 8, 0, 4, 0),
                5,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals(0, plan.workloadPercent);
        assertEquals(1, plan.target);
        assertEquals(1, plan.newAdmissionLimit);
    }

    @Test
    public void allKanjiModeAdmitsAllCurrentCandidates() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(8),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                2,
                Collections.emptySet(),
                100,
                1000L
        );

        assertTrue(plan.allKanjiMode);
        assertEquals(8, plan.target);
        assertEquals(8, plan.newAdmissionLimit);
        assertEquals(8, plan.focusKanji.size());
    }

    @Test
    public void emptyManualAllKanjiModeKeepsAllKanjiFlag() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Collections.emptyList(),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                0,
                Collections.emptySet(),
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
    public void highMissAndWritingFailureLowerTarget() {
        RecordsSchedulerModels.AdaptiveLoadPlan steady = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(10, 0, 0, 9, 1, 8, 0),
                5,
                Collections.emptySet(),
                50,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan rough = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(10, 5, 2, 3, 0, 8, 4),
                5,
                Collections.emptySet(),
                50,
                1000L
        );

        assertTrue(rough.target < steady.target);
    }

    @Test
    public void stableStreakWithLowMissesRaisesTargetSlightly() {
        RecordsSchedulerModels.AdaptiveLoadPlan noStreak = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                0,
                Collections.emptySet(),
                50,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan streak = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                4,
                Collections.emptySet(),
                50,
                1000L
        );

        assertEquals(noStreak.target + 1, streak.target);
    }

    @Test
    public void steadyStreakBoostRequiresLowHardAndWritingRates() {
        RecordsSchedulerModels.AdaptiveLoadPlan boosted = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                4,
                Collections.emptySet(),
                50,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan hardBlocked = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(4, 0, 2, 2, 0, 4, 0),
                4,
                Collections.emptySet(),
                50,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan writingBlocked = plan(
                rows(20),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 1),
                4,
                Collections.emptySet(),
                50,
                1000L
        );

        assertEquals(8, boosted.target);
        assertEquals(6, hardBlocked.target);
        assertEquals(7, writingBlocked.target);
    }

    @Test
    public void overduePressurePreventsNewAdmissions() {
        List<RecordsStudyModels.StudyItem> due = Arrays.asList(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L)
        );

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(10),
                due,
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                1000L
        );

        assertEquals(0, plan.newAdmissionLimit);
        assertTrue(plan.focusKanji.contains("字0"));
        assertTrue(plan.focusKanji.contains("字1"));
        assertTrue(plan.focusKanji.contains("字2"));
    }

    @Test
    public void learningItemsCountAsRecoveryOnlyWhenDue() {
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(
                item("学", "learning", 999L, 0, 0),
                item("済", "retired", 0L, 10, 0),
                item("待", "review", 2000L, 10, 0)
        );

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("学", 10, null, null, null, 3, 1),
                        row("済", 9, null, null, null, 3, 1),
                        row("待", 8, null, null, null, 3, 1)
                ),
                items,
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                0,
                Collections.singleton("済"),
                20,
                1000L
        );

        assertTrue(plan.focusKanji.contains("学"));
        assertEquals(2, plan.remaining);
    }

    @Test
    public void fsrsLowRetrievabilityOutranksOtherwiseSimilarKanji() {
        RecordsImportModels.DashboardRow weakFsrs = row("弱", 10, 0.30, 7.0, 3.0, 10, 2);
        RecordsImportModels.DashboardRow ordinary = row("普", 10, 0.95, 4.0, 30.0, 10, 2);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(ordinary, weakFsrs),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("弱", plan.focusKanji.get(0));
    }

    @Test
    public void missingFsrsDataFallsBackToIntervalRepsAndLapses() {
        RecordsImportModels.DashboardRow shakyMature = row("揺", 10, null, null, null, 10, 1);
        RecordsImportModels.DashboardRow betterSupported = row("支", 10, null, null, null, 40, 12);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(betterSupported, shakyMature),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("揺", plan.focusKanji.get(0));
    }

    @Test
    public void studiedKanjiReduceRemainingUnlessRecoveryIsStillDue() {
        HashSet<String> studied = new HashSet<>();
        studied.add("字0");

        RecordsSchedulerModels.AdaptiveLoadPlan complete = plan(
                rows(1),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                studied,
                0,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan dueAgain = plan(
                rows(1),
                Collections.singletonList(reviewed("字0", 0L)),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
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
    public void learningCardWithFutureDueStillCountsAsRemaining() {
        // Simulates failing a card during study-ahead: the card enters
        // relearning with dueAtMillis in the future (e.g. +1 minute).
        // It should still count as "remaining" so the session does not
        // end prematurely with focusComplete().
        long now = 100_000L;
        HashSet<String> studied = new HashSet<>();
        studied.add("字0");

        // Card in learning state with due time in the future (relearning step)
        RecordsStudyModels.StudyItem learningFutureDue = item("字0", "learning", now + 60_000L, 1, 1);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(1),
                Collections.singletonList(learningFutureDue),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                studied,
                0,
                now
        );

        assertEquals(1, plan.remaining);
        assertFalse(plan.focusComplete());
    }

    @Test
    public void autoWorkloadUsesFirstMajorParetoDropOff() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1),
                        row("薄", 8, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.autoMode);
        assertEquals(2, plan.target);
        assertEquals(Arrays.asList("強", "重"), plan.focusKanji);
        assertTrue(plan.status.contains("drop-off"));
    }

    @Test
    public void autoWorkloadReportsDropOffWhileWaitingForHistory() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.status.contains("drop-off"));
        assertTrue(plan.status.contains("starts small"));
    }

    @Test
    public void autoWorkloadOrdersDropOffByCompositePriorityScore() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("査", 1, 0.75, null, null, 3, 1),
                        row("濃", 100, null, null, null, 3, 1),
                        row("濁", 90, null, null, null, 3, 1),
                        row("薄", 10, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(2, plan.target);
        assertEquals(Arrays.asList("濃", "濁"), plan.focusKanji);
    }

    @Test
    public void autoWorkloadFallsBackToSmallParetoFocusWhenCurveIsFlat() {
        List<RecordsImportModels.DashboardRow> flat = Arrays.asList(
                row("字0", 40, null, null, null, 3, 1),
                row("字1", 38, null, null, null, 3, 1),
                row("字2", 36, null, null, null, 3, 1),
                row("字3", 34, null, null, null, 3, 1),
                row("字4", 32, null, null, null, 3, 1),
                row("字5", 30, null, null, null, 3, 1)
        );

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                flat,
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(AdaptiveLoadPlanner.targetCeiling(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT), plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    public void autoWorkloadFallsBackWhenPriorityScoresAreZero() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("穏", 0, null, null, null, 45, 12),
                        row("静", 0, null, null, null, 45, 12)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals(2, plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    public void autoWorkloadIncludesDueRecoveryBeforeNewAdmissions() {
        List<RecordsStudyModels.StudyItem> due = Arrays.asList(
                reviewed("復", 0L),
                reviewed("習", 0L)
        );

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("復", 9, null, null, null, 3, 1),
                        row("習", 8, null, null, null, 3, 1),
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1)
                ),
                due,
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(plan.focusKanji.contains("復"));
        assertTrue(plan.focusKanji.contains("習"));
        assertEquals(2, plan.newAdmissionLimit);
    }

    @Test
    public void autoWorkloadStillShrinksWhenRecentReviewsAreRough() {
        List<RecordsImportModels.DashboardRow> steep = Arrays.asList(
                row("強", 42, null, null, null, 3, 1),
                row("重", 38, null, null, null, 3, 1),
                row("固", 36, null, null, null, 3, 1),
                row("軽", 10, null, null, null, 3, 1)
        );
        RecordsSchedulerModels.AdaptiveLoadPlan steady = plan(
                steep,
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );
        RecordsSchedulerModels.AdaptiveLoadPlan rough = plan(
                steep,
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 4, 2, 2, 0, 6, 3),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(rough.target < steady.target);
    }

    @Test
    public void manualModeStillHonorsAllKanjiOverride() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(8),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                2,
                Collections.emptySet(),
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
    public void manualModeStatusCoversNoHistoryRecoveryAndFullRange() {
        RecordsSchedulerModels.AdaptiveLoadPlan noHistory = plan(
                rows(4),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan recovery = plan(
                rows(2),
                Arrays.asList(reviewed("字0", 0L), reviewed("字1", 0L)),
                new RecordsSchedulerModels.ReviewStats(6, 0, 0, 6, 0, 4, 0),
                0,
                Collections.emptySet(),
                5,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan fullRange = plan(
                rows(4),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                5,
                Collections.emptySet(),
                5,
                1000L
        );

        assertTrue(noHistory.status.contains("starts small"));
        assertTrue(recovery.status.contains("Due recovery"));
        assertTrue(fullRange.status.contains("full focus range"));
    }

    @Test
    public void nullInputsAndModeHelpersFallBackSafely() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
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
    public void workloadLabelsAndCeilingsCoverBoundaries() {
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
    public void autoModeCoversRecoveryAndSteadyStreakStatusBranches() {
        List<RecordsImportModels.DashboardRow> steep = Arrays.asList(
                row("強", 42, null, null, null, 3, 1),
                row("重", 38, null, null, null, 3, 1),
                row("軽", 10, null, null, null, 3, 1)
        );
        RecordsSchedulerModels.AdaptiveLoadPlan recoveryOnly = plan(
                steep,
                Arrays.asList(reviewed("強", 0L), reviewed("重", 0L), reviewed("軽", 0L)),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );
        RecordsSchedulerModels.AdaptiveLoadPlan steady = plan(
                steep,
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                5,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertTrue(recoveryOnly.status.contains("Due recovery"));
        assertTrue(steady.status.contains("steady streak"));
    }

    @Test
    public void autoWorkloadRespectsMaxItems() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(12),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size());
        assertEquals(5, plan.newAdmissionLimit);
    }

    @Test
    public void dueRecoveryIsCappedByMaxItems() {
        List<RecordsStudyModels.StudyItem> due = Arrays.asList(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L),
                reviewed("字3", 0L),
                reviewed("字4", 0L),
                reviewed("字5", 0L)
        );

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(8),
                due,
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size());
        assertEquals(0, plan.newAdmissionLimit);
        assertFalse(plan.focusKanji.contains("字5"));
    }

    @Test
    public void manualAllKanjiModeIsLimitedByMaxItems() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(8),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                100,
                AdaptiveLoadPlanner.WorkloadMode.MANUAL,
                5,
                1000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertFalse(plan.allKanjiMode);
        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size());
        assertTrue(plan.status.contains("capped"));
    }

    @Test
    public void manualAllKanjiModeWithMaxAboveRowsStaysUncapped() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                rows(4),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
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
    public void fsrsRiskCoversInvalidPercentAndMatureStabilityBranches() {
        RecordsImportModels.DashboardRow percentRisk = row("百", 10, 75.0, 6.0, 2.0, 10, 5);
        RecordsImportModels.DashboardRow invalidRisk = row("無", 50, 101.0, null, null, 3, 1);
        RecordsImportModels.DashboardRow protectedMature = row("熟", 45, 0.95, 4.0, 50.0, 50, 10);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(invalidRisk, protectedMature, percentRisk),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("百", plan.focusKanji.get(0));
    }

    @Test
    public void typedPlanRequestBuilderShapesPolicyAndSettings() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        AdaptiveLoadPlanner.PlanRequest request = AdaptiveLoadPlanner.PlanRequest.builder(
                        rows(2),
                        Collections.emptyList(),
                        new RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                        0,
                        Collections.emptySet(),
                        AdaptiveLoadPlanner.WorkloadPolicy.of(AdaptiveLoadPlanner.WorkloadMode.MANUAL, 20, 1),
                        1000L
                )
                .settings(settings)
                .build();

        RecordsSchedulerModels.AdaptiveLoadPlan manual = plan(request);
        RecordsSchedulerModels.AdaptiveLoadPlan fromSettings = plan(
                AdaptiveLoadPlanner.PlanRequest.builder(
                                rows(2),
                                Collections.emptyList(),
                                new RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                                0,
                                Collections.emptySet(),
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
    public void nullPlanRequestFallsBackToEmptyDefaultPlan() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan((AdaptiveLoadPlanner.PlanRequest) null);

        assertFalse(plan.autoMode);
        assertEquals(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT, plan.workloadPercent);
        assertEquals(0, plan.target);
        assertTrue(plan.status.contains("No current problem"));
    }

    @Test
    public void targetAdjustmentCoversModerateMissesHardRateAndFutureLearning() {
        RecordsStudyModels.StudyItem futureLearning = item("字0", "learning", 5000L, 0, 0);
        RecordsStudyModels.StudyItem dueUnreviewedReview = item("字1", "review", 0L, 0, 0);
        HashSet<String> studied = new HashSet<>();
        studied.add("字0");

        RecordsSchedulerModels.AdaptiveLoadPlan moderateMisses = plan(
                rows(10),
                Collections.singletonList(futureLearning),
                new RecordsSchedulerModels.ReviewStats(4, 1, 0, 3, 0, 4, 0),
                0,
                studied,
                20,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan hardHeavy = plan(
                rows(10),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(4, 0, 2, 2, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                1000L
        );
        RecordsSchedulerModels.AdaptiveLoadPlan unreviewedReview = plan(
                rows(10),
                Collections.singletonList(dueUnreviewedReview),
                new RecordsSchedulerModels.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
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
    public void autoNoHistoryWithoutDropUsesSmallStartStatus() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("穏", 0, null, null, null, 45, 12),
                        row("静", 0, null, null, null, 45, 12)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.status.contains("Auto Pareto starts small"));
    }

    @Test
    public void fsrsRiskCoversNegativeRetrievabilityAndIntervalFallback() {
        RecordsImportModels.DashboardRow intervalFallback = row("間", 10, null, null, null, 3, 9);
        RecordsImportModels.DashboardRow negativeRetrievability = row("負", 50, -0.1, null, null, 30, 9);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(negativeRetrievability, intervalFallback),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("間", plan.focusKanji.get(0));
    }

    @Test
    public void fsrsRiskDoesNotProtectImmatureHighStabilityExamples() {
        RecordsImportModels.DashboardRow immatureHighStability = row("未", 10, 0.95, null, 50.0, 3, 9);
        RecordsImportModels.DashboardRow protectedMature = row("熟", 10, 0.95, null, 50.0, 50, 9);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(protectedMature, immatureHighStability),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("未", plan.focusKanji.get(0));
    }

    @Test
    public void autoParetoRequiresAbsoluteDropAsWellAsRelativeDrop() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("十", 10, null, null, null, 3, 1),
                        row("七", 7, null, null, null, 3, 1),
                        row("一", 1, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.target >= 2);
    }

    @Test
    public void fsrsRiskProtectsOnlyVeryStableMatureExamples() {
        RecordsImportModels.DashboardRow matureWithoutProtection = row("並", 10, 0.95, 10.0, 30.0, 50, 9);
        RecordsImportModels.DashboardRow protectedMature = row("熟", 10, 0.95, 10.0, 90.0, 90, 9);

        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(protectedMature, matureWithoutProtection),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals("並", plan.focusKanji.get(0));
    }

    @Test
    public void autoParetoScanContinuesAcrossZeroPriorityTail() {
        RecordsSchedulerModels.AdaptiveLoadPlan plan = plan(
                Arrays.asList(
                        row("十", 10, null, null, null, 3, 1),
                        row("零", 0, null, null, null, 45, 12),
                        row("空", 0, null, null, null, 45, 12)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.WorkloadMode.AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.target >= 1);
    }

    private AdaptiveLoadPlanner planner() {
        return new AdaptiveLoadPlanner();
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(AdaptiveLoadPlanner.PlanRequest request) {
        return planner().plan(request);
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            java.util.Set<String> studiedToday,
            int workloadPercent,
            long nowMillis
    ) {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, nowMillis, RecordsSyncModels.Settings.kikuDefaults());
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            java.util.Set<String> studiedToday,
            int workloadPercent,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, AdaptiveLoadPlanner.WorkloadMode.MANUAL, nowMillis, settings);
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            java.util.Set<String> studiedToday,
            int workloadPercent,
            AdaptiveLoadPlanner.WorkloadMode workloadMode,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        return plan(rows, items, recentStats, currentStreakDays, studiedToday, workloadPercent, workloadMode, Integer.MAX_VALUE, nowMillis, settings);
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            java.util.Set<String> studiedToday,
            int workloadPercent,
            AdaptiveLoadPlanner.WorkloadMode workloadMode,
            int maxItems,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
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

    private List<RecordsImportModels.DashboardRow> rows(int count) {
        List<RecordsImportModels.DashboardRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row("字" + i, 20 - i, null, null, null, 3, 1));
        }
        return rows;
    }

    private RecordsImportModels.DashboardRow row(
            String kanji,
            int weakness,
            Double retrievability,
            Double difficulty,
            Double stability,
            int intervalDays,
            int reps
    ) {
        RecordsImportModels.Example example = new RecordsImportModels.Example(
                "active",
                kanji.charAt(0),
                kanji.charAt(0),
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
        return new RecordsImportModels.DashboardRow(
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
                intervalDays >= RecordsSyncModels.Settings.kikuDefaults().matureDays ? 1 : 0,
                Collections.singletonList(example)
        );
    }

    private RecordsStudyModels.StudyItem reviewed(String kanji, long dueAt) {
        return new RecordsStudyModels.StudyItem(kanji, "review", dueAt, 1.0, 5.0, 2, 0, 2, 1, null, 0L);
    }

    private RecordsStudyModels.StudyItem item(String kanji, String state, long dueAt, int totalReviews, int lapses) {
        return new RecordsStudyModels.StudyItem(kanji, state, dueAt, 1.0, 5.0, totalReviews, lapses, 0, 1, null, 0L);
    }

    private RecordsSyncModels.Settings settingsWithMatureSupport(int matureSupportThreshold) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
