package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyAheadTextCopy {
    @JvmStatic
    fun studyAheadTitle(): String = "Study ahead"

    @JvmStatic
    fun studyAheadBody(): String {
        return "Pull soon-due cards into the queue. 0 disables it; learning delays still apply."
    }

    @JvmStatic
    fun saveStudyAheadLabel(): String = "Save study ahead"

    @JvmStatic
    fun studyAheadSavedToast(): String = "Study ahead saved."

    @JvmStatic
    fun studyAheadMinutesLabel(): String {
        return String.format(Locale.ROOT, "Minutes (%s)", studyAheadMinutesRange())
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
        return String.format(Locale.ROOT, "Use a whole number of minutes (%s).", studyAheadMinutesRange())
    }

    @JvmStatic
    fun studyAheadOutOfRangeErrorText(): String {
        return String.format(
            Locale.ROOT,
            "Use %d to disable, or up to %s.",
            SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES,
            studyAheadMaxDescription(),
        )
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
