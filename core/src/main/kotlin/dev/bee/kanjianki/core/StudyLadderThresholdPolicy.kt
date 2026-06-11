package dev.bee.kanjianki.core

import java.util.Locale

object StudyLadderThresholdPolicy {
    const val POSITIVE_WHOLE_NUMBER_ERROR: String = "Use positive whole numbers."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun saveRequest(promotionDaysText: String, failStreakText: String): SaveResult {
        val promotionDays: Int
        val failStreak: Int
        try {
            promotionDays = parseWholeNumber(promotionDaysText)
            failStreak = parseWholeNumber(failStreakText)
        } catch (error: NumberFormatException) {
            return SaveResult.invalid(positiveWholeNumberError())
        }
        if (promotionDays < 1 || failStreak < 1) {
            return SaveResult.invalid(positiveWholeNumberError())
        }
        return SaveResult.valid(promotionDays, failStreak)
    }

    @JvmStatic
    fun positiveWholeNumberError(): String = localizedText(
        POSITIVE_WHOLE_NUMBER_ERROR,
        "正の整数を入力してください。",
    )

    private fun parseWholeNumber(value: String): Int {
        return value.trim().toInt()
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
    }

    class SaveResult private constructor(
        @JvmField val valid: Boolean,
        @JvmField val promotionDays: Int,
        @JvmField val failStreak: Int,
        @JvmField val message: String,
    ) {
        companion object {
            fun valid(promotionDays: Int, failStreak: Int): SaveResult {
                return SaveResult(true, promotionDays, failStreak, "")
            }

            fun invalid(message: String): SaveResult {
                return SaveResult(false, 0, 0, message)
            }
        }
    }
}
