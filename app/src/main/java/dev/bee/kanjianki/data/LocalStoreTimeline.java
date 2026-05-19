package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.TimelineCopy;

import java.util.List;
import java.util.Map;

final class LocalStoreTimeline {
    private final LocalStoreHistory activity;

    LocalStoreTimeline(LocalStoreHistory activity) {
        this.activity = activity;
    }

    void createTimelineTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kanji_timeline_events (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, occurred_at INTEGER NOT NULL, event_type TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, source_expression TEXT NOT NULL, source_reading TEXT NOT NULL, rating TEXT NOT NULL, writing_required INTEGER NOT NULL DEFAULT 0, writing_passed INTEGER NOT NULL DEFAULT 0, manual_override INTEGER NOT NULL DEFAULT 0, weakness_score INTEGER, mature_support_count INTEGER, sync_id INTEGER, dedupe_key TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_timeline_dedupe ON kanji_timeline_events(dedupe_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timeline_kanji_time ON kanji_timeline_events(kanji, occurred_at, id)");
    }

    void backfillTimelineEvents(SQLiteDatabase db) {
        Map<String, LocalStoreHistory.RowSnapshot> rows = activity.rowSnapshots(db);
        backfillSuspendedImportTimeline(db);
        backfillRowTimeline(db, rows);
        backfillStudyTimeline(db, rows);
        backfillReviewTimeline(db);
    }

    void backfillSuspendedImportTimeline(SQLiteDatabase db) {
        Cursor imports = db.query(LocalStoreBase.TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "first_imported_at ASC, kanji ASC");
        try {
            while (imports.moveToNext()) {
                String kanji = LocalStoreBase.string(imports, LocalStoreBase.COLUMN_KANJI);
                LocalStoreHistory.SourceSnapshot source = activity.firstSuspendedSourceForKanji(db, kanji);
                long importedAt = LocalStoreBase.longValue(imports, LocalStoreBase.COLUMN_FIRST_IMPORTED_AT);
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
                        LocalStoreBase.longValue(imports, LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID),
                        "suspended_imported:" + kanji
                );
            }
        } finally {
            imports.close();
        }
    }

    void backfillRowTimeline(SQLiteDatabase db, Map<String, LocalStoreHistory.RowSnapshot> rows) {
        for (LocalStoreHistory.RowSnapshot row : rows.values()) {
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    LocalStoreBase.TIMELINE_FIRST_SEEN,
                    LocalStoreBase.TIMELINE_FIRST_SEEN_TITLE,
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
                    LocalStoreBase.TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
            );
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    "weak_support_seen",
                    "Weak support seen",
                    TimelineCopy.supportDetail("Anki evidence still needs repair", row.matureSupportCount, RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold),
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

    void backfillStudyTimeline(SQLiteDatabase db, Map<String, LocalStoreHistory.RowSnapshot> rows) {
        Cursor study = db.query(LocalStoreBase.TABLE_STUDY_ITEMS, null, null, null, null, null, "created_at ASC, kanji ASC");
        try {
            while (study.moveToNext()) {
                backfillStudyTimelineRow(db, rows, study);
            }
        } finally {
            study.close();
        }
    }

    void backfillStudyTimelineRow(SQLiteDatabase db, Map<String, LocalStoreHistory.RowSnapshot> rows, Cursor study) {
        String kanji = LocalStoreBase.string(study, LocalStoreBase.COLUMN_KANJI);
        long occurredAt = defaultTimelineTime(LocalStoreBase.longValue(study, LocalStoreBase.COLUMN_CREATED_AT));
        LocalStoreHistory.RowSnapshot row = rows.get(kanji);
        LocalStoreHistory.SourceSnapshot source = row == null ? activity.firstExampleForKanji(db, kanji) : row.source;
        if (row == null) {
            insertTimelineEvent(
                    db,
                    kanji,
                    occurredAt,
                    LocalStoreBase.TIMELINE_FIRST_SEEN,
                    LocalStoreBase.TIMELINE_FIRST_SEEN_TITLE,
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
                    LocalStoreBase.TIMELINE_FIRST_SEEN_KEY_PREFIX + kanji
            );
        }
        if (LocalStoreBase.STATE_RETIRED.equals(LocalStoreBase.string(study, LocalStoreBase.COLUMN_STATE))) {
            Integer mature = row == null ? null : row.matureSupportCount;
            insertTimelineEvent(
                    db,
                    kanji,
                    occurredAt,
                    LocalStoreBase.STATE_RETIRED,
                    "Retired by Anki support",
                    mature == null
                            ? "Kani had already retired this repair before timeline tracking was added."
                            : TimelineCopy.supportDetail("Mature Anki support met the target", mature, RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold),
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
        Cursor reviews = db.query(LocalStoreBase.TABLE_REVIEW_LOG, null, null, null, null, null, "reviewed_at ASC, id ASC");
        try {
            while (reviews.moveToNext()) {
                RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest(
                        LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_KANJI),
                        LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_TOKEN),
                        LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_RATING),
                        LocalStoreBase.integer(reviews, LocalStoreBase.COLUMN_WRITING_REQUIRED) == 1,
                        LocalStoreBase.integer(reviews, LocalStoreBase.COLUMN_WRITING_PASSED) == 1,
                        LocalStoreBase.integer(reviews, LocalStoreBase.COLUMN_MANUAL_OVERRIDE) == 1,
                        0
                );
                appendReviewTimelineEvent(db, request, LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_RATING), LocalStoreBase.longValue(reviews, LocalStoreBase.COLUMN_REVIEWED_AT), "review:" + request.token);
            }
        } finally {
            reviews.close();
        }
    }

    void appendSyncTimelineEvents(
            SQLiteDatabase db,
            Map<String, LocalStoreHistory.RowSnapshot> previousRows,
            List<RecordsImportModels.SuspendedImport> imports,
            List<RecordsImportModels.DashboardRow> rows,
            long syncId,
            long occurredAt,
            RecordsSyncModels.Settings settings
    ) {
        int target = settings == null ? RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (RecordsImportModels.SuspendedImport imported : imports) {
            LocalStoreHistory.SourceSnapshot source = activity.sourceFromImport(imported);
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
            LocalStoreHistory.RowSnapshot previous = previousRows.get(row.kanji);
            LocalStoreHistory.SourceSnapshot source = activity.sourceForRow(row);
            insertTimelineEvent(
                    db,
                    row.kanji,
                    occurredAt,
                    LocalStoreBase.TIMELINE_FIRST_SEEN,
                    LocalStoreBase.TIMELINE_FIRST_SEEN_TITLE,
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
                    LocalStoreBase.TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
            );
            if (previous == null) {
                insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "weak_support_seen",
                        "Weak support seen",
                        TimelineCopy.supportDetail("Anki evidence still needs repair", row.matureSupportCount, target),
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
            Map<String, LocalStoreHistory.StudySnapshot> previousItems,
            List<RecordsStudyModels.StudyItem> currentItems,
            long syncId,
            long occurredAt,
            RecordsSyncModels.Settings settings
    ) {
        int target = settings == null ? RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (RecordsStudyModels.StudyItem item : currentItems) {
            LocalStoreHistory.StudySnapshot previous = previousItems.get(LocalStoreBase.studyFamilyKey(item.kanji, item.answerSignature));
            if (previous != null) {
                appendStudyStateTimelineEvent(db, item, previous, syncId, occurredAt, target);
            }
        }
    }

    void appendStudyStateTimelineEvent(
            SQLiteDatabase db,
            RecordsStudyModels.StudyItem item,
            LocalStoreHistory.StudySnapshot previous,
            long syncId,
            long occurredAt,
            int target
    ) {
        if (LocalStoreBase.STATE_RETIRED.equals(item.state) == LocalStoreBase.STATE_RETIRED.equals(previous.state)) {
            return;
        }
        LocalStoreHistory.RowSnapshot row = activity.rowSnapshot(db, item.kanji);
        LocalStoreHistory.SourceSnapshot source = row == null ? activity.firstExampleForKanji(db, item.kanji) : row.source;
        Integer mature = row == null ? null : row.matureSupportCount;
        boolean retired = LocalStoreBase.STATE_RETIRED.equals(item.state);
        insertTimelineEvent(
                db,
                item.kanji,
                occurredAt,
                retired ? LocalStoreBase.STATE_RETIRED : "reopened",
                retired ? "Retired by Anki support" : "Repair reopened",
                TimelineCopy.studyStateDetail(retired, mature, target),
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                row == null ? null : row.weaknessScore,
                mature,
                syncId,
                (retired ? "retired:" : "reopened:") + LocalStoreBase.studyTimelineKey(item) + ":" + syncId
        );
    }

    void appendReviewTimelineEvent(SQLiteDatabase db, RecordsSchedulerModels.ReviewRequest request, String appliedRating, long reviewedAt, String dedupeKey) {
        TimelineCopy.ReviewEvent event = TimelineCopy.reviewEvent(request, appliedRating);
        LocalStoreHistory.SourceSnapshot source = activity.firstExampleForKanji(db, request.kanji);
        LocalStoreHistory.RowSnapshot row = activity.rowSnapshot(db, request.kanji);
        insertTimelineEvent(
                db,
                request.kanji,
                reviewedAt,
                event.eventType(),
                event.title(),
                event.detail(),
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

    RecordsImportModels.KanjiTimelineEvent readTimelineEvent(Cursor cursor) {
        return new RecordsImportModels.KanjiTimelineEvent(
                LocalStoreBase.longValue(cursor, "id"),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_OCCURRED_AT),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_EVENT_TYPE),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TITLE),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_DETAIL),
                LocalStoreBase.string(cursor, "source_expression"),
                LocalStoreBase.string(cursor, "source_reading"),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_RATING),
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRITING_REQUIRED) == 1,
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRITING_PASSED) == 1,
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_MANUAL_OVERRIDE) == 1,
                LocalStoreBase.nullableInt(cursor, LocalStoreBase.COLUMN_WEAKNESS_SCORE),
                LocalStoreBase.nullableInt(cursor, LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT),
                LocalStoreBase.nullableLong(cursor, LocalStoreBase.COLUMN_SYNC_ID),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_DEDUPE_KEY)
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
        values.put(LocalStoreBase.COLUMN_KANJI, kanji);
        values.put(LocalStoreBase.COLUMN_OCCURRED_AT, occurredAt);
        values.put(LocalStoreBase.COLUMN_EVENT_TYPE, eventType == null ? "" : eventType);
        values.put(LocalStoreBase.COLUMN_TITLE, title == null ? "" : title);
        values.put(LocalStoreBase.COLUMN_DETAIL, detail == null ? "" : detail);
        String sourceExpression = activity.stringValueAt(eventValues, 0);
        String sourceReading = activity.stringValueAt(eventValues, 1);
        String rating = activity.stringValueAt(eventValues, 2);
        boolean writingRequired = activity.booleanValueAt(eventValues, 3);
        boolean writingPassed = activity.booleanValueAt(eventValues, 4);
        boolean manualOverride = activity.booleanValueAt(eventValues, 5);
        Integer weaknessScore = activity.integerValueAt(eventValues, 6);
        Integer matureSupportCount = activity.integerValueAt(eventValues, 7);
        Long syncId = activity.longValueAt(eventValues, 8);
        String dedupeKey = activity.stringValueAt(eventValues, 9);
        values.put("source_expression", sourceExpression);
        values.put("source_reading", sourceReading);
        values.put(LocalStoreBase.COLUMN_RATING, rating);
        values.put(LocalStoreBase.COLUMN_WRITING_REQUIRED, writingRequired ? 1 : 0);
        values.put(LocalStoreBase.COLUMN_WRITING_PASSED, writingPassed ? 1 : 0);
        values.put(LocalStoreBase.COLUMN_MANUAL_OVERRIDE, manualOverride ? 1 : 0);
        values.put(LocalStoreBase.COLUMN_WEAKNESS_SCORE, weaknessScore);
        values.put(LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT, matureSupportCount);
        values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId);
        values.put(LocalStoreBase.COLUMN_DEDUPE_KEY, dedupeKey == null ? "" : dedupeKey);
        db.insertWithOnConflict(LocalStoreBase.TABLE_KANJI_TIMELINE_EVENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    long defaultTimelineTime(long occurredAt) {
        return occurredAt == 0L ? System.currentTimeMillis() : occurredAt;
    }
}
