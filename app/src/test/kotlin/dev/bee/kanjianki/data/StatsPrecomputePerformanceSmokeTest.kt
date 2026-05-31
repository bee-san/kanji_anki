package dev.bee.kanjianki.data

import dev.bee.kanjianki.StatsScreenStatsSource
import dev.bee.kanjianki.buildStatsScreenModel
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPrecomputePerformanceSmokeTest {
    @Test
    fun statsRouteCachedReadPathAvoidsDirectImpactRecomputation() {
        val source = GuardedStatsSource(fresh = snapshot(21))

        buildStatsScreenModel(source, nowMillis = 44_444L)

        assertEquals(1, source.freshReads)
        assertEquals(0, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(listOf(44_444L), source.studyTimeReads)
    }

    private class GuardedStatsSource(
        private val fresh: StatsCacheStore.Snapshot? = null,
    ) : StatsScreenStatsSource {
        var freshReads = 0
        var latestReads = 0
        var directRecomputes = 0
        val studyTimeReads = mutableListOf<Long>()

        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            freshReads += 1
            return fresh
        }

        override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
            latestReads += 1
            return null
        }

        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
            directRecomputes += 1
            throw AssertionError("cached stats route must not recompute impact report synchronously")
        }

        override fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
            studyTimeReads += nowMillis
            return StudyStatsStore.StudyTaskTimeStats(1_000L, 2_000L, 2)
        }
    }

    private companion object {
        fun snapshot(improvedCount: Int): StatsCacheStore.Snapshot {
            return StatsCacheStore.Snapshot(
                StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric(improvedCount, 80.0, 40.0, emptyList()),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    StudyStatsStore.LadderHealthMetric.empty(),
                ),
                KanjiImpactAnalyzer.Report(improvedCount, 0, 0, emptyList()),
                1_111L,
                1L,
            )
        }
    }
}
