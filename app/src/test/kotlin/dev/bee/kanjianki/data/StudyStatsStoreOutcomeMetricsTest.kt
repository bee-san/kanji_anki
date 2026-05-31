package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays
import java.util.Collections
import java.util.LinkedHashMap

class StudyStatsStoreOutcomeMetricsTest {
    @Test
    fun outcomeStatsUseOnlyKanjiWithReviewsAndBothSnapshots() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            Arrays.asList(
                outcome("弱", snapshot(90, 0), snapshot(45, 2)),
                outcome("支", snapshot(70, 1), snapshot(64, 3)),
                outcome("前", null, snapshot(30, 1)),
                outcome("後", snapshot(60, 0), null),
                outcome("浅", snapshot(60, 0), snapshot(57, 1)),
                outcome("悪", snapshot(30, 2), snapshot(50, 1)),
            ),
            Collections.emptyList(),
            3,
        )

        assertEquals(2, stats.weakKanjiImproved.improvedCount)
        assertEquals(0.80, stats.weakKanjiImproved.averageBeforeWeakness, 0.001)
        assertEquals(0.545, stats.weakKanjiImproved.averageAfterWeakness, 0.001)
        assertEquals("弱", stats.weakKanjiImproved.examples[0].kanji)
        assertEquals("支", stats.weakKanjiImproved.examples[1].kanji)

        assertEquals(3, stats.matureSupportGained.gainedSupportCount)
        assertEquals(5, stats.matureSupportGained.matureSupportGained)
        assertEquals(2, stats.matureSupportGained.firstSupportCount)
        assertEquals("弱", stats.matureSupportGained.examples[0].kanji)
        assertEquals("支", stats.matureSupportGained.examples[1].kanji)
        assertEquals("浅", stats.matureSupportGained.examples[2].kanji)
    }

    @Test
    fun outcomeExamplesAreRankedByImpactThenKanjiAndLimitedToThree() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            Arrays.asList(
                outcome("丁", snapshot(80, 1), snapshot(30, 3)),
                outcome("一", snapshot(80, 0), snapshot(30, 2)),
                outcome("七", snapshot(90, 0), snapshot(60, 1)),
                outcome("二", snapshot(60, 0), snapshot(50, 1)),
                outcome("万", snapshot(70, 5), snapshot(66, 4)),
            ),
            Collections.emptyList(),
            3,
        )

        assertEquals(4, stats.weakKanjiImproved.improvedCount)
        assertEquals(3, stats.weakKanjiImproved.examples.size)
        assertEquals("一", stats.weakKanjiImproved.examples[0].kanji)
        assertEquals("丁", stats.weakKanjiImproved.examples[1].kanji)
        assertEquals("七", stats.weakKanjiImproved.examples[2].kanji)

        assertEquals(4, stats.matureSupportGained.gainedSupportCount)
        assertEquals(6, stats.matureSupportGained.matureSupportGained)
        assertEquals(3, stats.matureSupportGained.examples.size)
        assertEquals("一", stats.matureSupportGained.examples[0].kanji)
        assertEquals("丁", stats.matureSupportGained.examples[1].kanji)
        assertEquals("七", stats.matureSupportGained.examples[2].kanji)
    }

    @Test
    fun ladderHealthUsesConfiguredFsrsAndFailThresholdsOnlyInReviewPhase() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            emptyList(),
            Arrays.asList(
                item("review", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 2, 0),
                item("review", RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.SchedulerPhase.REVIEW, 2, 0, 22),
                item("review", RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 1),
                item("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 1, 0, 21),
                item("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.NEW_LEARNING, 8, 8, 40),
                item("review", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.RELEARNING, 8, 8, 40),
                item("retired", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 9, 9, 40),
            ),
            21,
            2,
        )

        assertEquals(6, stats.ladderHealth.totalActiveItems)
        assertEquals(21, stats.ladderHealth.ladderPromotionIntervalDays)
        assertEquals(2, stats.ladderHealth.ladderDemotionFailStreak)
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.WRITE_KANJI))
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.TYPE_MEANING))
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.FONT_MEANING))
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.WORD_READING))
        assertEquals(1, stats.ladderHealth.promotionReadyCount)
        assertEquals(2, stats.ladderHealth.demotionRiskCount)
        assertEquals(1, stats.ladderHealth.demotionReadyCount)
    }

    @Test
    fun ladderHealthFallsBackToOneWhenThresholdSettingsAreInvalid() {
        val stats = StudyStatsStore.calculateKaniOutcomeStats(
            null,
            Arrays.asList(
                null,
                item(null, null, null, -4, -8),
                item("review", RecordsBase.LadderRung.TYPE_MEANING, RecordsBase.SchedulerPhase.REVIEW, 1, 1, 2),
            ),
            0,
            0,
        )

        assertEquals(1, stats.ladderHealth.ladderPromotionIntervalDays)
        assertEquals(1, stats.ladderHealth.ladderDemotionFailStreak)
        assertEquals(2, stats.ladderHealth.totalActiveItems)
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(1, stats.ladderHealth.countFor(RecordsBase.LadderRung.TYPE_MEANING))
        assertEquals(1, stats.ladderHealth.promotionReadyCount)
        assertEquals(1, stats.ladderHealth.demotionRiskCount)
        assertEquals(1, stats.ladderHealth.demotionReadyCount)
    }

    @Test
    fun publicMetricValuesClampAndDefaultNullInputs() {
        val taskStats = StudyStatsStore.StudyTaskTimeStats(-5L, -6L, -7)
        assertEquals(0L, taskStats.todayMillis)
        assertEquals(0L, taskStats.lastSevenDaysMillis)
        assertEquals(0, taskStats.answeredTasks)
        assertEquals(0L, taskStats.averageMillisPerTask())

        val empty = StudyStatsStore.KaniOutcomeStats.empty()
        assertEquals(0, empty.weakKanjiImproved.improvedCount)
        assertEquals(0, empty.matureSupportGained.gainedSupportCount)
        assertEquals(0, empty.ladderHealth.totalActiveItems)

        val nullBacked = StudyStatsStore.KaniOutcomeStats(null, null, null)
        assertEquals(0, nullBacked.weakKanjiImproved.examples.size)
        assertEquals(0, nullBacked.matureSupportGained.examples.size)
        assertEquals(RecordsSyncModels.Settings.kikuDefaults().realDueReviewsToMove, nullBacked.ladderHealth.realDueReviewsToMove)

        val weak = StudyStatsStore.WeakKanjiImprovedMetric(-1, -2.0, -3.0, null)
        assertEquals(0, weak.improvedCount)
        assertEquals(0.0, weak.averageBeforeWeakness, 0.0)
        assertEquals(0.0, weak.averageAfterWeakness, 0.0)
        assertTrue(weak.examples.isEmpty())

        val support = StudyStatsStore.MatureSupportGainedMetric(-1, -2, -3, null)
        assertEquals(0, support.gainedSupportCount)
        assertEquals(0, support.matureSupportGained)
        assertEquals(0, support.firstSupportCount)
        assertTrue(support.examples.isEmpty())

        val legacySupport = StudyStatsStore.MatureSupportGainedMetric(2, 1, Collections.emptyList())
        assertEquals(2, legacySupport.gainedSupportCount)
        assertEquals(2, legacySupport.matureSupportGained)
        assertEquals(1, legacySupport.firstSupportCount)

        val legacyOutcome = StudyStatsStore.KaniOutcomeStats(weak, legacySupport)
        assertEquals(2, legacyOutcome.matureSupportGained.matureSupportGained)
        assertEquals(RecordsSyncModels.Settings.kikuDefaults().realDueReviewsToMove, legacyOutcome.ladderHealth.realDueReviewsToMove)
    }

    @Test
    fun exampleAndLadderMetricValuesNormalizeUnsafeInput() {
        val improvement = StudyStatsStore.KanjiImprovement(null, -0.2, -0.1)
        assertEquals("", improvement.kanji)
        assertEquals(0.0, improvement.beforeWeakness, 0.0)
        assertEquals(0.0, improvement.afterWeakness, 0.0)

        val gain = StudyStatsStore.KanjiSupportGain(null, -1, -2)
        assertEquals("", gain.kanji)
        assertEquals(0, gain.beforeMatureSupport)
        assertEquals(0, gain.afterMatureSupport)

        val ladder = StudyStatsStore.LadderHealthMetric(
            LinkedHashMap<RecordsBase.LadderRung?, Int?>().apply { put(null, -4) },
            -1,
            -2,
            -3,
            -4,
            -5,
        )
        assertEquals(0, ladder.totalActiveItems)
        assertEquals(1, ladder.realDueReviewsToMove)
        assertEquals(0, ladder.promotionReadyCount)
        assertEquals(0, ladder.demotionRiskCount)
        assertEquals(0, ladder.demotionReadyCount)
        assertEquals(0, ladder.countFor(RecordsBase.LadderRung.WRITE_KANJI))

        val mistake = StudyStatsStore.RecentMistake(null, null, 123L)
        assertEquals("", mistake.kanji)
        assertEquals("", mistake.rating)
        assertEquals(123L, mistake.reviewedAtMillis)
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
        state: String?,
        rung: RecordsBase.LadderRung?,
        phase: RecordsBase.SchedulerPhase?,
        realPassStreak: Int,
        realAgainStreak: Int,
    ): StudyStatsStore.LadderItemEvidence {
        return StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak)
    }

    private fun item(
        state: String?,
        rung: RecordsBase.LadderRung?,
        phase: RecordsBase.SchedulerPhase?,
        realPassStreak: Int,
        realAgainStreak: Int,
        matureIntervalDays: Int,
    ): StudyStatsStore.LadderItemEvidence {
        return StudyStatsStore.LadderItemEvidence(state, rung, phase, realPassStreak, realAgainStreak, matureIntervalDays)
    }
}
