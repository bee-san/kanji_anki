package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsBase
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyStatsStoreTest {
    @Test
    fun kaniOutcomeStatsEmptyStateHasNoCounts() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            emptyList<StudyStatsStore.OutcomeEvidence?>(),
            emptyList<StudyStatsStore.LadderItemEvidence?>(),
            3,
        )

        assertEquals(0, stats.weakKanjiImproved.improvedCount)
        assertEquals(0.0, stats.weakKanjiImproved.averageBeforeWeakness, 0.001)
        assertEquals(0.0, stats.weakKanjiImproved.averageAfterWeakness, 0.001)
        assertEquals(0, stats.matureSupportGained.gainedSupportCount)
        assertEquals(0, stats.matureSupportGained.matureSupportGained)
        assertEquals(0, stats.matureSupportGained.firstSupportCount)
        assertEquals(0, stats.ladderHealth.totalActiveItems)
        for (rung in RecordsBase.LadderRung.values()) {
            assertEquals(0, stats.ladderHealth.countFor(rung))
        }
    }

    @Test
    fun weaknessDropsNeedBeforeAndAfterSnapshotsAroundKaniReviews() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            listOf(
                outcome("痛", snapshot(82, 1), snapshot(46, 1)),
                outcome("薬", snapshot(76, 0), null),
                outcome("疲", null, snapshot(44, 1)),
                outcome("平", snapshot(74, 1), snapshot(72, 1)),
            ),
            emptyList<StudyStatsStore.LadderItemEvidence?>(),
            3,
        )

        assertEquals(1, stats.weakKanjiImproved.improvedCount)
        assertEquals(0.82, stats.weakKanjiImproved.averageBeforeWeakness, 0.001)
        assertEquals(0.46, stats.weakKanjiImproved.averageAfterWeakness, 0.001)
        assertEquals(1, stats.weakKanjiImproved.examples.size)
        assertEquals("痛", stats.weakKanjiImproved.examples[0].kanji)
    }

    @Test
    fun matureSupportGainsTrackTotalCardsFirstSupportAndTopExamples() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            listOf(
                outcome("痛", snapshot(82, 1), snapshot(46, 3)),
                outcome("薬", snapshot(76, 0), snapshot(51, 2)),
                outcome("疲", snapshot(69, 0), snapshot(44, 1)),
                outcome("平", snapshot(74, 2), snapshot(50, 2)),
            ),
            emptyList<StudyStatsStore.LadderItemEvidence?>(),
            3,
        )

        assertEquals(3, stats.matureSupportGained.gainedSupportCount)
        assertEquals(5, stats.matureSupportGained.matureSupportGained)
        assertEquals(2, stats.matureSupportGained.firstSupportCount)
        assertEquals(3, stats.matureSupportGained.examples.size)
        assertEquals("痛", stats.matureSupportGained.examples[0].kanji)
        assertEquals(1, stats.matureSupportGained.examples[0].beforeMatureSupport)
        assertEquals(3, stats.matureSupportGained.examples[0].afterMatureSupport)
    }

    @Test
    fun ladderHealthCountsActiveItemsOnEveryRung() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            emptyList<StudyStatsStore.OutcomeEvidence?>(),
            listOf(
                item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.MEANING_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.KANJI_READING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.READING_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("review", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 0, 0),
                item("retired", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 3, 3),
            ),
            3,
        )

        assertEquals(9, stats.ladderHealth.totalActiveItems)
        for (rung in RecordsBase.LadderRung.values()) {
            assertEquals(1, stats.ladderHealth.countFor(rung))
        }
    }

    @Test
    fun ladderReadinessUsesReviewPhaseFsrsIntervalsAndFailThreshold() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            emptyList<StudyStatsStore.OutcomeEvidence?>(),
            listOf(
                item("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 9, 0, 22),
                item("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.REVIEW, 9, 0, 21),
                item("review", RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 1),
                item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 3),
                item("review", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.NEW_LEARNING, 5, 5, 40),
                item("review", RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.SchedulerPhase.RELEARNING, 0, 5, 40),
            ),
            21,
            3,
        )

        assertEquals(21, stats.ladderHealth.ladderPromotionIntervalDays)
        assertEquals(3, stats.ladderHealth.ladderDemotionFailStreak)
        assertEquals(1, stats.ladderHealth.promotionReadyCount)
        assertEquals(2, stats.ladderHealth.demotionRiskCount)
        assertEquals(1, stats.ladderHealth.demotionReadyCount)
    }

    @Test
    fun stuckCountCountsFloorCardsAtDoubleTheFailThreshold() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            emptyList<StudyStatsStore.OutcomeEvidence?>(),
            listOf(
                // Floor (write_kanji) card failing at 2x the threshold -> stuck.
                item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 6),
                // Floor card just below the 2x threshold -> not stuck.
                item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 5),
                // Non-floor card with a huge streak -> not stuck (can still demote).
                item("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 0, 20),
            ),
            21,
            3,
        )

        assertEquals(1, stats.ladderHealth.stuckCount)
    }

    private fun outcome(
        kanji: String,
        before: StudyStatsStore.OutcomeSnapshot?,
        after: StudyStatsStore.OutcomeSnapshot?,
    ): StudyStatsStore.OutcomeEvidence {
        return StudyStatsStore.OutcomeEvidence(kanji, before, after)
    }

    private fun snapshot(weaknessScore: Int, matureSupportCount: Int): StudyStatsStore.OutcomeSnapshot {
        return StudyStatsStore.OutcomeSnapshot(weaknessScore, matureSupportCount)
    }

    private fun item(
        state: String,
        rung: RecordsBase.LadderRung,
        phase: RecordsBase.SchedulerPhase,
        realPassStreak: Int,
        realAgainStreak: Int,
    ): StudyStatsStore.LadderItemEvidence {
        return StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak)
    }

    private fun item(
        state: String,
        rung: RecordsBase.LadderRung,
        phase: RecordsBase.SchedulerPhase,
        realPassStreak: Int,
        realAgainStreak: Int,
        matureIntervalDays: Int,
    ): StudyStatsStore.LadderItemEvidence {
        return StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak, matureIntervalDays)
    }
}
