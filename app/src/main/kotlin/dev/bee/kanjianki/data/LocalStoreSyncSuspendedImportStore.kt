package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsImportModels

internal class LocalStoreSyncSuspendedImportStore(
    private val store: LocalStoreSync,
) {
    fun saveSuspendedImports(
        db: SQLiteDatabase,
        imports: List<RecordsImportModels.SuspendedImport>,
        finishedAt: Long,
        syncId: Long,
    ) {
        for (imported in imports) {
            saveSuspendedImport(db, imported, finishedAt, syncId)
        }
    }

    fun saveSuspendedImport(
        db: SQLiteDatabase,
        imported: RecordsImportModels.SuspendedImport,
        finishedAt: Long,
        syncId: Long,
    ) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_KANJI, imported.kanji)
        imported.jitenRank?.let { values.put(LocalStoreBase.COLUMN_JITEN_RANK, it) }
        values.put(LocalStoreBase.COLUMN_RANK_KNOWN, if (imported.rankKnown) 1 else 0)
        values.put(LocalStoreBase.COLUMN_CUTOFF_USED, imported.cutoffUsed)
        values.put(LocalStoreBase.COLUMN_FIRST_IMPORTED_AT, store.firstImportedAt(db, imported.kanji, finishedAt))
        values.put(LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID, syncId)
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_SUSPENDED_IMPORTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        for (source in imported.sources) {
            val sourceValues = ContentValues()
            sourceValues.put(LocalStoreBase.COLUMN_KANJI, imported.kanji)
            sourceValues.put(LocalStoreBase.COLUMN_CARD_ID, source.cardId)
            sourceValues.put(LocalStoreBase.COLUMN_NOTE_ID, source.noteId)
            sourceValues.put(LocalStoreBase.COLUMN_EXPRESSION, source.expression)
            sourceValues.put(LocalStoreBase.COLUMN_READING, source.reading)
            sourceValues.put(LocalStoreBase.COLUMN_MEANING, source.meaning)
            sourceValues.put(LocalStoreBase.COLUMN_SENTENCE, source.sentence)
            sourceValues.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SUSPENDED_SOURCES,
                null,
                sourceValues,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }
}
