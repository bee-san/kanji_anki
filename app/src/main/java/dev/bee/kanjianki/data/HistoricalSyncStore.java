package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import static dev.bee.kanjianki.data.LocalStoreBase.*;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.HistoricalKanjiAggregate;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class HistoricalSyncStore {
    private final LocalStoreHistory localStore;

    HistoricalSyncStore(LocalStoreHistory localStore) {
        this.localStore = localStore;
    }

    void appendHistoricalSyncSnapshots(
            SQLiteDatabase db,
            RecordsSyncModels.CollectionSnapshot snapshot,
            Map<Long, RecordsSyncModels.Note> notesById,
            List<RecordsImportModels.DashboardRow> rows,
            RecordsSyncModels.Settings settings,
            long syncId,
            SyncTiming timing
    ) {
        localStore.createHistoricalSyncTables(db);
        Map<Long, LinkedHashSet<String>> deckIdsByNote = deckIdsByNote(snapshot.cards);
        Map<Long, LinkedHashSet<String>> deckNamesByNote = deckNamesByNote(snapshot.cards);
        Map<String, HistoricalKanjiAggregate> aggregates = new LinkedHashMap<>();

        for (RecordsSyncModels.Card card : snapshot.cards) {
            RecordsSyncModels.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            ContentValues cardValues = new ContentValues();
            cardValues.put(COLUMN_SYNC_ID, syncId);
            cardValues.put(COLUMN_STARTED_AT, timing.startedAt);
            cardValues.put(COLUMN_FINISHED_AT, timing.finishedAt);
            cardValues.put(COLUMN_CARD_ID, card.cardId);
            cardValues.put(COLUMN_NOTE_ID, card.noteId);
            cardValues.put(COLUMN_DECK_ID, card.deckId);
            cardValues.put(COLUMN_DECK_NAME, card.deckName);
            cardValues.put(COLUMN_MODEL_ID, note.modelId);
            cardValues.put(COLUMN_MODEL_NAME, note.modelName);
            cardValues.put("ord", card.ord);
            cardValues.put(COLUMN_QUEUE, card.queue);
            cardValues.put("type", card.type);
            cardValues.put("due", card.due);
            cardValues.put(COLUMN_INTERVAL_DAYS, card.intervalDays);
            cardValues.put(COLUMN_REPS, card.reps);
            cardValues.put(COLUMN_LAPSES, card.lapses);
            cardValues.put("suspended", card.suspended ? 1 : 0);
            putNullableDouble(cardValues, COLUMN_FSRS_STABILITY, card.fsrsStability);
            putNullableDouble(cardValues, COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty);
            putNullableDouble(cardValues, COLUMN_FSRS_RETRIEVABILITY, card.fsrsRetrievability);
            cardValues.put(COLUMN_MATURE, card.mature(settings.matureDays) ? 1 : 0);
            db.insertWithOnConflict(TABLE_SYNC_CARD_SNAPSHOTS, null, cardValues, SQLiteDatabase.CONFLICT_REPLACE);

            for (String kanji : extractedKanji(note, settings)) {
                aggregateFor(aggregates, kanji).add(card, settings.matureDays);
            }
        }

        for (RecordsSyncModels.Note note : snapshot.notes) {
            LinkedHashSet<String> deckIds = deckIdsByNote.get(note.noteId);
            LinkedHashSet<String> decks = deckNamesByNote.get(note.noteId);
            if (decks == null || decks.isEmpty()) {
                continue;
            }
            String expression = TextUtil.normalizeJapanese(note.expression(settings));
            String reading = TextUtil.normalizeJapanese(note.reading(settings));
            String meaning = TextUtil.firstMeaningLine(note.meaning(settings));
            String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
            ContentValues noteValues = new ContentValues();
            noteValues.put(COLUMN_SYNC_ID, syncId);
            noteValues.put(COLUMN_FINISHED_AT, timing.finishedAt);
            noteValues.put(COLUMN_NOTE_ID, note.noteId);
            noteValues.put(COLUMN_MODEL_ID, note.modelId);
            noteValues.put(COLUMN_MODEL_NAME, note.modelName);
            noteValues.put(COLUMN_DECK_IDS, deckIds == null ? "" : String.join(" ", deckIds));
            noteValues.put(COLUMN_DECK_NAMES, String.join(" ", decks));
            noteValues.put(COLUMN_EXPRESSION, expression);
            noteValues.put(COLUMN_READING, reading);
            noteValues.put(COLUMN_MEANING, meaning);
            noteValues.put(COLUMN_SENTENCE, sentence);
            noteValues.put(COLUMN_TAGS, String.join(" ", note.tags));
            noteValues.put(COLUMN_FIELDS_JSON, fieldsJson(note.fields));
            noteValues.put("extracted_kanji", String.join("", TextUtil.extractKanji(expression + " " + sentence)));
            db.insertWithOnConflict(TABLE_SYNC_NOTE_SNAPSHOTS, null, noteValues, SQLiteDatabase.CONFLICT_REPLACE);
        }

        overlayDashboardRows(aggregates, rows);
        insertHistoricalKanjiAggregates(db, syncId, timing.finishedAt, aggregates);
    }

    void backfillLatestHistoricalSync(SQLiteDatabase db) {
        if (tableHasRows(db, TABLE_SYNC_KANJI_SNAPSHOTS)) {
            return;
        }
        HistoricalSyncRun sync = latestSuccessfulSyncRun(db);
        if (sync == null) {
            return;
        }
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        Map<Long, HistoricalNoteSnapshot> notes = currentSourceNotes(db);
        if (notes.isEmpty()) {
            return;
        }
        Map<Long, LinkedHashSet<String>> deckIdsByNote = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<String>> deckNamesByNote = new LinkedHashMap<>();
        Map<String, HistoricalKanjiAggregate> aggregates = new LinkedHashMap<>();
        HistoricalBackfillContext context = new HistoricalBackfillContext(settings, deckIdsByNote, deckNamesByNote, aggregates);
        backfillHistoricalCards(db, sync, notes, context);
        backfillHistoricalNotes(db, sync, notes, deckIdsByNote, deckNamesByNote);
        overlayDashboardRows(aggregates, currentDashboardRows(db));
        insertHistoricalKanjiAggregates(db, sync.id, sync.finishedAt, aggregates);
    }

    void backfillHistoricalCards(
            SQLiteDatabase db,
            HistoricalSyncRun sync,
            Map<Long, HistoricalNoteSnapshot> notes,
            HistoricalBackfillContext context
    ) {
        Cursor cards = db.query(TABLE_SOURCE_CARDS, null, null, null, null, null, "card_id ASC");
        try {
            while (cards.moveToNext()) {
                HistoricalNoteSnapshot note = notes.get(longValue(cards, COLUMN_NOTE_ID));
                if (note == null) {
                    continue;
                }
                backfillHistoricalCard(db, cards, sync, note, context);
            }
        } finally {
            cards.close();
        }
    }

    void backfillHistoricalCard(
            SQLiteDatabase db,
            Cursor cards,
            HistoricalSyncRun sync,
            HistoricalNoteSnapshot note,
            HistoricalBackfillContext context
    ) {
        String deck = string(cards, COLUMN_DECK_NAME);
        linkedSetFor(context.deckIdsByNote(), note.noteId).add(deck);
        linkedSetFor(context.deckNamesByNote(), note.noteId).add(deck);
        int intervalDays = integer(cards, COLUMN_INTERVAL_DAYS);
        int reps = integer(cards, COLUMN_REPS);
        int lapses = integer(cards, COLUMN_LAPSES);
        boolean mature = intervalDays >= context.settings().matureDays;
        db.insertWithOnConflict(
                TABLE_SYNC_CARD_SNAPSHOTS,
                null,
                historicalCardValues(cards, sync, note, deck, new HistoricalCardMetrics(intervalDays, reps, lapses, mature)),
                SQLiteDatabase.CONFLICT_REPLACE
        );
        for (String kanji : TextUtil.extractKanji(note.expression + " " + note.sentence)) {
            aggregateFor(context.aggregates(), kanji).addCard(
                    intervalDays,
                    reps,
                    lapses,
                    false,
                    mature,
                    nullableDouble(cards, COLUMN_FSRS_STABILITY),
                    nullableDouble(cards, COLUMN_FSRS_DIFFICULTY),
                    nullableDouble(cards, COLUMN_FSRS_RETRIEVABILITY)
            );
        }
    }

    ContentValues historicalCardValues(
            Cursor cards,
            HistoricalSyncRun sync,
            HistoricalNoteSnapshot note,
            String deck,
            HistoricalCardMetrics metrics
    ) {
        ContentValues cardValues = new ContentValues();
        cardValues.put(COLUMN_SYNC_ID, sync.id);
        cardValues.put(COLUMN_STARTED_AT, sync.startedAt);
        cardValues.put(COLUMN_FINISHED_AT, sync.finishedAt);
        cardValues.put(COLUMN_CARD_ID, longValue(cards, COLUMN_CARD_ID));
        cardValues.put(COLUMN_NOTE_ID, note.noteId);
        cardValues.put(COLUMN_DECK_ID, deck);
        cardValues.put(COLUMN_DECK_NAME, deck);
        cardValues.put(COLUMN_MODEL_ID, note.modelId);
        cardValues.put(COLUMN_MODEL_NAME, note.modelName);
        cardValues.put("ord", integer(cards, "ord"));
        cardValues.put(COLUMN_QUEUE, integer(cards, COLUMN_QUEUE));
        cardValues.put("type", integer(cards, "type"));
        cardValues.put("due", integer(cards, "due"));
        cardValues.put(COLUMN_INTERVAL_DAYS, metrics.intervalDays());
        cardValues.put(COLUMN_REPS, metrics.reps());
        cardValues.put(COLUMN_LAPSES, metrics.lapses());
        cardValues.put("suspended", 0);
        putNullableDouble(cardValues, COLUMN_FSRS_STABILITY, nullableDouble(cards, COLUMN_FSRS_STABILITY));
        putNullableDouble(cardValues, COLUMN_FSRS_DIFFICULTY, nullableDouble(cards, COLUMN_FSRS_DIFFICULTY));
        putNullableDouble(cardValues, COLUMN_FSRS_RETRIEVABILITY, nullableDouble(cards, COLUMN_FSRS_RETRIEVABILITY));
        cardValues.put(COLUMN_MATURE, metrics.mature() ? 1 : 0);
        return cardValues;
    }

    void backfillHistoricalNotes(
            SQLiteDatabase db,
            HistoricalSyncRun sync,
            Map<Long, HistoricalNoteSnapshot> notes,
            Map<Long, LinkedHashSet<String>> deckIdsByNote,
            Map<Long, LinkedHashSet<String>> deckNamesByNote
    ) {
        for (HistoricalNoteSnapshot note : notes.values()) {
            LinkedHashSet<String> deckIds = deckIdsByNote.get(note.noteId);
            LinkedHashSet<String> decks = deckNamesByNote.get(note.noteId);
            if (decks == null || decks.isEmpty()) {
                continue;
            }
            ContentValues noteValues = new ContentValues();
            noteValues.put(COLUMN_SYNC_ID, sync.id);
            noteValues.put(COLUMN_FINISHED_AT, sync.finishedAt);
            noteValues.put(COLUMN_NOTE_ID, note.noteId);
            noteValues.put(COLUMN_MODEL_ID, note.modelId);
            noteValues.put(COLUMN_MODEL_NAME, note.modelName);
            noteValues.put(COLUMN_DECK_IDS, deckIds == null ? "" : String.join(" ", deckIds));
            noteValues.put(COLUMN_DECK_NAMES, String.join(" ", decks));
            noteValues.put(COLUMN_EXPRESSION, note.expression);
            noteValues.put(COLUMN_READING, note.reading);
            noteValues.put(COLUMN_MEANING, note.meaning);
            noteValues.put(COLUMN_SENTENCE, note.sentence);
            noteValues.put(COLUMN_TAGS, note.tags);
            noteValues.put(COLUMN_FIELDS_JSON, note.fieldsJson);
            noteValues.put("extracted_kanji", String.join("", TextUtil.extractKanji(note.expression + " " + note.sentence)));
            db.insertWithOnConflict(TABLE_SYNC_NOTE_SNAPSHOTS, null, noteValues, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    Map<Long, LinkedHashSet<String>> deckNamesByNote(List<RecordsSyncModels.Card> cards) {
        Map<Long, LinkedHashSet<String>> out = new LinkedHashMap<>();
        for (RecordsSyncModels.Card card : cards) {
            linkedSetFor(out, card.noteId).add(card.deckName);
        }
        return out;
    }

    Map<Long, LinkedHashSet<String>> deckIdsByNote(List<RecordsSyncModels.Card> cards) {
        Map<Long, LinkedHashSet<String>> out = new LinkedHashMap<>();
        for (RecordsSyncModels.Card card : cards) {
            linkedSetFor(out, card.noteId).add(card.deckId);
        }
        return out;
    }

    LinkedHashSet<String> linkedSetFor(Map<Long, LinkedHashSet<String>> map, long key) {
        LinkedHashSet<String> values = map.get(key);
        if (values == null) {
            values = new LinkedHashSet<>();
            map.put(key, values);
        }
        return values;
    }

    List<String> extractedKanji(RecordsSyncModels.Note note, RecordsSyncModels.Settings settings) {
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
        return TextUtil.extractKanji(expression + " " + sentence);
    }

    HistoricalKanjiAggregate aggregateFor(Map<String, HistoricalKanjiAggregate> aggregates, String kanji) {
        HistoricalKanjiAggregate aggregate = aggregates.get(kanji);
        if (aggregate == null) {
            aggregate = new HistoricalKanjiAggregate(kanji);
            aggregates.put(kanji, aggregate);
        }
        return aggregate;
    }

    void overlayDashboardRows(Map<String, HistoricalKanjiAggregate> aggregates, List<RecordsImportModels.DashboardRow> rows) {
        for (RecordsImportModels.DashboardRow row : rows) {
            HistoricalKanjiAggregate aggregate = aggregateFor(aggregates, row.kanji);
            aggregate.mergeDashboardEvidence(
                    row.weaknessScore,
                    row.reasonCode,
                    row.activeExampleCount,
                    row.suspendedExampleCount,
                    row.matureSupportCount
            );
        }
    }

    void insertHistoricalKanjiAggregates(SQLiteDatabase db, long syncId, long finishedAt, Map<String, HistoricalKanjiAggregate> aggregates) {
        for (HistoricalKanjiAggregate aggregate : aggregates.values()) {
            if (aggregate.kanji().isEmpty()) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_SYNC_ID, syncId);
            values.put(COLUMN_FINISHED_AT, finishedAt);
            values.put(COLUMN_KANJI, aggregate.kanji());
            values.put("active_cards", aggregate.activeCards());
            values.put("suspended_cards", aggregate.suspendedCards());
            values.put(COLUMN_MATURE_SUPPORT_COUNT, aggregate.matureSupportCount());
            values.put("average_interval_days", aggregate.averageIntervalDays());
            values.put("total_lapses", aggregate.totalLapses());
            values.put("total_reps", aggregate.totalReps());
            putNullableDouble(values, "fsrs_stability_avg", aggregate.averageStability());
            putNullableDouble(values, "fsrs_difficulty_avg", aggregate.averageDifficulty());
            putNullableDouble(values, "fsrs_retrievability_avg", aggregate.averageRetrievability());
            values.put(COLUMN_WEAKNESS_SCORE, aggregate.weaknessScore());
            values.put(COLUMN_REASON_CODE, aggregate.reasonCode());
            values.put(COLUMN_ACTIVE_EXAMPLE_COUNT, aggregate.activeExampleCount());
            values.put(COLUMN_SUSPENDED_EXAMPLE_COUNT, aggregate.suspendedExampleCount());
            db.insertWithOnConflict(TABLE_SYNC_KANJI_SNAPSHOTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    boolean tableHasRows(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + table + " LIMIT 1", null);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    HistoricalSyncRun latestSuccessfulSyncRun(SQLiteDatabase db) {
        Cursor cursor = db.query(
                TABLE_SYNC_RUNS,
                new String[]{"id", COLUMN_STARTED_AT, COLUMN_FINISHED_AT},
                "status=?",
                new String[]{STATUS_SUCCESS},
                null,
                null,
                ORDER_ID_DESC,
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalSyncRun(
                    longValue(cursor, "id"),
                    longValue(cursor, COLUMN_STARTED_AT),
                    longValue(cursor, COLUMN_FINISHED_AT)
            );
        } finally {
            cursor.close();
        }
    }

    Map<Long, HistoricalNoteSnapshot> currentSourceNotes(SQLiteDatabase db) {
        Map<Long, HistoricalNoteSnapshot> notes = new LinkedHashMap<>();
        Cursor cursor = db.query(TABLE_SOURCE_NOTES, null, null, null, null, null, "note_id ASC");
        try {
            while (cursor.moveToNext()) {
                long noteId = longValue(cursor, COLUMN_NOTE_ID);
                notes.put(noteId, new HistoricalNoteSnapshot(new HistoricalNoteFields(
                        noteId,
                        0L,
                        string(cursor, COLUMN_MODEL_NAME),
                        string(cursor, COLUMN_EXPRESSION),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_MEANING),
                        string(cursor, COLUMN_SENTENCE),
                        string(cursor, COLUMN_TAGS),
                        string(cursor, COLUMN_FIELDS_JSON)
                )));
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    List<RecordsImportModels.DashboardRow> currentDashboardRows(SQLiteDatabase db) {
        List<RecordsImportModels.DashboardRow> rows = new ArrayList<>();
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, new String[]{COLUMN_KANJI}, null, null, null, null, ORDER_KANJI_ASC);
        try {
            while (cursor.moveToNext()) {
                RecordsImportModels.DashboardRow row = localStore.readDashboardRow(db, string(cursor, COLUMN_KANJI));
                if (row != null) {
                    rows.add(row);
                }
            }
        } finally {
            cursor.close();
        }
        return rows;
    }
}
