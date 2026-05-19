package dev.bee.kanjianki.core;

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
}
