package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import org.junit.Assert.assertEquals
import org.junit.Test

class NewCardSortPolicyTest {
    private val policy = NewCardSortPolicy()

    @Test
    fun frequencySortUsesJitenRankAscendingWithNullsLast() {
        val rows = listOf(
            row("低", rank = 300),
            row("謎", rank = null),
            row("難", rank = 100),
            row("弱", rank = 200),
        )

        assertEquals(
            listOf("難", "弱", "低", "謎"),
            rows.sortedWith(policy.comparator(NewCardSortMode.FREQUENCY)).map { it.kanji },
        )
    }

    @Test
    fun difficultySortUsesHighestFiniteDifficultyThenRankAndKanji() {
        val rows = listOf(
            row("低", rank = 300, difficulty = 3.0),
            row("難", rank = 100, difficulty = 8.0),
            row("弱", rank = 200, difficulty = null),
            row("同", rank = 50, difficulty = 3.0),
        )

        assertEquals(
            listOf("難", "同", "低", "弱"),
            rows.sortedWith(policy.comparator(NewCardSortMode.FSRS_DIFFICULTY)).map { it.kanji },
        )
    }

    @Test
    fun retrievabilityRiskSortNormalizesPercentValuesAndUsesLowestFirst() {
        val rows = listOf(
            row("低", rank = 300, retrievability = 0.60),
            row("難", rank = 100, retrievability = 0.90),
            row("弱", rank = 200, retrievability = 45.0),
            row("謎", rank = 50, retrievability = 200.0),
        )

        assertEquals(
            listOf("弱", "低", "難", "謎"),
            rows.sortedWith(policy.comparator(NewCardSortMode.RETRIEVABILITY_RISK)).map { it.kanji },
        )
    }

    @Test
    fun kaniWeaknessSortUsesWeaknessThenSuspendedExamplesThenRank() {
        val rows = listOf(
            row("低", rank = 300, weakness = 40, suspendedExamples = 1),
            row("難", rank = 100, weakness = 20, suspendedExamples = 0),
            row("弱", rank = 200, weakness = 80, suspendedExamples = 0),
            row("同", rank = 50, weakness = 80, suspendedExamples = 2),
        )

        assertEquals(
            listOf("同", "弱", "低", "難"),
            rows.sortedWith(policy.comparator(NewCardSortMode.KANI_WEAKNESS)).map { it.kanji },
        )
    }

    @Test
    fun nullRowsSortAfterPresentRows() {
        val present = row("裂", rank = 100)

        assertEquals(-1, policy.compare(present, null, NewCardSortMode.FREQUENCY))
        assertEquals(1, policy.compare(null, present, NewCardSortMode.FREQUENCY))
        assertEquals(0, policy.compare(null, null, NewCardSortMode.FREQUENCY))
    }

    private fun NewCardSortPolicy.comparator(
        mode: NewCardSortMode,
    ): Comparator<StudyDashboardRow> = Comparator { left, right ->
        compare(left, right, mode)
    }

    private fun row(
        kanji: String,
        rank: Int?,
        weakness: Int = 0,
        suspendedExamples: Int = 0,
        difficulty: Double? = null,
        retrievability: Double? = null,
    ): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = rank,
        primaryMeaning = "meaning",
        reading = "reading",
        browserSearch = "search",
        weaknessScore = weakness,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = suspendedExamples,
        matureSupportCount = 0,
        examples = listOf(
            StudyExample(
                sourceType = "active",
                expression = kanji,
                reading = "reading",
                meaning = "meaning",
                fsrsDifficulty = difficulty,
                fsrsRetrievability = retrievability,
            ),
        ),
    )
}
