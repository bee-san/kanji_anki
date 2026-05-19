package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract class LocalStoreSync extends LocalStoreInventory {
    LocalStoreSync(Context context) {
        super(context);
    }

    private LocalStoreSyncRunStore syncRunStore() {
        return new LocalStoreSyncRunStore(this);
    }

    private LocalStoreSyncImportAuditStore importAuditStore() {
        return new LocalStoreSyncImportAuditStore();
    }

    private LocalStoreSyncSourceStore sourceStore() {
        return new LocalStoreSyncSourceStore();
    }

    private LocalStoreSyncSuspendedImportStore suspendedImportStore() {
        return new LocalStoreSyncSuspendedImportStore(this);
    }

    public long saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot snapshot,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            RecordsSyncModels.Settings settings,
            long startedAt,
            long finishedAt,
            String removalMessage
    ) {
        return saveSuccessfulSync(snapshot, imports, rows, settings, new SyncTiming(startedAt, finishedAt), removalMessage, null);
    }

    public long saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot snapshot,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            RecordsSyncModels.Settings settings,
            SyncTiming timing,
            String removalMessage,
            SimilarKanjiIndex similarIndex
    ) {
        return saveSuccessfulSync(snapshot, imports, rows, settings, timing, removalMessage, similarIndex, imports);
    }

    public long saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot snapshot,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            RecordsSyncModels.Settings settings,
            SyncTiming timing,
            String removalMessage,
            SimilarKanjiIndex similarIndex,
            List<RecordsImportModels.SuspendedImport> auditImports
    ) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<RecordsImportModels.SuspendedImport> decisionImports = auditImports == null ? imports : auditImports;
            Map<String, RowSnapshot> previousRows = rowSnapshots(db);
            ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
            Set<Long> selectedSuspendedCardIds = selectedSuspendedCardIds(imports);
            int deletedNotes = countDeletedExisting(db, TABLE_SOURCE_NOTES, COLUMN_NOTE_ID, activeIndex.noteIds);
            int deletedCards = countDeletedExisting(db, TABLE_SOURCE_CARDS, COLUMN_CARD_ID, activeIndex.cardIds);
            long syncId = syncRunStore().insertSyncRun(db, new SyncRunInsert(
                    timing.startedAt,
                    timing.finishedAt,
                    STATUS_SUCCESS,
                    activeIndex,
                    selectedSuspendedCardIds.size(),
                    imports.size(),
                    null,
                    null,
                    removalMessage == null ? "" : removalMessage,
                    deletedNotes,
                    deletedCards
            ));
            Map<Long, RecordsSyncModels.Note> notesById = snapshot.notesById();
            appendHistoricalSyncSnapshots(db, snapshot, notesById, rows, settings, syncId, timing);
            clearSyncMirrorTables(db);
            LocalStoreSyncSourceStore sourceStoreHelper = sourceStore();
            sourceStoreHelper.saveSourceNotes(db, snapshot.notes, activeIndex, settings, syncId);
            sourceStoreHelper.saveSourceCardsAndArchive(db, snapshot.cards, notesById, selectedSuspendedCardIds, settings, timing.finishedAt, syncId);
            suspendedImportStore().saveSuspendedImports(db, imports, timing.finishedAt, syncId);
            saveImportAudit(db, decisionImports, settings, timing.finishedAt, syncId);

            saveRows(db, rows, timing.finishedAt);
            rebuildKanjiInventory(db, snapshot, imports, rows, timing.finishedAt, settings);
            if (similarIndex != null) {
                rebuildSimilarKanjiPairs(db, similarIndex, timing.finishedAt);
            }
            rebuildSimilarKanjiChoiceStates(db, timing.finishedAt);
            appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, timing.finishedAt, settings);
            db.setTransactionSuccessful();
            return syncId;
        } finally {
            db.endTransaction();
        }
    }

    void saveImportAudit(
            SQLiteDatabase db,
            List<RecordsImportModels.SuspendedImport> imports,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        importAuditStore().saveImportAudit(db, imports, settings, finishedAt, syncId);
    }

    void clearSyncMirrorTables(SQLiteDatabase db) {
        db.delete(TABLE_SOURCE_CARDS, null, null);
        db.delete(TABLE_SOURCE_NOTES, null, null);
        db.delete(TABLE_DASHBOARD_ROWS, null, null);
        db.delete(TABLE_KANJI_EXAMPLES, null, null);
    }

    public void saveFailedSync(long startedAt, long finishedAt, String status, String errorCode, String errorMessage) {
        syncRunStore().saveFailedSync(startedAt, finishedAt, status, errorCode, errorMessage);
    }

    public void updateSyncRemovalMessage(long syncId, String message) {
        syncRunStore().updateSyncRemovalMessage(syncId, message);
    }
}
