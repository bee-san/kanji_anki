package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.STATS_RECENT_MISTAKE_LIMIT
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MainActivityStatsModelTest {
    @Test
    fun buildStatsScreenModelUsesFreshCacheWithoutDirectRecompute() {
        val source = FakeStatsSource(
            fresh = snapshot(
                improvedCount = 3,
                sourceVersion = 7,
                studyStreak = StudyStatsStore.StudyStreak(3, 9, true, 8, 3_600_000L),
                studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(4_000L, 9_000L, 2),
            ),
            direct = snapshot(improvedCount = 9, sourceVersion = 11),
        )

        val model = buildStatsScreenModel(source, nowMillis = 4_500_000L)

        assertEquals(1, source.freshReads)
        assertEquals(0, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(0, source.studyImpactReads)
        assertEquals(emptyList<Long>(), source.studyStreakReads)
        assertEquals(emptyList<Int>(), source.recentMistakeLimits)
        assertEquals(emptyList<Long>(), source.studyTimeReads)
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
        val source = FakeStatsSource(
            direct = snapshot(
                improvedCount = 5,
                sourceVersion = 9,
                studyStreak = StudyStatsStore.StudyStreak(7, 10, true, 4, 8_000L),
                studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(5_000L, 10_000L, 3),
            ),
        )

        buildStatsScreenModel(source, nowMillis = 22_222L)

        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(1, source.directRecomputes)
        assertEquals(0, source.studyImpactReads)
        assertEquals(emptyList<Long>(), source.studyStreakReads)
        assertEquals(emptyList<Int>(), source.recentMistakeLimits)
        assertEquals(listOf(22_222L), source.recomputeTimes)
        assertEquals(emptyList<Long>(), source.studyTimeReads)
    }

    @Test
    fun buildStatsScreenModelFallsBackToDirectStatsOnlyWhenLegacyCacheVersion() {
        val source = FakeStatsSource(
            latest = snapshot(
                improvedCount = 8,
                sourceVersion = 3,
                cacheFormatVersion = 1,
            ),
            direct = snapshot(improvedCount = 13, sourceVersion = 10),
        )

        buildStatsScreenModel(source, nowMillis = 33_333L)

        assertEquals(1, source.freshReads)
        assertEquals(1, source.latestReads)
        assertEquals(0, source.directRecomputes)
        assertEquals(1, source.studyImpactReads)
        assertEquals(listOf(33_333L), source.studyStreakReads)
        assertEquals(listOf(STATS_RECENT_MISTAKE_LIMIT), source.recentMistakeLimits)
        assertEquals(listOf(33_333L), source.studyTimeReads)
    }

    @Test
    fun buildStatsScreenModelTranslatesStatsCopyInJapaneseLocale() {
        withLocale(Locale.JAPAN) {
            val source = FakeStatsSource(
                fresh = snapshot(
                    improvedCount = 3,
                    sourceVersion = 7,
                    studyStreak = StudyStatsStore.StudyStreak(3, 9, true, 8, 3_600_000L),
                    studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(4_000L, 9_000L, 2),
                ),
                direct = snapshot(improvedCount = 9, sourceVersion = 11),
            )

            val model = buildStatsScreenModel(source, nowMillis = 4_500_000L)

            assertEquals("統計", model.title)
            assertEquals(
                listOf(
                    "弱い漢字の推移",
                    "Ankiの支え",
                    "学習連続",
                    "学習の影響",
                    "要対応",
                    "ラダー状況",
                    "最近のミス",
                    "学習時間",
                ),
                model.sections.map { it.title },
            )
            assertEquals("弱い漢字3件が改善", model.sections[0].summary)
            assertEquals("成熟カード0件が増加", model.sections[1].summary)
            assertEquals("3日連続", model.sections[2].summary)
            assertEquals("12件の復習", model.sections[3].summary)
            assertEquals("ラダー上のアクティブ漢字0件", model.sections[5].summary)
            assertEquals("最近のミス2件", model.sections[6].summary)
            assertEquals("今日: 4秒", model.sections[7].summary)
            assertEquals("直近7日: 9秒", model.sections[7].body)
            assertEquals("Kaniは動いています", model.verdict.title)
            assertEquals("3件の弱い漢字が改善しました。", model.verdict.body)
        }
    }

    @Test
    fun buildStatsScreenModelShowsRepairEvidenceCardWhenAvailable() {
        val source = FakeStatsSource(
            repairEvidenceRows = listOf(
                StudyStatsStore.repairEvidence(
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
            ),
        )

        val model = buildStatsScreenModel(source, nowMillis = 4_500_000L)

        assertTrue(model.sections.any { it.title == "Repair evidence" })
        val repairCard = model.sections.first { it.title == "Repair evidence" }
        assertEquals("1 repair evidence item", repairCard.summary)
        assertEquals("Latest entries first.", repairCard.body)
        assertEquals(1, repairCard.lines.size)
        assertEquals("弱 · Improving · 70 → 40", repairCard.lines.first().text)
        assertEquals(1, source.repairEvidenceReads)
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
        private val repairEvidenceRows: List<StudyStatsStore.KanjiRepairEvidence> = emptyList(),
    ) : StatsScreenStatsSource {
        var freshReads = 0
        var latestReads = 0
        var directRecomputes = 0
        var studyImpactReads = 0
        var repairEvidenceReads = 0
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

        override fun kanjiRepairEvidence(): List<StudyStatsStore.KanjiRepairEvidence> {
            repairEvidenceReads += 1
            return repairEvidenceRows
        }
    }

    private companion object {
        fun snapshot(
            improvedCount: Int,
            sourceVersion: Long,
            cacheFormatVersion: Int = STATS_CACHE_FORMAT_VERSION,
            studyStreak: StudyStatsStore.StudyStreak = StudyStatsStore.StudyStreak(0, 0, false, 0, 0L),
            studyTaskTimeStats: StudyStatsStore.StudyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(4_000L, 9_000L, 2),
        ): StatsCacheStore.Snapshot {
            return StatsCacheStore.Snapshot(
                StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric(improvedCount, 80.0, 40.0, emptyList()),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    StudyStatsStore.LadderHealthMetric.empty(),
                ),
                KanjiImpactAnalyzer.Report(improvedCount, 0, 0, emptyList()),
                1_111L,
                sourceVersion,
                StudyStatsStore.StudyImpactStats(12, 4, 6, 4, 2, 1),
                listOf(
                    StudyStatsStore.RecentMistake("痛", "again", 4_200_000L),
                    StudyStatsStore.RecentMistake("疲", "hard", 4_140_000L),
                ),
                studyStreak,
                studyTaskTimeStats,
                cacheFormatVersion,
            )
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
