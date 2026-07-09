package dev.bee.kanjianki.core

import java.util.Locale

object StudyLadderThresholdPolicy {
    const val POSITIVE_WHOLE_NUMBER_ERROR: String = "Use positive whole numbers."

    // Upper bounds keep the ladder responsive: an unbounded promotion interval
    // effectively freezes the ladder, and an unbounded fail streak makes
    // demotion unreachable. These caps are generous but finite.
    const val MAX_PROMOTION_INTERVAL_DAYS: Int = 365
    const val MAX_DEMOTION_FAIL_STREAK: Int = 30

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
        if (promotionDays > MAX_PROMOTION_INTERVAL_DAYS || failStreak > MAX_DEMOTION_FAIL_STREAK) {
            return SaveResult.invalid(rangeError())
        }
        return SaveResult.valid(promotionDays, failStreak)
    }

    @JvmStatic
    fun positiveWholeNumberError(): String = localizedText(
        POSITIVE_WHOLE_NUMBER_ERROR,
        "正の整数を入力してください。",
    )

    @JvmStatic
    fun rangeError(): String = localizedText(
        "Use at most $MAX_PROMOTION_INTERVAL_DAYS promotion days and $MAX_DEMOTION_FAIL_STREAK fails.",
        "昇格日数は最大 $MAX_PROMOTION_INTERVAL_DAYS、降格失敗回数は最大 $MAX_DEMOTION_FAIL_STREAK です。",
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
