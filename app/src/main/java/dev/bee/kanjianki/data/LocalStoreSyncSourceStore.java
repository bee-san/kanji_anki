package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.TextUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class LocalStoreSyncSourceStore {
    void saveSourceNotes(
            SQLiteDatabase db,
            List<RecordsSyncModels.Note> notes,
            LocalStoreBase.ActiveCardIndex activeIndex,
            RecordsSyncModels.Settings settings,
            long syncId
    ) {
        for (RecordsSyncModels.Note note : notes) {
            if (!activeIndex.noteIds.contains(note.noteId)) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(LocalStoreBase.COLUMN_NOTE_ID, note.noteId);
            values.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName);
            values.put(LocalStoreBase.COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)));
            values.put(LocalStoreBase.COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)));
            values.put(LocalStoreBase.COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)));
            values.put(LocalStoreBase.COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)));
            values.put(LocalStoreBase.COLUMN_FIELDS_JSON, LocalStoreBase.fieldsJson(note.fields));
            values.put(LocalStoreBase.COLUMN_TAGS, String.join(" ", note.tags));
            values.put(LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID, syncId);
            db.insertWithOnConflict(LocalStoreBase.TABLE_SOURCE_NOTES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void saveSourceCardsAndArchive(
            SQLiteDatabase db,
            List<RecordsSyncModels.Card> cards,
            Map<Long, RecordsSyncModels.Note> notesById,
            Set<Long> selectedSuspendedCardIds,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        for (RecordsSyncModels.Card card : cards) {
            RecordsSyncModels.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            if (card.suspended) {
                if (selectedSuspendedCardIds.contains(card.cardId)) {
                    saveSuspendedArchiveCard(db, card, note, settings, finishedAt, syncId);
                }
            } else {
                saveSourceCard(db, card, syncId);
            }
        }
    }

    void saveSuspendedArchiveCard(
            SQLiteDatabase db,
            RecordsSyncModels.Card card,
            RecordsSyncModels.Note note,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_CARD_ID, card.cardId);
        values.put(LocalStoreBase.COLUMN_NOTE_ID, card.noteId);
        values.put(LocalStoreBase.COLUMN_DECK_NAME, card.deckName);
        values.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName);
        values.put(LocalStoreBase.COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)));
        values.put(LocalStoreBase.COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)));
        values.put(LocalStoreBase.COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)));
        values.put(LocalStoreBase.COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)));
        values.put(LocalStoreBase.COLUMN_FIELDS_JSON, LocalStoreBase.fieldsJson(note.fields));
        values.put("archived_at", finishedAt);
        values.put("archived_sync_id", syncId);
        db.insertWithOnConflict(LocalStoreBase.TABLE_SUSPENDED_ARCHIVE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    void saveSourceCard(SQLiteDatabase db, RecordsSyncModels.Card card, long syncId) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_CARD_ID, card.cardId);
        values.put(LocalStoreBase.COLUMN_NOTE_ID, card.noteId);
        values.put(LocalStoreBase.COLUMN_DECK_NAME, card.deckName);
        values.put("ord", card.ord);
        values.put(LocalStoreBase.COLUMN_QUEUE, card.queue);
        values.put("type", card.type);
        values.put("due", card.due);
        values.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, card.intervalDays);
        values.put(LocalStoreBase.COLUMN_REPS, card.reps);
        values.put(LocalStoreBase.COLUMN_LAPSES, card.lapses);
        LocalStoreBase.putNullableDouble(values, LocalStoreBase.COLUMN_FSRS_STABILITY, card.fsrsStability);
        LocalStoreBase.putNullableDouble(values, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty);
        LocalStoreBase.putNullableDouble(values, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY, card.fsrsRetrievability);
        values.put(LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID, syncId);
        db.insertWithOnConflict(LocalStoreBase.TABLE_SOURCE_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
