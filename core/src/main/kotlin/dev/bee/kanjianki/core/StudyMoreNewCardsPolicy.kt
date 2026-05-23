package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min

object StudyMoreNewCardsPolicy {
    const val NO_NEW_CARDS_AVAILABLE_MESSAGE: String = "No new cards are available."
    const val WHOLE_NUMBER_ERROR_MESSAGE: String = "Use a whole number of new cards."
    const val POSITIVE_COUNT_ERROR_MESSAGE: String = "Use at least 1 new card."

    @JvmStatic
    fun defaultRequestCount(availableCount: Int): Int {
        return max(1, min(5, availableCount))
    }

    @JvmStatic
    fun requestedCount(rawText: String): RequestDecision {
        val requested = try {
            rawText.trim().toInt()
        } catch (error: NumberFormatException) {
            return RequestDecision.rejected(WHOLE_NUMBER_ERROR_MESSAGE)
        }
        if (requested <= 0) {
            return RequestDecision.rejected(POSITIVE_COUNT_ERROR_MESSAGE)
        }
        return RequestDecision.accepted(requested)
    }

    @JvmStatic
    fun partialAvailabilityMessage(admittedCount: Int): String {
        return "Only " + StudyTextCopy.countText(admittedCount, "new card was", "new cards were") + " available."
    }

    class RequestDecision private constructor(
        private val requestedCount: Int,
        private val message: String,
    ) {
        fun requestedCount(): Int = requestedCount

        fun message(): String = message

        fun accepted(): Boolean = requestedCount > 0

        companion object {
            fun accepted(requestedCount: Int): RequestDecision = RequestDecision(requestedCount, "")

            fun rejected(message: String): RequestDecision = RequestDecision(-1, message)
        }
    }
}
