package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderThrottlePolicyTest {
    @Test
    fun firstPostIsAllowedWithNoHistory() {
        val decision = ReminderThrottlePolicy.decide(
            request(now = 10 * HOUR, currentSignature = "3:5"),
        )

        assertTrue(decision.allow)
        assertEquals(10 * HOUR, decision.nextEligibleAtMillis)
        assertEquals(ReminderThrottlePolicy.REASON_ALLOW, decision.reasonId)
    }

    @Test
    fun d1TimelinePostsOnceThenSchedulesNextAttemptAfterMinGap() {
        val postAt = 10 * HOUR
        val signature = ReminderThrottlePolicy.signatureFor(5, postAt)

        // First fire: allowed.
        val first = ReminderThrottlePolicy.decide(
            request(now = postAt, currentSignature = signature),
        )
        assertTrue(first.allow)

        // Receiver re-arms seconds later; same overdue set, same signature, within
        // the min gap. Must be denied and report an eligible time >= now + gap.
        val secondsLater = postAt + 5_000L
        val second = ReminderThrottlePolicy.decide(
            request(
                now = secondsLater,
                lastPostedAt = postAt,
                lastPostedSignature = signature,
                currentSignature = signature,
            ),
        )
        assertFalse(second.allow)
        assertEquals(postAt + ReminderThrottlePolicy.DEFAULT_MIN_GAP_MILLIS, second.nextEligibleAtMillis)
        assertTrue(second.nextEligibleAtMillis >= secondsLater + ReminderThrottlePolicy.DEFAULT_MIN_GAP_MILLIS - 5_000L)
    }

    @Test
    fun unchangedSignatureAfterGapStillDeniedWithNoTimeBasedReArm() {
        val postAt = 10 * HOUR
        val signature = ReminderThrottlePolicy.signatureFor(5, postAt)
        val wellAfterGap = postAt + 3 * HOUR

        val decision = ReminderThrottlePolicy.decide(
            request(
                now = wellAfterGap,
                lastPostedAt = postAt,
                lastPostedSignature = signature,
                currentSignature = signature,
                lastReviewAt = postAt, // grace long past
            ),
        )

        assertFalse(decision.allow)
        assertEquals(0L, decision.nextEligibleAtMillis)
        assertEquals(ReminderThrottlePolicy.REASON_SIGNATURE_UNCHANGED, decision.reasonId)
    }

    @Test
    fun changedSignatureAfterGapReEnablesPost() {
        val postAt = 10 * HOUR
        val oldSignature = ReminderThrottlePolicy.signatureFor(5, postAt)
        val newSignature = ReminderThrottlePolicy.signatureFor(8, postAt + 4 * HOUR)
        val wellAfterGap = postAt + 3 * HOUR

        val decision = ReminderThrottlePolicy.decide(
            request(
                now = wellAfterGap,
                lastPostedAt = postAt,
                lastPostedSignature = oldSignature,
                currentSignature = newSignature,
                lastReviewAt = postAt,
            ),
        )

        assertTrue(decision.allow)
        assertEquals(ReminderThrottlePolicy.REASON_ALLOW, decision.reasonId)
    }

    @Test
    fun activityGraceDeniesRightAfterAReview() {
        val reviewAt = 10 * HOUR
        val justAfter = reviewAt + 10 * MINUTE // within the 45m grace

        val decision = ReminderThrottlePolicy.decide(
            request(
                now = justAfter,
                currentSignature = "3:5",
                lastReviewAt = reviewAt,
            ),
        )

        assertFalse(decision.allow)
        assertEquals(reviewAt + ReminderThrottlePolicy.DEFAULT_ACTIVITY_GRACE_MILLIS, decision.nextEligibleAtMillis)
        assertEquals(ReminderThrottlePolicy.REASON_ACTIVITY_GRACE, decision.reasonId)
    }

    @Test
    fun dailyTimeOverrideBypassesGapGraceAndSignature() {
        val postAt = 10 * HOUR
        val signature = ReminderThrottlePolicy.signatureFor(1, postAt)

        val decision = ReminderThrottlePolicy.decide(
            request(
                now = postAt + MINUTE,
                lastPostedAt = postAt,
                lastPostedSignature = signature,
                currentSignature = signature,
                lastReviewAt = postAt,
                dailyTimeOverride = true,
            ),
        )

        assertTrue(decision.allow)
        assertEquals(ReminderThrottlePolicy.REASON_DAILY_OVERRIDE, decision.reasonId)
    }

    @Test
    fun signatureIsEmptyForEmptySetAndNeverCountsAsUnchanged() {
        assertEquals("", ReminderThrottlePolicy.signatureFor(0, 10 * HOUR))

        val decision = ReminderThrottlePolicy.decide(
            request(
                now = 10 * HOUR + 3 * HOUR,
                lastPostedAt = 10 * HOUR,
                lastPostedSignature = "",
                currentSignature = "",
                lastReviewAt = 0L,
            ),
        )
        // Empty current signature is not "unchanged"; nothing else blocks it.
        assertTrue(decision.allow)
    }

    @Test
    fun signatureChangesWithCountOrLatestDueBucket() {
        val base = ReminderThrottlePolicy.signatureFor(3, 10 * HOUR)
        assertEquals(base, ReminderThrottlePolicy.signatureFor(3, 10 * HOUR + 59 * MINUTE))
        assertFalse(base == ReminderThrottlePolicy.signatureFor(4, 10 * HOUR))
        assertFalse(base == ReminderThrottlePolicy.signatureFor(3, 12 * HOUR))
    }

    @Test
    fun gapAndGraceDeadlinesSaturateInsteadOfWrapping() {
        val decision = ReminderThrottlePolicy.decide(
            ReminderThrottlePolicy.Request(
                nowMillis = Long.MAX_VALUE - 1L,
                lastPostedAtMillis = Long.MAX_VALUE - 2L,
                lastPostedSignature = "",
                currentSignature = "new",
                lastReviewAtMillis = Long.MAX_VALUE - 3L,
                minGapMillis = 10L,
                activityGraceMillis = 20L,
            ),
        )

        assertFalse(decision.allow)
        assertEquals(Long.MAX_VALUE, decision.nextEligibleAtMillis)
        assertEquals(ReminderThrottlePolicy.REASON_ACTIVITY_GRACE, decision.reasonId)
    }

    private fun request(
        now: Long,
        lastPostedAt: Long = 0L,
        lastPostedSignature: String = "",
        currentSignature: String = "",
        lastReviewAt: Long = 0L,
        dailyTimeOverride: Boolean = false,
    ): ReminderThrottlePolicy.Request {
        return ReminderThrottlePolicy.Request(
            nowMillis = now,
            lastPostedAtMillis = lastPostedAt,
            lastPostedSignature = lastPostedSignature,
            currentSignature = currentSignature,
            lastReviewAtMillis = lastReviewAt,
            dailyTimeOverride = dailyTimeOverride,
        )
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val MINUTE = 60L * 1000L
    }
}
