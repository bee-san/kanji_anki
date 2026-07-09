package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.HashSet

/**
 * Finding 9: a failed *first* review after a promotion (the capped validation
 * review) demotes immediately, because a failed validation is direct evidence
 * the promotion was premature. A brand-new card's first failed review at the
 * starting rung is deliberately excluded so new cards are never demoted below
 * where they begin.
 */
class LadderFailFastDemotionTest {
    private val scheduler = BridgeScheduler()

    @Test
    fun failedPromotionValidationDemotesImmediately() {
        val promoted = promotedValidationItem(
            "裂",
            rung = RecordsBase.LadderRung.FONT_MEANING,
            matureIntervalDays = 7,
        )
        val result = scheduler.applyReview(
            promoted.withToken("t0"),
            RecordsSchedulerModels.ReviewRequest("裂", "t0", "again", false, false, false, 0),
            HashSet(),
            1000L,
        )
        assertEquals(
            "One failed validation demotes despite the default 3-fail streak",
            RecordsBase.LadderRung.KANJI_MEANING,
            result.item.rung,
        )
        assertEquals("streak resets when the demotion moves the rung", 0, result.item.realAgainStreak)
    }

    @Test
    fun newCardFirstFailAtStartingRungDoesNotDemote() {
        val graduate = promotedValidationItem(
            "裂",
            rung = RecordsBase.LadderRung.KANJI_MEANING,
            matureIntervalDays = 2,
        )
        val result = scheduler.applyReview(
            graduate.withToken("t0"),
            RecordsSchedulerModels.ReviewRequest("裂", "t0", "again", false, false, false, 0),
            HashSet(),
            1000L,
        )
        assertEquals(
            "starting-rung card is never demoted below its start on a single fail",
            RecordsBase.LadderRung.KANJI_MEANING,
            result.item.rung,
        )
        assertEquals(1, result.item.realAgainStreak)
    }

    @Test
    fun failAfterValidatedPromotionUsesNormalStreak() {
        // Interval well past the capped validation window: no longer the first
        // review after a promotion, so a single fail should not demote.
        val settled = promotedValidationItem(
            "裂",
            rung = RecordsBase.LadderRung.FONT_MEANING,
            matureIntervalDays = 40,
        )
        val result = scheduler.applyReview(
            settled.withToken("t0"),
            RecordsSchedulerModels.ReviewRequest("裂", "t0", "again", false, false, false, 0),
            HashSet(),
            1000L,
        )
        assertEquals(
            "settled rung uses the normal fail streak, not fast demotion",
            RecordsBase.LadderRung.FONT_MEANING,
            result.item.rung,
        )
        assertEquals(1, result.item.realAgainStreak)
    }

    private fun promotedValidationItem(
        kanji: String,
        rung: RecordsBase.LadderRung,
        matureIntervalDays: Int,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji, "review", 0L, 20.0, 5.0, 3, 0, 0, 0, 0, 0, 0L, false, null, 0L,
        ).copyBuilder()
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(matureIntervalDays)
            .realPassStreak(0)
            .realAgainStreak(0)
            .build()
    }
}
