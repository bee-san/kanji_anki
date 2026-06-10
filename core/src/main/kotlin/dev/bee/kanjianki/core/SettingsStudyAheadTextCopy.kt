package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyAheadTextCopy {
    @JvmStatic
    fun studyAheadTitle(): String = "Study ahead"

    @JvmStatic
    fun studyAheadBody(): String {
        return "Review early; learning waits stay fixed."
    }

    @JvmStatic
    fun saveStudyAheadLabel(): String = "Save look-ahead"

    @JvmStatic
    fun studyAheadSavedToast(): String = "Look-ahead saved."

    @JvmStatic
    fun studyAheadMinutesLabel(): String {
        return String.format(Locale.ROOT, "Look-ahead minutes (%s)", studyAheadMinutesRange())
    }

    @JvmStatic
    fun studyAheadMinutesRange(): String {
        return String.format(
            Locale.ROOT,
            "%d-%d",
            SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES,
            SettingsInputRules.MAX_STUDY_AHEAD_MINUTES,
        )
    }

    @JvmStatic
    fun studyAheadWholeNumberErrorText(): String {
        return String.format(Locale.ROOT, "Enter whole minutes (%s).", studyAheadMinutesRange())
    }

    @JvmStatic
    fun studyAheadOutOfRangeErrorText(): String {
        return String.format(Locale.ROOT, "Enter %s minutes; 0 turns it off.", studyAheadMinutesRange())
    }

    @JvmStatic
    fun studyAheadMaxDescription(): String {
        val maxMinutes = SettingsInputRules.MAX_STUDY_AHEAD_MINUTES
        if (maxMinutes % 60 == 0) {
            return String.format(Locale.ROOT, "%d minutes (%dh)", maxMinutes, maxMinutes / 60)
        }
        return String.format(Locale.ROOT, "%d minutes", maxMinutes)
    }
}
