package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels

internal class LocalStoreInventoryMaintenance(
    private val activity: LocalStoreHistory,
) {
    fun backfillKanjiInventory(
        db: SQLiteDatabase,
        nowMillis: Long,
        settings: RecordsSyncModels.Settings,
    ) {
        rebuildKanjiInventory(
            db,
            null,
            activity.suspendedImportsFromDb(db),
            activity.dashboardRowsFromDb(db),
            nowMillis,
            settings,
        )
    }

    fun rebuildKanjiInventory(
        db: SQLiteDatabase,
        snapshot: RecordsSyncModels.CollectionSnapshot?,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        settings: RecordsSyncModels.Settings,
    ) {
        val inventory = KanjiInventoryBuilder(nowMillis, settings)
        addSnapshotInventory(inventory, snapshot)
        addImportedInventory(inventory, imports)
        addDashboardInventory(inventory, rows)
        addKnownKanji(inventory, db, LocalStoreBase.TABLE_STUDY_ITEMS)
        addKnownKanji(inventory, db, LocalStoreBase.TABLE_REVIEW_LOG)
        addKnownKanji(inventory, db, LocalStoreBase.TABLE_KANJI_TIMELINE_EVENTS)
        writeKanjiInventory(db, inventory)
    }

    fun addSnapshotInventory(
        inventory: KanjiInventoryBuilder,
        snapshot: RecordsSyncModels.CollectionSnapshot?,
    ) {
        if (snapshot == null) {
            return
        }
        val activeIndex = LocalStoreSyncMirrorAdapters.activeCardIndex(snapshot.cards)
        for (note in snapshot.notes) {
            if (activeIndex.noteIds.contains(note.noteId)) {
                inventory.addSnapshotNote(note)
            }
        }
    }

    fun addImportedInventory(
        inventory: KanjiInventoryBuilder,
        imports: List<RecordsImportModels.SuspendedImport>,
    ) {
        for (imported in imports) {
            inventory.addSuspendedImport(imported)
        }
    }

    fun addDashboardInventory(
        inventory: KanjiInventoryBuilder,
        rows: List<RecordsImportModels.DashboardRow>,
    ) {
        for (row in rows) {
            inventory.addDashboardRow(row)
        }
    }

    fun addKnownKanji(inventory: KanjiInventoryBuilder, db: SQLiteDatabase, table: String) {
        db.query(
            true,
            table,
            arrayOf(LocalStoreBase.COLUMN_KANJI),
            null,
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                inventory.addKnownKanji(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI))
            }
        }
    }

    fun writeKanjiInventory(db: SQLiteDatabase, inventory: KanjiInventoryBuilder) {
        for (item in inventory.build(activity.previousInventoryItems(db))) {
            val values = ContentValues()
            values.put(LocalStoreBase.COLUMN_KANJI, item.kanji())
            values.put(LocalStoreBase.COLUMN_PRIMARY_MEANING, item.primaryMeaning())
            values.put("readings", item.readings())
            values.put(LocalStoreBase.COLUMN_BROWSER_SEARCH, item.browserSearch())
            values.put("search_text", item.searchText())
            values.put("source_count", item.sourceCount())
            values.put("example_count", item.exampleCount())
            values.put(LocalStoreBase.COLUMN_FIRST_SEEN_AT, item.firstSeenAtMillis())
            values.put(LocalStoreBase.COLUMN_LAST_SEEN_AT, item.lastSeenAtMillis())
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_KANJI_INVENTORY,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        activity.clearKanjiInventoryAllCache()
    }

    fun saveRows(
        db: SQLiteDatabase,
        rows: List<RecordsImportModels.DashboardRow>,
        rebuiltAt: Long,
    ) {
        for (row in rows) {
            val values = ContentValues()
            values.put(LocalStoreBase.COLUMN_KANJI, row.kanji)
            if (row.jitenRank != null) {
                values.put(LocalStoreBase.COLUMN_JITEN_RANK, row.jitenRank)
            }
            values.put(LocalStoreBase.COLUMN_PRIMARY_MEANING, row.primaryMeaning)
            values.put(LocalStoreBase.COLUMN_READING, row.reading)
            values.put(LocalStoreBase.COLUMN_BROWSER_SEARCH, row.browserSearch)
            values.put(LocalStoreBase.COLUMN_WEAKNESS_SCORE, row.weaknessScore)
            values.put(LocalStoreBase.COLUMN_REASON_CODE, row.reasonCode)
            values.put(LocalStoreBase.COLUMN_REASON_TEXT, row.reasonText)
            values.put(LocalStoreBase.COLUMN_ACTIVE_EXAMPLE_COUNT, row.activeExampleCount)
            values.put(LocalStoreBase.COLUMN_SUSPENDED_EXAMPLE_COUNT, row.suspendedExampleCount)
            values.put(LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT, row.matureSupportCount)
            values.put("rebuilt_at", rebuiltAt)
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_DASHBOARD_ROWS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            for (example in row.examples) {
                val ex = ContentValues()
                ex.put(LocalStoreBase.COLUMN_KANJI, row.kanji)
                ex.put("source_type", example.sourceType)
                ex.put(LocalStoreBase.COLUMN_CARD_ID, example.cardId)
                ex.put(LocalStoreBase.COLUMN_NOTE_ID, example.noteId)
                ex.put(LocalStoreBase.COLUMN_EXPRESSION, example.expression)
                ex.put(LocalStoreBase.COLUMN_READING, example.reading)
                ex.put(LocalStoreBase.COLUMN_MEANING, example.meaning)
                ex.put(LocalStoreBase.COLUMN_SENTENCE, example.sentence)
                ex.put(LocalStoreBase.COLUMN_MATURE, if (example.mature) 1 else 0)
                ex.put(LocalStoreBase.COLUMN_LAPSES, example.lapses)
                ex.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, example.intervalDays)
                ex.put(LocalStoreBase.COLUMN_REPS, example.reps)
                LocalStoreBase.putNullableDouble(ex, LocalStoreBase.COLUMN_FSRS_STABILITY, example.fsrsStability)
                LocalStoreBase.putNullableDouble(ex, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, example.fsrsDifficulty)
                LocalStoreBase.putNullableDouble(ex, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY, example.fsrsRetrievability)
                db.insert(LocalStoreBase.TABLE_KANJI_EXAMPLES, null, ex)
            }
        }
    }
}
