package dev.bee.kanjianki.core

object SettingsImportFiltersTextCopy {
    @JvmStatic
    fun importFiltersTitle(): String = "Import filters"

    @JvmStatic
    fun importFiltersBody(): String {
        return "Suspend cards by default. Turn on active, tagged, or weak only when needed; Kani skips leech tags."
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
    fun browserQueryLabel(): String = "Use browser query"

    @JvmStatic
    fun ankiBrowserQueryHint(): String = "deck:Japanese tag:kani"

    @JvmStatic
    fun ankiBrowserQueryLabel(): String = "Browser query"

    @JvmStatic
    fun ankiBrowserQueryHelperText(): String {
        return "Examples: is:suspended, rated:31:1, tag:kani. Kani keeps note type, rank, and threshold."
    }

    @JvmStatic
    fun ankiNoteTagsHint(): String = "tag1, tag2"

    @JvmStatic
    fun ankiNoteTagsLabel(): String = "Note tags"

    @JvmStatic
    fun fsrsDifficultyLabel(): String = "FSRS difficulty"

    @JvmStatic
    fun lapsesLabel(): String = "Lapses"

    @JvmStatic
    fun minimumMatchingCardsLabel(): String = "Minimum matching cards per kanji"

    @JvmStatic
    fun saveImportFiltersLabel(): String = "Save import filters"

    @JvmStatic
    fun browserQueryRequiredToast(): String = "Enter a browser query or turn it off."

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
