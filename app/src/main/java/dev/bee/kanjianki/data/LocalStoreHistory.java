package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.HistoricalKanjiAggregate;
import dev.bee.kanjianki.core.KanjiInventoryBuilder;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.SimilarChoiceCodec;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.SimilarKanjiRepairPolicy;
import dev.bee.kanjianki.core.SimilarKanjiStorageKeys;
import dev.bee.kanjianki.core.TextUtil;
import dev.bee.kanjianki.core.TimelineCopy;
import dev.bee.kanjianki.syncdomain.SyncMirrorPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class LocalStoreHistory extends LocalStoreBase {
    private final HistoricalSyncStore historicalSyncStore;

    LocalStoreHistory(Context context) {
        super(context);
        historicalSyncStore = new HistoricalSyncStore(this);
    }

    private LocalStoreTimeline timeline() {
        return new LocalStoreTimeline(this);
    }

    private LocalStoreInventoryMaintenance inventoryMaintenance() {
        return new LocalStoreInventoryMaintenance(this);
    }

    private LocalStoreSimilarKanjiMaintenance similarKanjiMaintenance() {
        return new LocalStoreSimilarKanjiMaintenance(this);
    }

    private LocalStoreSimilarKanjiData similarKanjiData() {
        return new LocalStoreSimilarKanjiData(this);
    }

    private LocalStoreInventoryData inventoryData() {
        return new LocalStoreInventoryData(this);
    }

    void createTimelineTables(SQLiteDatabase db) {
        timeline().createTimelineTables(db);
    }

    void addNullableColumn(SQLiteDatabase db, String table, String column, String type) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (RuntimeException error) {
            if (error.getMessage() == null || !error.getMessage().contains("duplicate column")) {
                throw error;
            }
        }
    }

    void backfillTimelineEvents(SQLiteDatabase db) {
        timeline().backfillTimelineEvents(db);
    }

    long defaultTimelineTime(long occurredAt) {
        return timeline().defaultTimelineTime(occurredAt);
    }

    void appendSyncTimelineEvents(
            SQLiteDatabase db,
            Map<String, RowSnapshot> previousRows,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            long syncId,
            long occurredAt,
            RecordsSyncModels.Settings settings
    ) {
        timeline().appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, occurredAt, settings);
    }

    void appendStudyStateTimelineEvents(
            SQLiteDatabase db,
            Map<String, StudySnapshot> previousItems,
            List<RecordsStudyModels.StudyItem> currentItems,
            long syncId,
            long occurredAt,
            RecordsSyncModels.Settings settings
    ) {
        timeline().appendStudyStateTimelineEvents(db, previousItems, currentItems, syncId, occurredAt, settings);
    }

    void appendStudyStateTimelineEvent(
            SQLiteDatabase db,
            RecordsStudyModels.StudyItem item,
            StudySnapshot previous,
            long syncId,
            long occurredAt,
            int target
    ) {
        timeline().appendStudyStateTimelineEvent(db, item, previous, syncId, occurredAt, target);
    }

    void appendReviewTimelineEvent(SQLiteDatabase db, RecordsSchedulerModels.ReviewRequest request, String appliedRating, long reviewedAt, String dedupeKey) {
        timeline().appendReviewTimelineEvent(db, request, appliedRating, reviewedAt, dedupeKey);
    }

    void backfillKanjiInventory(SQLiteDatabase db, long nowMillis, RecordsSyncModels.Settings settings) {
        inventoryMaintenance().backfillKanjiInventory(db, nowMillis, settings);
    }

    List<RecordsImportModels.DashboardRow> dashboardRowsFromDb(SQLiteDatabase db) {
        List<RecordsImportModels.DashboardRow> rows = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, ORDER_KANJI_ASC)) {
            while (cursor.moveToNext()) {
                rows.add(readDashboardRow(db, cursor));
            }
        }
        return rows;
    }

    List<RecordsImportModels.SuspendedImport> suspendedImportsFromDb(SQLiteDatabase db) {
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        try (Cursor cursor = db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "jiten_rank ASC, kanji ASC")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        integer(cursor, COLUMN_RANK_KNOWN) == 1,
                        integer(cursor, COLUMN_CUTOFF_USED)
                ));
            }
        }
        try (Cursor sources = db.query(TABLE_SUSPENDED_SOURCES, null, null, null, null, null, "kanji ASC, card_id ASC")) {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, COLUMN_KANJI));
                if (imported != null) {
                    imported.sources.add(new RecordsImportModels.SuspendedSource(
                            imported.kanji,
                            longValue(sources, COLUMN_CARD_ID),
                            longValue(sources, COLUMN_NOTE_ID),
                            string(sources, COLUMN_EXPRESSION),
                            string(sources, COLUMN_READING),
                            string(sources, COLUMN_MEANING),
                            string(sources, COLUMN_SENTENCE)
                    ));
                }
            }
        }
        List<RecordsImportModels.SuspendedImport> out = new ArrayList<>();
        for (MutableSuspendedImport imported : imports.values()) {
            out.add(imported.build());
        }
        return out;
    }

    void rebuildKanjiInventory(
            SQLiteDatabase db,
            RecordsSyncModels.CollectionSnapshot snapshot,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        inventoryMaintenance().rebuildKanjiInventory(db, snapshot, imports, rows, nowMillis, settings);
    }

    void addSnapshotInventory(
            KanjiInventoryBuilder inventory,
            RecordsSyncModels.CollectionSnapshot snapshot,
            RecordsSyncModels.Settings settings
    ) {
        if (snapshot == null) {
            return;
        }
        ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
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
        try (Cursor cursor = db.query(true, table, new String[]{COLUMN_KANJI}, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                inventory.addKnownKanji(string(cursor, COLUMN_KANJI));
            }
        }
    }

    void writeKanjiInventory(SQLiteDatabase db, KanjiInventoryBuilder inventory) {
        for (KanjiInventoryBuilder.BuiltItem item : inventory.build(previousInventoryItems(db))) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI, item.kanji());
            values.put(COLUMN_PRIMARY_MEANING, item.primaryMeaning());
            values.put("readings", item.readings());
            values.put(COLUMN_BROWSER_SEARCH, item.browserSearch());
            values.put("search_text", item.searchText());
            values.put("source_count", item.sourceCount());
            values.put("example_count", item.exampleCount());
            values.put(COLUMN_FIRST_SEEN_AT, item.firstSeenAtMillis());
            values.put(COLUMN_LAST_SEEN_AT, item.lastSeenAtMillis());
            db.insertWithOnConflict(TABLE_KANJI_INVENTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void rebuildSimilarKanjiPairs(SQLiteDatabase db, SimilarKanjiIndex similarIndex, long nowMillis) {
        similarKanjiMaintenance().rebuildSimilarKanjiPairs(db, similarIndex, nowMillis);
    }

    void rebuildSimilarKanjiChoiceStates(SQLiteDatabase db, long nowMillis) {
        similarKanjiMaintenance().rebuildSimilarKanjiChoiceStates(db, nowMillis);
    }

    void upsertSimilarKanjiChoiceState(
            SQLiteDatabase db,
            RecordsImportModels.SimilarKanjiChoiceCard card,
            SimilarChoiceSnapshot old,
            long nowMillis
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_TARGET_KANJI, card.targetKanji);
        values.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
        values.put(COLUMN_PRIMARY_MEANING, card.primaryMeaning);
        values.put(COLUMN_CHOICES, serializeChoices(card.choices));
        values.put(COLUMN_DUE_AT, old == null ? 0L : old.dueAtMillis);
        values.put(COLUMN_PASSED_AT, old == null ? 0L : old.passedAtMillis);
        values.put(COLUMN_LAST_REVIEWED_AT, old == null ? 0L : old.lastReviewedAtMillis);
        values.put(COLUMN_CORRECT_COUNT, old == null ? 0 : old.correctCount);
        values.put(COLUMN_WRONG_COUNT, old == null ? 0 : old.wrongCount);
        values.put(COLUMN_ACTIVE_TOKEN, "");
        values.put(COLUMN_FIRST_SEEN_AT, old == null ? nowMillis : old.firstSeenAtMillis);
        values.put(COLUMN_LAST_SEEN_AT, nowMillis);
        db.insertWithOnConflict(TABLE_SIMILAR_KANJI_CHOICE_STATE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteStaleSimilarChoiceStates(SQLiteDatabase db, Set<String> previousKeys, Set<String> currentKeys) {
        for (String key : previousKeys) {
            String[] parts = SimilarKanjiStorageKeys.splitChoiceKey(key);
            if (!currentKeys.contains(key) && parts.length == 2) {
                db.delete(
                        TABLE_SIMILAR_KANJI_CHOICE_STATE,
                        WHERE_SIMILAR_CHOICE,
                        new String[]{parts[0], parts[1]}
                );
                db.delete(
                        TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                        "status=? AND target_kanji=? AND choice_signature=?",
                        new String[]{STATUS_PENDING, parts[0], parts[1]}
                );
            }
        }
    }

    Map<String, Long> similarPairFirstSeen(SQLiteDatabase db) {
        return similarKanjiData().similarPairFirstSeen(db);
    }

    Set<String> localInventoryKanji(SQLiteDatabase db) {
        return similarKanjiData().localInventoryKanji(db);
    }

    RecordsImportModels.SimilarKanjiPair readSimilarPair(Cursor cursor) {
        return similarKanjiData().readSimilarPair(cursor);
    }

    List<RecordsImportModels.SimilarKanjiPair> allSimilarPairs(SQLiteDatabase db) {
        return similarKanjiData().allSimilarPairs(db);
    }

    List<RecordsImportModels.KanjiInventoryItem> allInventoryItems(SQLiteDatabase db) {
        return similarKanjiData().allInventoryItems(db);
    }

    Map<String, SimilarChoiceSnapshot> similarChoiceSnapshots(SQLiteDatabase db) {
        return similarKanjiData().similarChoiceSnapshots(db);
    }

    RecordsImportModels.SimilarKanjiChoiceCard similarChoiceCard(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                WHERE_SIMILAR_CHOICE,
                new String[]{targetKanji, choiceSignature},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? readSimilarChoiceCard(cursor) : null;
        }
    }

    RecordsImportModels.SimilarKanjiChoiceCard readSimilarChoiceCard(Cursor cursor) {
        return similarKanjiData().readSimilarChoiceCard(cursor);
    }

    boolean hasPendingSimilarRepairs(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        return similarKanjiData().hasPendingSimilarRepairs(db, targetKanji, choiceSignature);
    }

    void enqueueSimilarWritingRepair(
            SQLiteDatabase db,
            RecordsImportModels.SimilarKanjiChoiceCard card,
            String repairKanji,
            String wrongSelection,
            long nowMillis
    ) {
        similarKanjiData().enqueueSimilarWritingRepair(db, card, repairKanji, wrongSelection, nowMillis);
    }

    RecordsImportModels.SimilarKanjiWritingRepair similarWritingRepair(SQLiteDatabase db, long repairId) {
        return similarKanjiData().similarWritingRepair(db, repairId);
    }

    RecordsImportModels.SimilarKanjiWritingRepair readSimilarWritingRepair(Cursor cursor) {
        return similarKanjiData().readSimilarWritingRepair(cursor);
    }

    static String serializeChoices(List<String> choices) {
        return SimilarChoiceCodec.serializeChoices(choices);
    }

    static List<String> deserializeChoices(String encoded) {
        return SimilarChoiceCodec.deserializeChoices(encoded);
    }

    RecordsImportModels.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, String kanji) {
        return inventoryData().readInventoryItem(db, kanji);
    }

    RecordsImportModels.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, Cursor cursor) {
        return inventoryData().readInventoryItem(db, cursor);
    }

    boolean isKanjiSuspended(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    Map<String, KanjiInventoryBuilder.PreviousItem> previousInventoryItems(SQLiteDatabase db) {
        Map<String, KanjiInventoryBuilder.PreviousItem> previous = new LinkedHashMap<>();
        try (Cursor cursor = db.query(TABLE_KANJI_INVENTORY, null, null, null, null, null, ORDER_KANJI_ASC)) {
            while (cursor.moveToNext()) {
                previous.put(
                        string(cursor, COLUMN_KANJI),
                        new KanjiInventoryBuilder.PreviousItem(
                                string(cursor, COLUMN_PRIMARY_MEANING),
                                string(cursor, "readings"),
                                string(cursor, COLUMN_BROWSER_SEARCH),
                                integer(cursor, "source_count"),
                                integer(cursor, "example_count"),
                                longValue(cursor, COLUMN_FIRST_SEEN_AT),
                                longValue(cursor, COLUMN_LAST_SEEN_AT)
                        )
                );
            }
        }
        return previous;
    }

    static String normalizeSingleKanji(String value) {
        return TextUtil.normalizeSingleKanji(value);
    }

    static String[] canonicalSimilarPair(String first, String second) {
        return SimilarKanjiStorageKeys.canonicalPair(first, second);
    }

    static String similarKey(String first, String second, String source) {
        return SimilarKanjiStorageKeys.pairKey(first, second, source);
    }

    static String similarChoiceKey(String targetKanji, String choiceSignature) {
        return SimilarKanjiStorageKeys.choiceKey(targetKanji, choiceSignature);
    }

    RecordsImportModels.DashboardRow readDashboardRow(SQLiteDatabase db, String kanji) {
        return inventoryData().readDashboardRow(db, kanji);
    }

    RecordsImportModels.DashboardRow readDashboardRow(SQLiteDatabase db, Cursor cursor) {
        return inventoryData().readDashboardRow(db, cursor);
    }

    RecordsStudyModels.StudyItem studyItemForKanji(SQLiteDatabase db, String kanji) {
        return inventoryData().studyItemForKanji(db, kanji);
    }

    boolean kanjiHasSimilarNeighbor(SQLiteDatabase db, String kanji) {
        return inventoryData().kanjiHasSimilarNeighbor(db, kanji);
    }

    RecordsImportModels.KanjiTimelineEvent readTimelineEvent(Cursor cursor) {
        return timeline().readTimelineEvent(cursor);
    }

    void insertTimelineEvent(
            SQLiteDatabase db,
            String kanji,
            long occurredAt,
            String eventType,
            String title,
            String detail,
            Object... eventValues
    ) {
        timeline().insertTimelineEvent(db, kanji, occurredAt, eventType, title, detail, eventValues);
    }

    static String stringValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof String value ? value : "";
    }

    static boolean booleanValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof Boolean value && value;
    }

    static Integer integerValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof Integer value ? value : null;
    }

    static Long longValueAt(Object[] values, int index) {
        return values.length > index && values[index] instanceof Long value ? value : null;
    }

    Map<String, RowSnapshot> rowSnapshots(SQLiteDatabase db) {
        Map<String, RowSnapshot> rows = new LinkedHashMap<>();
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, ORDER_KANJI_ASC);
        try {
            while (cursor.moveToNext()) {
                RowSnapshot row = rowSnapshotFromCursor(db, cursor);
                rows.put(row.kanji, row);
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    RowSnapshot rowSnapshot(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? rowSnapshotFromCursor(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    RowSnapshot rowSnapshotFromCursor(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, COLUMN_KANJI);
        return new RowSnapshot(
                kanji,
                integer(cursor, COLUMN_WEAKNESS_SCORE),
                integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                longValue(cursor, "rebuilt_at"),
                firstExampleForKanji(db, kanji)
        );
    }

    Map<String, StudySnapshot> studySnapshots(SQLiteDatabase db) {
        Map<String, StudySnapshot> items = new HashMap<>();
        Cursor cursor = db.query(TABLE_STUDY_ITEMS, new String[]{COLUMN_KANJI, COLUMN_ANSWER_SIGNATURE, COLUMN_STATE}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                String answerSignature = string(cursor, COLUMN_ANSWER_SIGNATURE);
                items.put(studyFamilyKey(kanji, answerSignature), new StudySnapshot(string(cursor, COLUMN_STATE)));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    SourceSnapshot firstExampleForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_KANJI_EXAMPLES, new String[]{COLUMN_EXPRESSION, COLUMN_READING}, WHERE_KANJI, new String[]{kanji}, null, null, "source_type ASC, id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, COLUMN_EXPRESSION), string(cursor, COLUMN_READING));
        } finally {
            cursor.close();
        }
    }

    SourceSnapshot firstSuspendedSourceForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_SUSPENDED_SOURCES, new String[]{COLUMN_EXPRESSION, COLUMN_READING}, WHERE_KANJI, new String[]{kanji}, null, null, "card_id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, COLUMN_EXPRESSION), string(cursor, COLUMN_READING));
        } finally {
            cursor.close();
        }
    }

    SourceSnapshot sourceFromImport(RecordsImportModels.SuspendedImport imported) {
        if (imported.sources.isEmpty()) {
            return SourceSnapshot.EMPTY;
        }
        RecordsImportModels.SuspendedSource source = imported.sources.get(0);
        return new SourceSnapshot(source.expression, source.reading);
    }

    SourceSnapshot sourceForRow(RecordsImportModels.DashboardRow row) {
        RecordsImportModels.Example fallback = null;
        for (RecordsImportModels.Example example : row.examples) {
            if ("active".equals(example.sourceType)) {
                return new SourceSnapshot(example.expression, example.reading);
            }
            if (fallback == null) {
                fallback = example;
            }
        }
        return fallback == null ? SourceSnapshot.EMPTY : new SourceSnapshot(fallback.expression, fallback.reading);
    }

    void saveRows(SQLiteDatabase db, List<RecordsImportModels.DashboardRow> rows, long rebuiltAt) {
        for (RecordsImportModels.DashboardRow row : rows) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI, row.kanji);
            if (row.jitenRank != null) {
                values.put(COLUMN_JITEN_RANK, row.jitenRank);
            }
            values.put(COLUMN_PRIMARY_MEANING, row.primaryMeaning);
            values.put(COLUMN_READING, row.reading);
            values.put(COLUMN_BROWSER_SEARCH, row.browserSearch);
            values.put(COLUMN_WEAKNESS_SCORE, row.weaknessScore);
            values.put(COLUMN_REASON_CODE, row.reasonCode);
            values.put(COLUMN_REASON_TEXT, row.reasonText);
            values.put(COLUMN_ACTIVE_EXAMPLE_COUNT, row.activeExampleCount);
            values.put(COLUMN_SUSPENDED_EXAMPLE_COUNT, row.suspendedExampleCount);
            values.put(COLUMN_MATURE_SUPPORT_COUNT, row.matureSupportCount);
            values.put("rebuilt_at", rebuiltAt);
            db.insertWithOnConflict(TABLE_DASHBOARD_ROWS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            for (RecordsImportModels.Example example : row.examples) {
                ContentValues ex = new ContentValues();
                ex.put(COLUMN_KANJI, row.kanji);
                ex.put("source_type", example.sourceType);
                ex.put(COLUMN_CARD_ID, example.cardId);
                ex.put(COLUMN_NOTE_ID, example.noteId);
                ex.put(COLUMN_EXPRESSION, example.expression);
                ex.put(COLUMN_READING, example.reading);
                ex.put(COLUMN_MEANING, example.meaning);
                ex.put(COLUMN_SENTENCE, example.sentence);
                ex.put(COLUMN_MATURE, example.mature ? 1 : 0);
                ex.put(COLUMN_LAPSES, example.lapses);
                ex.put(COLUMN_INTERVAL_DAYS, example.intervalDays);
                ex.put(COLUMN_REPS, example.reps);
                putNullableDouble(ex, COLUMN_FSRS_STABILITY, example.fsrsStability);
                putNullableDouble(ex, COLUMN_FSRS_DIFFICULTY, example.fsrsDifficulty);
                putNullableDouble(ex, COLUMN_FSRS_RETRIEVABILITY, example.fsrsRetrievability);
                db.insert(TABLE_KANJI_EXAMPLES, null, ex);
            }
        }
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
        historicalSyncStore.appendHistoricalSyncSnapshots(db, snapshot, notesById, rows, settings, syncId, timing);
    }

    void backfillLatestHistoricalSync(SQLiteDatabase db) {
        historicalSyncStore.backfillLatestHistoricalSync(db);
    }

    void insertHistoricalKanjiAggregates(SQLiteDatabase db, long syncId, long finishedAt, Map<String, HistoricalKanjiAggregate> aggregates) {
        historicalSyncStore.insertHistoricalKanjiAggregates(db, syncId, finishedAt, aggregates);
    }

    List<RecordsImportModels.Example> examplesForKanji(SQLiteDatabase db, String kanji) {
        List<RecordsImportModels.Example> examples = new ArrayList<>();
        Cursor cursor = db.query(TABLE_KANJI_EXAMPLES, null, WHERE_KANJI, new String[]{kanji}, null, null, "source_type DESC, id ASC", "8");
        try {
            while (cursor.moveToNext()) {
                examples.add(new RecordsImportModels.Example(
                        string(cursor, "source_type"),
                        longValue(cursor, COLUMN_CARD_ID),
                        longValue(cursor, COLUMN_NOTE_ID),
                        string(cursor, COLUMN_EXPRESSION),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_MEANING),
                        string(cursor, COLUMN_SENTENCE),
                        integer(cursor, COLUMN_MATURE) == 1,
                        integer(cursor, COLUMN_LAPSES),
                        integer(cursor, COLUMN_INTERVAL_DAYS),
                        integer(cursor, COLUMN_REPS),
                        nullableDouble(cursor, COLUMN_FSRS_STABILITY),
                        nullableDouble(cursor, COLUMN_FSRS_DIFFICULTY),
                        nullableDouble(cursor, COLUMN_FSRS_RETRIEVABILITY)
                ));
            }
        } finally {
            cursor.close();
        }
        return examples;
    }

    void upsertStudyItem(SQLiteDatabase db, RecordsStudyModels.StudyItem item) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, item.kanji);
        values.put(COLUMN_STATE, item.state);
        values.put(COLUMN_DUE_AT, item.dueAtMillis);
        values.put("stability", item.stability);
        values.put("difficulty", item.difficulty);
        values.put("total_reviews", item.totalReviews);
        values.put(COLUMN_LAPSES, item.lapses);
        values.put("learning_step", item.learningStep);
        values.put("writing_level", item.writingLevel);
        values.put(COLUMN_RECOGNITION_STAGE, item.recognitionStage);
        values.put(COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS, item.consecutiveFailedRecognitionDays);
        values.put(COLUMN_LAST_FAILED_RECOGNITION_DAY, item.lastFailedRecognitionDayMillis);
        values.put(COLUMN_WRITING_REMEDIATION_PENDING, item.writingRemediationPending ? 1 : 0);
        values.put(COLUMN_SUPPRESSED_BY_TASK_TYPE, item.suppressedByTaskType);
        values.put(COLUMN_SUPPRESSED_AT, item.suppressedAtMillis);
        values.put(COLUMN_MATURE_INTERVAL_DAYS, item.matureIntervalDays);
        values.put(COLUMN_ANSWER_SIGNATURE, item.answerSignature);
        values.put(COLUMN_TYPING_MEANING_MEMORY, item.typingMeaningMemory.encode());
        values.put(COLUMN_MEANING_KANJI_MEMORY, item.meaningKanjiMemory.encode());
        values.put(COLUMN_KANJI_MEANING_MEMORY, item.kanjiMeaningMemory.encode());
        values.put(COLUMN_FONT_MEANING_MEMORY, item.fontMeaningMemory.encode());
        values.put(COLUMN_WORD_READING_MEMORY, item.wordReadingMemory.encode());
        values.put(COLUMN_WRITING_REMEDIATION_MEMORY, item.writingRemediationMemory.encode());
        values.put(COLUMN_RUNG, item.rung == null ? RecordsBase.LadderRung.KANJI_MEANING.wireName() : item.rung.wireName());
        values.put(COLUMN_PHASE, item.phase == null ? RecordsBase.SchedulerPhase.NEW_LEARNING.wireName() : item.phase.wireName());
        values.put(COLUMN_REAL_PASS_STREAK, item.realPassStreak);
        values.put(COLUMN_REAL_AGAIN_STREAK, item.realAgainStreak);
        values.put(COLUMN_LAST_REAL_REVIEW_DUE_AT, item.lastRealReviewDueAtMillis);
        values.put(COLUMN_SIMILAR_KANJI_MEMORY, item.similarKanjiMemory == null ? "" : item.similarKanjiMemory.encode());
        values.put(COLUMN_ACTIVE_TOKEN, item.activeToken);
        values.put(COLUMN_CREATED_AT, item.createdAtMillis);
        db.insertWithOnConflict(TABLE_STUDY_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    RecordsStudyModels.StudyItem readStudyItem(Cursor cursor) {
        String state = string(cursor, COLUMN_STATE);
        long dueAt = longValue(cursor, COLUMN_DUE_AT);
        double stability = cursor.getDouble(cursor.getColumnIndexOrThrow("stability"));
        double difficulty = cursor.getDouble(cursor.getColumnIndexOrThrow("difficulty"));
        int totalReviews = integer(cursor, "total_reviews");
        int lapses = integer(cursor, COLUMN_LAPSES);
        int learningStep = integer(cursor, "learning_step");
        int recognitionStage = integer(cursor, COLUMN_RECOGNITION_STAGE);
        boolean writingRemediationPending = integer(cursor, COLUMN_WRITING_REMEDIATION_PENDING) == 1;
        int matureIntervalDays = integer(cursor, COLUMN_MATURE_INTERVAL_DAYS);
        StudyMemoryFields memoryFields = new StudyMemoryFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        RecordsStudyModels.TaskMemory typingFallback = taskMemoryFallback(-1, recognitionStage, memoryFields);
        RecordsStudyModels.TaskMemory kanjiFallback = taskMemoryFallback(0, recognitionStage, memoryFields);
        RecordsStudyModels.TaskMemory fontFallback = taskMemoryFallback(1, recognitionStage, memoryFields);
        RecordsStudyModels.TaskMemory wordFallback = taskMemoryFallback(2, recognitionStage, memoryFields);
        RecordsStudyModels.TaskMemory writingFallback = writingRemediationPending
                ? RecordsStudyModels.TaskMemory.fromStudyFields(state, dueAt, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays)
                : RecordsStudyModels.TaskMemory.initial();
        RecordsBase.LadderRung rung = RecordsBase.LadderRung.fromWireName(string(cursor, COLUMN_RUNG));
        RecordsBase.SchedulerPhase phase = RecordsBase.SchedulerPhase.fromWireName(string(cursor, COLUMN_PHASE));
        int realPassStreak = integer(cursor, COLUMN_REAL_PASS_STREAK);
        int realAgainStreak = integer(cursor, COLUMN_REAL_AGAIN_STREAK);
        long lastRealReviewDueAtMillis = longValue(cursor, COLUMN_LAST_REAL_REVIEW_DUE_AT);
        RecordsStudyModels.TaskMemory similarKanjiMemory = RecordsStudyModels.TaskMemory.decode(
                string(cursor, COLUMN_SIMILAR_KANJI_MEMORY),
                RecordsStudyModels.TaskMemory.initial()
        );
        return new RecordsStudyModels.StudyItem(
                string(cursor, COLUMN_KANJI),
                state,
                dueAt,
                stability,
                difficulty,
                totalReviews,
                lapses,
                learningStep,
                integer(cursor, "writing_level"),
                recognitionStage,
                integer(cursor, COLUMN_CONSECUTIVE_FAILED_RECOGNITION_DAYS),
                longValue(cursor, COLUMN_LAST_FAILED_RECOGNITION_DAY),
                writingRemediationPending,
                string(cursor, COLUMN_SUPPRESSED_BY_TASK_TYPE),
                longValue(cursor, COLUMN_SUPPRESSED_AT),
                matureIntervalDays,
                string(cursor, COLUMN_ANSWER_SIGNATURE),
                string(cursor, COLUMN_ACTIVE_TOKEN),
                longValue(cursor, COLUMN_CREATED_AT),
                RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_TYPING_MEANING_MEMORY), typingFallback),
                RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_MEANING_KANJI_MEMORY), RecordsStudyModels.TaskMemory.initial()),
                RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_KANJI_MEANING_MEMORY), kanjiFallback),
                RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_FONT_MEANING_MEMORY), fontFallback),
                RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_WORD_READING_MEMORY), wordFallback),
                RecordsStudyModels.TaskMemory.decode(string(cursor, COLUMN_WRITING_REMEDIATION_MEMORY), writingFallback),
                rung,
                phase,
                realPassStreak,
                realAgainStreak,
                lastRealReviewDueAtMillis,
                false,
                similarKanjiMemory
        );
    }

    RecordsSchedulerModels.LearningRepeat readLearningRepeat(Cursor cursor) {
        return new RecordsSchedulerModels.LearningRepeat(
                string(cursor, COLUMN_KANJI),
                string(cursor, COLUMN_ANSWER_SIGNATURE),
                string(cursor, COLUMN_TASK_TYPE),
                string(cursor, "repeat_type"),
                integer(cursor, "step_index"),
                longValue(cursor, COLUMN_DUE_AT),
                string(cursor, COLUMN_ACTIVE_TOKEN),
                longValue(cursor, COLUMN_CREATED_AT),
                longValue(cursor, COLUMN_UPDATED_AT)
        );
    }

    RecordsStudyModels.TaskMemory taskMemoryFallback(
            int memoryStage,
            int recognitionStage,
            StudyMemoryFields fields
    ) {
        if (Math.max(-1, Math.min(2, recognitionStage)) == memoryStage) {
            return RecordsStudyModels.TaskMemory.fromStudyFields(
                    fields.state(),
                    fields.dueAtMillis(),
                    fields.stability(),
                    fields.difficulty(),
                    fields.totalReviews(),
                    fields.lapses(),
                    fields.learningStep(),
                    fields.matureIntervalDays()
            );
        }
        return RecordsStudyModels.TaskMemory.initial();
    }

    long firstImportedAt(SQLiteDatabase db, String kanji, long fallback) {
        Cursor cursor = db.query(TABLE_SUSPENDED_IMPORTS, new String[]{COLUMN_FIRST_IMPORTED_AT}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? longValue(cursor, COLUMN_FIRST_IMPORTED_AT) : fallback;
        } finally {
            cursor.close();
        }
    }

    Set<Long> selectedSuspendedCardIds(List<RecordsImportModels.SuspendedImport> imports) {
        List<SyncMirrorPolicy.SelectedSource> sources = new ArrayList<>();
        for (RecordsImportModels.SuspendedImport imported : imports) {
            for (RecordsImportModels.SuspendedSource source : imported.sources) {
                sources.add(new SyncMirrorPolicy.SelectedSource(source.cardId, source.suspended));
            }
        }
        return SyncMirrorPolicy.selectedSuspendedCardIds(sources);
    }

    ActiveCardIndex activeCardIndex(List<RecordsSyncModels.Card> cards) {
        List<SyncMirrorPolicy.Card> policyCards = new ArrayList<>();
        for (RecordsSyncModels.Card card : cards) {
            policyCards.add(new SyncMirrorPolicy.Card(card.cardId, card.noteId, card.suspended));
        }
        SyncMirrorPolicy.ActiveCardIndex index = SyncMirrorPolicy.activeCardIndex(policyCards);
        return new ActiveCardIndex(index.noteIds(), index.cardIds(), index.activeCardCount());
    }

    int countDeletedExisting(SQLiteDatabase db, String table, String idColumn, Set<Long> currentIds) {
        int missing = 0;
        Cursor cursor = db.query(table, new String[]{idColumn}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                if (!currentIds.contains(cursor.getLong(0))) {
                    missing++;
                }
            }
        } finally {
            cursor.close();
        }
        return missing;
    }

}
