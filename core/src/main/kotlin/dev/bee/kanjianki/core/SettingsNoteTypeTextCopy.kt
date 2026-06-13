package dev.bee.kanjianki.core

import java.util.Locale

object SettingsNoteTypeTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun noteTypeFieldsTitle(): String = localizedText("Note type", "ノートタイプ")

    @JvmStatic
    fun noteTypeUsingText(modelName: String?): String {
        val safeModelName = modelName?.javaTrim() ?: ""
        if (safeModelName.isEmpty()) {
            return localizedText("Select a note type", "ノートタイプを選択")
        }
        return safeModelName
    }

    @JvmStatic
    fun noteTypeFieldsBody(): String = localizedText("Use Kiku or map Anki fields.", "Kikuを使うか、Ankiのフィールドを割り当ててください。")

    @JvmStatic
    fun requiredFieldsTitle(): String = localizedText("Fields", "フィールド")

    @JvmStatic
    fun requiredFieldsBody(): String = localizedText("Choose the fields Kani reads.", "Kaniが読むフィールドを選んでください。")

    @JvmStatic
    fun expressionFieldLabel(): String = localizedText("Expression field", "表現フィールド")

    @JvmStatic
    fun readingFieldLabel(): String = localizedText("Reading field", "読みフィールド")

    @JvmStatic
    fun meaningFieldLabel(): String = localizedText("Meaning field", "意味フィールド")

    @JvmStatic
    fun sentenceFieldLabel(): String = localizedText("Sentence field", "例文フィールド")

    @JvmStatic
    fun frequencyFieldLabel(): String = localizedText("Frequency field", "頻度フィールド")

    @JvmStatic
    fun frequencySortFieldLabel(): String = localizedText("Frequency sort field", "頻度順フィールド")

    @JvmStatic
    fun chooseFromAnkiDroidLabel(): String = localizedText("Choose note type", "ノートタイプを選択")

    @JvmStatic
    fun useKikuLabel(): String = localizedText("Use Kiku", "Kikuを使う")

    @JvmStatic
    fun saveNoteTypeLabel(): String = localizedText("Save note type", "ノートタイプを保存")

    @JvmStatic
    fun noteTypeRequiredToast(): String = localizedText("Enter a note type name.", "ノートタイプ名を入力してください。")

    @JvmStatic
    fun expressionFieldRequiredToast(): String = localizedText("Choose the kanji field.", "漢字フィールドを選んでください。")

    @JvmStatic
    fun noteTypeSavedToast(): String = localizedText("Saved. Sync to apply fields.", "保存しました。同期するとフィールドが反映されます。")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }
}
