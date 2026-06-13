package dev.bee.kanjianki.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object StudyMoreNewCardsPolicy {
    const val NO_NEW_CARDS_AVAILABLE_MESSAGE: String = "No new cards are available."
    const val WHOLE_NUMBER_ERROR_MESSAGE: String = "Use a whole number of new cards."
    const val POSITIVE_COUNT_ERROR_MESSAGE: String = "Use at least 1 new card."

    private const val NO_NEW_CARDS_AVAILABLE_MESSAGE_JAPANESE: String = "新しいカードはありません。"
    private const val WHOLE_NUMBER_ERROR_MESSAGE_JAPANESE: String = "新規カード数は整数で入力してください。"
    private const val POSITIVE_COUNT_ERROR_MESSAGE_JAPANESE: String = "新規カード数は1以上で入力してください。"
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun noNewCardsAvailableMessage(): String = localizedText(
        NO_NEW_CARDS_AVAILABLE_MESSAGE,
        NO_NEW_CARDS_AVAILABLE_MESSAGE_JAPANESE,
    )

    @JvmStatic
    fun wholeNumberErrorMessage(): String = localizedText(
        WHOLE_NUMBER_ERROR_MESSAGE,
        WHOLE_NUMBER_ERROR_MESSAGE_JAPANESE,
    )

    @JvmStatic
    fun positiveCountErrorMessage(): String = localizedText(
        POSITIVE_COUNT_ERROR_MESSAGE,
        POSITIVE_COUNT_ERROR_MESSAGE_JAPANESE,
    )

    @JvmStatic
    fun defaultRequestCount(availableCount: Int): Int {
        return max(1, min(5, availableCount))
    }

    @JvmStatic
    fun requestedCount(rawText: String): RequestDecision {
        val requested = try {
            rawText.trim().toInt()
        } catch (error: NumberFormatException) {
            return RequestDecision.rejected(wholeNumberErrorMessage())
        }
        if (requested <= 0) {
            return RequestDecision.rejected(positiveCountErrorMessage())
        }
        return RequestDecision.accepted(requested)
    }

    @JvmStatic
    fun partialAvailabilityMessage(admittedCount: Int): String {
        return localizedText(
            "Only " + StudyTextCopy.countText(admittedCount, "new card was", "new cards were") + " available.",
            "新規カードは${admittedCount}件のみ使用できます。",
        )
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

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
