package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
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
        assertEquals(0, source.latestReads)
        assertEquals(0, source.liveReads)
        assertEquals(0, source.activeRowsByKanjiReads)
        assertTrue(data.mistakes.isEmpty())
        assertTrue(data.rowsByKanji.isEmpty())
    }

    @Test
    fun recentMistakesRouteDataUsesLatestSnapshotWhenFreshCacheMissing() {
        val source = CountingRecentMistakesRouteDataSource(
            latestSnapshot = snapshot(
                mistakes = listOf(StudyStatsStore.RecentMistake("痛", "again", 1_000L)),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            ),
        )

        val data = recentMistakesRouteData(source)

        assertEquals(1, source.cachedReads)
        assertEquals(1, source.latestReads)
        assertEquals(0, source.liveReads)
        assertEquals(1, source.activeRowsByKanjiReads)
        assertEquals("痛", data.mistakes.single().kanji)
        assertTrue(data.rowsByKanji.isEmpty())
    }

    @Test
    fun recentMistakesRouteDataFallsBackToLiveQueryForLegacySnapshot() {
        val source = CountingRecentMistakesRouteDataSource(
            cachedSnapshot = snapshot(
                mistakes = listOf(StudyStatsStore.RecentMistake("痛", "again", 1_000L)),
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION - 1,
            ),
            liveMistakes = listOf(StudyStatsStore.RecentMistake("落", "good", 3_000L)),
        )

        val data = recentMistakesRouteData(source)

        assertEquals(1, source.cachedReads)
        assertEquals(0, source.latestReads)
        assertEquals(listOf(STATS_RECENT_MISTAKE_LIMIT), source.liveMistakeLimits)
        assertEquals(1, source.liveReads)
        assertEquals(1, source.activeRowsByKanjiReads)
        assertEquals("落", data.mistakes.single().kanji)
        assertTrue(data.rowsByKanji.isEmpty())
    }

    private class CountingRecentMistakesRouteDataSource : RecentMistakesRouteDataSource {
        var cachedSnapshot: StatsCacheStore.Snapshot? = null
        var latestSnapshot: StatsCacheStore.Snapshot? = null
        var liveMistakes: List<StudyStatsStore.RecentMistake> = emptyList()
        var cachedReads = 0
        var latestReads = 0
        var liveReads = 0
        var activeRowsByKanjiReads = 0
        val liveMistakeLimits = mutableListOf<Int>()

        constructor(
            cachedSnapshot: StatsCacheStore.Snapshot? = null,
            latestSnapshot: StatsCacheStore.Snapshot? = null,
            liveMistakes: List<StudyStatsStore.RecentMistake> = emptyList(),
        ) {
            this.cachedSnapshot = cachedSnapshot
            this.latestSnapshot = latestSnapshot
            this.liveMistakes = liveMistakes
        }

        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            cachedReads += 1
            return cachedSnapshot
        }

        override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            latestReads += 1
            return latestSnapshot
        }

        override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
            liveReads += 1
            liveMistakeLimits += limit
            return liveMistakes
        }

        override fun activeDashboardRowsByKanji(): Map<String, RecordsImportModels.DashboardRow> {
            activeRowsByKanjiReads += 1
            return emptyMap()
        }
    }

    private companion object {
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
