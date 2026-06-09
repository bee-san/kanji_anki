package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNoteTypeTextCopyTest {
    @Test
    fun noteTypeHelpersKeepLabelsReadable() {
        assertEquals("Note type", SettingsNoteTypeTextCopy.noteTypeFieldsTitle())
        assertEquals("Using Kiku", SettingsNoteTypeTextCopy.noteTypeUsingText("Kiku"))
        assertEquals("Using Custom Mining", SettingsNoteTypeTextCopy.noteTypeUsingText("  Custom Mining  "))
        assertEquals("Choose a note type", SettingsNoteTypeTextCopy.noteTypeUsingText(null))
        assertEquals("Choose a note type", SettingsNoteTypeTextCopy.noteTypeUsingText("   "))
        assertEquals("Use Kiku or map custom fields.", SettingsNoteTypeTextCopy.noteTypeFieldsBody())
        assertEquals("Field mappings", SettingsNoteTypeTextCopy.requiredFieldsTitle())
        assertEquals("Map the fields Kani needs.", SettingsNoteTypeTextCopy.requiredFieldsBody())
        assertEquals("Kanji field", SettingsNoteTypeTextCopy.expressionFieldLabel())
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
        assertEquals("Saved. Sync to rebuild practice.", SettingsNoteTypeTextCopy.noteTypeSavedToast())
    }
}
