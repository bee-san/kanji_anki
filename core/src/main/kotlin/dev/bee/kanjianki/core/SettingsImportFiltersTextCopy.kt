package dev.bee.kanjianki.core

object SettingsImportFiltersTextCopy {
    @JvmStatic
    fun importFiltersTitle(): String = "Import filters"

    @JvmStatic
    fun importFiltersBody(): String {
        return "Pick sources, save, then sync to refresh practice."
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
    fun browserQueryLabel(): String = "Anki search results"

    @JvmStatic
    fun ankiBrowserQueryHint(): String = "deck:Japanese tag:kani"

    @JvmStatic
    fun ankiBrowserQueryLabel(): String = "Anki search"

    @JvmStatic
    fun ankiBrowserQueryHelperText(): String {
        return "Try is:suspended or tag:kani."
    }

    @JvmStatic
    fun ankiNoteTagsHint(): String = "tag1, tag2"

    @JvmStatic
    fun ankiNoteTagsLabel(): String = "Tags to include"

    @JvmStatic
    fun fsrsDifficultyLabel(): String = "Minimum FSRS difficulty"

    @JvmStatic
    fun lapsesLabel(): String = "Minimum lapses"

    @JvmStatic
    fun minimumMatchingCardsLabel(): String = "Matching cards per kanji"

    @JvmStatic
    fun saveImportFiltersLabel(): String = "Save import filters"

    @JvmStatic
    fun browserQueryRequiredToast(): String = "Add an Anki search or turn it off."

    @JvmStatic
    fun importSourceRequiredToast(): String = "Turn on at least one source."

    @JvmStatic
    fun importFiltersSavedToast(): String = "Saved. Sync to refresh practice."

    @JvmStatic
    fun presetsTitle(): String = "Presets"

    @JvmStatic
    fun importPresetSavedToast(): String = "Preset saved. Sync to refresh."

    @JvmStatic
    fun numericImportThresholdsToast(): String = "Use numbers for thresholds."

    @JvmStatic
    fun importThresholdRangeToast(): String = "Use difficulty 1-10, lapses 1-100, cards 1-1000."
}
