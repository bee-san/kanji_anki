package dev.bee.kanjianki.core

object StudyLadderThresholdPolicy {
    const val POSITIVE_WHOLE_NUMBER_ERROR: String = "Use positive whole numbers."

    @JvmStatic
    fun saveRequest(promotionDaysText: String, failStreakText: String): SaveResult {
        val promotionDays: Int
        val failStreak: Int
        try {
            promotionDays = parseWholeNumber(promotionDaysText)
            failStreak = parseWholeNumber(failStreakText)
        } catch (error: NumberFormatException) {
            return SaveResult.invalid(POSITIVE_WHOLE_NUMBER_ERROR)
        }
        if (promotionDays < 1 || failStreak < 1) {
            return SaveResult.invalid(POSITIVE_WHOLE_NUMBER_ERROR)
        }
        return SaveResult.valid(promotionDays, failStreak)
    }

    private fun parseWholeNumber(value: String): Int {
        return value.trim().toInt()
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
