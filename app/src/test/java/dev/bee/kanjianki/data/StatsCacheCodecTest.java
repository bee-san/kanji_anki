package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.KanjiImpactAnalyzer;
import dev.bee.kanjianki.core.RecordsBase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StatsCacheCodecTest {
    @Test
    public void outcomeStatsRoundTripPreservesWeakSupportAndLadderMetrics() {
        Map<RecordsBase.LadderRung, Integer> rungCounts = new LinkedHashMap<>();
        rungCounts.put(RecordsBase.LadderRung.WRITE_KANJI, 4);
        rungCounts.put(RecordsBase.LadderRung.KANJI_MEANING, 7);
        StudyStatsStore.KaniOutcomeStats stats = new StudyStatsStore.KaniOutcomeStats(
                new StudyStatsStore.WeakKanjiImprovedMetric(
                        2,
                        80.0,
                        45.5,
                        Arrays.asList(
                                new StudyStatsStore.KanjiImprovement("痛", 90.0, 40.0),
                                new StudyStatsStore.KanjiImprovement("弱", 70.0, 51.0)
                        )
                ),
                new StudyStatsStore.MatureSupportGainedMetric(
                        3,
                        4,
                        1,
                        Arrays.asList(new StudyStatsStore.KanjiSupportGain("漢", 0, 2))
                ),
                new StudyStatsStore.LadderHealthMetric(
                        rungCounts,
                        11,
                        21,
                        3,
                        5,
                        6,
                        7
                )
        );

        String json = StatsCacheCodec.outcomeToJson(stats);
        StudyStatsStore.KaniOutcomeStats decoded = StatsCacheCodec.outcomeFromJson(json);

        assertEquals(2, decoded.weakKanjiImproved.improvedCount);
        assertEquals(80.0, decoded.weakKanjiImproved.averageBeforeWeakness, 0.001);
        assertEquals(45.5, decoded.weakKanjiImproved.averageAfterWeakness, 0.001);
        assertEquals(2, decoded.weakKanjiImproved.examples.size());
        assertEquals("痛", decoded.weakKanjiImproved.examples.get(0).kanji);
        assertEquals(90.0, decoded.weakKanjiImproved.examples.get(0).beforeWeakness, 0.001);
        assertEquals(40.0, decoded.weakKanjiImproved.examples.get(0).afterWeakness, 0.001);
        assertEquals("弱", decoded.weakKanjiImproved.examples.get(1).kanji);

        assertEquals(3, decoded.matureSupportGained.gainedSupportCount);
        assertEquals(4, decoded.matureSupportGained.matureSupportGained);
        assertEquals(1, decoded.matureSupportGained.firstSupportCount);
        assertEquals(1, decoded.matureSupportGained.examples.size());
        assertEquals("漢", decoded.matureSupportGained.examples.get(0).kanji);
        assertEquals(0, decoded.matureSupportGained.examples.get(0).beforeMatureSupport);
        assertEquals(2, decoded.matureSupportGained.examples.get(0).afterMatureSupport);

        assertEquals(11, decoded.ladderHealth.totalActiveItems);
        assertEquals(21, decoded.ladderHealth.ladderPromotionIntervalDays);
        assertEquals(3, decoded.ladderHealth.ladderDemotionFailStreak);
        assertEquals(5, decoded.ladderHealth.promotionReadyCount);
        assertEquals(6, decoded.ladderHealth.demotionRiskCount);
        assertEquals(7, decoded.ladderHealth.demotionReadyCount);
        assertEquals(4, decoded.ladderHealth.countFor(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals(7, decoded.ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(0, decoded.ladderHealth.countFor(RecordsBase.LadderRung.WORD_READING));
    }

    @Test
    public void outcomeStatsInvalidJsonReturnsEmptyStats() {
        StudyStatsStore.KaniOutcomeStats decoded = StatsCacheCodec.outcomeFromJson("not-json");

        assertEquals(0, decoded.weakKanjiImproved.improvedCount);
        assertEquals(0, decoded.weakKanjiImproved.examples.size());
        assertEquals(0, decoded.matureSupportGained.matureSupportGained);
        assertEquals(0, decoded.matureSupportGained.examples.size());
        assertEquals(0, decoded.ladderHealth.totalActiveItems);
    }

    @Test
    public void impactReportRoundTripPreservesCountsAndRows() {
        KanjiImpactAnalyzer.Row first = KanjiImpactAnalyzer.Row.Companion.create(
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
                "Add clearer Anki support."
        );
        KanjiImpactAnalyzer.Row second = KanjiImpactAnalyzer.Row.Companion.create(
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
                "Review more before judging."
        );
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer.Report(
                1,
                2,
                3,
                Arrays.asList(first, second)
        );

        String json = StatsCacheCodec.impactReportToJson(report);
        KanjiImpactAnalyzer.Report decoded = StatsCacheCodec.impactReportFromJson(json);

        assertEquals(1, decoded.helpedCount);
        assertEquals(2, decoded.notHelpingCount);
        assertEquals(3, decoded.needsMoreCardsCount);
        assertEquals(2, decoded.rows.size());
        assertImpactRowEquals(first, decoded.rows.get(0));
        assertImpactRowEquals(second, decoded.rows.get(1));
    }

    @Test
    public void impactReportInvalidJsonReturnsEmptyReport() {
        KanjiImpactAnalyzer.Report decoded = StatsCacheCodec.impactReportFromJson("not-json");

        assertEquals(0, decoded.helpedCount);
        assertEquals(0, decoded.notHelpingCount);
        assertEquals(0, decoded.needsMoreCardsCount);
        assertEquals(0, decoded.rows.size());
    }

    private static void assertImpactRowEquals(KanjiImpactAnalyzer.Row expected, KanjiImpactAnalyzer.Row actual) {
        assertEquals(expected.kanji, actual.kanji);
        assertEquals(expected.bucket, actual.bucket);
        assertEquals(expected.baselineDifficulty, actual.baselineDifficulty, 0.001);
        assertEquals(expected.currentDifficulty, actual.currentDifficulty, 0.001);
        assertEquals(expected.baselineRetention, actual.baselineRetention, 0.001);
        assertEquals(expected.currentRetention, actual.currentRetention, 0.001);
        assertEquals(expected.retentionDelta, actual.retentionDelta, 0.001);
        assertEquals(expected.difficultyDelta, actual.difficultyDelta, 0.001);
        assertEquals(expected.baselineMatureCards, actual.baselineMatureCards);
        assertEquals(expected.currentMatureCards, actual.currentMatureCards);
        assertEquals(expected.sameCardCount, actual.sameCardCount);
        assertEquals(expected.newCardCount, actual.newCardCount);
        assertEquals(expected.currentCardCount, actual.currentCardCount);
        assertEquals(expected.reviewCount, actual.reviewCount);
        assertEquals(expected.advice, actual.advice);
    }
}
