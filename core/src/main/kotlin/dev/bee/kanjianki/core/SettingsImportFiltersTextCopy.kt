package dev.bee.kanjianki.core

object SettingsImportFiltersTextCopy {
    @JvmStatic
    fun importFiltersTitle(): String = "Import filters"

    @JvmStatic
    fun importFiltersBody(): String {
        return "Choose import sources. Leech tags stay excluded."
    }

    @JvmStatic
    fun activeCardsLabel(): String = "Include active cards"

    @JvmStatic
    fun suspendedCardsLabel(): String = "Include suspended cards"

    @JvmStatic
    fun taggedCardsLabel(): String = "Include tagged cards"

    @JvmStatic
    fun weakCardsLabel(): String = "Include weak cards"

    @JvmStatic
    fun browserQueryLabel(): String = "Include browser query"

    @JvmStatic
    fun ankiBrowserQueryHint(): String = "deck:Japanese tag:kani"

    @JvmStatic
    fun ankiBrowserQueryLabel(): String = "Browser query"

    @JvmStatic
    fun ankiBrowserQueryHelperText(): String {
        return "Try is:suspended, rated:31:1, or tag:kani."
    }

    @JvmStatic
    fun ankiNoteTagsHint(): String = "tag1, tag2"

    @JvmStatic
    fun ankiNoteTagsLabel(): String = "Note tags"

    @JvmStatic
    fun fsrsDifficultyLabel(): String = "Minimum FSRS difficulty"

    @JvmStatic
    fun lapsesLabel(): String = "Minimum lapses"

    @JvmStatic
    fun minimumMatchingCardsLabel(): String = "Cards per kanji"

    @JvmStatic
    fun saveImportFiltersLabel(): String = "Save import filters"

    @JvmStatic
    fun browserQueryRequiredToast(): String = "Add a browser query or turn it off."

    @JvmStatic
    fun importSourceRequiredToast(): String = "Choose at least one source."

    @JvmStatic
    fun importFiltersSavedToast(): String = "Filters saved. Sync to refresh practice."

    @JvmStatic
    fun presetsTitle(): String = "Presets"

    @JvmStatic
    fun importPresetSavedToast(): String = "Preset saved. Sync to refresh practice."

    @JvmStatic
    fun numericImportThresholdsToast(): String = "Enter numeric thresholds."

    @JvmStatic
    fun importThresholdRangeToast(): String = "Difficulty 1-10, lapses 1-100, cards 1-1000."
}
