package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.TextUtil

/** Durable user-authored notes that are intentionally independent of Anki sync data. */
internal class LocalStoreMnemonicNotes(
    private val store: LocalStoreBase,
) {
    fun read(kanji: String?): String {
        val key = normalizeKey(kanji)
        if (key.isEmpty()) {
            return ""
        }
        store.readableDatabase.query(
            LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES,
            arrayOf(COLUMN_NOTE),
            LocalStoreBase.WHERE_KANJI,
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }
    }

    fun save(kanji: String?, note: String?, updatedAtMillis: Long) {
        val key = normalizeKey(kanji)
        if (key.isEmpty()) {
            return
        }
        val normalizedNote = normalizeNote(note)
        val db = store.writableDatabase
        if (normalizedNote.isEmpty()) {
            db.delete(
                LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES,
                LocalStoreBase.WHERE_KANJI,
                arrayOf(key),
            )
            return
        }
        val values = ContentValues().apply {
            put(LocalStoreBase.COLUMN_KANJI, key)
            put(COLUMN_NOTE, normalizedNote)
            put(LocalStoreBase.COLUMN_UPDATED_AT, updatedAtMillis)
        }
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    companion object {
        private const val COLUMN_NOTE = "note"

        private fun normalizeNote(note: String?): String = note.orEmpty().trim()

        private fun normalizeKey(kanji: String?): String = TextUtil.normalizeSingleKanji(kanji)
    }
}
