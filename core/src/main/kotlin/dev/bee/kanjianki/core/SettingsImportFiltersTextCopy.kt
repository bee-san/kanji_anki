package dev.bee.kanjianki.core

object SettingsImportFiltersTextCopy {
    @JvmStatic
    fun importFiltersTitle(): String = "Import filters"

    @JvmStatic
    fun importFiltersBody(): String {
        return "Suspended AnkiDroid cards are the default source for Kani practice. Turn on active, tagged, or weak cards only when you want those sources included."
    }

    @JvmStatic
    fun activeCardsLabel(): String = "Active cards"

    @JvmStatic
    fun suspendedCardsLabel(): String = "Suspended cards"

    @JvmStatic
    fun taggedCardsLabel(): String = "Tagged cards"

    @JvmStatic
    fun weakCardsLabel(): String = "Weak cards"

    @JvmStatic
    fun browserQueryLabel(): String = "Browser query"

    @JvmStatic
    fun ankiBrowserQueryHint(): String = "deck:Japanese tag:kani"

    @JvmStatic
    fun ankiBrowserQueryLabel(): String = "Anki browser query"

    @JvmStatic
    fun ankiNoteTagsHint(): String = "tag1, tag2"

    @JvmStatic
    fun ankiNoteTagsLabel(): String = "Anki note tags"

    @JvmStatic
    fun fsrsDifficultyLabel(): String = "FSRS difficulty"

    @JvmStatic
    fun lapsesLabel(): String = "Lapses"

    @JvmStatic
    fun minimumMatchingCardsLabel(): String = "Minimum matching cards per kanji"

    @JvmStatic
    fun saveImportFiltersLabel(): String = "Save import filters"

    @JvmStatic
    fun browserQueryRequiredToast(): String = "Enter an Anki browser query or turn off Browser query."

    @JvmStatic
    fun importSourceRequiredToast(): String = "Turn on at least one import source."

    @JvmStatic
    fun importFiltersSavedToast(): String = "Import filters saved. Sync again to rebuild practice."

    @JvmStatic
    fun presetsTitle(): String = "Presets"

    @JvmStatic
    fun importPresetSavedToast(): String = "Import preset saved. Sync again to rebuild practice."

    @JvmStatic
    fun numericImportThresholdsToast(): String = "Use numeric import thresholds."

    @JvmStatic
    fun importThresholdRangeToast(): String = "Use difficulty 1-10, lapses 1-100, and cards 1-1000."
}
