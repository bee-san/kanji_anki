package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Arrays
import java.util.LinkedHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsCacheCodecTest {
    @Test
    fun outcomeStatsRoundTripPreservesWeakSupportAndLadderMetrics() {
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
            ),
        )

        val json = StatsCacheCodec.outcomeToJson(stats)
        val decoded = StatsCacheCodec.outcomeFromJson(json)

        assertEquals(2, decoded.weakKanjiImproved.improvedCount)
        assertEquals(80.0, decoded.weakKanjiImproved.averageBeforeWeakness, 0.001)
        assertEquals(45.5, decoded.weakKanjiImproved.averageAfterWeakness, 0.001)
        assertEquals(2, decoded.weakKanjiImproved.examples.size)
        assertEquals("痛", decoded.weakKanjiImproved.examples[0].kanji)
        assertEquals(90.0, decoded.weakKanjiImproved.examples[0].beforeWeakness, 0.001)
        assertEquals(40.0, decoded.weakKanjiImproved.examples[0].afterWeakness, 0.001)
        assertEquals("弱", decoded.weakKanjiImproved.examples[1].kanji)

        assertEquals(3, decoded.matureSupportGained.gainedSupportCount)
        assertEquals(4, decoded.matureSupportGained.matureSupportGained)
        assertEquals(1, decoded.matureSupportGained.firstSupportCount)
        assertEquals(1, decoded.matureSupportGained.examples.size)
        assertEquals("漢", decoded.matureSupportGained.examples[0].kanji)
        assertEquals(0, decoded.matureSupportGained.examples[0].beforeMatureSupport)
        assertEquals(2, decoded.matureSupportGained.examples[0].afterMatureSupport)

        assertEquals(11, decoded.ladderHealth.totalActiveItems)
        assertEquals(21, decoded.ladderHealth.ladderPromotionIntervalDays)
        assertEquals(3, decoded.ladderHealth.ladderDemotionFailStreak)
        assertEquals(5, decoded.ladderHealth.promotionReadyCount)
        assertEquals(6, decoded.ladderHealth.demotionRiskCount)
        assertEquals(7, decoded.ladderHealth.demotionReadyCount)
        assertEquals(4, decoded.ladderHealth.countFor(RecordsBase.LadderRung.WRITE_KANJI))
        assertEquals(7, decoded.ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(0, decoded.ladderHealth.countFor(RecordsBase.LadderRung.WORD_READING))
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
