package dev.bee.kanjianki.core

object SettingsImportFiltersTextCopy {
    @JvmStatic
    fun importFiltersTitle(): String = "Import filters"

    @JvmStatic
    fun importFiltersBody(): String {
        return "Prefer suspended cards. Leech tags stay skipped."
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
        return "Examples: is:suspended, rated:31:1, tag:kani. Filters still apply."
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
    fun saveImportFiltersLabel(): String = "Save filters"

    @JvmStatic
    fun browserQueryRequiredToast(): String = "Add a query or turn it off."

    @JvmStatic
    fun importSourceRequiredToast(): String = "Enable at least one source."

    @JvmStatic
    fun importFiltersSavedToast(): String = "Saved. Sync to refresh practice."

    @JvmStatic
    fun presetsTitle(): String = "Presets"

    @JvmStatic
    fun importPresetSavedToast(): String = "Preset saved. Sync to refresh practice."

    @JvmStatic
    fun numericImportThresholdsToast(): String = "Use numeric import thresholds."

    @JvmStatic
    fun importThresholdRangeToast(): String = "Use difficulty 1-10, lapses 1-100, cards 1-1000."
}
