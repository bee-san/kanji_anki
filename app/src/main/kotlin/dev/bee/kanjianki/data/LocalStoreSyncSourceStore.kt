package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TextUtil

internal class LocalStoreSyncSourceStore {
    fun saveSourceNotes(
        db: SQLiteDatabase,
        notes: List<RecordsSyncModels.Note>,
        activeIndex: LocalStoreBase.ActiveCardIndex,
        settings: RecordsSyncModels.Settings,
        syncId: Long,
    ) {
        for (note in notes) {
            if (!activeIndex.noteIds.contains(note.noteId)) {
                continue
            }
            val values = ContentValues()
            values.put(LocalStoreBase.COLUMN_NOTE_ID, note.noteId)
            values.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName)
            values.put(LocalStoreBase.COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)))
            values.put(LocalStoreBase.COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)))
            values.put(LocalStoreBase.COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)))
            values.put(LocalStoreBase.COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)))
            values.put(LocalStoreBase.COLUMN_FIELDS_JSON, LocalStoreBase.fieldsJson(note.fields))
            values.put(LocalStoreBase.COLUMN_TAGS, note.tags.joinToString(" "))
            values.put(LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID, syncId)
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SOURCE_NOTES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun saveSourceCardsAndArchive(
        db: SQLiteDatabase,
        cards: List<RecordsSyncModels.Card>,
        notesById: Map<Long, RecordsSyncModels.Note>,
        selectedSuspendedCardIds: Set<Long>,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        for (card in cards) {
            val note = notesById[card.noteId] ?: continue
            if (card.suspended) {
                if (selectedSuspendedCardIds.contains(card.cardId)) {
                    saveSuspendedArchiveCard(db, card, note, settings, finishedAt, syncId)
                }
            } else {
                saveSourceCard(db, card, syncId)
            }
        }
    }

    fun saveSuspendedArchiveCard(
        db: SQLiteDatabase,
        card: RecordsSyncModels.Card,
        note: RecordsSyncModels.Note,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_CARD_ID, card.cardId)
        values.put(LocalStoreBase.COLUMN_NOTE_ID, card.noteId)
        values.put(LocalStoreBase.COLUMN_DECK_NAME, card.deckName)
        values.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName)
        values.put(LocalStoreBase.COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)))
        values.put(LocalStoreBase.COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)))
        values.put(LocalStoreBase.COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)))
        values.put(LocalStoreBase.COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)))
        values.put(LocalStoreBase.COLUMN_FIELDS_JSON, LocalStoreBase.fieldsJson(note.fields))
        values.put("archived_at", finishedAt)
        values.put("archived_sync_id", syncId)
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_SUSPENDED_ARCHIVE,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun saveSourceCard(db: SQLiteDatabase, card: RecordsSyncModels.Card, syncId: Long) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_CARD_ID, card.cardId)
        values.put(LocalStoreBase.COLUMN_NOTE_ID, card.noteId)
        values.put(LocalStoreBase.COLUMN_DECK_NAME, card.deckName)
        values.put("ord", card.ord)
        values.put(LocalStoreBase.COLUMN_QUEUE, card.queue)
        values.put("type", card.type)
        values.put("due", card.due)
        values.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, card.intervalDays)
        values.put(LocalStoreBase.COLUMN_REPS, card.reps)
        values.put(LocalStoreBase.COLUMN_LAPSES, card.lapses)
        LocalStoreBase.putNullableDouble(values, LocalStoreBase.COLUMN_FSRS_STABILITY, card.fsrsStability)
        LocalStoreBase.putNullableDouble(values, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty)
        LocalStoreBase.putNullableDouble(values, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY, card.fsrsRetrievability)
        values.put(LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID, syncId)
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_SOURCE_CARDS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}
