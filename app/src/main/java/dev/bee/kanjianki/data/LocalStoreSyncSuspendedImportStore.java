package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;

import java.util.List;

final class LocalStoreSyncSuspendedImportStore {
    private final LocalStoreSync store;

    LocalStoreSyncSuspendedImportStore(LocalStoreSync store) {
        this.store = store;
    }

    void saveSuspendedImports(
            SQLiteDatabase db,
            List<RecordsImportModels.SuspendedImport> imports,
            long finishedAt,
            long syncId
    ) {
        for (RecordsImportModels.SuspendedImport imported : imports) {
            saveSuspendedImport(db, imported, finishedAt, syncId);
        }
    }

    void saveSuspendedImport(
            SQLiteDatabase db,
            RecordsImportModels.SuspendedImport imported,
            long finishedAt,
            long syncId
    ) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_KANJI, imported.kanji);
        if (imported.jitenRank != null) {
            values.put(LocalStoreBase.COLUMN_JITEN_RANK, imported.jitenRank);
        }
        values.put(LocalStoreBase.COLUMN_RANK_KNOWN, imported.rankKnown ? 1 : 0);
        values.put(LocalStoreBase.COLUMN_CUTOFF_USED, imported.cutoffUsed);
        values.put(LocalStoreBase.COLUMN_FIRST_IMPORTED_AT, store.firstImportedAt(db, imported.kanji, finishedAt));
        values.put(LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID, syncId);
        db.insertWithOnConflict(LocalStoreBase.TABLE_SUSPENDED_IMPORTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        for (RecordsImportModels.SuspendedSource source : imported.sources) {
            ContentValues sourceValues = new ContentValues();
            sourceValues.put(LocalStoreBase.COLUMN_KANJI, imported.kanji);
            sourceValues.put(LocalStoreBase.COLUMN_CARD_ID, source.cardId);
            sourceValues.put(LocalStoreBase.COLUMN_NOTE_ID, source.noteId);
            sourceValues.put(LocalStoreBase.COLUMN_EXPRESSION, source.expression);
            sourceValues.put(LocalStoreBase.COLUMN_READING, source.reading);
            sourceValues.put(LocalStoreBase.COLUMN_MEANING, source.meaning);
            sourceValues.put(LocalStoreBase.COLUMN_SENTENCE, source.sentence);
            sourceValues.put(LocalStoreBase.COLUMN_SYNC_ID, syncId);
            db.insertWithOnConflict(LocalStoreBase.TABLE_SUSPENDED_SOURCES, null, sourceValues, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
}
