package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNoteTypeTextCopyTest {
    @Test
    fun noteTypeHelpersPreserveFormatting() {
        assertEquals("Note type & clue fields", SettingsNoteTypeTextCopy.noteTypeFieldsTitle())
        assertEquals("Using Kiku", SettingsNoteTypeTextCopy.noteTypeUsingText("Kiku"))
        assertEquals("Default: Kiku. One card owns the note type and clue mapping.", SettingsNoteTypeTextCopy.noteTypeFieldsBody())
        assertEquals("Required fields", SettingsNoteTypeTextCopy.requiredFieldsTitle())
        assertEquals("Expression source, Reading=reading, Meaning=meaning, Sentence=context, Frequency/FreqSort=metadata.", SettingsNoteTypeTextCopy.requiredFieldsBody())
        assertEquals("Expression field", SettingsNoteTypeTextCopy.expressionFieldLabel())
        assertEquals("Reading field", SettingsNoteTypeTextCopy.readingFieldLabel())
        assertEquals("Meaning field", SettingsNoteTypeTextCopy.meaningFieldLabel())
        assertEquals("Sentence field", SettingsNoteTypeTextCopy.sentenceFieldLabel())
        assertEquals("Frequency field", SettingsNoteTypeTextCopy.frequencyFieldLabel())
        assertEquals("Frequency sort field", SettingsNoteTypeTextCopy.frequencySortFieldLabel())
        assertEquals("Choose from AnkiDroid", SettingsNoteTypeTextCopy.chooseFromAnkiDroidLabel())
        assertEquals("Use Kiku", SettingsNoteTypeTextCopy.useKikuLabel())
        assertEquals("Save note type", SettingsNoteTypeTextCopy.saveNoteTypeLabel())
        assertEquals("Enter a note type name.", SettingsNoteTypeTextCopy.noteTypeRequiredToast())
        assertEquals("Choose the field that contains kanji.", SettingsNoteTypeTextCopy.expressionFieldRequiredToast())
        assertEquals("Note type saved. Sync again to rebuild practice.", SettingsNoteTypeTextCopy.noteTypeSavedToast())
    }
}
