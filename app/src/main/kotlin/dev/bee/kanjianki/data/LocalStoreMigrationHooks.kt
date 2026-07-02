package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsSyncModels

internal class LocalStoreMigrationHooks(
    private val store: LocalStoreBase,
) {
    fun createTimelineTables(db: SQLiteDatabase) {
        store.createTimelineTables(db)
    }

    fun backfillTimelineEvents(db: SQLiteDatabase) {
        store.backfillTimelineEvents(db)
    }

    fun addNullableColumn(db: SQLiteDatabase, table: String, column: String, type: String) {
        store.addNullableColumn(db, table, column, type)
    }

    fun backfillKanjiInventory(db: SQLiteDatabase, nowMillis: Long, settings: RecordsSyncModels.Settings) {
        store.backfillKanjiInventory(db, nowMillis, settings)
    }

    fun rebuildSimilarKanjiChoiceStates(db: SQLiteDatabase, nowMillis: Long) {
        store.rebuildSimilarKanjiChoiceStates(db, nowMillis)
    }

    fun backfillLatestHistoricalSync(db: SQLiteDatabase) {
        store.backfillLatestHistoricalSync(db)
    }

    fun rebuildStudyItemsWithAnswerSignatureKey(db: SQLiteDatabase) {
        store.rebuildStudyItemsWithAnswerSignatureKey(db)
    }

    fun rebuildStudyItemsForLadderScheduler(db: SQLiteDatabase) {
        store.rebuildStudyItemsForLadderScheduler(db)
    }

    fun clearStaleSuppressionFlags(db: SQLiteDatabase) {
        store.clearStaleSuppressionFlags(db)
    }

    fun createStudyTaskLogTable(db: SQLiteDatabase) {
        store.createStudyTaskLogTable(db)
    }

    fun createStatsIndexes(db: SQLiteDatabase) {
        store.createStatsIndexes(db)
    }

    fun createStatsCacheTables(db: SQLiteDatabase) {
        store.createStatsCacheTables(db)
    }

    fun ensureStatsAggregateStorage(db: SQLiteDatabase) {
        store.ensureStatsAggregateStorage(db)
    }

    fun repairHistoricalSyncSnapshotsIfPossible(db: SQLiteDatabase) {
        store.repairHistoricalSyncSnapshotsIfPossible(db)
    }

    fun createKanjiInventoryTables(db: SQLiteDatabase) {
        store.createKanjiInventoryTables(db)
    }

    fun createSimilarKanjiTables(db: SQLiteDatabase) {
        store.createSimilarKanjiTables(db)
    }

    fun createSimilarKanjiPracticeTables(db: SQLiteDatabase) {
        store.createSimilarKanjiPracticeTables(db)
    }

    fun createHistoricalSyncTables(db: SQLiteDatabase) {
        store.createHistoricalSyncTables(db)
    }

    fun createImportAuditTables(db: SQLiteDatabase) {
        store.createImportAuditTables(db)
    }

    fun addRichReviewColumns(db: SQLiteDatabase) {
        store.addRichReviewColumns(db)
    }

    fun addHistoricalIdentityColumns(db: SQLiteDatabase) {
        store.addHistoricalIdentityColumns(db)
    }
}
