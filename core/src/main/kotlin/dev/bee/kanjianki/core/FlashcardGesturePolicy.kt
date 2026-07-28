package dev.bee.kanjianki.core

import kotlin.math.abs
import kotlin.math.max

object FlashcardGesturePolicy {
    private const val HORIZONTAL_DOMINANCE = 1.25f

    /** Portable setting key controlling whether the left/right swipe-to-grade gesture is active. */
    const val SWIPE_GESTURE_ENABLED_SETTING_KEY: String = "flashcard_swipe_gesture_enabled"

    /** Swipe-to-grade ships on by default; stored as an Int (1 = on, 0 = off). */
    const val SWIPE_GESTURE_ENABLED_DEFAULT: Int = 1

    @JvmStatic
    fun release(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        touchSlop: Int,
        minimumSwipeDistance: Int,
        answerRevealed: Boolean,
        swipeEnabled: Boolean = true,
    ): Decision {
        val dx = endX - startX
        val dy = endY - startY
        val absX = abs(dx)
        val absY = abs(dy)
        val safeTouchSlop = max(0, touchSlop)
        if (absX <= safeTouchSlop && absY <= safeTouchSlop) {
            return if (answerRevealed) Decision.none() else Decision.reveal()
        }
        val swipeThreshold = max(
            max(0, minimumSwipeDistance),
            saturatingMultiplyNonNegative(safeTouchSlop, 6),
        )
        if (swipeEnabled && absX >= swipeThreshold && absX > absY * HORIZONTAL_DOMINANCE && answerRevealed) {
            return Decision.review(if (dx > 0) StudyRatings.GOOD else StudyRatings.AGAIN)
        }
        return Decision.none()
    }

    class Decision private constructor(
        @JvmField val action: Action,
        @JvmField val rating: String,
    ) {
        enum class Action {
            NONE,
            REVEAL,
            REVIEW,
        }

        companion object {
            @JvmStatic
            fun none(): Decision = Decision(Action.NONE, "")

            @JvmStatic
            fun reveal(): Decision = Decision(Action.REVEAL, "")

            @JvmStatic
            fun review(rating: String): Decision = Decision(Action.REVIEW, rating)
        }
    }
}
