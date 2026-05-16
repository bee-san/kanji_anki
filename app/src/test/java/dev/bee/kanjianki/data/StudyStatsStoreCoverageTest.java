package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.Records;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class StudyStatsStoreCoverageTest {
    @Test
    public void outcomeStatsUseOnlyKanjiWithReviewsAndBothSnapshots() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Arrays.asList(
                        outcome("弱", snapshot(90, 0), snapshot(45, 2)),
                        outcome("支", snapshot(70, 1), snapshot(64, 3)),
                        outcome("前", null, snapshot(30, 1)),
                        outcome("後", snapshot(60, 0), null),
                        outcome("浅", snapshot(60, 0), snapshot(57, 1)),
                        outcome("悪", snapshot(30, 2), snapshot(50, 1))
                ),
                Collections.emptyList(),
                3
        );

        assertEquals(2, stats.weakKanjiImproved.improvedCount);
        assertEquals(0.80, stats.weakKanjiImproved.averageBeforeWeakness, 0.001);
        assertEquals(0.545, stats.weakKanjiImproved.averageAfterWeakness, 0.001);
        assertEquals("弱", stats.weakKanjiImproved.examples.get(0).kanji);
        assertEquals("支", stats.weakKanjiImproved.examples.get(1).kanji);

        assertEquals(3, stats.matureSupportGained.gainedSupportCount);
        assertEquals(5, stats.matureSupportGained.matureSupportGained);
        assertEquals(2, stats.matureSupportGained.firstSupportCount);
        assertEquals("弱", stats.matureSupportGained.examples.get(0).kanji);
        assertEquals("支", stats.matureSupportGained.examples.get(1).kanji);
        assertEquals("浅", stats.matureSupportGained.examples.get(2).kanji);
    }

    @Test
    public void outcomeExamplesAreRankedByImpactThenKanjiAndLimitedToThree() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Arrays.asList(
                        outcome("丁", snapshot(80, 1), snapshot(30, 3)),
                        outcome("一", snapshot(80, 0), snapshot(30, 2)),
                        outcome("七", snapshot(90, 0), snapshot(60, 1)),
                        outcome("二", snapshot(60, 0), snapshot(50, 1)),
                        outcome("万", snapshot(70, 5), snapshot(66, 4))
                ),
                Collections.emptyList(),
                3
        );

        assertEquals(4, stats.weakKanjiImproved.improvedCount);
        assertEquals(3, stats.weakKanjiImproved.examples.size());
        assertEquals("一", stats.weakKanjiImproved.examples.get(0).kanji);
        assertEquals("丁", stats.weakKanjiImproved.examples.get(1).kanji);
        assertEquals("七", stats.weakKanjiImproved.examples.get(2).kanji);

        assertEquals(4, stats.matureSupportGained.gainedSupportCount);
        assertEquals(6, stats.matureSupportGained.matureSupportGained);
        assertEquals(3, stats.matureSupportGained.examples.size());
        assertEquals("一", stats.matureSupportGained.examples.get(0).kanji);
        assertEquals("丁", stats.matureSupportGained.examples.get(1).kanji);
        assertEquals("七", stats.matureSupportGained.examples.get(2).kanji);
    }

    @Test
    public void ladderHealthUsesConfiguredFsrsAndFailThresholdsOnlyInReviewPhase() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Collections.emptyList(),
                Arrays.asList(
                        item("review", Records.LadderRung.WRITE_KANJI, Records.SchedulerPhase.REVIEW, 0, 2, 0),
                        item("review", Records.LadderRung.TYPE_MEANING, Records.SchedulerPhase.REVIEW, 2, 0, 22),
                        item("review", Records.LadderRung.SIMILAR_KANJI, Records.SchedulerPhase.REVIEW, 0, 1),
                        item("review", Records.LadderRung.KANJI_MEANING, Records.SchedulerPhase.REVIEW, 1, 0, 21),
                        item("review", Records.LadderRung.FONT_MEANING, Records.SchedulerPhase.NEW_LEARNING, 8, 8, 40),
                        item("review", Records.LadderRung.WORD_READING, Records.SchedulerPhase.RELEARNING, 8, 8, 40),
                        item("retired", Records.LadderRung.WORD_READING, Records.SchedulerPhase.REVIEW, 9, 9, 40)
                ),
                21,
                2
        );

        assertEquals(6, stats.ladderHealth.totalActiveItems);
        assertEquals(21, stats.ladderHealth.ladderPromotionIntervalDays);
        assertEquals(2, stats.ladderHealth.ladderDemotionFailStreak);
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.WRITE_KANJI));
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.TYPE_MEANING));
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.SIMILAR_KANJI));
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.KANJI_MEANING));
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.FONT_MEANING));
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.WORD_READING));
        assertEquals(1, stats.ladderHealth.promotionReadyCount);
        assertEquals(2, stats.ladderHealth.demotionRiskCount);
        assertEquals(1, stats.ladderHealth.demotionReadyCount);
    }

    @Test
    public void ladderHealthFallsBackToOneWhenThresholdSettingsAreInvalid() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                null,
                Arrays.asList(
                        null,
                        item(null, null, null, -4, -8),
                        item("review", Records.LadderRung.TYPE_MEANING, Records.SchedulerPhase.REVIEW, 1, 1, 2)
                ),
                0,
                0
        );

        assertEquals(1, stats.ladderHealth.ladderPromotionIntervalDays);
        assertEquals(1, stats.ladderHealth.ladderDemotionFailStreak);
        assertEquals(2, stats.ladderHealth.totalActiveItems);
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.KANJI_MEANING));
        assertEquals(1, stats.ladderHealth.countFor(Records.LadderRung.TYPE_MEANING));
        assertEquals(1, stats.ladderHealth.promotionReadyCount);
        assertEquals(1, stats.ladderHealth.demotionRiskCount);
        assertEquals(1, stats.ladderHealth.demotionReadyCount);
    }

    private static StudyStatsStore.OutcomeEvidence outcome(
            String kanji,
            StudyStatsStore.OutcomeSnapshot before,
            StudyStatsStore.OutcomeSnapshot after
    ) {
        return new StudyStatsStore.OutcomeEvidence(kanji, before, after);
    }

    private static StudyStatsStore.OutcomeSnapshot snapshot(int weaknessScore, int matureSupportCount) {
        return new StudyStatsStore.OutcomeSnapshot(weaknessScore, matureSupportCount);
    }

    private static StudyStatsStore.LadderItemEvidence item(
            String state,
            Records.LadderRung rung,
            Records.SchedulerPhase phase,
            int realPassStreak,
            int realAgainStreak
    ) {
        return new StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak);
    }

    private static StudyStatsStore.LadderItemEvidence item(
            String state,
            Records.LadderRung rung,
            Records.SchedulerPhase phase,
            int realPassStreak,
            int realAgainStreak,
            int matureIntervalDays
    ) {
        return new StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak, matureIntervalDays);
    }
}
