package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.RecordsBase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.util.Arrays
import java.util.LinkedHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsCacheCodecTest {
    @Test
    fun currentFormatIsTenAfterAdaptiveHealthExtension() {
        assertEquals(10, STATS_CACHE_FORMAT_VERSION)
        assertEquals(366, STATS_REVIEW_DAY_SUMMARY_LIMIT)
    }

    @Test
    fun extendedStatsSnapshotFieldsRoundTrip() {
        val taskTypes = listOf(StatsCacheStore.TaskTypeDaySummarySnapshot(1_000L, "kanji_meaning", 4, 5))
        val cumulative = listOf(StatsCacheStore.CumulativeKanjiSnapshot(1_000L, 12))
        val wrong = mapOf("徴" to mapOf("微" to 5))
        val meanings = mapOf("徴" to "sign", "微" to "minute")
        val forecast = LadderCompletionForecastPolicy.Forecast(
            12,
            listOf(LadderCompletionForecastPolicy.MonthPoint(1_000L, 3, 9)),
            2_000L,
            false,
            1,
            2,
            3,
            listOf("all_passes", "anki_retirement_separate"),
        )
        val json = StatsCacheCodec.outcomeToJson(
            StudyStatsStore.KaniOutcomeStats.empty(),
            taskTypeDaySummaries = taskTypes,
            cumulativeKanjiPracticed = cumulative,
            wrongPickCounts = wrong,
            confusionMeanings = meanings,
            ladderForecast = forecast,
        )
        val root = JSONObject(json)
        assertEquals(taskTypes, StatsCacheCodec.taskTypeDaySummariesFromJson(root.optJSONArray("taskTypeDaySummaries")))
        assertEquals(cumulative, StatsCacheCodec.cumulativeKanjiFromJson(root.optJSONArray("cumulativeKanjiPracticed")))
        assertEquals(wrong, StatsCacheCodec.wrongPickCountsFromJson(root.optJSONObject("wrongPickCounts")))
        assertEquals(meanings, StatsCacheCodec.stringMapFromJson(root.optJSONObject("confusionMeanings")))
        assertEquals(forecast, StatsCacheCodec.forecastFromJson(root.optJSONObject("ladderForecast")))
    }
    @Test
    fun outcomeStatsRoundTripPreservesCachedExtras() {
        val rungCounts = LinkedHashMap<RecordsBase.LadderRung, Int>()
        rungCounts[RecordsBase.LadderRung.WRITE_KANJI] = 4
        rungCounts[RecordsBase.LadderRung.KANJI_MEANING] = 7
        val stats = StudyStatsStore.KaniOutcomeStats(
            StudyStatsStore.WeakKanjiImprovedMetric(
                2,
                80.0,
                45.5,
                Arrays.asList(
                    StudyStatsStore.KanjiImprovement("痛", 90.0, 40.0),
                    StudyStatsStore.KanjiImprovement("弱", 70.0, 51.0),
                ),
            ),
            StudyStatsStore.MatureSupportGainedMetric(
                3,
                4,
                1,
                Arrays.asList(StudyStatsStore.KanjiSupportGain("漢", 0, 2)),
            ),
            StudyStatsStore.LadderHealthMetric(
                rungCounts,
                11,
                21,
                3,
                5,
                6,
                7,
                8,
            ),
        )
        val studyImpact = StudyStatsStore.StudyImpactStats(12, 4, 3, 2, 1, 0)
        val mistakes = Arrays.asList(
            StudyStatsStore.RecentMistake("痛", "again", 1_000L),
            StudyStatsStore.RecentMistake("弱", "hard", 2_000L),
        )
        val streak = StudyStatsStore.StudyStreak(5, 9, true, 4, 7_000L)
        val taskTimes = StudyStatsStore.StudyTaskTimeStats(5_500L, 12_000L, 3)

        val json = StatsCacheCodec.outcomeToJson(stats, studyImpact, mistakes, streak, taskTimes)
        val root = JSONObject(json)
        val decoded = StatsCacheCodec.outcomeFromJson(json)
        val decodedImpact = StatsCacheCodec.studyImpactStatsFromJson(root.optJSONObject("studyImpactStats"))
        val decodedMistakes = StatsCacheCodec.recentMistakesFromJson(root.optJSONArray("recentMistakes"))
        val decodedStreak = StatsCacheCodec.studyStreakFromJson(root.optJSONObject("studyStreak"))
        val decodedTaskTimes = StatsCacheCodec.studyTaskTimeStatsFromJson(root.optJSONObject("studyTaskTimeStats"))

        assertEquals(STATS_CACHE_FORMAT_VERSION, root.optInt("cacheFormatVersion", 0))
        assertEquals(2, decoded.weakKanjiImproved.improvedCount)
        assertEquals(8, decoded.ladderHealth.stuckCount)
        assertEquals(7, decoded.ladderHealth.demotionReadyCount)
        assertEquals(12, decodedImpact.totalReviews)
        assertEquals(4, decodedImpact.distinctReviewedKanji)
        assertEquals(2, decodedMistakes.size)
        assertEquals("痛", decodedMistakes[0].kanji)
        assertEquals("again", decodedMistakes[0].rating)
        assertEquals(1_000L, decodedMistakes[0].reviewedAtMillis)

        assertEquals(5, decodedStreak.currentDays)
        assertEquals(9, decodedStreak.bestDays)
        assertEquals(true, decodedStreak.studiedToday)
        assertEquals(4, decodedStreak.reviewsToday)
        assertEquals(7_000L, decodedStreak.lastStudyAtMillis)

        assertEquals(5_500L, decodedTaskTimes.todayMillis)
        assertEquals(12_000L, decodedTaskTimes.lastSevenDaysMillis)
        assertEquals(3, decodedTaskTimes.answeredTasks)
    }

    @Test
    fun outcomeStatsRoundTripPreservesReviewDaySummaries() {
        val stats = StudyStatsStore.KaniOutcomeStats(
            StudyStatsStore.WeakKanjiImprovedMetric(1, 91.0, 44.0, emptyList()),
            StudyStatsStore.MatureSupportGainedMetric.empty(),
            StudyStatsStore.LadderHealthMetric.empty(),
        )
        val studyImpact = StudyStatsStore.StudyImpactStats(12, 4, 3, 2, 1, 0)
        val mistakes = Arrays.asList(
            StudyStatsStore.RecentMistake("痛", "again", 1_000L),
            StudyStatsStore.RecentMistake("弱", "hard", 2_000L),
        )
        val streak = StudyStatsStore.StudyStreak(5, 9, true, 4, 7_000L)
        val taskTimes = StudyStatsStore.StudyTaskTimeStats(5_500L, 12_000L, 3)
        val reviewDaySummaries = listOf(
            StatsCacheStore.ReviewDaySummarySnapshot(1_000L, 8, 2, 1, 3, 2, 4, 1),
            StatsCacheStore.ReviewDaySummarySnapshot(2_000L, 4, 1, 1, 1, 1, 0, 0),
        )

        val json = StatsCacheCodec.outcomeToJson(stats, studyImpact, mistakes, streak, taskTimes, reviewDaySummaries)
        val root = JSONObject(json)
        val decoded = StatsCacheCodec.reviewDaySummariesFromJson(root.optJSONArray("reviewDaySummaries"))

        assertEquals(STATS_CACHE_FORMAT_VERSION, root.optInt("cacheFormatVersion", 0))
        assertEquals(reviewDaySummaries, decoded)
    }

    @Test
    fun outcomeStatsRoundTripPreservesRepairEvidence() {
        val stats = StudyStatsStore.KaniOutcomeStats.empty()
        val repairEvidence = listOf(
            StudyStatsStore.repairEvidence(
                KanjiRepairEvidencePolicy.Evidence(
                    kanjiArg = "弱",
                    statusArg = KanjiRepairEvidencePolicy.Status.REGRESSING,
                    reasonArg = "regressing_weakness_after_reviews",
                    explanationArg = "After Kani reviews, AnkiDroid weakness moved 40 → 70.",
                    beforeWeaknessArg = 40,
                    afterWeaknessArg = 70,
                    beforeMatureSupportArg = 3,
                    afterMatureSupportArg = 1,
                    kaniReviewsArg = 4,
                    writingFailuresArg = 2,
                    lastMistakeAtMillisArg = 9_000L,
                    lastSyncAtMillisArg = 10_000L,
                    confidenceArg = 0.79,
                    confidenceReasonArg = "Weakness moved 40 → 70 after 4 Kani reviews.",
                )
            ),
            StudyStatsStore.repairEvidence(
                KanjiRepairEvidencePolicy.Evidence(
                    kanjiArg = "痛",
                    statusArg = KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE,
                    reasonArg = "no_post_review_sync",
                    explanationArg = "Study recorded; waiting for a later AnkiDroid sync.",
                    beforeWeaknessArg = 65,
                    afterWeaknessArg = null,
                    beforeMatureSupportArg = 2,
                    afterMatureSupportArg = null,
                    kaniReviewsArg = 1,
                    writingFailuresArg = 0,
                    lastMistakeAtMillisArg = 0L,
                    lastSyncAtMillisArg = 6_000L,
                    confidenceArg = 0.10,
                    confidenceReasonArg = "A later AnkiDroid sync is still missing.",
                )
            ),
        )

        val json = StatsCacheCodec.outcomeToJson(stats, kanjiRepairEvidence = repairEvidence)
        val root = JSONObject(json)
        val decoded = StatsCacheCodec.kanjiRepairEvidenceFromJson(root.optJSONArray("kanjiRepairEvidence"))

        assertEquals(STATS_CACHE_FORMAT_VERSION, root.optInt("cacheFormatVersion", 0))
        assertEquals(2, decoded.size)
        assertRepairEvidenceEquals(repairEvidence[0], decoded[0])
        assertRepairEvidenceEquals(repairEvidence[1], decoded[1])
    }

    @Test
    fun outcomeStatsInvalidJsonReturnsEmptyStats() {
        val decoded = StatsCacheCodec.outcomeFromJson("not-json")

        assertEquals(0, decoded.weakKanjiImproved.improvedCount)
        assertEquals(0, decoded.weakKanjiImproved.examples.size)
        assertEquals(0, decoded.matureSupportGained.matureSupportGained)
        assertEquals(0, decoded.matureSupportGained.examples.size)
        assertEquals(0, decoded.ladderHealth.totalActiveItems)
    }

    @Test
    fun impactReportRoundTripPreservesCountsAndRows() {
        val first = KanjiImpactAnalyzer.Row.create(
            "弱",
            KanjiImpactAnalyzer.BUCKET_NOT_HELPING,
            6.5,
            8.0,
            0.82,
            0.61,
            2,
            3,
            4,
            1,
            5,
            12,
            "Add clearer Anki support.",
        )
        val second = KanjiImpactAnalyzer.Row.create(
            "漢",
            KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS,
            5.0,
            5.5,
            0.50,
            0.55,
            0,
            1,
            0,
            2,
            3,
            4,
            "Review more before judging.",
        )
        val report = KanjiImpactAnalyzer.Report(
            1,
            2,
            3,
            Arrays.asList(first, second),
        )

        val json = StatsCacheCodec.impactReportToJson(report)
        val decoded = StatsCacheCodec.impactReportFromJson(json)

        assertEquals(1, decoded.helpedCount)
        assertEquals(2, decoded.notHelpingCount)
        assertEquals(3, decoded.needsMoreCardsCount)
        assertEquals(2, decoded.rows.size)
        assertImpactRowEquals(first, decoded.rows[0])
        assertImpactRowEquals(second, decoded.rows[1])
    }

    @Test
    fun impactReportInvalidJsonReturnsEmptyReport() {
        val decoded = StatsCacheCodec.impactReportFromJson("not-json")

        assertEquals(0, decoded.helpedCount)
        assertEquals(0, decoded.notHelpingCount)
        assertEquals(0, decoded.needsMoreCardsCount)
        assertEquals(0, decoded.rows.size)
    }

    private fun assertRepairEvidenceEquals(expected: StudyStatsStore.KanjiRepairEvidence, actual: StudyStatsStore.KanjiRepairEvidence) {
        assertEquals(expected.kanji, actual.kanji)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.reason, actual.reason)
        assertEquals(expected.explanation, actual.explanation)
        assertEquals(expected.beforeWeakness, actual.beforeWeakness)
        assertEquals(expected.afterWeakness, actual.afterWeakness)
        assertEquals(expected.beforeMatureSupport, actual.beforeMatureSupport)
        assertEquals(expected.afterMatureSupport, actual.afterMatureSupport)
        assertEquals(expected.kaniReviews, actual.kaniReviews)
        assertEquals(expected.writingFailures, actual.writingFailures)
        assertEquals(expected.lastMistakeAtMillis, actual.lastMistakeAtMillis)
        assertEquals(expected.lastSyncAtMillis, actual.lastSyncAtMillis)
        assertEquals(expected.confidence, actual.confidence, 0.001)
        assertEquals(expected.confidenceReason, actual.confidenceReason)
    }

    private fun assertImpactRowEquals(expected: KanjiImpactAnalyzer.Row, actual: KanjiImpactAnalyzer.Row) {
        assertEquals(expected.kanji, actual.kanji)
        assertEquals(expected.bucket, actual.bucket)
        assertEquals(expected.baselineDifficulty, actual.baselineDifficulty, 0.001)
        assertEquals(expected.currentDifficulty, actual.currentDifficulty, 0.001)
        assertEquals(expected.baselineRetention, actual.baselineRetention, 0.001)
        assertEquals(expected.currentRetention, actual.currentRetention, 0.001)
        assertEquals(expected.retentionDelta, actual.retentionDelta, 0.001)
        assertEquals(expected.difficultyDelta, actual.difficultyDelta, 0.001)
        assertEquals(expected.baselineMatureCards, actual.baselineMatureCards)
        assertEquals(expected.currentMatureCards, actual.currentMatureCards)
        assertEquals(expected.sameCardCount, actual.sameCardCount)
        assertEquals(expected.newCardCount, actual.newCardCount)
        assertEquals(expected.currentCardCount, actual.currentCardCount)
        assertEquals(expected.reviewCount, actual.reviewCount)
        assertEquals(expected.advice, actual.advice)
    }
}
