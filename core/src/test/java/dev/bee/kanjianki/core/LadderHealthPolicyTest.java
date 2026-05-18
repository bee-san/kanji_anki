package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class LadderHealthPolicyTest {
    @Test
    public void summarizeCountsActiveRungsAndReviewReadiness() {
        LadderHealthPolicy.Metric metric = LadderHealthPolicy.summarize(
                Arrays.asList(
                        evidence("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 22, 0),
                        evidence("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.REVIEW, 3, 2),
                        evidence("learning", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.NEW_LEARNING, 90, 4),
                        evidence("retired", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 99, 9),
                        null
                ),
                21,
                3
        );

        assertEquals(3, metric.totalActiveItems());
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.FONT_MEANING));
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals(0, metric.countFor(RecordsBase.LadderRung.WORD_READING));
        assertEquals(1, metric.promotionReadyCount());
        assertEquals(1, metric.demotionRiskCount());
        assertEquals(0, metric.demotionReadyCount());
    }

    @Test
    public void summarizeNormalizesNullsAndThresholds() {
        LadderHealthPolicy.Metric metric = LadderHealthPolicy.summarize(
                Collections.singletonList(new LadderHealthPolicy.ItemEvidence(null, null, null, -1, -2, -3)),
                0,
                0
        );

        assertEquals(1, metric.totalActiveItems());
        assertEquals(1, metric.ladderPromotionIntervalDays());
        assertEquals(1, metric.ladderDemotionFailStreak());
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(0, metric.promotionReadyCount());
        assertEquals(0, metric.demotionRiskCount());
        assertEquals(0, metric.demotionReadyCount());
    }

    @Test
    public void fromCountsClampsNegativeValuesAndFillsMissingRungs() {
        LadderHealthPolicy.Metric metric = LadderHealthPolicy.fromCounts(
                Map.of(RecordsBase.LadderRung.TYPE_MEANING, -4),
                -10,
                -1,
                -2,
                -3,
                -4,
                -5
        );

        assertEquals(0, metric.totalActiveItems());
        assertEquals(1, metric.ladderPromotionIntervalDays());
        assertEquals(1, metric.ladderDemotionFailStreak());
        assertEquals(0, metric.countFor(RecordsBase.LadderRung.TYPE_MEANING));
        assertEquals(0, metric.countFor(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(0, metric.promotionReadyCount());
        assertEquals(0, metric.demotionRiskCount());
        assertEquals(0, metric.demotionReadyCount());
    }

    private static LadderHealthPolicy.ItemEvidence evidence(
            String state,
            RecordsBase.LadderRung rung,
            RecordsBase.SchedulerPhase phase,
            int matureIntervalDays,
            int realAgainStreak
    ) {
        return new LadderHealthPolicy.ItemEvidence(state, rung, phase, 0, realAgainStreak, matureIntervalDays);
    }
}
