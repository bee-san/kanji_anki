package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyAheadTextCopy {
    @JvmStatic
    fun studyAheadTitle(): String = "Study ahead"

    @JvmStatic
    fun studyAheadBody(): String {
        return "Show due reviews early. Learning delays still apply."
    }

    @JvmStatic
    fun saveStudyAheadLabel(): String = "Save study ahead"

    @JvmStatic
    fun studyAheadSavedToast(): String = "Study ahead saved."

    @JvmStatic
    fun studyAheadMinutesLabel(): String {
        return String.format(Locale.ROOT, "Minutes ahead (%s)", studyAheadMinutesRange())
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
        return String.format(Locale.ROOT, "Enter whole minutes from %s.", studyAheadMinutesRange())
    }

    @JvmStatic
    fun studyAheadOutOfRangeErrorText(): String {
        return String.format(Locale.ROOT, "Use %s minutes. 0 turns it off.", studyAheadMinutesRange())
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
