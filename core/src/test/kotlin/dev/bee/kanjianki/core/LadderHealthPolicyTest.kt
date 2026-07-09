package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LadderHealthPolicyTest {
    @Test
    fun summarizeCountsActiveRungsAndReviewReadiness() {
        val metric = LadderHealthPolicy.summarize(
            listOf(
                evidence("review", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 22, 0),
                evidence("review", RecordsBase.LadderRung.FONT_MEANING, RecordsBase.SchedulerPhase.REVIEW, 3, 2),
                evidence("learning", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.NEW_LEARNING, 90, 4),
                evidence("retired", RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 99, 9),
                null
            ),
            21,
            3
        )

        assertEquals(3, metric.totalActiveItems())
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.FONT_MEANING))
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.WRITE_KANJI))
        assertEquals(0, metric.countFor(RecordsBase.LadderRung.WORD_READING))
        assertEquals(1, metric.promotionReadyCount())
        assertEquals(1, metric.demotionRiskCount())
        assertEquals(0, metric.demotionReadyCount())
    }

    @Test
    fun summarizeNormalizesNullsAndThresholds() {
        val normalized = LadderHealthPolicy.ItemEvidence(null, null, null, -1, -2, -3)
        val legacyCtor = LadderHealthPolicy.ItemEvidence(null, null, null, -1, -2)
        val metric = LadderHealthPolicy.summarize(
            listOf(normalized),
            0,
            0
        )
        val rungCounts = metric.rungCounts() as MutableMap<RecordsBase.LadderRung, Int>

        assertEquals(LadderHealthPolicy.ItemEvidence("", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.NEW_LEARNING, 0, 0, 0), normalized)
        assertEquals(0, legacyCtor.matureIntervalDays())
        assertEquals("ItemEvidence[state=, rung=KANJI_MEANING, phase=NEW_LEARNING, realPassStreak=0, realAgainStreak=0, matureIntervalDays=0, hasSimilarKanji=false]", normalized.toString())
        assertEquals(1, metric.totalActiveItems())
        assertEquals(1, metric.ladderPromotionIntervalDays())
        assertEquals(1, metric.ladderDemotionFailStreak())
        assertEquals(1, metric.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(0, metric.promotionReadyCount())
        assertEquals(0, metric.demotionRiskCount())
        assertEquals(0, metric.demotionReadyCount())
        assertThrows(UnsupportedOperationException::class.java) {
            rungCounts[RecordsBase.LadderRung.KANJI_MEANING] = 2
        }
    }

    @Test
    fun fromCountsClampsNegativeValuesAndFillsMissingRungs() {
        val metric = LadderHealthPolicy.fromCounts(
            mapOf(RecordsBase.LadderRung.TYPE_MEANING to -4),
            -10,
            -1,
            -2,
            -3,
            -4,
            -5
        )

        assertEquals(0, metric.totalActiveItems())
        assertEquals(1, metric.ladderPromotionIntervalDays())
        assertEquals(1, metric.ladderDemotionFailStreak())
        assertEquals(0, metric.countFor(RecordsBase.LadderRung.TYPE_MEANING))
        assertEquals(0, metric.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(0, metric.promotionReadyCount())
        assertEquals(0, metric.demotionRiskCount())
        assertEquals(0, metric.demotionReadyCount())
    }

    private fun evidence(
        state: String,
        rung: RecordsBase.LadderRung,
        phase: RecordsBase.SchedulerPhase,
        matureIntervalDays: Int,
        realAgainStreak: Int
    ): LadderHealthPolicy.ItemEvidence {
        return LadderHealthPolicy.ItemEvidence(state, rung, phase, 0, realAgainStreak, matureIntervalDays)
    }
}
