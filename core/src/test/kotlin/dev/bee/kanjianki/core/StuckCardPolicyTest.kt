package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StuckCardPolicyTest {
    private val ladder = RecordsBase.StudyLadderSettings.defaults()
    private val failStreak = RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK

    @Test
    fun floorReviewCardWithDoubleThresholdStreakIsStuck() {
        // write_kanji is the demotion floor for a card without similar content.
        assertTrue(
            StuckCardPolicy.isStuck(
                "review",
                RecordsBase.LadderRung.WRITE_KANJI,
                RecordsBase.SchedulerPhase.REVIEW,
                2 * failStreak,
                false,
                ladder,
                failStreak,
            ),
        )
    }

    @Test
    fun floorCardBelowDoubleThresholdIsNotStuck() {
        assertFalse(
            StuckCardPolicy.isStuck(
                "review",
                RecordsBase.LadderRung.WRITE_KANJI,
                RecordsBase.SchedulerPhase.REVIEW,
                2 * failStreak - 1,
                false,
                ladder,
                failStreak,
            ),
        )
    }

    @Test
    fun nonFloorRungIsNotStuck() {
        // kanji_meaning is not the floor, so even a long streak is not "stuck"
        // (it can still demote).
        assertFalse(
            StuckCardPolicy.isStuck(
                "review",
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.SchedulerPhase.REVIEW,
                10 * failStreak,
                false,
                ladder,
                failStreak,
            ),
        )
    }

    @Test
    fun nonReviewPhaseIsNotStuck() {
        assertFalse(
            StuckCardPolicy.isStuck(
                "learning",
                RecordsBase.LadderRung.WRITE_KANJI,
                RecordsBase.SchedulerPhase.RELEARNING,
                10 * failStreak,
                false,
                ladder,
                failStreak,
            ),
        )
    }

    @Test
    fun retiredCardIsNotStuck() {
        assertFalse(
            StuckCardPolicy.isStuck(
                StudyLadderRules.STATE_RETIRED,
                RecordsBase.LadderRung.WRITE_KANJI,
                RecordsBase.SchedulerPhase.REVIEW,
                10 * failStreak,
                false,
                ladder,
                failStreak,
            ),
        )
    }

    @Test
    fun floorIsComputedPerItemSimilarContent() {
        // With similar-kanji content available, write_kanji is still the floor
        // (similar_kanji sits above it), so a floor write_kanji card is stuck
        // regardless of content...
        assertTrue(
            StuckCardPolicy.isStuck(
                "review",
                RecordsBase.LadderRung.WRITE_KANJI,
                RecordsBase.SchedulerPhase.REVIEW,
                2 * failStreak,
                true,
                ladder,
                failStreak,
            ),
        )
        // ...but a card sitting on similar_kanji is only at its floor when it
        // lacks the lower always-available rungs. With content it can still
        // demote to a lower rung, so it is not stuck.
        assertFalse(
            StuckCardPolicy.isStuck(
                "review",
                RecordsBase.LadderRung.SIMILAR_KANJI,
                RecordsBase.SchedulerPhase.REVIEW,
                10 * failStreak,
                true,
                ladder,
                failStreak,
            ),
        )
    }
}
