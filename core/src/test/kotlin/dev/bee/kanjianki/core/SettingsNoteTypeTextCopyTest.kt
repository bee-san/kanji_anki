package dev.bee.kanjianki.core

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
}
