package dev.bee.kanjianki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCardFrameDiagnosticsTest {
    @Test
    fun nextCardFramesCorrelateOpaqueTokensWithReviewReleaseTiming() {
        var nowNanos = 1_000_000_000L
        val messages = mutableListOf<String>()
        val tracker = StudyCardFrameTracker(
            nowNanos = { nowNanos },
            log = messages::add,
        )

        tracker.onReviewEnqueued("private-origin-token", "card", "good", nowNanos)
        nowNanos += 12_000_000L
        tracker.onFrameScheduled("private-next-token")
        nowNanos += 30_000_000L
        tracker.onTransitionStateComplete("private-next-token")

        assertTrue(messages[1].contains("event=frame-scheduled"))
        assertTrue(messages[1].contains("submit_to_frame_schedule_ms=12"))
        assertTrue(messages[2].contains("event=transition-state-complete"))
        assertTrue(messages[2].contains("submit_to_transition_complete_ms=42"))
        assertFalse(messages.joinToString().contains("private-origin-token"))
        assertFalse(messages.joinToString().contains("private-next-token"))

        tracker.onFrameScheduled("later-card")
        assertTrue(messages.last().contains("origin_token_id=none"))
    }

    @Test
    fun staleReviewIsNotCorrelatedToAMuchLaterCard() {
        var nowNanos = 0L
        val messages = mutableListOf<String>()
        val tracker = StudyCardFrameTracker(
            nowNanos = { nowNanos },
            log = messages::add,
            staleAfterNanos = 10L,
        )

        tracker.onReviewEnqueued("origin", "button", "again", nowNanos)
        nowNanos = 11L
        tracker.onFrameScheduled("next")

        assertTrue(messages.last().contains("origin_token_id=none"))
    }

    @Test
    fun outgoingCardCompletionDoesNotConsumeFastSwipeCorrelation() {
        var nowNanos = 0L
        val messages = mutableListOf<String>()
        val tracker = StudyCardFrameTracker(
            nowNanos = { nowNanos },
            log = messages::add,
        )

        tracker.onReviewEnqueued("outgoing", "card", "good", nowNanos)
        nowNanos = 2_000_000L
        tracker.onTransitionStateComplete("outgoing")
        nowNanos = 8_000_000L
        tracker.onFrameScheduled("next")

        assertTrue(messages[1].contains("origin_token_id=none"))
        assertTrue(messages[2].contains("origin_token_id=${studyCardTokenId("outgoing")}"))
        assertTrue(messages[2].contains("submit_to_frame_schedule_ms=8"))
    }

    @Test
    fun retryableFailureClearsOnlyItsOwnPendingCorrelation() {
        val messages = mutableListOf<String>()
        val tracker = StudyCardFrameTracker(
            nowNanos = { 1_000L },
            log = messages::add,
        )

        tracker.onReviewEnqueued("origin", "card", "good", 1_000L)
        tracker.clearReview("different", "processing-error")
        tracker.onFrameScheduled("next")
        assertTrue(messages.last().contains("source=card rating=good"))

        tracker.clearReview("origin", "processing-error")
        tracker.onFrameScheduled("later")
        assertTrue(messages.last().contains("origin_token_id=none"))
        assertTrue(messages.any { it.contains("event=correlation-cleared") })
    }

    @Test
    fun suppressedDuplicateCannotOverwriteAcceptedSourceAndRating() {
        val messages = mutableListOf<String>()
        val tracker = StudyCardFrameTracker(
            nowNanos = { 1_000L },
            log = messages::add,
        )

        // The review gate calls diagnostics only for the accepted enqueue. A
        // suppressed action-bar duplicate therefore performs no second mutation.
        tracker.onReviewEnqueued("origin", "card", "good", 1_000L)
        tracker.onFrameScheduled("next")

        assertTrue(messages.last().contains("source=card rating=good"))
        assertFalse(messages.last().contains("source=action-bar"))
    }
}
