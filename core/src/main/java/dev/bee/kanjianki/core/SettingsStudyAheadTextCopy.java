package dev.bee.kanjianki.core;

import java.util.Locale;

public final class SettingsStudyAheadTextCopy {
    private SettingsStudyAheadTextCopy() {
    }

    public static String studyAheadTitle() {
        return "Study ahead";
    }

    public static String studyAheadBody() {
        return "Pull cards becoming due within this many minutes into the queue. Set 0 to disable. Learning step delays still apply normally (just like Anki).";
    }

    public static String saveStudyAheadLabel() {
        return "Save study ahead";
    }

    public static String studyAheadSavedToast() {
        return "Study ahead saved.";
    }

    public static String studyAheadMinutesLabel() {
        return String.format(Locale.ROOT, "Minutes (%s)", studyAheadMinutesRange());
    }

    public static String studyAheadMinutesRange() {
        return String.format(Locale.ROOT, "%d-%d", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES, SettingsInputRules.MAX_STUDY_AHEAD_MINUTES);
    }

    public static String studyAheadWholeNumberErrorText() {
        return String.format(Locale.ROOT, "Use a whole number of minutes (%s).", studyAheadMinutesRange());
    }

    public static String studyAheadOutOfRangeErrorText() {
        return String.format(Locale.ROOT, "Use %d to disable, or up to %s.", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES, studyAheadMaxDescription());
    }

    public static String studyAheadMaxDescription() {
        int maxMinutes = SettingsInputRules.MAX_STUDY_AHEAD_MINUTES;
        if (maxMinutes % 60 == 0) {
            return String.format(Locale.ROOT, "%d minutes (%dh)", maxMinutes, maxMinutes / 60);
        }
        return String.format(Locale.ROOT, "%d minutes", maxMinutes);
    }
}
