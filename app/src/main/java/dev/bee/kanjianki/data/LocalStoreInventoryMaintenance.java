package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.KanjiInventoryBuilder;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import java.util.List;

final class LocalStoreInventoryMaintenance {
    private final LocalStoreHistory activity;

    LocalStoreInventoryMaintenance(LocalStoreHistory activity) {
        this.activity = activity;
    }

    void backfillKanjiInventory(SQLiteDatabase db, long nowMillis, RecordsSyncModels.Settings settings) {
        rebuildKanjiInventory(db, null, activity.suspendedImportsFromDb(db), activity.dashboardRowsFromDb(db), nowMillis, settings);
    }

    void rebuildKanjiInventory(
            SQLiteDatabase db,
            RecordsSyncModels.CollectionSnapshot snapshot,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        KanjiInventoryBuilder inventory = new KanjiInventoryBuilder(nowMillis, settings);
        addSnapshotInventory(inventory, snapshot);
        addImportedInventory(inventory, imports);
        addDashboardInventory(inventory, rows);
        addKnownKanji(inventory, db, LocalStoreBase.TABLE_STUDY_ITEMS);
        addKnownKanji(inventory, db, LocalStoreBase.TABLE_REVIEW_LOG);
        addKnownKanji(inventory, db, LocalStoreBase.TABLE_KANJI_TIMELINE_EVENTS);
        writeKanjiInventory(db, inventory);
    }

    void addSnapshotInventory(KanjiInventoryBuilder inventory, RecordsSyncModels.CollectionSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        LocalStoreHistory.ActiveCardIndex activeIndex = activity.activeCardIndex(snapshot.cards);
        for (RecordsSyncModels.Note note : snapshot.notes) {
            if (activeIndex.noteIds.contains(note.noteId)) {
                inventory.addSnapshotNote(note);
            }
        }
    }

    void addImportedInventory(KanjiInventoryBuilder inventory, List<RecordsImportModels.SuspendedImport> imports) {
        for (RecordsImportModels.SuspendedImport imported : imports) {
            inventory.addSuspendedImport(imported);
        }
    }

    void addDashboardInventory(KanjiInventoryBuilder inventory, List<RecordsImportModels.DashboardRow> rows) {
        for (RecordsImportModels.DashboardRow row : rows) {
            inventory.addDashboardRow(row);
        }
    }

    void addKnownKanji(KanjiInventoryBuilder inventory, SQLiteDatabase db, String table) {
        try (Cursor cursor = db.query(true, table, new String[]{LocalStoreBase.COLUMN_KANJI}, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                inventory.addKnownKanji(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI));
            }
        }
    }

    void writeKanjiInventory(SQLiteDatabase db, KanjiInventoryBuilder inventory) {
        for (KanjiInventoryBuilder.BuiltItem item : inventory.build(activity.previousInventoryItems(db))) {
            ContentValues values = new ContentValues();
            values.put(LocalStoreBase.COLUMN_KANJI, item.kanji());
            values.put(LocalStoreBase.COLUMN_PRIMARY_MEANING, item.primaryMeaning());
            values.put("readings", item.readings());
            values.put(LocalStoreBase.COLUMN_BROWSER_SEARCH, item.browserSearch());
            values.put("search_text", item.searchText());
            values.put("source_count", item.sourceCount());
            values.put("example_count", item.exampleCount());
            values.put(LocalStoreBase.COLUMN_FIRST_SEEN_AT, item.firstSeenAtMillis());
            values.put(LocalStoreBase.COLUMN_LAST_SEEN_AT, item.lastSeenAtMillis());
            db.insertWithOnConflict(LocalStoreBase.TABLE_KANJI_INVENTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void saveRows(SQLiteDatabase db, List<RecordsImportModels.DashboardRow> rows, long rebuiltAt) {
        for (RecordsImportModels.DashboardRow row : rows) {
            ContentValues values = new ContentValues();
            values.put(LocalStoreBase.COLUMN_KANJI, row.kanji);
            if (row.jitenRank != null) {
                values.put(LocalStoreBase.COLUMN_JITEN_RANK, row.jitenRank);
            }
            values.put(LocalStoreBase.COLUMN_PRIMARY_MEANING, row.primaryMeaning);
            values.put(LocalStoreBase.COLUMN_READING, row.reading);
            values.put(LocalStoreBase.COLUMN_BROWSER_SEARCH, row.browserSearch);
            values.put(LocalStoreBase.COLUMN_WEAKNESS_SCORE, row.weaknessScore);
            values.put(LocalStoreBase.COLUMN_REASON_CODE, row.reasonCode);
            values.put(LocalStoreBase.COLUMN_REASON_TEXT, row.reasonText);
            values.put(LocalStoreBase.COLUMN_ACTIVE_EXAMPLE_COUNT, row.activeExampleCount);
            values.put(LocalStoreBase.COLUMN_SUSPENDED_EXAMPLE_COUNT, row.suspendedExampleCount);
            values.put(LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT, row.matureSupportCount);
            values.put("rebuilt_at", rebuiltAt);
            db.insertWithOnConflict(LocalStoreBase.TABLE_DASHBOARD_ROWS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            for (RecordsImportModels.Example example : row.examples) {
                ContentValues ex = new ContentValues();
                ex.put(LocalStoreBase.COLUMN_KANJI, row.kanji);
                ex.put("source_type", example.sourceType);
                ex.put(LocalStoreBase.COLUMN_CARD_ID, example.cardId);
                ex.put(LocalStoreBase.COLUMN_NOTE_ID, example.noteId);
                ex.put(LocalStoreBase.COLUMN_EXPRESSION, example.expression);
                ex.put(LocalStoreBase.COLUMN_READING, example.reading);
                ex.put(LocalStoreBase.COLUMN_MEANING, example.meaning);
                ex.put(LocalStoreBase.COLUMN_SENTENCE, example.sentence);
                ex.put(LocalStoreBase.COLUMN_MATURE, example.mature ? 1 : 0);
                ex.put(LocalStoreBase.COLUMN_LAPSES, example.lapses);
                ex.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, example.intervalDays);
                ex.put(LocalStoreBase.COLUMN_REPS, example.reps);
                LocalStoreBase.putNullableDouble(ex, LocalStoreBase.COLUMN_FSRS_STABILITY, example.fsrsStability);
                LocalStoreBase.putNullableDouble(ex, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, example.fsrsDifficulty);
                LocalStoreBase.putNullableDouble(ex, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY, example.fsrsRetrievability);
                db.insert(LocalStoreBase.TABLE_KANJI_EXAMPLES, null, ex);
            }
        }
    }
}
