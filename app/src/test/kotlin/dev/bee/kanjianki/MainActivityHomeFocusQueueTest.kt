package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.data.toRepositorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityHomeFocusQueueTest {
    @Test
    fun recentMistakesRouteDataKeepsAnEligibleRepositoryMistake() {
        val row = dashboardRow("落")

        val data = recentMistakesRouteData(
            snapshot = snapshot(listOf(StudyStatsStore.RecentMistake("落", "again", 2_000L))),
            rows = listOf(row),
            items = listOf(studyItem("落")),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
        )

        assertEquals("落", data.mistakes.single().kanji)
        assertEquals(setOf("落"), data.rowsByKanji.keys)
    }

    @Test
    fun recentMistakesRouteDataAvoidsRowWorkForAnEmptySnapshot() {
        val data = recentMistakesRouteData(
            snapshot = snapshot(emptyList()),
            rows = listOf(dashboardRow("落")),
            items = listOf(studyItem("落")),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
        )

        assertTrue(data.mistakes.isEmpty())
        assertTrue(data.rowsByKanji.isEmpty())
    }

    @Test
    fun recentMistakesRouteDataRevalidatesAgainstTheMatureSupportGate() {
        val data = recentMistakesRouteData(
            snapshot = snapshot(listOf(StudyStatsStore.RecentMistake("済", "again", 1_000L))),
            rows = listOf(dashboardRow("済", matureSupportCount = 2)),
            items = listOf(studyItem("済")),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
        )

        assertTrue(data.mistakes.isEmpty())
        assertTrue(data.rowsByKanji.isEmpty())
    }

    private companion object {
        fun studyItem(kanji: String): RecordsStudyModels.StudyItem =
            RecordsStudyModels.StudyItem(
                kanji,
                StudyLadderRules.STATE_REVIEW,
                0L,
                1.0,
                1.0,
                0,
                0,
                0,
                0,
                "",
                0L,
            )

        fun dashboardRow(
            kanji: String,
            matureSupportCount: Int = 0,
        ): RecordsImportModels.DashboardRow =
            RecordsImportModels.DashboardRow(
                kanji,
                1,
                "meaning-$kanji",
                "reading-$kanji",
                "",
                0,
                "weak_support",
                "",
                0,
                0,
                matureSupportCount,
                emptyList<RecordsImportModels.Example>(),
            )

        fun snapshot(
            mistakes: List<StudyStatsStore.RecentMistake>,
        ) = StatsCacheStore.Snapshot(
            outcomeStats = StudyStatsStore.KaniOutcomeStats.empty(),
            impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
            generatedAtMillis = 1_234L,
            sourceVersion = 1L,
            studyImpactStats = StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0),
            recentMistakes = mistakes,
        ).toRepositorySnapshot()
    }
}
