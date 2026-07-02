package dev.bee.kanjianki.data

import dev.bee.kanjianki.StatsScreenStatsSource
import dev.bee.kanjianki.buildStatsScreenModel
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPrecomputePerformanceSmokeTest {
    @Test
    fun statsRouteCachedReadPathUsesCachedRepairEvidenceWithoutLiveRangeQuery() {
        val source = GuardedStatsSource(
            fresh = snapshot(
                improvedCount = 21,
                repairEvidence = listOf(cachedRepairEvidence()),
            )
        )

        val model = buildStatsScreenModel(source, nowMillis = 44_444L)

        assertEquals(1, source.freshReads)
        assertEquals(0, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(0, source.studyImpactReads)
        assertEquals(0, source.repairEvidenceReads)
        assertEquals(emptyList<Long>(), source.studyStreakReads)
        assertEquals(emptyList<Int>(), source.recentMistakeLimits)
        assertEquals(emptyList<Long>(), source.studyTimeReads)
        val repairSections = model.sections.filter { it.title.startsWith("Repair evidence") }
        assertEquals(listOf("Repair evidence cohort", "Repair evidence"), repairSections.map { it.title })
        assertEquals("1 repair evidence item", repairSections[0].summary)
        assertEquals(
            listOf("0 repairs retired in the last 30 days", "弱 · Improving · high confidence"),
            repairSections[0].lines.map { it.text },
        )
        assertEquals("弱 · Improving · 70 → 40", repairSections[1].lines.single().text)
    }

    private class GuardedStatsSource(
        private val fresh: StatsCacheStore.Snapshot? = null,
    ) : StatsScreenStatsSource {
        var freshReads = 0
        var latestReads = 0
        var directRecomputes = 0
        var studyImpactReads = 0
        var repairEvidenceReads = 0
        val studyStreakReads = mutableListOf<Long>()
        val recentMistakeLimits = mutableListOf<Int>()
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

        override fun studyImpactStats(): StudyStatsStore.StudyImpactStats {
            studyImpactReads += 1
            return StudyStatsStore.StudyImpactStats(8, 3, 2, 1, 1, 0)
        }

        override fun studyStreak(nowMillis: Long): StudyStatsStore.StudyStreak {
            studyStreakReads += nowMillis
            return StudyStatsStore.StudyStreak(2, 5, true, 4, 40_000L)
        }

        override fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
            recentMistakeLimits += limit
            return listOf(StudyStatsStore.RecentMistake("痛", "again", 40_000L))
        }

        override fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
            studyTimeReads += nowMillis
            return StudyStatsStore.StudyTaskTimeStats(1_000L, 2_000L, 2)
        }

        override fun kanjiRepairEvidence(): List<StudyStatsStore.KanjiRepairEvidence> {
            repairEvidenceReads += 1
            return emptyList()
        }

        override fun retiredRepairsLast30Days(nowMillis: Long): Int {
            // The retired-repairs count is an intentionally cheap live COUNT query,
            // allowed even on the cached stats route.
            return 0
        }
    }

    private companion object {
        fun snapshot(
            improvedCount: Int,
            repairEvidence: List<StudyStatsStore.KanjiRepairEvidence> = emptyList(),
        ): StatsCacheStore.Snapshot {
            return StatsCacheStore.Snapshot(
                StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric(improvedCount, 80.0, 40.0, emptyList()),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    StudyStatsStore.LadderHealthMetric.empty(),
                ),
                KanjiImpactAnalyzer.Report(improvedCount, 0, 0, emptyList()),
                1_111L,
                1L,
                StudyStatsStore.StudyImpactStats(8, 3, 2, 1, 1, 0),
                listOf(StudyStatsStore.RecentMistake("痛", "again", 40_000L)),
                StudyStatsStore.StudyStreak(2, 5, true, 4, 40_000L),
                StudyStatsStore.StudyTaskTimeStats(1_000L, 2_000L, 2),
                STATS_CACHE_FORMAT_VERSION,
                kanjiRepairEvidence = repairEvidence,
            )
        }

        fun cachedRepairEvidence(): StudyStatsStore.KanjiRepairEvidence {
            return StudyStatsStore.repairEvidence(
                KanjiRepairEvidencePolicy.Evidence(
                    kanjiArg = "弱",
                    statusArg = KanjiRepairEvidencePolicy.Status.IMPROVING,
                    reasonArg = "improved_weakness_after_reviews",
                    explanationArg = "After Kani reviews, AnkiDroid weakness moved 70 → 40.",
                    beforeWeaknessArg = 70,
                    afterWeaknessArg = 40,
                    beforeMatureSupportArg = 0,
                    afterMatureSupportArg = 3,
                    kaniReviewsArg = 3,
                    writingFailuresArg = 0,
                    lastMistakeAtMillisArg = 2_000L,
                    lastSyncAtMillisArg = 5_000L,
                    confidenceArg = 0.84,
                    confidenceReasonArg = "Weakness moved 70 → 40 after 3 Kani reviews and 2 post-review samples.",
                )
            )
        }
    }
}
