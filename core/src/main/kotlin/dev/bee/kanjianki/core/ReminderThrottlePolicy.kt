package dev.bee.kanjianki.core

/**
 * Spacing brake for study reminders. The per-day cap
 * ([ReminderReviewBatchPolicy]) is a fuse, not a spacer: without this policy an
 * already-overdue set fires, the receiver re-arms, the set is still overdue, and
 * the second alarm fires seconds later — burning the whole daily budget at one
 * instant (D1) and nagging the user right after they left (D5).
 *
 * This policy denies a post when:
 *  - it falls within [Request.minGapMillis] of the last post (spacing);
 *  - the notified due-set signature is unchanged since the last post (the same
 *    overdue set — never re-notify for it);
 *  - it falls within [Request.activityGraceMillis] of the last recorded review
 *    (the user is or was just here).
 *
 * A denied post reports [Decision.nextEligibleAtMillis] so the caller re-arms
 * for the next eligible time instead of `now`, removing the immediate-fire loop
 * structurally rather than relying on the cap. When the only reason is an
 * unchanged signature there is no time-based next attempt
 * ([Decision.nextEligibleAtMillis] is 0); the caller falls back to the daily
 * reminder time and a signature change (new cards due, or the user studied)
 * re-enables posting.
 *
 * [Request.dailyTimeOverride] lets the user's configured daily reminder time
 * post once regardless of gap/grace/signature (quiet hours are enforced
 * separately by [DailyReminderDecisionPolicy]).
 */
object ReminderThrottlePolicy {
    const val DEFAULT_MIN_GAP_MILLIS: Long = 90L * 60L * 1000L
    const val DEFAULT_ACTIVITY_GRACE_MILLIS: Long = 45L * 60L * 1000L

    const val REASON_ALLOW: String = "reminder:throttle-allow"
    const val REASON_DAILY_OVERRIDE: String = "reminder:daily-time-override"
    const val REASON_MIN_GAP: String = "reminder:min-gap"
    const val REASON_ACTIVITY_GRACE: String = "reminder:activity-grace"
    const val REASON_SIGNATURE_UNCHANGED: String = "reminder:signature-unchanged"

    data class Request(
        val nowMillis: Long,
        val lastPostedAtMillis: Long,
        val lastPostedSignature: String,
        val currentSignature: String,
        val lastReviewAtMillis: Long,
        val minGapMillis: Long = DEFAULT_MIN_GAP_MILLIS,
        val activityGraceMillis: Long = DEFAULT_ACTIVITY_GRACE_MILLIS,
        val dailyTimeOverride: Boolean = false,
    )

    data class Decision(
        val allow: Boolean,
        /**
         * Earliest time a post could next be allowed, for re-arming a suppressed
         * fire. 0 means no time-based re-arm applies (unchanged signature): wait
         * for the daily time or a signature change.
         */
        val nextEligibleAtMillis: Long,
        val reasonId: String,
    )

    private const val SIGNATURE_BUCKET_MILLIS: Long = 60L * 60L * 1000L

    /**
     * A stable fingerprint of a notified due-set: its size plus the latest
     * due-at bucketed to the hour. An identical overdue set produces the same
     * signature (suppressing a repeat post), while a new card coming due — which
     * changes either the count or the latest due-at bucket — produces a new
     * signature that re-enables posting. Empty sets get an empty signature,
     * which never counts as "unchanged".
     */
    @JvmStatic
    fun signatureFor(dueCount: Int, latestDueAtMillis: Long): String {
        if (dueCount <= 0) {
            return ""
        }
        val bucket = if (latestDueAtMillis > 0L) latestDueAtMillis / SIGNATURE_BUCKET_MILLIS else 0L
        return "$dueCount:$bucket"
    }

    @JvmStatic
    fun decide(request: Request): Decision {
        val now = request.nowMillis
        if (request.dailyTimeOverride) {
            return Decision(true, now, REASON_DAILY_OVERRIDE)
        }

        val gapMillis = request.minGapMillis.coerceAtLeast(0L)
        val graceMillis = request.activityGraceMillis.coerceAtLeast(0L)
        val gapEnd = if (request.lastPostedAtMillis > 0L) request.lastPostedAtMillis + gapMillis else 0L
        val graceEnd = if (request.lastReviewAtMillis > 0L) request.lastReviewAtMillis + graceMillis else 0L
        val withinGap = now < gapEnd
        val withinGrace = now < graceEnd

        if (withinGap || withinGrace) {
            val nextEligible = maxOf(if (withinGap) gapEnd else 0L, if (withinGrace) graceEnd else 0L)
            // Report the binding (later) constraint as the reason.
            val reason = if (graceEnd >= gapEnd && withinGrace) REASON_ACTIVITY_GRACE else REASON_MIN_GAP
            return Decision(false, nextEligible, reason)
        }

        if (signatureUnchanged(request)) {
            return Decision(false, 0L, REASON_SIGNATURE_UNCHANGED)
        }

        return Decision(true, now, REASON_ALLOW)
    }

    private fun signatureUnchanged(request: Request): Boolean {
        return request.lastPostedAtMillis > 0L &&
            request.currentSignature.isNotEmpty() &&
            request.currentSignature == request.lastPostedSignature
    }
}
