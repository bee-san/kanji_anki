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
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(10),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                new Object[]{1000L}
        );

        assertEquals(20, plan.workloadPercent);
        assertTrue(plan.target >= 1);
        assertTrue(plan.target <= 5);
        assertEquals(plan.target, plan.newAdmissionLimit);
        assertFalse(plan.allKanjiMode);
    }

    @Test
    public void veryLowWorkloadProducesOneKanji() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(5),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 0, 8, 0, 4, 0),
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
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(8),
                Collections.emptyList(),
                new Records.ReviewStats(2, 0, 0, 2, 0, 2, 0),
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
        Records.AdaptiveLoadPlan plan = planner().plan(
                Collections.emptyList(),
                Collections.emptyList(),
                new Records.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                0,
                Collections.emptySet(),
                100,
                AdaptiveLoadPlanner.MODE_MANUAL,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertFalse(plan.autoMode);
        assertTrue(plan.allKanjiMode);
        assertEquals("No current problem kanji.", plan.status);
    }

    @Test
    public void highMissAndWritingFailureLowerTarget() {
        Records.AdaptiveLoadPlan steady = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(10, 0, 0, 9, 1, 8, 0),
                5,
                Collections.emptySet(),
                50,
                1000L
        );
        Records.AdaptiveLoadPlan rough = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(10, 5, 2, 3, 0, 8, 4),
                5,
                Collections.emptySet(),
                50,
                1000L
        );

        assertTrue(rough.target < steady.target);
    }

    @Test
    public void stableStreakWithLowMissesRaisesTargetSlightly() {
        Records.AdaptiveLoadPlan noStreak = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                0,
                Collections.emptySet(),
                50,
                1000L
        );
        Records.AdaptiveLoadPlan streak = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                4,
                Collections.emptySet(),
                50,
                1000L
        );

        assertEquals(noStreak.target + 1, streak.target);
    }

    @Test
    public void steadyStreakBoostRequiresLowHardAndWritingRates() {
        Records.AdaptiveLoadPlan boosted = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                4,
                Collections.emptySet(),
                50,
                1000L
        );
        Records.AdaptiveLoadPlan hardBlocked = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 2, 2, 0, 4, 0),
                4,
                Collections.emptySet(),
                50,
                1000L
        );
        Records.AdaptiveLoadPlan writingBlocked = planner().plan(
                rows(20),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 1),
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
        List<Records.StudyItem> due = Arrays.asList(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L)
        );

        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(10),
                due,
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
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
        List<Records.StudyItem> items = Arrays.asList(
                item("学", "learning", 999L, 0, 0),
                item("済", "retired", 0L, 10, 0),
                item("待", "review", 2000L, 10, 0)
        );

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("学", 10, null, null, null, 3, 1),
                        row("済", 9, null, null, null, 3, 1),
                        row("待", 8, null, null, null, 3, 1)
                ),
                items,
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
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
        Records.DashboardRow weakFsrs = row("弱", 10, 0.30, 7.0, 3.0, 10, 2);
        Records.DashboardRow ordinary = row("普", 10, 0.95, 4.0, 30.0, 10, 2);

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(ordinary, weakFsrs),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("弱", plan.focusKanji.get(0));
    }

    @Test
    public void missingFsrsDataFallsBackToIntervalRepsAndLapses() {
        Records.DashboardRow shakyMature = row("揺", 10, null, null, null, 10, 1);
        Records.DashboardRow betterSupported = row("支", 10, null, null, null, 40, 12);

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(betterSupported, shakyMature),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
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

        Records.AdaptiveLoadPlan complete = planner().plan(
                rows(1),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                studied,
                0,
                1000L
        );
        Records.AdaptiveLoadPlan dueAgain = planner().plan(
                rows(1),
                Collections.singletonList(reviewed("字0", 0L)),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
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
        Records.StudyItem learningFutureDue = item("字0", "learning", now + 60_000L, 1, 1);

        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(1),
                Collections.singletonList(learningFutureDue),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
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
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1),
                        row("薄", 8, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertTrue(plan.autoMode);
        assertEquals(2, plan.target);
        assertEquals(Arrays.asList("強", "重"), plan.focusKanji);
        assertTrue(plan.status.contains("drop-off"));
    }

    @Test
    public void autoWorkloadReportsDropOffWhileWaitingForHistory() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertTrue(plan.status.contains("drop-off"));
        assertTrue(plan.status.contains("starts small"));
    }

    @Test
    public void autoWorkloadOrdersDropOffByCompositePriorityScore() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("査", 1, 0.75, null, null, 3, 1),
                        row("濃", 100, null, null, null, 3, 1),
                        row("濁", 90, null, null, null, 3, 1),
                        row("薄", 10, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertEquals(2, plan.target);
        assertEquals(Arrays.asList("濃", "濁"), plan.focusKanji);
    }

    @Test
    public void autoWorkloadFallsBackToSmallParetoFocusWhenCurveIsFlat() {
        List<Records.DashboardRow> flat = Arrays.asList(
                row("字0", 40, null, null, null, 3, 1),
                row("字1", 38, null, null, null, 3, 1),
                row("字2", 36, null, null, null, 3, 1),
                row("字3", 34, null, null, null, 3, 1),
                row("字4", 32, null, null, null, 3, 1),
                row("字5", 30, null, null, null, 3, 1)
        );

        Records.AdaptiveLoadPlan plan = planner().plan(
                flat,
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertEquals(AdaptiveLoadPlanner.targetCeiling(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT), plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    public void autoWorkloadFallsBackWhenPriorityScoresAreZero() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("穏", 0, null, null, null, 45, 12),
                        row("静", 0, null, null, null, 45, 12)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertEquals(2, plan.target);
        assertTrue(plan.status.contains("small Pareto focus"));
    }

    @Test
    public void autoWorkloadIncludesDueRecoveryBeforeNewAdmissions() {
        List<Records.StudyItem> due = Arrays.asList(
                reviewed("復", 0L),
                reviewed("習", 0L)
        );

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("復", 9, null, null, null, 3, 1),
                        row("習", 8, null, null, null, 3, 1),
                        row("強", 42, null, null, null, 3, 1),
                        row("重", 38, null, null, null, 3, 1),
                        row("軽", 10, null, null, null, 3, 1)
                ),
                due,
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertTrue(plan.focusKanji.contains("復"));
        assertTrue(plan.focusKanji.contains("習"));
        assertEquals(2, plan.newAdmissionLimit);
    }

    @Test
    public void autoWorkloadStillShrinksWhenRecentReviewsAreRough() {
        List<Records.DashboardRow> steep = Arrays.asList(
                row("強", 42, null, null, null, 3, 1),
                row("重", 38, null, null, null, 3, 1),
                row("固", 36, null, null, null, 3, 1),
                row("軽", 10, null, null, null, 3, 1)
        );
        Records.AdaptiveLoadPlan steady = planner().plan(
                steep,
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );
        Records.AdaptiveLoadPlan rough = planner().plan(
                steep,
                Collections.emptyList(),
                new Records.ReviewStats(8, 4, 2, 2, 0, 6, 3),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertTrue(rough.target < steady.target);
    }

    @Test
    public void manualModeStillHonorsAllKanjiOverride() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(8),
                Collections.emptyList(),
                new Records.ReviewStats(2, 0, 0, 2, 0, 2, 0),
                2,
                Collections.emptySet(),
                100,
                AdaptiveLoadPlanner.MODE_MANUAL,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertFalse(plan.autoMode);
        assertTrue(plan.allKanjiMode);
        assertEquals(8, plan.target);
    }

    @Test
    public void manualModeStatusCoversNoHistoryRecoveryAndFullRange() {
        Records.AdaptiveLoadPlan noHistory = planner().plan(
                rows(4),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                1000L
        );
        Records.AdaptiveLoadPlan recovery = planner().plan(
                rows(2),
                Arrays.asList(reviewed("字0", 0L), reviewed("字1", 0L)),
                new Records.ReviewStats(6, 0, 0, 6, 0, 4, 0),
                0,
                Collections.emptySet(),
                5,
                1000L
        );
        Records.AdaptiveLoadPlan fullRange = planner().plan(
                rows(4),
                Collections.emptyList(),
                new Records.ReviewStats(10, 0, 1, 8, 1, 8, 0),
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
        Records.AdaptiveLoadPlan plan = planner().plan(
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
        List<Records.DashboardRow> steep = Arrays.asList(
                row("強", 42, null, null, null, 3, 1),
                row("重", 38, null, null, null, 3, 1),
                row("軽", 10, null, null, null, 3, 1)
        );
        Records.AdaptiveLoadPlan recoveryOnly = planner().plan(
                steep,
                Arrays.asList(reviewed("強", 0L), reviewed("重", 0L), reviewed("軽", 0L)),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );
        Records.AdaptiveLoadPlan steady = planner().plan(
                steep,
                Collections.emptyList(),
                new Records.ReviewStats(10, 0, 1, 8, 1, 8, 0),
                5,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertTrue(recoveryOnly.status.contains("Due recovery"));
        assertTrue(steady.status.contains("steady streak"));
    }

    @Test
    public void autoWorkloadRespectsMaxItems() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(12),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                5,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size());
        assertEquals(5, plan.newAdmissionLimit);
    }

    @Test
    public void dueRecoveryIsCappedByMaxItems() {
        List<Records.StudyItem> due = Arrays.asList(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L),
                reviewed("字3", 0L),
                reviewed("字4", 0L),
                reviewed("字5", 0L)
        );

        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(8),
                due,
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                5,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size());
        assertEquals(0, plan.newAdmissionLimit);
        assertFalse(plan.focusKanji.contains("字5"));
    }

    @Test
    public void manualAllKanjiModeIsLimitedByMaxItems() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(8),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                100,
                AdaptiveLoadPlanner.MODE_MANUAL,
                5,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertFalse(plan.allKanjiMode);
        assertEquals(5, plan.target);
        assertEquals(5, plan.focusKanji.size());
        assertTrue(plan.status.contains("capped"));
    }

    @Test
    public void manualAllKanjiModeWithMaxAboveRowsStaysUncapped() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                rows(4),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                100,
                AdaptiveLoadPlanner.MODE_MANUAL,
                20,
                1000L,
                Records.Settings.kikuDefaults()
        );

        assertTrue(plan.allKanjiMode);
        assertEquals(4, plan.target);
        assertTrue(plan.status.contains("All current problem kanji"));
    }

    @Test
    public void fsrsRiskCoversInvalidPercentAndMatureStabilityBranches() {
        Records.DashboardRow percentRisk = row("百", 10, 75.0, 6.0, 2.0, 10, 5);
        Records.DashboardRow invalidRisk = row("無", 50, 101.0, null, null, 3, 1);
        Records.DashboardRow protectedMature = row("熟", 45, 0.95, 4.0, 50.0, 50, 10);

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(invalidRisk, protectedMature, percentRisk),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("百", plan.focusKanji.get(0));
    }

    @Test
    public void legacyOptionShapesCoverLooseAndManualCompatibilityPaths() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.AdaptiveLoadPlan nullOptions = planner().plan(
                rows(2),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                (Object[]) null
        );
        Records.AdaptiveLoadPlan oldTwoOptionManual = planner().plan(
                rows(2),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                1000L,
                settings
        );
        Records.AdaptiveLoadPlan looseOptions = planner().plan(
                rows(2),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                new Object[]{1000L}
        );
        Records.AdaptiveLoadPlan settingsOnlyLooseOption = planner().plan(
                rows(2),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                new Object[]{settings}
        );
        Records.AdaptiveLoadPlan ignoredLooseOption = planner().plan(
                rows(2),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                new Object[]{"ignored"}
        );
        Records.AdaptiveLoadPlan legacyManualWithNonLongNow = planner().plan(
                rows(2),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                new Object[]{"not-now", settings}
        );

        assertFalse(nullOptions.autoMode);
        assertFalse(oldTwoOptionManual.autoMode);
        assertFalse(looseOptions.autoMode);
        assertFalse(settingsOnlyLooseOption.autoMode);
        assertFalse(ignoredLooseOption.autoMode);
        assertFalse(legacyManualWithNonLongNow.autoMode);
    }

    @Test
    public void nullPlanRequestFallsBackToEmptyDefaultPlan() {
        Records.AdaptiveLoadPlan plan = planner().plan((AdaptiveLoadPlanner.PlanRequest) null);

        assertFalse(plan.autoMode);
        assertEquals(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT, plan.workloadPercent);
        assertEquals(0, plan.target);
        assertTrue(plan.status.contains("No current problem"));
    }

    @Test
    public void targetAdjustmentCoversModerateMissesHardRateAndFutureLearning() {
        Records.StudyItem futureLearning = item("字0", "learning", 5000L, 0, 0);
        Records.StudyItem dueUnreviewedReview = item("字1", "review", 0L, 0, 0);
        HashSet<String> studied = new HashSet<>();
        studied.add("字0");

        Records.AdaptiveLoadPlan moderateMisses = planner().plan(
                rows(10),
                Collections.singletonList(futureLearning),
                new Records.ReviewStats(4, 1, 0, 3, 0, 4, 0),
                0,
                studied,
                20,
                1000L
        );
        Records.AdaptiveLoadPlan hardHeavy = planner().plan(
                rows(10),
                Collections.emptyList(),
                new Records.ReviewStats(4, 0, 2, 2, 0, 4, 0),
                0,
                Collections.emptySet(),
                20,
                1000L
        );
        Records.AdaptiveLoadPlan unreviewedReview = planner().plan(
                rows(10),
                Collections.singletonList(dueUnreviewedReview),
                new Records.ReviewStats(4, 0, 0, 4, 0, 4, 0),
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
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("穏", 0, null, null, null, 45, 12),
                        row("静", 0, null, null, null, 45, 12)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.status.contains("Auto Pareto starts small"));
    }

    @Test
    public void fsrsRiskCoversNegativeRetrievabilityAndIntervalFallback() {
        Records.DashboardRow intervalFallback = row("間", 10, null, null, null, 3, 9);
        Records.DashboardRow negativeRetrievability = row("負", 50, -0.1, null, null, 30, 9);

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(negativeRetrievability, intervalFallback),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("間", plan.focusKanji.get(0));
    }

    @Test
    public void fsrsRiskDoesNotProtectImmatureHighStabilityExamples() {
        Records.DashboardRow immatureHighStability = row("未", 10, 0.95, null, 50.0, 3, 9);
        Records.DashboardRow protectedMature = row("熟", 10, 0.95, null, 50.0, 50, 9);

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(protectedMature, immatureHighStability),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                0,
                1000L
        );

        assertEquals("未", plan.focusKanji.get(0));
    }

    @Test
    public void autoParetoRequiresAbsoluteDropAsWellAsRelativeDrop() {
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("十", 10, null, null, null, 3, 1),
                        row("七", 7, null, null, null, 3, 1),
                        row("一", 1, null, null, null, 3, 1)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.target >= 2);
    }

    @Test
    public void fsrsRiskProtectsOnlyVeryStableMatureExamples() {
        Records.DashboardRow matureWithoutProtection = row("並", 10, 0.95, 10.0, 30.0, 50, 9);
        Records.DashboardRow protectedMature = row("熟", 10, 0.95, 10.0, 90.0, 90, 9);

        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(protectedMature, matureWithoutProtection),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
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
        Records.AdaptiveLoadPlan plan = planner().plan(
                Arrays.asList(
                        row("十", 10, null, null, null, 3, 1),
                        row("零", 0, null, null, null, 45, 12),
                        row("空", 0, null, null, null, 45, 12)
                ),
                Collections.emptyList(),
                new Records.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                20,
                AdaptiveLoadPlanner.MODE_AUTO,
                1000L,
                settingsWithMatureSupport(0)
        );

        assertTrue(plan.target >= 1);
    }

    private AdaptiveLoadPlanner planner() {
        return new AdaptiveLoadPlanner();
    }

    private List<Records.DashboardRow> rows(int count) {
        List<Records.DashboardRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row("字" + i, 20 - i, null, null, null, 3, 1));
        }
        return rows;
    }

    private Records.DashboardRow row(
            String kanji,
            int weakness,
            Double retrievability,
            Double difficulty,
            Double stability,
            int intervalDays,
            int reps
    ) {
        Records.Example example = new Records.Example(
                "active",
                kanji.charAt(0),
                kanji.charAt(0),
                kanji + "語",
                "よみ",
                "meaning",
                kanji + "を見た。",
                intervalDays >= Records.Settings.kikuDefaults().matureDays,
                0,
                intervalDays,
                reps,
                stability,
                difficulty,
                retrievability
        );
        return new Records.DashboardRow(
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
                intervalDays >= Records.Settings.kikuDefaults().matureDays ? 1 : 0,
                Collections.singletonList(example)
        );
    }

    private Records.StudyItem reviewed(String kanji, long dueAt) {
        return new Records.StudyItem(kanji, "review", dueAt, 1.0, 5.0, 2, 0, 2, 1, null, 0L);
    }

    private Records.StudyItem item(String kanji, String state, long dueAt, int totalReviews, int lapses) {
        return new Records.StudyItem(kanji, state, dueAt, 1.0, 5.0, totalReviews, lapses, 0, 1, null, 0L);
    }

    private Records.Settings settingsWithMatureSupport(int matureSupportThreshold) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        return new Records.Settings(
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
