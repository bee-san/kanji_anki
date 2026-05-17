package dev.bee.kanjianki;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LegacyAdaptiveStudyPlannerBridgeTest {
    private final LegacyAdaptiveStudyPlannerBridge bridge = new LegacyAdaptiveStudyPlannerBridge();

    @Test
    public void matchesLegacyPlannerForManualParetoAndFsrsRisk() {
        AdaptiveLoadPlanner.PlanRequest request = request(
                Arrays.asList(
                        row("普", 10, 0.95, 4.0, 30.0, 10, 2),
                        row("弱", 10, 0.30, 7.0, 3.0, 10, 2),
                        row("揺", 10, null, null, null, 10, 1)
                ),
                Collections.emptyList(),
                new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                AdaptiveLoadPlanner.WorkloadPolicy.manual(20),
                1_000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertMatchesLegacyPlanner(request);
    }

    @Test
    public void matchesLegacyPlannerForAutoParetoDropOff() {
        AdaptiveLoadPlanner.PlanRequest request = request(
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
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                        20,
                        AdaptiveLoadPlanner.MODE_AUTO,
                        AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
                ),
                1_000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertMatchesLegacyPlanner(request);
    }

    @Test
    public void matchesLegacyPlannerForDueRecoveryAndMaxItems() {
        List<RecordsStudyModels.StudyItem> due = Arrays.asList(
                reviewed("字0", 0L),
                reviewed("字1", 0L),
                reviewed("字2", 0L),
                reviewed("字3", 0L),
                reviewed("字4", 0L),
                reviewed("字5", 0L)
        );

        AdaptiveLoadPlanner.PlanRequest request = request(
                rows(8),
                due,
                new RecordsSchedulerModels.ReviewStats(8, 0, 1, 7, 0, 6, 0),
                1,
                Collections.emptySet(),
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                        20,
                        AdaptiveLoadPlanner.MODE_AUTO,
                        5
                ),
                1_000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertMatchesLegacyPlanner(request);
    }

    @Test
    public void matchesLegacyPlannerForNullPlanRequest() {
        assertMatchesLegacyPlanner(null);
    }

    @Test
    public void matchesLegacyPlannerForNullInputsInsideRequest() {
        AdaptiveLoadPlanner.PlanRequest request = AdaptiveLoadPlanner.PlanRequest.builder(
                        null,
                        null,
                        null,
                        0,
                        null,
                        null,
                        1_000L
                )
                .settings(null)
                .build();

        assertMatchesLegacyPlanner(request);
    }

    @Test
    public void explicitSettingsEntryPointMatchesPlanRequestEntryPoint() {
        RecordsSchedulerModels.AdaptiveLoadPlan expected = bridge.plan(request(
                rows(3),
                Collections.singletonList(reviewed("字0", 0L)),
                new RecordsSchedulerModels.ReviewStats(4, 1, 0, 3, 0, 2, 1),
                2,
                new HashSet<>(Collections.singletonList("字1")),
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                        35,
                        AdaptiveLoadPlanner.MODE_MANUAL,
                        4
                ),
                2_000L,
                RecordsSyncModels.Settings.kikuDefaults()
        ));

        RecordsSchedulerModels.AdaptiveLoadPlan actual = bridge.plan(
                rows(3),
                Collections.singletonList(reviewed("字0", 0L)),
                new RecordsSchedulerModels.ReviewStats(4, 1, 0, 3, 0, 2, 1),
                2,
                new HashSet<>(Collections.singletonList("字1")),
                35,
                AdaptiveLoadPlanner.MODE_MANUAL,
                4,
                2_000L,
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertPlanEquals(expected, actual);
    }

    private void assertMatchesLegacyPlanner(AdaptiveLoadPlanner.PlanRequest request) {
        RecordsSchedulerModels.AdaptiveLoadPlan expected = new AdaptiveLoadPlanner().plan(request);
        RecordsSchedulerModels.AdaptiveLoadPlan actual = bridge.plan(request);

        assertPlanEquals(expected, actual);
    }

    private static void assertPlanEquals(
            RecordsSchedulerModels.AdaptiveLoadPlan expected,
            RecordsSchedulerModels.AdaptiveLoadPlan actual
    ) {
        assertEquals(expected.autoMode, actual.autoMode);
        assertEquals(expected.workloadPercent, actual.workloadPercent);
        assertEquals(expected.target, actual.target);
        assertEquals(expected.remaining, actual.remaining);
        assertEquals(expected.focusKanji, actual.focusKanji);
        assertEquals(expected.newAdmissionLimit, actual.newAdmissionLimit);
        assertEquals(expected.allKanjiMode, actual.allKanjiMode);
        assertEquals(expected.status, actual.status);
        assertEquals(expected.focusComplete(), actual.focusComplete());
    }

    private static AdaptiveLoadPlanner.PlanRequest request(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            java.util.Set<String> studiedToday,
            AdaptiveLoadPlanner.WorkloadPolicy workloadPolicy,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        return AdaptiveLoadPlanner.PlanRequest.builder(
                        rows,
                        items,
                        recentStats,
                        currentStreakDays,
                        studiedToday,
                        workloadPolicy,
                        nowMillis
                )
                .settings(settings)
                .build();
    }

    private static List<RecordsImportModels.DashboardRow> rows(int count) {
        List<RecordsImportModels.DashboardRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row("字" + i, 20 - i, null, null, null, 3, 1));
        }
        return rows;
    }

    private static RecordsImportModels.DashboardRow row(
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

    private static RecordsStudyModels.StudyItem reviewed(String kanji, long dueAtMillis) {
        return new RecordsStudyModels.StudyItem(
                kanji,
                "review",
                dueAtMillis,
                1.0,
                5.0,
                2,
                0,
                2,
                1,
                null,
                0L
        );
    }
}
