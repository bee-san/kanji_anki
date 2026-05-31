package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityStatsModelTest {
    @Test
    fun buildStatsScreenModelUsesFreshCacheWithoutDirectRecompute() {
        val source = FakeStatsSource(fresh = snapshot(improvedCount = 3, sourceVersion = 7))

        buildStatsScreenModel(source, nowMillis = 12_345L)

        assertEquals(1, source.freshReads)
        assertEquals(0, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(listOf(12_345L), source.studyTimeReads)
    }

    @Test
    fun buildStatsScreenModelFallsBackToDirectComputeWhenNoCacheExists() {
        val source = FakeStatsSource(direct = snapshot(improvedCount = 5, sourceVersion = 9))

        buildStatsScreenModel(source, nowMillis = 22_222L)

        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(1, source.directRecomputes)
        assertEquals(listOf(22_222L), source.recomputeTimes)
        assertEquals(listOf(22_222L), source.studyTimeReads)
    }

    @Test
    fun buildStatsScreenModelUsesLatestStaleCacheWithoutDirectRecompute() {
        val source = FakeStatsSource(
            latest = snapshot(improvedCount = 8, sourceVersion = 3),
            direct = snapshot(improvedCount = 13, sourceVersion = 10),
        )

        buildStatsScreenModel(source, nowMillis = 33_333L)

        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(listOf(33_333L), source.studyTimeReads)
    }

    private class FakeStatsSource(
        private val fresh: StatsCacheStore.Snapshot? = null,
        private val latest: StatsCacheStore.Snapshot? = null,
        private val direct: StatsCacheStore.Snapshot = snapshot(improvedCount = 1, sourceVersion = 1),
    ) : StatsScreenStatsSource {
        var freshReads = 0
        var latestReads = 0
        var directRecomputes = 0
        val recomputeTimes = mutableListOf<Long>()
        val studyTimeReads = mutableListOf<Long>()

        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            freshReads += 1
            return fresh
        }

        override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            latestReads += 1
            return latest
        }

        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
            directRecomputes += 1
            recomputeTimes += nowMillis
            return direct
        }

        override fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
            studyTimeReads += nowMillis
            return StudyStatsStore.StudyTaskTimeStats(4_000L, 9_000L, 2)
        }
    }

    private companion object {
        fun snapshot(improvedCount: Int, sourceVersion: Long): StatsCacheStore.Snapshot {
            return StatsCacheStore.Snapshot(
                StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric(improvedCount, 80.0, 40.0, emptyList()),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    StudyStatsStore.LadderHealthMetric.empty(),
                ),
                KanjiImpactAnalyzer.Report(improvedCount, 0, 0, emptyList()),
                1_111L,
                sourceVersion,
            )
        }
    }
}
