package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashcardGesturePolicyTest {
    @Test
    fun tapRevealsOnlyWhenAnswerIsHidden() {
        assertDecision(
            FlashcardGesturePolicy.release(10f, 10f, 13f, 14f, 8, 72, false),
            FlashcardGesturePolicy.Decision.Action.REVEAL,
            "",
        )
        assertDecision(
            FlashcardGesturePolicy.release(10f, 10f, 13f, 14f, 8, 72, true),
            FlashcardGesturePolicy.Decision.Action.NONE,
            "",
        )
    }

    @Test
    fun horizontalSwipeReviewsOnlyAfterReveal() {
        assertDecision(
            FlashcardGesturePolicy.release(0f, 0f, 90f, 10f, 8, 72, false),
            FlashcardGesturePolicy.Decision.Action.NONE,
            "",
        )
        assertDecision(
            FlashcardGesturePolicy.release(0f, 0f, 90f, 10f, 8, 72, true),
            FlashcardGesturePolicy.Decision.Action.REVIEW,
            StudyRatings.GOOD,
        )
        assertDecision(
            FlashcardGesturePolicy.release(90f, 0f, 0f, 10f, 8, 72, true),
            FlashcardGesturePolicy.Decision.Action.REVIEW,
            StudyRatings.AGAIN,
        )
    }

    @Test
    fun ignoresShortOrVerticalDominantSwipes() {
        assertDecision(
            FlashcardGesturePolicy.release(0f, 0f, 71f, 0f, 8, 72, true),
            FlashcardGesturePolicy.Decision.Action.NONE,
            "",
        )
        assertDecision(
            FlashcardGesturePolicy.release(0f, 0f, 90f, 80f, 8, 72, true),
            FlashcardGesturePolicy.Decision.Action.NONE,
            "",
        )
    }

    @Test
    fun thresholdUsesTouchSlopFloor() {
        assertDecision(
            FlashcardGesturePolicy.release(0f, 0f, 100f, 0f, 20, 72, true),
            FlashcardGesturePolicy.Decision.Action.NONE,
            "",
        )
        assertDecision(
            FlashcardGesturePolicy.release(0f, 0f, 120f, 0f, 20, 72, true),
            FlashcardGesturePolicy.Decision.Action.REVIEW,
            StudyRatings.GOOD,
        )
    }

    private fun assertDecision(
        decision: FlashcardGesturePolicy.Decision,
        action: FlashcardGesturePolicy.Decision.Action,
        rating: String,
    ) {
        assertEquals(action, decision.action)
        assertEquals(rating, decision.rating)
    }
}
