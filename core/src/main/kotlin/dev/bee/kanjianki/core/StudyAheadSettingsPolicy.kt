package dev.bee.kanjianki.core

object StudyAheadSettingsPolicy {
    @JvmStatic
    fun saveRequest(minutesText: String): SaveResult {
        val minutes = try {
            minutesText.trim().toInt()
        } catch (error: NumberFormatException) {
            return SaveResult.invalid(SettingsTextCopy.studyAheadWholeNumberErrorText())
        }
        if (
            minutes < SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES ||
            minutes > SettingsInputRules.MAX_STUDY_AHEAD_MINUTES
        ) {
            return SaveResult.invalid(SettingsTextCopy.studyAheadOutOfRangeErrorText())
        }
        return SaveResult.valid(minutes)
    }

    class SaveResult private constructor(
        @JvmField val valid: Boolean,
        @JvmField val minutes: Int,
        @JvmField val message: String,
    ) {
        companion object {
            fun valid(minutes: Int): SaveResult = SaveResult(true, minutes, "")

            fun invalid(message: String): SaveResult = SaveResult(false, 0, message)
        }
    }
}
