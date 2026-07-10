package dev.bee.kanjianki

import android.os.SystemClock

/**
 * Correlates a review gesture/button with the first rendered frame of its next
 * card. Only opaque token hashes are retained or logged; card/answer content is
 * never emitted. The enqueue log is gated by [AppDebugLog], like the rest of the
 * user-enabled performance trace.
 */
internal object StudyCardFrameDiagnostics {
    private val tracker = StudyCardFrameTracker(
        nowNanos = ::studyCardElapsedRealtimeNanos,
        log = AppDebugLog::log,
    )

    fun onReviewEnqueued(
        token: String,
        source: String,
        rating: String,
        submittedAtNanos: Long,
    ) {
        if (AppDebugLog.isCapturing()) {
            tracker.onReviewEnqueued(token, source, rating, submittedAtNanos)
        }
    }

    fun onFrameScheduled(token: String) {
        // Always advance/expire an existing marker, even if capture was toggled
        // off after enqueue. AppDebugLog itself remains a cheap no-op while off.
        tracker.onFrameScheduled(token)
    }

    fun onTransitionStateComplete(token: String) {
        tracker.onTransitionStateComplete(token)
    }

    fun onReviewFailed(token: String, reason: String) {
        tracker.clearReview(token, reason)
    }

    fun clear(reason: String) {
        tracker.clearAll(reason)
    }
}

internal class StudyCardFrameTracker(
    private val nowNanos: () -> Long,
    private val log: (String) -> Unit,
    private val staleAfterNanos: Long = STUDY_CARD_FRAME_STALE_NANOS,
) {
    private var pendingReview: PendingReview? = null

    @Synchronized
    fun onReviewEnqueued(
        token: String,
        source: String,
        rating: String,
        submittedAtNanos: Long,
    ) {
        val pending = PendingReview(
            tokenId = studyCardTokenId(token),
            source = source,
            rating = rating,
            startedAtNanos = submittedAtNanos,
        )
        pendingReview = pending
        log(
            "study-card event=review-enqueued token_id=${pending.tokenId} " +
                "source=${pending.source} rating=${pending.rating}"
        )
    }

    @Synchronized
    fun onFrameScheduled(token: String) {
        val now = nowNanos()
        val nextTokenId = studyCardTokenId(token)
        val pending = currentPending(now)?.takeUnless { it.tokenId == nextTokenId }
        log(frameMessage("frame-scheduled", nextTokenId, pending, now))
    }

    @Synchronized
    fun onTransitionStateComplete(token: String) {
        val now = nowNanos()
        val nextTokenId = studyCardTokenId(token)
        val currentPending = currentPending(now)
        val pending = currentPending?.takeUnless { it.tokenId == nextTokenId }
        log(frameMessage("transition-state-complete", nextTokenId, pending, now))
        // A very fast swipe can land before the outgoing card's 90 ms enter
        // motion has completed. Do not let that old completion consume the
        // marker intended for the genuinely next card.
        if (pending != null || currentPending == null) {
            pendingReview = null
        }
    }

    @Synchronized
    fun clearReview(token: String, reason: String) {
        val tokenId = studyCardTokenId(token)
        if (pendingReview?.tokenId != tokenId) {
            return
        }
        pendingReview = null
        log("study-card event=correlation-cleared token_id=$tokenId reason=$reason")
    }

    @Synchronized
    fun clearAll(reason: String) {
        val pending = pendingReview ?: return
        pendingReview = null
        log("study-card event=correlation-cleared token_id=${pending.tokenId} reason=$reason")
    }

    private fun currentPending(nowNanos: Long): PendingReview? {
        val pending = pendingReview ?: return null
        val ageNanos = nowNanos - pending.startedAtNanos
        if (ageNanos < 0L || ageNanos > staleAfterNanos) {
            pendingReview = null
            return null
        }
        return pending
    }

    private fun frameMessage(
        event: String,
        nextTokenId: String,
        pending: PendingReview?,
        nowNanos: Long,
    ): String {
        if (pending == null) {
            return "study-card event=$event token_id=$nextTokenId origin_token_id=none"
        }
        val elapsedMs = ((nowNanos - pending.startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
        val elapsedName = if (event == "frame-scheduled") {
            "submit_to_frame_schedule_ms"
        } else {
            "submit_to_transition_complete_ms"
        }
        return "study-card event=$event token_id=$nextTokenId " +
            "origin_token_id=${pending.tokenId} source=${pending.source} rating=${pending.rating} " +
            "$elapsedName=$elapsedMs"
    }

    private data class PendingReview(
        val tokenId: String,
        val source: String,
        val rating: String,
        val startedAtNanos: Long,
    )
}

internal fun studyCardTokenId(token: String): String {
    return Integer.toUnsignedString(token.hashCode(), 16)
}

private fun studyCardElapsedRealtimeNanos(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}

private const val STUDY_CARD_FRAME_STALE_NANOS = 5_000_000_000L
