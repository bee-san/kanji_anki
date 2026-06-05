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

        val model = buildStatsScreenModel(source, nowMillis = 4_500_000L)

        assertEquals(1, source.freshReads)
        assertEquals(0, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(1, source.studyImpactReads)
        assertEquals(listOf(4_500_000L), source.studyStreakReads)
        assertEquals(listOf(5), source.recentMistakeLimits)
        assertEquals(listOf(4_500_000L), source.studyTimeReads)
        assertEquals(
            listOf(
                "Weak kanji trend",
                "Anki support",
                "Study streak",
                "Study impact",
                "Needs attention",
                "Ladder status",
                "Recent mistakes",
                "Study time",
            ),
            model.sections.map { it.title },
        )
        assertEquals("3-day streak", model.sections[2].summary)
        assertEquals("2 recent mistakes", model.sections[6].summary)
    }

    @Test
    fun buildStatsScreenModelFallsBackToDirectComputeWhenNoCacheExists() {
        val source = FakeStatsSource(direct = snapshot(improvedCount = 5, sourceVersion = 9))

        buildStatsScreenModel(source, nowMillis = 22_222L)

        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(1, source.directRecomputes)
        assertEquals(1, source.studyImpactReads)
        assertEquals(listOf(22_222L), source.studyStreakReads)
        assertEquals(listOf(5), source.recentMistakeLimits)
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
        assertEquals(1, source.studyImpactReads)
        assertEquals(listOf(33_333L), source.studyStreakReads)
        assertEquals(listOf(5), source.recentMistakeLimits)
        assertEquals(listOf(33_333L), source.studyTimeReads)
    }

    private class FakeStatsSource(
        private val fresh: StatsCacheStore.Snapshot? = null,
        private val latest: StatsCacheStore.Snapshot? = null,
        private val direct: StatsCacheStore.Snapshot = snapshot(improvedCount = 1, sourceVersion = 1),
        private val impact: StudyStatsStore.StudyImpactStats = StudyStatsStore.StudyImpactStats(12, 4, 6, 4, 2, 1),
        private val streak: StudyStatsStore.StudyStreak = StudyStatsStore.StudyStreak(3, 9, true, 8, 3_600_000L),
        private val recentMistakeRows: List<StudyStatsStore.RecentMistake> = listOf(
            StudyStatsStore.RecentMistake("痛", "again", 4_200_000L),
            StudyStatsStore.RecentMistake("疲", "hard", 4_140_000L),
        ),
    ) : StatsScreenStatsSource {
        var freshReads = 0
        var latestReads = 0
        var directRecomputes = 0
        var studyImpactReads = 0
        val studyStreakReads = mutableListOf<Long>()
        val recentMistakeLimits = mutableListOf<Int>()
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

        override fun studyImpactStats(): StudyStatsStore.StudyImpactStats {
            studyImpactReads += 1
            return impact
        }

        override fun studyStreak(nowMillis: Long): StudyStatsStore.StudyStreak {
            studyStreakReads += nowMillis
            return streak
        }

        override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
            recentMistakeLimits += limit
            return recentMistakeRows
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
