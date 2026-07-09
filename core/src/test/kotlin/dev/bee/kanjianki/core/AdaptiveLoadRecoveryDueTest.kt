package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PS2: a mid-learning card is "recovery-due" (and therefore counts toward the
 * home "to study" totals) only once its step delay has elapsed. Immediately
 * after a session, a card just answered `Again` sits in learning due a few
 * minutes out and must read 0 until then.
 */
class AdaptiveLoadRecoveryDueTest {

    private val now = 1_000_000L

    private fun item(
        state: String,
        dueAt: Long,
        totalReviews: Int,
    ): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem("字", state, dueAt, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)

    @Test
    fun learningItemDueInFutureIsNotRecoveryDue() {
        val future = item(StudyLadderRules.STATE_LEARNING, now + 60_000L, 1)
        assertFalse(AdaptiveLoadCandidate.isRecoveryDue(future, now))
    }

    @Test
    fun learningItemDueNowIsRecoveryDue() {
        val dueNow = item(StudyLadderRules.STATE_LEARNING, now, 1)
        assertTrue(AdaptiveLoadCandidate.isRecoveryDue(dueNow, now))

        val justPast = item(StudyLadderRules.STATE_LEARNING, now - 1L, 1)
        assertTrue(AdaptiveLoadCandidate.isRecoveryDue(justPast, now))
    }

    @Test
    fun learningItemWithNoReviewsPastDueIsRecoveryDue() {
        // A card abandoned mid-learning with no persisted review yet must still
        // count once it is past due — the learning clause omits totalReviews.
        val abandoned = item(StudyLadderRules.STATE_LEARNING, now - 60_000L, 0)
        assertTrue(AdaptiveLoadCandidate.isRecoveryDue(abandoned, now))
    }

    @Test
    fun reviewItemNeedsTotalReviewsAndPastDue() {
        val pastDueNoReviews = item(StudyLadderRules.STATE_REVIEW, now - 60_000L, 0)
        assertFalse(AdaptiveLoadCandidate.isRecoveryDue(pastDueNoReviews, now))

        val pastDueReviewed = item(StudyLadderRules.STATE_REVIEW, now - 60_000L, 1)
        assertTrue(AdaptiveLoadCandidate.isRecoveryDue(pastDueReviewed, now))

        val futureReviewed = item(StudyLadderRules.STATE_REVIEW, now + 60_000L, 1)
        assertFalse(AdaptiveLoadCandidate.isRecoveryDue(futureReviewed, now))
    }

    @Test
    fun retiredItemIsNeverRecoveryDue() {
        val retiredPastDue = item(StudyLadderRules.STATE_RETIRED, now - 60_000L, 5)
        assertFalse(AdaptiveLoadCandidate.isRecoveryDue(retiredPastDue, now))
    }

    @Test
    fun nullItemIsNeverRecoveryDue() {
        assertFalse(AdaptiveLoadCandidate.isRecoveryDue(null, now))
    }
}
