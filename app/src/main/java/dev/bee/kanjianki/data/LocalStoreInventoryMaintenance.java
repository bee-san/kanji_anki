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
}
