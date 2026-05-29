package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class NewCardSortPlannerTest {
    private final NewCardSortPlanner planner = new NewCardSortPlanner();

    @Test
    public void frequencySortUsesLowestJitenRank() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(row("語", 30), row("字", 10), row("文", 20)),
                settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)
        );

        assertEquals(Arrays.asList("字", "文", "語"), kanji(sorted));
    }

    @Test
    public void fsrsDifficultySortUsesHighestFiniteExampleDifficulty() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(
                        row("低", 30, 0, 0, example(4.0, 0.4)),
                        row("高", 20, 0, 0, example(6.0, 0.9), example(9.0, 0.7)),
                        row("中", 10, 0, 0, example(7.0, 0.2))
                ),
                settings(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        );

        assertEquals(Arrays.asList("高", "中", "低"), kanji(sorted));
    }

    @Test
    public void retrievabilityRiskSortUsesLowestNormalizedExampleRetrievability() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(
                        row("安", 30, 0, 0, example(5.0, 0.8)),
                        row("危", 20, 0, 0, example(5.0, 0.2), example(5.0, 75.0)),
                        row("注", 10, 0, 0, example(5.0, 50.0))
                ),
                settings(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)
        );

        assertEquals(Arrays.asList("危", "注", "安"), kanji(sorted));
    }

    @Test
    public void kaniWeaknessSortUsesWeaknessThenSuspendedExampleCount() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(
                        row("弱", 30, 5, 1),
                        row("脆", 20, 8, 0),
                        row("危", 10, 8, 3)
                ),
                settings(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS)
        );

        assertEquals(Arrays.asList("危", "脆", "弱"), kanji(sorted));
    }

    @Test
    public void missingPrimarySortValuesFallBackToJitenRankThenKanji() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(
                        row("乙", 20),
                        row("甲", 20),
                        row("丙", 10),
                        row("丁", 30, 0, 0, example(8.0, 0.7))
                ),
                settings(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        );

        assertEquals(Arrays.asList("丁", "丙", "乙", "甲"), kanji(sorted));
    }

    @Test
    public void nullSettingsAndUnknownModeUseFrequencySort() {
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("語", 30), row("字", 10));

        assertEquals(Arrays.asList("字", "語"), kanji(planner.sortedRows(rows, null)));
        assertEquals(Arrays.asList("字", "語"), kanji(planner.sortedRows(rows, settings("unknown-mode"))));
    }

    @Test
    public void compareRowsKeepsNullRowsAfterRankedRows() {
        RecordsImportModels.DashboardRow ranked = row("字", 10);

        assertTrue(planner.compareRows(null, ranked, settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)) > 0);
        assertTrue(planner.compareRows(ranked, null, settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)) < 0);
        assertEquals(0, planner.compareRows(null, null, settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)));
    }

    @Test
    public void nonFiniteDifficultyFallsBackToRankAndKanji() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(
                        row("乙", 20, 0, 0, example(Double.NaN, 0.4)),
                        row("甲", 10, 0, 0, example(Double.POSITIVE_INFINITY, 0.2)),
                        row("丁", 30, 0, 0, example(5.0, 0.9))
                ),
                settings(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        );

        assertEquals(Arrays.asList("丁", "甲", "乙"), kanji(sorted));
    }

    @Test
    public void invalidRetrievabilityValuesFallBackToRankAndKanji() {
        List<RecordsImportModels.DashboardRow> sorted = planner.sortedRows(
                Arrays.asList(
                        row("乙", 20, 0, 0, example(5.0, -0.1)),
                        row("甲", 10, 0, 0, example(5.0, 101.0)),
                        row("丁", 30, 0, 0, example(5.0, 25.0))
                ),
                settings(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)
        );

        assertEquals(Arrays.asList("丁", "甲", "乙"), kanji(sorted));
    }

    private static List<String> kanji(List<RecordsImportModels.DashboardRow> rows) {
        return rows.stream().map(row -> row.kanji).collect(Collectors.toList());
    }

    private static RecordsImportModels.DashboardRow row(String kanji, Integer rank) {
        return row(kanji, rank, 0, 0);
    }

    private static RecordsImportModels.DashboardRow row(String kanji, Integer rank, int weakness, int suspended) {
        return row(kanji, rank, weakness, suspended, example(null, null));
    }

    private static RecordsImportModels.DashboardRow row(
            String kanji,
            Integer rank,
            int weakness,
            int suspended,
            RecordsImportModels.Example... examples
    ) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                rank,
                "meaning " + kanji,
                "reading " + kanji,
                "browser " + kanji,
                weakness,
                "reason",
                "reason text",
                1,
                suspended,
                0,
                Arrays.asList(examples)
        );
    }

    private static RecordsImportModels.Example example(Double difficulty, Double retrievability) {
        return new RecordsImportModels.Example(
                "active",
                1L,
                2L,
                "expression",
                "reading",
                "meaning",
                "sentence",
                false,
                0,
                0,
                0,
                null,
                difficulty,
                retrievability
        );
    }

    private static RecordsSyncModels.Settings settings(String newCardSortMode) {
        return new RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                2,
                RecordsBase.DEFAULT_SUSPENDED_RANK_MIN,
                RecordsBase.DEFAULT_SUSPENDED_RANK_MAX,
                24,
                3,
                RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS,
                RecordsBase.DEFAULT_RECOGNITION_PROMOTION_PASSES,
                RecordsBase.DEFAULT_REAL_DUE_REVIEWS_TO_MOVE,
                RecordsBase.DEFAULT_IMPORT_ACTIVE_CARDS,
                RecordsBase.DEFAULT_IMPORT_SUSPENDED_CARDS,
                RecordsBase.DEFAULT_IMPORT_TAGGED_CARDS,
                Collections.emptyList(),
                RecordsBase.DEFAULT_IMPORT_WEAK_CARDS,
                RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
                RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
                RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
                RecordsBase.DEFAULT_IMPORT_BROWSER_QUERY_CARDS,
                RecordsBase.DEFAULT_IMPORT_BROWSER_QUERY,
                newCardSortMode,
                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
        );
    }
}
