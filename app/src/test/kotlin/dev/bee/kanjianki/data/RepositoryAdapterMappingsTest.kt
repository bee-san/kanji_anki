package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.RecordsBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryAdapterMappingsTest {
    @Test
    fun richStatsSnapshotMapsEveryNestedRepositoryProjection() {
        val source = StatsCacheStore.Snapshot(
            outcomeStats = StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric(
                    1,
                    80.0,
                    40.0,
                    listOf(StudyStatsStore.KanjiImprovement("弱", 80.0, 40.0)),
                ),
                StudyStatsStore.MatureSupportGainedMetric(
                    1,
                    2,
                    1,
                    listOf(StudyStatsStore.KanjiSupportGain("漢", 0, 2)),
                ),
                StudyStatsStore.LadderHealthMetric(
                    mapOf(RecordsBase.LadderRung.KANJI_MEANING to 2),
                    2,
                    21,
                    3,
                    1,
                    1,
                    1,
                    1,
                ),
                StudyStatsStore.AdaptiveHealthMetric(
                    mapOf(CoreSkill.RECOGNITION to 2),
                    mapOf("write_kanji" to 1),
                    mapOf(FailureKind.WRITING_SHAPE to 1),
                    2,
                    1,
                    1,
                    1,
                    1,
                    1,
                    1,
                    0,
                ),
            ),
            impactReport = KanjiImpactAnalyzer().analyze(emptyList()),
            generatedAtMillis = 5_000L,
            sourceVersion = 7L,
            studyImpactStats = StudyStatsStore.StudyImpactStats(5, 2, 1, 1, 0, 1),
            recentMistakes = listOf(StudyStatsStore.RecentMistake("弱", "again", 4_000L)),
            studyStreak = StudyStatsStore.StudyStreak(3, 5, true, 2, 4_000L),
            studyTaskTimeStats = StudyStatsStore.StudyTaskTimeStats(600L, 1_200L, 3),
            cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            reviewDaySummaries = listOf(
                StatsCacheStore.ReviewDaySummarySnapshot(1_000L, 5, 1, 1, 2, 1, 1, 0),
            ),
            kanjiRepairEvidence = listOf(repairEvidence()),
            taskTypeDaySummaries = listOf(
                StatsCacheStore.TaskTypeDaySummarySnapshot(1_000L, "kanji_meaning", 4, 5),
            ),
            cumulativeKanjiPracticed = listOf(
                StatsCacheStore.CumulativeKanjiSnapshot(1_000L, 12),
            ),
            wrongPickCounts = mapOf("弱" to mapOf("弓" to 2)),
            confusionMeanings = mapOf("弓" to "bow"),
            ladderForecast = LadderCompletionForecastPolicy.Forecast(
                totalItems = 2,
                burnDown = listOf(LadderCompletionForecastPolicy.MonthPoint(1_000L, 1, 1)),
                projectedCompletionMonthMillis = 2_000L,
                beyondHorizon = false,
                alreadyAtCeiling = 0,
                alreadyParked = 0,
                alreadyRetired = 0,
                assumptionCopyIds = listOf("forecast_assumption"),
            ),
        )

        val actual = source.toRepositorySnapshot()

        assertEquals(1, actual.outcomeStats.weakKanjiImproved.improvedCount)
        assertEquals("弱", actual.outcomeStats.weakKanjiImproved.examples.single().kanji)
        assertEquals(2, actual.outcomeStats.matureSupportGained.matureSupportGained)
        assertEquals("漢", actual.outcomeStats.matureSupportGained.examples.single().kanji)
        assertEquals(2, actual.outcomeStats.ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(0, actual.outcomeStats.ladderHealth.countFor(null))
        assertEquals(2, actual.outcomeStats.adaptiveHealth.countFor(CoreSkill.RECOGNITION))
        assertEquals(0, actual.outcomeStats.adaptiveHealth.countFor(null))
        assertEquals(1, actual.outcomeStats.adaptiveHealth.repairCountFor("write_kanji"))
        assertEquals(0, actual.outcomeStats.adaptiveHealth.repairCountFor(null))
        assertEquals(1, actual.outcomeStats.adaptiveHealth.failureCountFor(FailureKind.WRITING_SHAPE))
        assertEquals(0, actual.outcomeStats.adaptiveHealth.failureCountFor(null))
        assertEquals("弱", actual.recentMistakes.single().kanji)
        assertEquals(1_000L, actual.reviewDaySummaries.single().dayStartMillis)
        assertEquals("kanji_meaning", actual.taskTypeDaySummaries.single().taskType)
        assertEquals(12, actual.cumulativeKanjiPracticed.single().cumulativeCount)
        assertEquals("regressing", actual.kanjiRepairEvidence.single().reason)
        assertEquals(2, actual.wrongPickCounts.getValue("弱").getValue("弓"))
        assertEquals("bow", actual.confusionMeanings["弓"])
        assertEquals(2, actual.ladderForecast?.totalItems)
        assertTrue(actual.studyTaskTimeStats.averageMillisPerTask() > 0L)
    }

    @Test
    fun settingsSnapshotsExposeNormalizedAndDisplayBehavior() {
        val reminder = ReminderSettingsSnapshot(true, 8, 5)
        val invalidReminder = ReminderSettingsSnapshot(true, 99, -1).normalized()
        val autoSync = AutoSyncSettingsSnapshot(true, true, 6, 45, 1L, 2L, 3L)
        val invalidAutoSync = AutoSyncSettingsSnapshot(true, true, -1, 99, -1L, -2L, -3L).normalized()

        assertEquals("08:05", reminder.displayTime())
        assertTrue(invalidReminder.hour in 0..23)
        assertTrue(invalidReminder.minute in 0..59)
        assertEquals("06:45", autoSync.displayTime())
        assertTrue(invalidAutoSync.hour in 0..23)
        assertTrue(invalidAutoSync.minute in 0..59)
        assertTrue(AutoUpdateStatusSnapshot(true, 1L, "ready", "v1", "update.apk", "").hasPendingUpdate())
        assertFalse(AutoUpdateStatusSnapshot(true, 1L, "idle", "v1", "", "").hasPendingUpdate())
    }

    private fun repairEvidence(): StudyStatsStore.KanjiRepairEvidence =
        StudyStatsStore.repairEvidence(
            KanjiRepairEvidencePolicy.Evidence(
                kanjiArg = "弱",
                statusArg = KanjiRepairEvidencePolicy.Status.REGRESSING,
                reasonArg = "regressing",
                explanationArg = "Weakness increased.",
                beforeWeaknessArg = 40,
                afterWeaknessArg = 70,
                beforeMatureSupportArg = 2,
                afterMatureSupportArg = 1,
                kaniReviewsArg = 4,
                writingFailuresArg = 1,
                lastMistakeAtMillisArg = 4_000L,
                lastSyncAtMillisArg = 5_000L,
                confidenceArg = 0.8,
                confidenceReasonArg = "Enough post-review evidence.",
            ),
        )
}
