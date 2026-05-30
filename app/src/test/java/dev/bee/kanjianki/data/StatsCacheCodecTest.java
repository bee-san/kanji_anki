package dev.bee.kanjianki.data;

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
}
