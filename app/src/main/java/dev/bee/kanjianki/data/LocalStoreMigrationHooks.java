package dev.bee.kanjianki.data;

import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.Records;

final class LocalStoreMigrationHooks {
    private final LocalStoreBase store;

    LocalStoreMigrationHooks(LocalStoreBase store) {
        this.store = store;
    }

    void createTimelineTables(SQLiteDatabase db) {
        store.createTimelineTables(db);
    }

    void backfillTimelineEvents(SQLiteDatabase db) {
        store.backfillTimelineEvents(db);
    }

    void addNullableColumn(SQLiteDatabase db, String table, String column, String type) {
        store.addNullableColumn(db, table, column, type);
    }

    void backfillKanjiInventory(SQLiteDatabase db, long nowMillis, Records.Settings settings) {
        store.backfillKanjiInventory(db, nowMillis, settings);
    }

    void rebuildSimilarKanjiChoiceStates(SQLiteDatabase db, long nowMillis) {
        store.rebuildSimilarKanjiChoiceStates(db, nowMillis);
    }

    void backfillLatestHistoricalSync(SQLiteDatabase db) {
        store.backfillLatestHistoricalSync(db);
    }

    void rebuildStudyItemsWithAnswerSignatureKey(SQLiteDatabase db) {
        store.rebuildStudyItemsWithAnswerSignatureKey(db);
    }

    void rebuildStudyItemsForLadderScheduler(SQLiteDatabase db) {
        store.rebuildStudyItemsForLadderScheduler(db);
    }

    void createStudyTaskLogTable(SQLiteDatabase db) {
        store.createStudyTaskLogTable(db);
    }

    void createStatsIndexes(SQLiteDatabase db) {
        store.createStatsIndexes(db);
    }

    void ensureStatsAggregateStorage(SQLiteDatabase db) {
        store.ensureStatsAggregateStorage(db);
    }

    void repairHistoricalSyncSnapshotsIfPossible(SQLiteDatabase db) {
        store.repairHistoricalSyncSnapshotsIfPossible(db);
    }

    void createKanjiInventoryTables(SQLiteDatabase db) {
        store.createKanjiInventoryTables(db);
    }

    void createSimilarKanjiTables(SQLiteDatabase db) {
        store.createSimilarKanjiTables(db);
    }

    void createSimilarKanjiPracticeTables(SQLiteDatabase db) {
        store.createSimilarKanjiPracticeTables(db);
    }

    void createHistoricalSyncTables(SQLiteDatabase db) {
        store.createHistoricalSyncTables(db);
    }

    void addRichReviewColumns(SQLiteDatabase db) {
        store.addRichReviewColumns(db);
    }

    void addHistoricalIdentityColumns(SQLiteDatabase db) {
        store.addHistoricalIdentityColumns(db);
    }
}
