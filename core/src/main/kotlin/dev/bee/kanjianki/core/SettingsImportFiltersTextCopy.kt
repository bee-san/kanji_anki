package dev.bee.kanjianki.core

import java.util.Locale

object SettingsImportFiltersTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun importFiltersTitle(): String = localizedText("Import filters", "インポートフィルター")

    @JvmStatic
    fun importFiltersBody(): String = localizedText("Pick sources, save, sync.", "ソースを選んで、保存して、同期する。")

    @JvmStatic
    fun activeCardsLabel(): String = localizedText("Active cards", "有効")

    @JvmStatic
    fun suspendedCardsLabel(): String = localizedText("Suspended cards", "停止")

    @JvmStatic
    fun taggedCardsLabel(): String = localizedText("Tagged cards", "タグ付き")

    @JvmStatic
    fun weakCardsLabel(): String = localizedText("Weak cards", "弱い")

    @JvmStatic
    fun browserQueryLabel(): String = localizedText("Browser query", "ブラウザ検索")

    @JvmStatic
    fun ankiBrowserQueryHint(): String = "deck:Japanese tag:kani"

    @JvmStatic
    fun ankiBrowserQueryLabel(): String = localizedText("Anki search", "Anki検索")

    @JvmStatic
    fun ankiBrowserQueryHelperText(): String = localizedText("Try is:suspended or tag:kani.", "is:suspended や tag:kani を試す。")

    @JvmStatic
    fun ankiNoteTagsHint(): String = "tag1, tag2"

    @JvmStatic
    fun ankiNoteTagsLabel(): String = localizedText("Tags to include", "含めるタグ")

    @JvmStatic
    fun fsrsDifficultyLabel(): String = localizedText("Minimum FSRS difficulty", "最小FSRS難度")

    @JvmStatic
    fun lapsesLabel(): String = localizedText("Minimum lapses", "最小失敗数")

    @JvmStatic
    fun minimumMatchingCardsLabel(): String = localizedText("Matching cards per kanji", "漢字ごとの一致カード数")

    @JvmStatic
    fun saveImportFiltersLabel(): String = localizedText("Save filters", "フィルターを保存")

    @JvmStatic
    fun browserQueryRequiredToast(): String = localizedText("Add a search or turn it off.", "検索条件を追加するか、オフにしてください。")

    @JvmStatic
    fun importSourceRequiredToast(): String = localizedText("Turn on at least one source.", "少なくとも1つのソースをオンにしてください。")

    @JvmStatic
    fun importFiltersSavedToast(): String = localizedText("Saved. Sync to refresh.", "保存しました。同期すると更新されます。")

    @JvmStatic
    fun presetsTitle(): String = localizedText("Presets", "プリセット")

    @JvmStatic
    fun importPresetSavedToast(): String = localizedText("Preset saved. Sync to refresh.", "プリセットを保存しました。同期すると更新されます。")

    @JvmStatic
    fun numericImportThresholdsToast(): String = localizedText("Use numeric thresholds.", "数値しきい値を使ってください。")

    @JvmStatic
    fun importThresholdRangeToast(): String = localizedText("Difficulty 1-10. Lapses 1-100. Cards 1-1000.", "難度 1-10。失敗 1-100。カード 1-1000。")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
