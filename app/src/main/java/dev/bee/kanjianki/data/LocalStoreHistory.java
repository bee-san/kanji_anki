package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract class LocalStoreHistory extends LocalStoreBase {
    private final HistoricalSyncStore historicalSyncStore;

    LocalStoreHistory(Context context) {
        super(context);
        historicalSyncStore = new HistoricalSyncStore(this);
    }

    void createTimelineTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kanji_timeline_events (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, occurred_at INTEGER NOT NULL, event_type TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, source_expression TEXT NOT NULL, source_reading TEXT NOT NULL, rating TEXT NOT NULL, writing_required INTEGER NOT NULL DEFAULT 0, writing_passed INTEGER NOT NULL DEFAULT 0, manual_override INTEGER NOT NULL DEFAULT 0, weakness_score INTEGER, mature_support_count INTEGER, sync_id INTEGER, dedupe_key TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_timeline_dedupe ON kanji_timeline_events(dedupe_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timeline_kanji_time ON kanji_timeline_events(kanji, occurred_at, id)");
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
        Map<String, RowSnapshot> rows = rowSnapshots(db);
        backfillSuspendedImportTimeline(db);
        backfillRowTimeline(db, rows);
        backfillStudyTimeline(db, rows);
        backfillReviewTimeline(db);
    }

    void backfillSuspendedImportTimeline(SQLiteDatabase db) {
        Cursor imports = db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "first_imported_at ASC, kanji ASC");
        try {
            while (imports.moveToNext()) {
                String kanji = string(imports, COLUMN_KANJI);
                SourceSnapshot source = firstSuspendedSourceForKanji(db, kanji);
                long importedAt = longValue(imports, COLUMN_FIRST_IMPORTED_AT);
                insertTimelineEvent(
                        db,
                        kanji,
                        importedAt == 0L ? System.currentTimeMillis() : importedAt,
                        "suspended_imported",
                        "Imported from suspended Anki",
                        "Kani recovered this kanji from a suspended AnkiDroid card.",
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        null,
                        null,
                        longValue(imports, COLUMN_LAST_SEEN_SYNC_ID),
                        "suspended_imported:" + kanji
                );
            }
        } finally {
            imports.close();
        }
    }

    void backfillRowTimeline(SQLiteDatabase db, Map<String, RowSnapshot> rows) {
        for (RowSnapshot row : rows.values()) {
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    TIMELINE_FIRST_SEEN,
                    TIMELINE_FIRST_SEEN_TITLE,
                    "This kanji entered Kani from local AnkiDroid evidence.",
                    row.source.expression,
                    row.source.reading,
                    "",
                    false,
                    false,
                    false,
                    row.weaknessScore,
                    row.matureSupportCount,
                    null,
                    TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
            );
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    "weak_support_seen",
                    "Weak support seen",
                    supportDetail("Anki evidence still needs repair", row.matureSupportCount, RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold),
                    row.source.expression,
                    row.source.reading,
                    "",
                    false,
                    false,
                    false,
                    row.weaknessScore,
                    row.matureSupportCount,
                    null,
                    "weak_support_seen:" + row.kanji + ":backfill"
            );
        }
    }

    void backfillStudyTimeline(SQLiteDatabase db, Map<String, RowSnapshot> rows) {
        Cursor study = db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "created_at ASC, kanji ASC");
        try {
            while (study.moveToNext()) {
                backfillStudyTimelineRow(db, rows, study);
            }
        } finally {
            study.close();
        }
    }

    void backfillStudyTimelineRow(SQLiteDatabase db, Map<String, RowSnapshot> rows, Cursor study) {
        String kanji = string(study, COLUMN_KANJI);
        long occurredAt = defaultTimelineTime(longValue(study, COLUMN_CREATED_AT));
        RowSnapshot row = rows.get(kanji);
        SourceSnapshot source = row == null ? firstExampleForKanji(db, kanji) : row.source;
        if (row == null) {
            insertTimelineEvent(
                    db,
                    kanji,
                    occurredAt,
                    TIMELINE_FIRST_SEEN,
                    TIMELINE_FIRST_SEEN_TITLE,
                    "This kanji has historical Kani study state.",
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    TIMELINE_FIRST_SEEN_KEY_PREFIX + kanji
            );
        }
        if (STATE_RETIRED.equals(string(study, COLUMN_STATE))) {
            Integer mature = row == null ? null : row.matureSupportCount;
            insertTimelineEvent(
                    db,
                    kanji,
                    occurredAt,
                    STATE_RETIRED,
                    "Retired by Anki support",
                    mature == null
                            ? "Kani had already retired this repair before timeline tracking was added."
                            : supportDetail("Mature Anki support met the target", mature, RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold),
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    row == null ? null : row.weaknessScore,
                    mature,
                    null,
                    "retired:" + kanji + ":backfill"
            );
        }
    }

    void backfillReviewTimeline(SQLiteDatabase db) {
        Cursor reviews = db.query(TABLE_REVIEW_LOG, null, null, null, null, null, "reviewed_at ASC, id ASC");
        try {
            while (reviews.moveToNext()) {
                RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest(
                        string(reviews, COLUMN_KANJI),
                        string(reviews, COLUMN_TOKEN),
                        string(reviews, COLUMN_RATING),
                        integer(reviews, COLUMN_WRITING_REQUIRED) == 1,
                        integer(reviews, COLUMN_WRITING_PASSED) == 1,
                        integer(reviews, COLUMN_MANUAL_OVERRIDE) == 1,
                        0
                );
                appendReviewTimelineEvent(db, request, string(reviews, COLUMN_RATING), longValue(reviews, COLUMN_REVIEWED_AT), "review:" + request.token);
            }
        } finally {
            reviews.close();
        }
    }

    long defaultTimelineTime(long occurredAt) {
        return occurredAt == 0L ? System.currentTimeMillis() : occurredAt;
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
        int target = settings == null ? RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (RecordsImportModels.SuspendedImport imported : imports) {
            SourceSnapshot source = sourceFromImport(imported);
            insertTimelineEvent(
                    db,
                    imported.kanji,
                    occurredAt,
                    "suspended_imported",
                    "Imported from suspended Anki",
                    "Kani recovered this kanji from a suspended AnkiDroid card.",
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    null,
                    null,
                    syncId,
                    "suspended_imported:" + imported.kanji
            );
        }

        for (RecordsImportModels.DashboardRow row : rows) {
            RowSnapshot previous = previousRows.get(row.kanji);
            SourceSnapshot source = sourceForRow(row);
            insertTimelineEvent(
                    db,
                    row.kanji,
                    occurredAt,
                    TIMELINE_FIRST_SEEN,
                    TIMELINE_FIRST_SEEN_TITLE,
                    "This kanji entered Kani from local AnkiDroid evidence.",
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    row.weaknessScore,
                    row.matureSupportCount,
                    syncId,
                    TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
            );
            if (previous == null) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "weak_support_seen",
                        "Weak support seen",
                        supportDetail("Anki evidence still needs repair", row.matureSupportCount, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "weak_support_seen:" + row.kanji + ":" + syncId
                );
            } else if (row.matureSupportCount > previous.matureSupportCount) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "support_improved",
                        "Anki support improved",
                        "Mature support rose from " + previous.matureSupportCount + " to " + row.matureSupportCount + ".",
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "support_improved:" + row.kanji + ":" + syncId + ":" + previous.matureSupportCount + "-" + row.matureSupportCount
                );
            } else if (row.matureSupportCount < previous.matureSupportCount) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "support_dropped",
                        "Anki support dropped",
                        "Mature support fell from " + previous.matureSupportCount + " to " + row.matureSupportCount + ".",
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "support_dropped:" + row.kanji + ":" + syncId + ":" + previous.matureSupportCount + "-" + row.matureSupportCount
                );
            }
        }
    }

    void appendStudyStateTimelineEvents(
            SQLiteDatabase db,
            Map<String, StudySnapshot> previousItems,
            List<RecordsStudyModels.StudyItem> currentItems,
            long syncId,
            long occurredAt,
            RecordsSyncModels.Settings settings
    ) {
        int target = settings == null ? RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (RecordsStudyModels.StudyItem item : currentItems) {
            StudySnapshot previous = previousItems.get(studyFamilyKey(item.kanji, item.answerSignature));
            if (previous != null) {
                appendStudyStateTimelineEvent(db, item, previous, syncId, occurredAt, target);
            }
        }
    }

    void appendStudyStateTimelineEvent(
            SQLiteDatabase db,
            RecordsStudyModels.StudyItem item,
            StudySnapshot previous,
            long syncId,
            long occurredAt,
            int target
    ) {
        if (!stateRetirementChanged(item, previous)) {
            return;
        }
        RowSnapshot row = rowSnapshot(db, item.kanji);
        SourceSnapshot source = row == null ? firstExampleForKanji(db, item.kanji) : row.source;
        Integer mature = row == null ? null : row.matureSupportCount;
        boolean retired = STATE_RETIRED.equals(item.state);
        insertTimelineEvent(
                db,
                item.kanji,
                occurredAt,
                retired ? STATE_RETIRED : "reopened",
                retired ? "Retired by Anki support" : "Repair reopened",
                studyStateTimelineDetail(retired, mature, target),
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                row == null ? null : row.weaknessScore,
                mature,
                syncId,
                (retired ? "retired:" : "reopened:") + studyTimelineKey(item) + ":" + syncId
        );
    }

    boolean stateRetirementChanged(RecordsStudyModels.StudyItem item, StudySnapshot previous) {
        return STATE_RETIRED.equals(item.state) != STATE_RETIRED.equals(previous.state);
    }

    String studyStateTimelineDetail(boolean retired, Integer mature, int target) {
        if (retired) {
            return mature == null
                    ? "No weak Anki evidence remained after sync, so Kani retired this repair."
                    : supportDetail("Mature Anki support met the target", mature, target);
        }
        return mature == null
                ? "Kani reopened this kanji after sync found weak evidence again."
                : supportDetail("Mature Anki support fell below target", mature, target);
    }

    void appendReviewTimelineEvent(SQLiteDatabase db, RecordsSchedulerModels.ReviewRequest request, String appliedRating, long reviewedAt, String dedupeKey) {
        String eventType;
        String title;
        if (request.manualOverride) {
            eventType = COLUMN_MANUAL_OVERRIDE;
            title = "Manual override";
        } else if (RATING_AGAIN.equals(appliedRating) || (request.writingRequired && !request.writingPassed)) {
            eventType = "review_failed";
            title = "Review failed";
        } else {
            eventType = "review_passed";
            title = "Review passed";
        }
        SourceSnapshot source = firstExampleForKanji(db, request.kanji);
        RowSnapshot row = rowSnapshot(db, request.kanji);
        insertTimelineEvent(
                db,
                request.kanji,
                reviewedAt,
                eventType,
                title,
                reviewDetail(request, appliedRating),
                source.expression,
                source.reading,
                appliedRating,
                request.writingRequired,
                request.writingPassed,
                request.manualOverride,
                row == null ? null : row.weaknessScore,
                row == null ? null : row.matureSupportCount,
                null,
                dedupeKey
        );
    }

    String reviewDetail(RecordsSchedulerModels.ReviewRequest request, String appliedRating) {
        if (request.manualOverride) {
            return "Saved as " + appliedRating + " after manual confirmation.";
        }
        if (RATING_AGAIN.equals(appliedRating)) {
            return request.writingRequired
                    ? "Writing missed; Kani scheduled another try."
                    : "Recall missed; Kani scheduled another try.";
        }
        if (request.writingRequired) {
            return request.writingPassed
                    ? "Writing passed and was rated " + appliedRating + "."
                    : "Writing was not passed and was rated " + appliedRating + ".";
        }
        return "Recall review was rated " + appliedRating + ".";
    }

    String supportDetail(String prefix, int matureSupportCount, int target) {
        return prefix + ": mature support " + matureSupportCount + " / target " + target + ".";
    }

    void backfillKanjiInventory(SQLiteDatabase db, long nowMillis, RecordsSyncModels.Settings settings) {
        rebuildKanjiInventory(db, null, suspendedImportsFromDb(db), dashboardRowsFromDb(db), nowMillis, settings);
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
        Map<String, MutableKanjiInventoryItem> inventory = new LinkedHashMap<>();
        addSnapshotInventory(inventory, snapshot, settings);
        addImportedInventory(inventory, imports);
        addDashboardInventory(inventory, rows);
        addKnownKanji(inventory, db, TABLE_STUDY_ITEMS);
        addKnownKanji(inventory, db, TABLE_REVIEW_LOG);
        addKnownKanji(inventory, db, TABLE_KANJI_TIMELINE_EVENTS);
        writeKanjiInventory(db, inventory, nowMillis, settings);
    }

    void addSnapshotInventory(
            Map<String, MutableKanjiInventoryItem> inventory,
            RecordsSyncModels.CollectionSnapshot snapshot,
            RecordsSyncModels.Settings settings
    ) {
        if (snapshot == null) {
            return;
        }
        ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
        for (RecordsSyncModels.Note note : snapshot.notes) {
            if (activeIndex.noteIds.contains(note.noteId)) {
                addInventoryTextForNote(inventory, note, settings);
            }
        }
    }

    void addInventoryTextForNote(
            Map<String, MutableKanjiInventoryItem> inventory,
            RecordsSyncModels.Note note,
            RecordsSyncModels.Settings settings
    ) {
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        String reading = TextUtil.normalizeJapanese(note.reading(settings));
        String meaning = TextUtil.firstMeaningLine(note.meaning(settings));
        String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
        addInventoryText(inventory, TextUtil.extractKanji(expression + " " + sentence), meaning, reading, expression, sentence);
    }

    void addImportedInventory(Map<String, MutableKanjiInventoryItem> inventory, List<RecordsImportModels.SuspendedImport> imports) {
        for (RecordsImportModels.SuspendedImport imported : imports) {
            MutableKanjiInventoryItem item = inventoryItem(inventory, imported.kanji);
            for (RecordsImportModels.SuspendedSource source : imported.sources) {
                item.add(source.meaning, source.reading, source.expression, source.sentence);
            }
        }
    }

    void addDashboardInventory(Map<String, MutableKanjiInventoryItem> inventory, List<RecordsImportModels.DashboardRow> rows) {
        for (RecordsImportModels.DashboardRow row : rows) {
            MutableKanjiInventoryItem item = inventoryItem(inventory, row.kanji);
            item.add(row.primaryMeaning, row.reading, row.reasonText, row.browserSearch);
            item.browserSearch = row.browserSearch;
            for (RecordsImportModels.Example example : row.examples) {
                item.exampleCount++;
                item.add(example.meaning, example.reading, example.expression, example.sentence);
            }
        }
    }

    void addKnownKanji(Map<String, MutableKanjiInventoryItem> inventory, SQLiteDatabase db, String table) {
        try (Cursor cursor = db.query(true, table, new String[]{COLUMN_KANJI}, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                inventoryItem(inventory, string(cursor, COLUMN_KANJI));
            }
        }
    }

    void writeKanjiInventory(
            SQLiteDatabase db,
            Map<String, MutableKanjiInventoryItem> inventory,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        for (MutableKanjiInventoryItem item : inventory.values()) {
            if (item.kanji.isEmpty()) {
                continue;
            }
            RecordsImportModels.KanjiInventoryItem previous = readInventoryItem(db, item.kanji);
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI, item.kanji);
            values.put(COLUMN_PRIMARY_MEANING, firstNonEmpty(item.primaryMeaning, previous == null ? "" : previous.primaryMeaning));
            values.put("readings", item.readingsText(previous == null ? "" : previous.readings));
            values.put(COLUMN_BROWSER_SEARCH, firstNonEmpty(item.browserSearch, previous == null ? TextUtil.browserSearchForKanji(item.kanji, settings) : previous.browserSearch));
            values.put("search_text", item.searchText(previous));
            values.put("source_count", Math.max(item.sourceCount, previous == null ? 0 : previous.sourceCount));
            values.put("example_count", Math.max(item.exampleCount, previous == null ? 0 : previous.exampleCount));
            values.put(COLUMN_FIRST_SEEN_AT, previous == null ? nowMillis : previous.lastSeenAtMillis);
            values.put(COLUMN_LAST_SEEN_AT, nowMillis);
            db.insertWithOnConflict(TABLE_KANJI_INVENTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void rebuildSimilarKanjiPairs(SQLiteDatabase db, SimilarKanjiIndex similarIndex, long nowMillis) {
        Map<String, Long> firstSeenByPair = similarPairFirstSeen(db);
        List<SimilarKanjiIndex.Pair> localPairs = similarIndex.pairsWithin(localInventoryKanji(db));
        db.delete(TABLE_SIMILAR_KANJI_PAIRS, null, null);
        for (SimilarKanjiIndex.Pair pair : localPairs) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_KANJI_A, pair.kanjiA);
            values.put(COLUMN_KANJI_B, pair.kanjiB);
            values.put(COLUMN_SOURCE, pair.source);
            values.put(COLUMN_FIRST_SEEN_AT, firstSeenByPair.getOrDefault(similarKey(pair.kanjiA, pair.kanjiB, pair.source), nowMillis));
            values.put(COLUMN_LAST_SEEN_AT, nowMillis);
            db.insertWithOnConflict(TABLE_SIMILAR_KANJI_PAIRS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void rebuildSimilarKanjiChoiceStates(SQLiteDatabase db, long nowMillis) {
        createSimilarKanjiPracticeTables(db);
        Map<String, SimilarChoiceSnapshot> previous = similarChoiceSnapshots(db);
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
        List<RecordsImportModels.SimilarKanjiChoiceCard> candidates = planner.buildCandidates(
                allInventoryItems(db),
                allSimilarPairs(db)
        );
        Set<String> currentKeys = new HashSet<>();
        for (RecordsImportModels.SimilarKanjiChoiceCard card : candidates) {
            String key = similarChoiceKey(card.targetKanji, card.choiceSignature);
            currentKeys.add(key);
            upsertSimilarKanjiChoiceState(db, card, previous.get(key), nowMillis);
        }
        deleteStaleSimilarChoiceStates(db, previous.keySet(), currentKeys);
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
            String[] parts = key.split(SIMILAR_CHOICE_KEY_DELIMITER, 2);
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
        Map<String, Long> out = new HashMap<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, new String[]{COLUMN_KANJI_A, COLUMN_KANJI_B, COLUMN_SOURCE, COLUMN_FIRST_SEEN_AT}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                out.put(
                        similarKey(string(cursor, COLUMN_KANJI_A), string(cursor, COLUMN_KANJI_B), string(cursor, COLUMN_SOURCE)),
                        longValue(cursor, COLUMN_FIRST_SEEN_AT)
                );
            }
        }
        return out;
    }

    Set<String> localInventoryKanji(SQLiteDatabase db) {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = db.query(TABLE_KANJI_INVENTORY, new String[]{COLUMN_KANJI}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String kanji = normalizeSingleKanji(string(cursor, COLUMN_KANJI));
                if (!kanji.isEmpty()) {
                    out.add(kanji);
                }
            }
        }
        return out;
    }

    RecordsImportModels.SimilarKanjiPair readSimilarPair(Cursor cursor) {
        return new RecordsImportModels.SimilarKanjiPair(
                string(cursor, COLUMN_KANJI_A),
                string(cursor, COLUMN_KANJI_B),
                string(cursor, COLUMN_SOURCE),
                longValue(cursor, COLUMN_FIRST_SEEN_AT),
                longValue(cursor, COLUMN_LAST_SEEN_AT)
        );
    }

    List<RecordsImportModels.SimilarKanjiPair> allSimilarPairs(SQLiteDatabase db) {
        List<RecordsImportModels.SimilarKanjiPair> out = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, null, null, null, null, null, ORDER_SIMILAR_PAIR)) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        }
        return out;
    }

    List<RecordsImportModels.KanjiInventoryItem> allInventoryItems(SQLiteDatabase db) {
        List<RecordsImportModels.KanjiInventoryItem> out = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_KANJI_INVENTORY, null, null, null, null, null, ORDER_KANJI_ASC)) {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
        }
        return out;
    }

    Map<String, SimilarChoiceSnapshot> similarChoiceSnapshots(SQLiteDatabase db) {
        Map<String, SimilarChoiceSnapshot> out = new HashMap<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_CHOICE_STATE, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String target = string(cursor, COLUMN_TARGET_KANJI);
                String signature = string(cursor, COLUMN_CHOICE_SIGNATURE);
                out.put(
                        similarChoiceKey(target, signature),
                        new SimilarChoiceSnapshot(
                                longValue(cursor, COLUMN_DUE_AT),
                                longValue(cursor, COLUMN_PASSED_AT),
                                longValue(cursor, COLUMN_LAST_REVIEWED_AT),
                                integer(cursor, COLUMN_CORRECT_COUNT),
                                integer(cursor, COLUMN_WRONG_COUNT),
                                longValue(cursor, COLUMN_FIRST_SEEN_AT)
                        )
                );
            }
        }
        return out;
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
        return new RecordsImportModels.SimilarKanjiChoiceCard(
                string(cursor, COLUMN_TARGET_KANJI),
                string(cursor, COLUMN_PRIMARY_MEANING),
                deserializeChoices(string(cursor, COLUMN_CHOICES)),
                string(cursor, COLUMN_CHOICE_SIGNATURE),
                longValue(cursor, COLUMN_DUE_AT),
                longValue(cursor, COLUMN_PASSED_AT),
                longValue(cursor, COLUMN_LAST_REVIEWED_AT),
                integer(cursor, COLUMN_CORRECT_COUNT),
                integer(cursor, COLUMN_WRONG_COUNT)
        );
    }

    boolean hasPendingSimilarRepairs(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=?",
                new String[]{STATUS_PENDING, targetKanji, choiceSignature},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    void enqueueSimilarWritingRepair(
            SQLiteDatabase db,
            RecordsImportModels.SimilarKanjiChoiceCard card,
            String repairKanji,
            String wrongSelection,
            long nowMillis
    ) {
        String normalized = normalizeSingleKanji(repairKanji);
        if (normalized.isEmpty()) {
            return;
        }
        try (Cursor pending = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=? AND repair_kanji=?",
                new String[]{STATUS_PENDING, card.targetKanji, card.choiceSignature, normalized},
                null,
                null,
                null,
                "1"
        )) {
            if (pending.moveToFirst()) {
                return;
            }
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_TARGET_KANJI, card.targetKanji);
        values.put("repair_kanji", normalized);
        values.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
        values.put("wrong_selection", wrongSelection == null ? "" : wrongSelection);
        values.put("prompt_meaning", card.primaryMeaning);
        values.put(COLUMN_STATUS, STATUS_PENDING);
        values.put(COLUMN_DUE_AT, nowMillis);
        values.put(COLUMN_ACTIVE_TOKEN, "");
        values.put(COLUMN_ATTEMPTS, 0);
        values.put(COLUMN_CREATED_AT, nowMillis);
        values.put(COLUMN_UPDATED_AT, nowMillis);
        values.put(COLUMN_COMPLETED_AT, 0L);
        db.insert(TABLE_SIMILAR_KANJI_REPAIR_QUEUE, null, values);
    }

    RecordsImportModels.SimilarKanjiWritingRepair similarWritingRepair(SQLiteDatabase db, long repairId) {
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                null,
                "id=?",
                new String[]{Long.toString(repairId)},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? readSimilarWritingRepair(cursor) : null;
        }
    }

    RecordsImportModels.SimilarKanjiWritingRepair readSimilarWritingRepair(Cursor cursor) {
        return new RecordsImportModels.SimilarKanjiWritingRepair(
                longValue(cursor, "id"),
                string(cursor, COLUMN_TARGET_KANJI),
                string(cursor, "repair_kanji"),
                string(cursor, COLUMN_CHOICE_SIGNATURE),
                string(cursor, "wrong_selection"),
                string(cursor, "prompt_meaning"),
                string(cursor, COLUMN_STATUS),
                longValue(cursor, COLUMN_DUE_AT),
                string(cursor, COLUMN_ACTIVE_TOKEN),
                integer(cursor, COLUMN_ATTEMPTS),
                longValue(cursor, COLUMN_CREATED_AT),
                longValue(cursor, COLUMN_UPDATED_AT),
                longValue(cursor, COLUMN_COMPLETED_AT)
        );
    }

    static String serializeChoices(List<String> choices) {
        return String.join("\t", choices == null ? Collections.emptyList() : choices);
    }

    static List<String> deserializeChoices(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        String[] parts = TAB_SEPARATOR.split(encoded, -1);
        for (String part : parts) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    void addInventoryText(Map<String, MutableKanjiInventoryItem> inventory, List<String> kanji, String meaning, String reading, String expression, String sentence) {
        for (String glyph : kanji) {
            inventoryItem(inventory, glyph).add(meaning, reading, expression, sentence);
        }
    }

    MutableKanjiInventoryItem inventoryItem(Map<String, MutableKanjiInventoryItem> inventory, String kanji) {
        MutableKanjiInventoryItem item = inventory.get(kanji);
        if (item == null) {
            item = new MutableKanjiInventoryItem(kanji);
            inventory.put(kanji, item);
        }
        return item;
    }

    RecordsImportModels.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_KANJI_INVENTORY, null, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? readInventoryItem(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    RecordsImportModels.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, COLUMN_KANJI);
        return new RecordsImportModels.KanjiInventoryItem(
                kanji,
                string(cursor, COLUMN_PRIMARY_MEANING),
                string(cursor, "readings"),
                string(cursor, COLUMN_BROWSER_SEARCH),
                integer(cursor, "source_count"),
                integer(cursor, "example_count"),
                isKanjiSuspended(db, kanji),
                longValue(cursor, COLUMN_LAST_SEEN_AT)
        );
    }

    boolean isKanjiSuspended(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? "" : second;
    }

    static String normalizeSingleKanji(String value) {
        String normalized = TextUtil.normalizeJapanese(value);
        if (normalized.codePointCount(0, normalized.length()) != 1) {
            return "";
        }
        return TextUtil.isKanji(normalized.codePointAt(0)) ? normalized : "";
    }

    static String[] canonicalSimilarPair(String first, String second) {
        if (first.compareTo(second) <= 0) {
            return new String[]{first, second};
        }
        return new String[]{second, first};
    }

    static String similarKey(String first, String second, String source) {
        return first + SIMILAR_KEY_DELIMITER + second + SIMILAR_KEY_DELIMITER + source;
    }

    static String similarChoiceKey(String targetKanji, String choiceSignature) {
        return targetKanji + SIMILAR_CHOICE_KEY_DELIMITER + (choiceSignature == null ? "" : choiceSignature);
    }

    RecordsImportModels.DashboardRow readDashboardRow(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readDashboardRow(db, cursor);
        } finally {
            cursor.close();
        }
    }

    RecordsImportModels.DashboardRow readDashboardRow(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, COLUMN_KANJI);
        return new RecordsImportModels.DashboardRow(
                kanji,
                nullableInt(cursor, COLUMN_JITEN_RANK),
                string(cursor, COLUMN_PRIMARY_MEANING),
                string(cursor, COLUMN_READING),
                string(cursor, COLUMN_BROWSER_SEARCH),
                integer(cursor, COLUMN_WEAKNESS_SCORE),
                string(cursor, COLUMN_REASON_CODE),
                string(cursor, COLUMN_REASON_TEXT),
                integer(cursor, COLUMN_ACTIVE_EXAMPLE_COUNT),
                integer(cursor, COLUMN_SUSPENDED_EXAMPLE_COUNT),
                integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                examplesForKanji(db, kanji)
        );
    }

    RecordsStudyModels.StudyItem studyItemForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(TABLE_STUDY_ITEMS, null, WHERE_KANJI, new String[]{kanji}, null, null, "state='retired' ASC, due_at ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            RecordsStudyModels.StudyItem item = readStudyItem(cursor);
            boolean hasSimilar = kanjiHasSimilarNeighbor(db, kanji);
            return hasSimilar != item.hasSimilarKanji ? item.withHasSimilarKanji(hasSimilar) : item;
        } finally {
            cursor.close();
        }
    }

    boolean kanjiHasSimilarNeighbor(SQLiteDatabase db, String kanji) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_SIMILAR_KANJI_PAIRS
                        + " WHERE kanji_a = ? OR kanji_b = ? LIMIT 1",
                new String[]{kanji, kanji}
        )) {
            return cursor.moveToFirst();
        }
    }

    RecordsImportModels.KanjiTimelineEvent readTimelineEvent(Cursor cursor) {
        return new RecordsImportModels.KanjiTimelineEvent(
                longValue(cursor, "id"),
                string(cursor, COLUMN_KANJI),
                longValue(cursor, COLUMN_OCCURRED_AT),
                string(cursor, COLUMN_EVENT_TYPE),
                string(cursor, COLUMN_TITLE),
                string(cursor, COLUMN_DETAIL),
                string(cursor, "source_expression"),
                string(cursor, "source_reading"),
                string(cursor, COLUMN_RATING),
                integer(cursor, COLUMN_WRITING_REQUIRED) == 1,
                integer(cursor, COLUMN_WRITING_PASSED) == 1,
                integer(cursor, COLUMN_MANUAL_OVERRIDE) == 1,
                nullableInt(cursor, COLUMN_WEAKNESS_SCORE),
                nullableInt(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                nullableLong(cursor, COLUMN_SYNC_ID),
                string(cursor, COLUMN_DEDUPE_KEY)
        );
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
        ContentValues values = new ContentValues();
        values.put(COLUMN_KANJI, kanji);
        values.put(COLUMN_OCCURRED_AT, occurredAt);
        values.put(COLUMN_EVENT_TYPE, eventType == null ? "" : eventType);
        values.put(COLUMN_TITLE, title == null ? "" : title);
        values.put(COLUMN_DETAIL, detail == null ? "" : detail);
        String sourceExpression = stringValueAt(eventValues, 0);
        String sourceReading = stringValueAt(eventValues, 1);
        String rating = stringValueAt(eventValues, 2);
        boolean writingRequired = booleanValueAt(eventValues, 3);
        boolean writingPassed = booleanValueAt(eventValues, 4);
        boolean manualOverride = booleanValueAt(eventValues, 5);
        Integer weaknessScore = integerValueAt(eventValues, 6);
        Integer matureSupportCount = integerValueAt(eventValues, 7);
        Long syncId = longValueAt(eventValues, 8);
        String dedupeKey = stringValueAt(eventValues, 9);
        values.put("source_expression", sourceExpression);
        values.put("source_reading", sourceReading);
        values.put(COLUMN_RATING, rating);
        values.put(COLUMN_WRITING_REQUIRED, writingRequired ? 1 : 0);
        values.put(COLUMN_WRITING_PASSED, writingPassed ? 1 : 0);
        values.put(COLUMN_MANUAL_OVERRIDE, manualOverride ? 1 : 0);
        if (weaknessScore == null) {
            values.putNull(COLUMN_WEAKNESS_SCORE);
        } else {
            values.put(COLUMN_WEAKNESS_SCORE, weaknessScore);
        }
        if (matureSupportCount == null) {
            values.putNull(COLUMN_MATURE_SUPPORT_COUNT);
        } else {
            values.put(COLUMN_MATURE_SUPPORT_COUNT, matureSupportCount);
        }
        if (syncId == null) {
            values.putNull(COLUMN_SYNC_ID);
        } else {
            values.put(COLUMN_SYNC_ID, syncId);
        }
        values.put(COLUMN_DEDUPE_KEY, dedupeKey);
        db.insertWithOnConflict(TABLE_KANJI_TIMELINE_EVENTS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
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

    long insertSyncRun(SQLiteDatabase db, SyncRunInsert syncRun) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STARTED_AT, syncRun.startedAt());
        values.put(COLUMN_FINISHED_AT, syncRun.finishedAt());
        values.put(COLUMN_STATUS, syncRun.status());
        values.put(COLUMN_ACTIVE_NOTES_COUNT, syncRun.activeIndex().noteIds.size());
        values.put(COLUMN_ACTIVE_CARDS_COUNT, syncRun.activeIndex().activeCardCount);
        values.put(COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, syncRun.archivedSuspendedCardCount());
        values.put(COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, syncRun.importCount());
        values.put("deleted_notes_count", syncRun.deletedNotes());
        values.put("deleted_cards_count", syncRun.deletedCards());
        values.put("error_code", syncRun.errorCode());
        values.put(COLUMN_ERROR_MESSAGE, syncRun.errorMessage());
        values.put(COLUMN_REMOVAL_MESSAGE, syncRun.removalMessage());
        return db.insert(TABLE_SYNC_RUNS, null, values);
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
        Set<Long> ids = new HashSet<>();
        for (RecordsImportModels.SuspendedImport imported : imports) {
            for (RecordsImportModels.SuspendedSource source : imported.sources) {
                if (source.suspended) {
                    ids.add(source.cardId);
                }
            }
        }
        return ids;
    }

    ActiveCardIndex activeCardIndex(List<RecordsSyncModels.Card> cards) {
        Set<Long> noteIds = new HashSet<>();
        Set<Long> cardIds = new HashSet<>();
        int activeCardCount = 0;
        for (RecordsSyncModels.Card card : cards) {
            if (!card.suspended) {
                activeCardCount++;
                noteIds.add(card.noteId);
                cardIds.add(card.cardId);
            }
        }
        return new ActiveCardIndex(noteIds, cardIds, activeCardCount);
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
