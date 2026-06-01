package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewCardSortPlannerTest {
    private val planner = NewCardSortPlanner()

    @Test
    fun frequencySortUsesLowestJitenRank() {
        val sorted = planner.sortedRows(
            listOf(row("語", 30), row("字", 10), row("文", 20)),
            settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)
        )

        assertEquals(listOf("字", "文", "語"), kanji(sorted))
    }

    @Test
    fun fsrsDifficultySortUsesHighestFiniteExampleDifficulty() {
        val sorted = planner.sortedRows(
            listOf(
                row("低", 30, 0, 0, example(4.0, 0.4)),
                row("高", 20, 0, 0, example(6.0, 0.9), example(9.0, 0.7)),
                row("中", 10, 0, 0, example(7.0, 0.2))
            ),
            settings(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        )

        assertEquals(listOf("高", "中", "低"), kanji(sorted))
    }

    @Test
    fun retrievabilityRiskSortUsesLowestNormalizedExampleRetrievability() {
        val sorted = planner.sortedRows(
            listOf(
                row("安", 30, 0, 0, example(5.0, 0.8)),
                row("危", 20, 0, 0, example(5.0, 0.2), example(5.0, 75.0)),
                row("注", 10, 0, 0, example(5.0, 50.0))
            ),
            settings(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)
        )

        assertEquals(listOf("危", "注", "安"), kanji(sorted))
    }

    @Test
    fun kaniWeaknessSortUsesWeaknessThenSuspendedExampleCount() {
        val sorted = planner.sortedRows(
            listOf(
                row("弱", 30, 5, 1),
                row("脆", 20, 8, 0),
                row("危", 10, 8, 3)
            ),
            settings(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS)
        )

        assertEquals(listOf("危", "脆", "弱"), kanji(sorted))
    }

    @Test
    fun missingPrimarySortValuesFallBackToJitenRankThenKanji() {
        val sorted = planner.sortedRows(
            listOf(
                row("乙", 20),
                row("甲", 20),
                row("丙", 10),
                row("丁", 30, 0, 0, example(8.0, 0.7))
            ),
            settings(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        )

        assertEquals(listOf("丁", "丙", "乙", "甲"), kanji(sorted))
    }

    @Test
    fun nullSettingsAndUnknownModeUseFrequencySort() {
        val rows = listOf(row("語", 30), row("字", 10))

        assertEquals(listOf("字", "語"), kanji(planner.sortedRows(rows, null)))
        assertEquals(listOf("字", "語"), kanji(planner.sortedRows(rows, settings("unknown-mode"))))
    }

    @Test
    fun compareRowsKeepsNullRowsAfterRankedRows() {
        val ranked = row("字", 10)

        assertTrue(planner.compareRows(null, ranked, settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)) > 0)
        assertTrue(planner.compareRows(ranked, null, settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)) < 0)
        assertEquals(0, planner.compareRows(null, null, settings(RecordsBase.NEW_CARD_SORT_FREQUENCY)))
    }

    @Test
    fun nonFiniteDifficultyFallsBackToRankAndKanji() {
        val sorted = planner.sortedRows(
            listOf(
                row("乙", 20, 0, 0, example(Double.NaN, 0.4)),
                row("甲", 10, 0, 0, example(Double.POSITIVE_INFINITY, 0.2)),
                row("丁", 30, 0, 0, example(5.0, 0.9))
            ),
            settings(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        )

        assertEquals(listOf("丁", "甲", "乙"), kanji(sorted))
    }

    @Test
    fun invalidRetrievabilityValuesFallBackToRankAndKanji() {
        val sorted = planner.sortedRows(
            listOf(
                row("乙", 20, 0, 0, example(5.0, -0.1)),
                row("甲", 10, 0, 0, example(5.0, 101.0)),
                row("丁", 30, 0, 0, example(5.0, 25.0))
            ),
            settings(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)
        )

        assertEquals(listOf("丁", "甲", "乙"), kanji(sorted))
    }

    @Test
    fun balancedPriorityScoresAcrossFullCandidateSet() {
        val sorted = planner.sortedRows(
            listOf(
                row("安", 1000, 0, 0, example(2.0, 0.95)),
                row("忘", 5000, 2, 0, example(3.0, 0.10)),
                row("弱", 2000, 10, 0, example(4.0, 0.80)),
                row("難", 3000, 1, 0, example(10.0, 0.90)),
                row("停", 4000, 1, 10, example(3.0, 0.85))
            ),
            settings(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )

        assertEquals(listOf("弱", "忘", "難", "停", "安"), kanji(sorted))
    }

    @Test
    fun balancedPriorityTreatsMissingInvalidFsrsValuesAsZeroSignals() {
        val sorted = planner.sortedRows(
            listOf(
                row("無", 20, 0, 0, example(Double.NaN, Double.POSITIVE_INFINITY)),
                row("有", 30, 1, 0, example(4.0, 0.80)),
                row("頻", 10, 0, 0, example(null, null))
            ),
            settings(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )

        assertEquals(listOf("有", "頻", "無"), kanji(sorted))
    }

    @Test
    fun balancedPriorityFallsBackToJitenRankThenKanjiWhenScoresTie() {
        val sorted = planner.sortedRows(
            listOf(
                row("乙", 20, 1, 1, example(5.0, 0.50)),
                row("甲", 20, 1, 1, example(5.0, 0.50)),
                row("丙", 10, 1, 1, example(5.0, 0.50))
            ),
            settings(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )

        assertEquals(listOf("丙", "乙", "甲"), kanji(sorted))
    }

    @Test
    fun balancedPriorityNormalizesPercentAndFractionRetrievabilityEqually() {
        val sorted = planner.sortedRows(
            listOf(
                row("率", 30, 1, 0, example(5.0, 45.0)),
                row("分", 30, 1, 0, example(5.0, 0.45)),
                row("高", 30, 1, 0, example(5.0, 0.90))
            ),
            settings(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )

        assertEquals(listOf("分", "率", "高"), kanji(sorted))
    }

    @Test
    fun balancedPriorityComparatorUsesDeterministicTwoRowScoringFallback() {
        val comparison = planner.compareRows(
            row("低", 10, 0, 0, example(1.0, 0.95)),
            row("危", 9999, 0, 0, example(1.0, 0.20)),
            settings(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY)
        )

        assertEquals(1, Integer.signum(comparison))
    }

    private fun kanji(rows: List<RecordsImportModels.DashboardRow>): List<String> {
        return rows.map { it.kanji }
    }

    private fun row(kanji: String, rank: Int?): RecordsImportModels.DashboardRow {
        return row(kanji, rank, 0, 0)
    }

    private fun row(kanji: String, rank: Int?, weakness: Int, suspended: Int): RecordsImportModels.DashboardRow {
        return row(kanji, rank, weakness, suspended, example(null, null))
    }

    private fun row(
        kanji: String,
        rank: Int?,
        weakness: Int,
        suspended: Int,
        vararg examples: RecordsImportModels.Example
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            rank,
            "meaning $kanji",
            "reading $kanji",
            "browser $kanji",
            weakness,
            "reason",
            "reason text",
            1,
            suspended,
            0,
            examples.toList()
        )
    }

    private fun example(difficulty: Double?, retrievability: Double?): RecordsImportModels.Example {
        return RecordsImportModels.Example(
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
        )
    }

    private fun settings(newCardSortMode: String): RecordsSyncModels.Settings {
        return RecordsSyncModels.Settings(
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
            emptyList<RecordsImportModels.Example>(),
            RecordsBase.DEFAULT_IMPORT_WEAK_CARDS,
            RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
            RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
            RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
            RecordsBase.DEFAULT_IMPORT_BROWSER_QUERY_CARDS,
            RecordsBase.DEFAULT_IMPORT_BROWSER_QUERY,
            newCardSortMode,
            RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
            RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
        )
    }
}
