package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsBase;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class StudyStatsStoreTest {
    @Test
    public void kaniOutcomeStatsEmptyStateHasNoCounts() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Collections.emptyList(),
                Collections.emptyList(),
                3
        );

        assertEquals(0, stats.weakKanjiImproved.improvedCount);
        assertEquals(0.0, stats.weakKanjiImproved.averageBeforeWeakness, 0.001);
        assertEquals(0.0, stats.weakKanjiImproved.averageAfterWeakness, 0.001);
        assertEquals(0, stats.matureSupportGained.gainedSupportCount);
        assertEquals(0, stats.matureSupportGained.matureSupportGained);
        assertEquals(0, stats.matureSupportGained.firstSupportCount);
        assertEquals(0, stats.ladderHealth.totalActiveItems);
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            assertEquals(0, stats.ladderHealth.countFor(rung));
        }
    }

    @Test
    public void weaknessDropsNeedBeforeAndAfterSnapshotsAroundKaniReviews() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Arrays.asList(
                        outcome("痛", snapshot(82, 1), snapshot(46, 1)),
                        outcome("薬", snapshot(76, 0), null),
                        outcome("疲", null, snapshot(44, 1)),
                        outcome("平", snapshot(74, 1), snapshot(72, 1))
                ),
                Collections.emptyList(),
                3
        );

        assertEquals(1, stats.weakKanjiImproved.improvedCount);
        assertEquals(0.82, stats.weakKanjiImproved.averageBeforeWeakness, 0.001);
        assertEquals(0.46, stats.weakKanjiImproved.averageAfterWeakness, 0.001);
        assertEquals(1, stats.weakKanjiImproved.examples.size());
        assertEquals("痛", stats.weakKanjiImproved.examples.get(0).kanji);
    }

    @Test
    public void matureSupportGainsTrackTotalCardsFirstSupportAndTopExamples() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Arrays.asList(
                        outcome("痛", snapshot(82, 1), snapshot(46, 3)),
                        outcome("薬", snapshot(76, 0), snapshot(51, 2)),
                        outcome("疲", snapshot(69, 0), snapshot(44, 1)),
                        outcome("平", snapshot(74, 2), snapshot(50, 2))
                ),
                Collections.emptyList(),
                3
        );

        assertEquals(3, stats.matureSupportGained.gainedSupportCount);
        assertEquals(5, stats.matureSupportGained.matureSupportGained);
        assertEquals(2, stats.matureSupportGained.firstSupportCount);
        assertEquals(3, stats.matureSupportGained.examples.size());
        assertEquals("痛", stats.matureSupportGained.examples.get(0).kanji);
        assertEquals(1, stats.matureSupportGained.examples.get(0).beforeMatureSupport);
        assertEquals(3, stats.matureSupportGained.examples.get(0).afterMatureSupport);
    }

    @Test
    public void ladderHealthCountsActiveItemsOnEveryRung() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Collections.emptyList(),
                Arrays.asList(
                        item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("review", RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("review", RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("review", RecordsBase.LadderRung.MEANING_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("review", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                        item("retired", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 3, 3)
                ),
                3
        );

        assertEquals(7, stats.ladderHealth.totalActiveItems);
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            assertEquals(1, stats.ladderHealth.countFor(rung));
        }
    }

    @Test
    public void ladderReadinessUsesReviewPhaseFsrsIntervalsAndFailThreshold() {
        StudyStatsStore.KaniOutcomeStats stats = StudyStatsStore.calculateKaniOutcomeStats(
                Collections.emptyList(),
                Arrays.asList(
                        item("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 9, 0, 22),
                        item("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.REVIEW, 9, 0, 21),
                        item("review", RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 1),
                        item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 3),
                        item("review", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.NEW_LEARNING, 5, 5, 40),
                        item("review", RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.SchedulerPhase.RELEARNING, 0, 5, 40)
                ),
                21,
                3
        );

        assertEquals(21, stats.ladderHealth.ladderPromotionIntervalDays);
        assertEquals(3, stats.ladderHealth.ladderDemotionFailStreak);
        assertEquals(1, stats.ladderHealth.promotionReadyCount);
        assertEquals(2, stats.ladderHealth.demotionRiskCount);
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
            RecordsBase.LadderRung rung,
            RecordsBase.SchedulerPhase phase,
            int realPassStreak,
            int realAgainStreak
    ) {
        return new StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak);
    }

    private static StudyStatsStore.LadderItemEvidence item(
            String state,
            RecordsBase.LadderRung rung,
            RecordsBase.SchedulerPhase phase,
            int realPassStreak,
            int realAgainStreak,
            int matureIntervalDays
    ) {
        return new StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak, matureIntervalDays);
    }
}
