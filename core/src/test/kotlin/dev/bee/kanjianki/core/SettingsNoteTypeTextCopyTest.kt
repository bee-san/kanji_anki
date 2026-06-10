package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNoteTypeTextCopyTest {
    @Test
    fun noteTypeHelpersPreserveFormatting() {
        assertEquals("Note type", SettingsNoteTypeTextCopy.noteTypeFieldsTitle())
        assertEquals("Kiku", SettingsNoteTypeTextCopy.noteTypeUsingText("Kiku"))
        assertEquals("Kiku", SettingsNoteTypeTextCopy.noteTypeUsingText("  Kiku  "))
        assertEquals("Select a note type", SettingsNoteTypeTextCopy.noteTypeUsingText(null))
        assertEquals("Select a note type", SettingsNoteTypeTextCopy.noteTypeUsingText("   "))
        assertEquals("Use Kiku or map Anki fields.", SettingsNoteTypeTextCopy.noteTypeFieldsBody())
        assertEquals("Fields", SettingsNoteTypeTextCopy.requiredFieldsTitle())
        assertEquals("Choose the fields Kani reads.", SettingsNoteTypeTextCopy.requiredFieldsBody())
        assertEquals("Expression field", SettingsNoteTypeTextCopy.expressionFieldLabel())
        assertEquals("Reading field", SettingsNoteTypeTextCopy.readingFieldLabel())
        assertEquals("Meaning field", SettingsNoteTypeTextCopy.meaningFieldLabel())
        assertEquals("Sentence field", SettingsNoteTypeTextCopy.sentenceFieldLabel())
        assertEquals("Frequency field", SettingsNoteTypeTextCopy.frequencyFieldLabel())
        assertEquals("Frequency sort field", SettingsNoteTypeTextCopy.frequencySortFieldLabel())
        assertEquals("Choose note type", SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel())
        assertEquals("Use Kiku", SettingsNoteTypeTextCopy.useKikuLabel())
        assertEquals("Save note type", SettingsNoteTypeTextCopy.saveNoteTypeLabel())
        assertEquals("Enter a note type name.", SettingsNoteTypeTextCopy.noteTypeRequiredToast())
        assertEquals("Choose the kanji field.", SettingsNoteTypeTextCopy.expressionFieldRequiredToast())
        assertEquals("Saved. Sync to apply fields.", SettingsNoteTypeTextCopy.noteTypeSavedToast())
    }

    @Test
    fun noteTypeHelpersTranslateToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("ノートタイプ", SettingsNoteTypeTextCopy.noteTypeFieldsTitle())
            assertEquals("Kiku", SettingsNoteTypeTextCopy.noteTypeUsingText("Kiku"))
            assertEquals("Kiku", SettingsNoteTypeTextCopy.noteTypeUsingText("  Kiku  "))
            assertEquals("ノートタイプを選択", SettingsNoteTypeTextCopy.noteTypeUsingText(null))
            assertEquals("ノートタイプを選択", SettingsNoteTypeTextCopy.noteTypeUsingText("   "))
            assertEquals("Kikuを使うか、Ankiのフィールドを割り当ててください。", SettingsNoteTypeTextCopy.noteTypeFieldsBody())
            assertEquals("フィールド", SettingsNoteTypeTextCopy.requiredFieldsTitle())
            assertEquals("Kaniが読むフィールドを選んでください。", SettingsNoteTypeTextCopy.requiredFieldsBody())
            assertEquals("表現フィールド", SettingsNoteTypeTextCopy.expressionFieldLabel())
            assertEquals("読みフィールド", SettingsNoteTypeTextCopy.readingFieldLabel())
            assertEquals("意味フィールド", SettingsNoteTypeTextCopy.meaningFieldLabel())
            assertEquals("例文フィールド", SettingsNoteTypeTextCopy.sentenceFieldLabel())
            assertEquals("頻度フィールド", SettingsNoteTypeTextCopy.frequencyFieldLabel())
            assertEquals("頻度順フィールド", SettingsNoteTypeTextCopy.frequencySortFieldLabel())
            assertEquals("ノートタイプを選択", SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel())
            assertEquals("Kikuを使う", SettingsNoteTypeTextCopy.useKikuLabel())
            assertEquals("ノートタイプを保存", SettingsNoteTypeTextCopy.saveNoteTypeLabel())
            assertEquals("ノートタイプ名を入力してください。", SettingsNoteTypeTextCopy.noteTypeRequiredToast())
            assertEquals("漢字フィールドを選んでください。", SettingsNoteTypeTextCopy.expressionFieldRequiredToast())
            assertEquals("保存しました。同期するとフィールドが反映されます。", SettingsNoteTypeTextCopy.noteTypeSavedToast())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
