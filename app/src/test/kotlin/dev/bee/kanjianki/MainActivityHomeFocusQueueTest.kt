package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityHomeFocusQueueTest {
    @Test
    fun recentMistakesRouteDataUsesFreshCachedSnapshotWithoutLiveQuery() {
        val source = CountingRecentMistakesRouteDataSource(
            cachedSnapshot = snapshot(
                mistakes = emptyList(),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            ),
        )

        val data = recentMistakesRouteData(source)

        assertEquals(1, source.cachedReads)
        assertEquals(0, source.liveReads)
        assertEquals(0, source.activeRowsByKanjiReads)
        assertTrue(data.mistakes.isEmpty())
        assertTrue(data.rowsByKanji.isEmpty())
    }

    @Test
    fun recentMistakesRouteDataUsesLiveQueryWhenFreshCacheMissing() {
        val source = CountingRecentMistakesRouteDataSource(
            liveMistakes = listOf(StudyStatsStore.RecentMistake("落", "again", 2_000L)),
            studyItems = listOf(studyItem("落")),
            dashboardRowsByKanji = mapOf("落" to dashboardRow("落")),
        )

        val data = recentMistakesRouteData(source)

        assertEquals(1, source.cachedReads)
        assertEquals(listOf(STATS_RECENT_MISTAKE_LIMIT), source.liveMistakeLimits)
        assertEquals(1, source.liveReads)
        assertEquals(1, source.studyItemReads)
        assertEquals(1, source.activeRowsByKanjiReads)
        assertEquals("落", data.mistakes.single().kanji)
        assertEquals(setOf("落"), data.rowsByKanji.keys)
    }

    @Test
    fun recentMistakesRouteDataFallsBackToLiveQueryForLegacySnapshot() {
        val source = CountingRecentMistakesRouteDataSource(
            cachedSnapshot = snapshot(
                mistakes = listOf(StudyStatsStore.RecentMistake("痛", "again", 1_000L)),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION - 1,
            ),
            liveMistakes = listOf(StudyStatsStore.RecentMistake("落", "good", 3_000L)),
            studyItems = listOf(studyItem("落")),
            dashboardRowsByKanji = mapOf("落" to dashboardRow("落")),
        )

        val data = recentMistakesRouteData(source)

        assertEquals(1, source.cachedReads)
        assertEquals(listOf(STATS_RECENT_MISTAKE_LIMIT), source.liveMistakeLimits)
        assertEquals(1, source.liveReads)
        assertEquals(1, source.studyItemReads)
        assertEquals(1, source.activeRowsByKanjiReads)
        assertEquals("落", data.mistakes.single().kanji)
        assertEquals(setOf("落"), data.rowsByKanji.keys)
    }

    @Test
    fun recentMistakesRouteDataRevalidatesCachedMistakeAgainstMatureSupportGate() {
        val source = CountingRecentMistakesRouteDataSource(
            cachedSnapshot = snapshot(
                mistakes = listOf(StudyStatsStore.RecentMistake("済", "again", 1_000L)),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            ),
            dashboardRowsByKanji = mapOf("済" to dashboardRow("済", matureSupportCount = 2)),
        )

        val data = recentMistakesRouteData(source)

        assertTrue(data.mistakes.isEmpty())
        assertTrue(data.rowsByKanji.isEmpty())
    }

    @Test
    fun recentMistakesRouteDataKeepsCachedMatureMistakeWhenEvidenceIsRegressing() {
        val source = CountingRecentMistakesRouteDataSource(
            cachedSnapshot = snapshot(
                mistakes = listOf(StudyStatsStore.RecentMistake("済", "again", 1_000L)),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            ),
            dashboardRowsByKanji = mapOf("済" to dashboardRow("済", matureSupportCount = 2)),
            evidenceStatusByKanji = mapOf("済" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )

        val data = recentMistakesRouteData(source)

        assertEquals("済", data.mistakes.single().kanji)
        assertEquals(setOf("済"), data.rowsByKanji.keys)
    }

    private class CountingRecentMistakesRouteDataSource : RecentMistakesRouteDataSource {
        var cachedSnapshot: StatsCacheStore.Snapshot? = null
        var liveMistakes: List<StudyStatsStore.RecentMistake> = emptyList()
        var studyItems: List<RecordsStudyModels.StudyItem> = emptyList()
        var dashboardRowsByKanji: Map<String, RecordsImportModels.DashboardRow> = emptyMap()
        var evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status> = emptyMap()
        var cachedReads = 0
        var liveReads = 0
        var studyItemReads = 0
        var activeRowsByKanjiReads = 0
        val liveMistakeLimits = mutableListOf<Int>()

        constructor(
            cachedSnapshot: StatsCacheStore.Snapshot? = null,
            liveMistakes: List<StudyStatsStore.RecentMistake> = emptyList(),
            studyItems: List<RecordsStudyModels.StudyItem> = emptyList(),
            dashboardRowsByKanji: Map<String, RecordsImportModels.DashboardRow> = emptyMap(),
            evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status> = emptyMap(),
        ) {
            this.cachedSnapshot = cachedSnapshot
            this.liveMistakes = liveMistakes
            this.studyItems = studyItems
            this.dashboardRowsByKanji = dashboardRowsByKanji
            this.evidenceStatusByKanji = evidenceStatusByKanji
        }

        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            cachedReads += 1
            return cachedSnapshot
        }

        override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
            liveReads += 1
            liveMistakeLimits += limit
            return liveMistakes
        }

        override fun studyItemsForKanji(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> {
            studyItemReads += 1
            return studyItems.filter { kanji.contains(it.kanji) }
        }

        override fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow> {
            activeRowsByKanjiReads += 1
            return dashboardRowsByKanji
        }

        override fun evidenceStatusByKanji(): Map<String, KanjiRepairEvidencePolicy.Status> {
            return evidenceStatusByKanji
        }
    }

    private companion object {
        fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
            return RecordsStudyModels.StudyItem(
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
        }

        fun dashboardRow(
            kanji: String,
            matureSupportCount: Int = 0,
        ): RecordsImportModels.DashboardRow {
            return RecordsImportModels.DashboardRow(
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
        }

        fun snapshot(
            mistakes: List<StudyStatsStore.RecentMistake>,
            cacheFormatVersion: Int,
        ): StatsCacheStore.Snapshot {
            return StatsCacheStore.Snapshot(
                outcomeStats = StudyStatsStore.KaniOutcomeStats.empty(),
                impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
                generatedAtMillis = 1_234L,
                sourceVersion = 1L,
                studyImpactStats = StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0),
                recentMistakes = mistakes,
                cacheFormatVersion = cacheFormatVersion,
            )
        }
    }
}
