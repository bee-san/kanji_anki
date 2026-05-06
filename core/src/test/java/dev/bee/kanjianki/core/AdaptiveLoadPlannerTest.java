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
}
