package dev.bee.kanjianki.core;

public final class SettingsImportFiltersTextCopy {
    private SettingsImportFiltersTextCopy() {
    }

    public static String importFiltersTitle() {
        return "Import filters";
    }

    public static String importFiltersBody() {
        return "Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included.";
    }

    public static String activeCardsLabel() {
        return "Active cards";
    }

    public static String suspendedCardsLabel() {
        return "Suspended cards";
    }

    public static String taggedCardsLabel() {
        return "Tagged cards";
    }

    public static String weakCardsLabel() {
        return "Weak cards";
    }

    public static String browserQueryLabel() {
        return "Browser query";
    }

    public static String ankiBrowserQueryHint() {
        return "deck:Japanese tag:kani";
    }

    public static String ankiBrowserQueryLabel() {
        return "Anki browser query";
    }

    public static String ankiNoteTagsHint() {
        return "tag1, tag2";
    }

    public static String ankiNoteTagsLabel() {
        return "Anki note tags";
    }

    public static String fsrsDifficultyLabel() {
        return "FSRS difficulty";
    }

    public static String lapsesLabel() {
        return "Lapses";
    }

    public static String minimumMatchingCardsLabel() {
        return "Minimum matching cards per kanji";
    }

    public static String saveImportFiltersLabel() {
        return "Save import filters";
    }

    public static String browserQueryRequiredToast() {
        return "Enter an Anki browser query or turn off Browser query.";
    }

    public static String importSourceRequiredToast() {
        return "Turn on at least one import source.";
    }

    public static String importFiltersSavedToast() {
        return "Import filters saved. Sync again to rebuild practice.";
    }

    public static String presetsTitle() {
        return "Presets";
    }

    public static String importPresetSavedToast() {
        return "Import preset saved. Sync again to rebuild practice.";
    }

    public static String numericImportThresholdsToast() {
        return "Use numeric import thresholds.";
    }

    public static String importThresholdRangeToast() {
        return "Use difficulty 1-10, lapses 1-100, and cards 1-1000.";
    }
}
