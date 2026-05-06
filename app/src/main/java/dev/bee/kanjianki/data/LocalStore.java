package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LocalStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "kanji_anki_simple.db";
    private static final int DB_VERSION = 3;
    private static final int DEFAULT_REMINDER_HOUR = 19;
    private static final int DEFAULT_REMINDER_MINUTE = 0;
    private static final int DEFAULT_AUTO_SYNC_HOUR = DEFAULT_REMINDER_HOUR;
    private static final int DEFAULT_AUTO_SYNC_MINUTE = DEFAULT_REMINDER_MINUTE;
    private static final String KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled";
    private static final String KEY_AUTO_UPDATE_LAST_CHECK_AT = "auto_update_last_check_at";
    private static final String KEY_AUTO_UPDATE_LAST_RESULT = "auto_update_last_result";
    private static final String KEY_AUTO_UPDATE_LAST_VERSION = "auto_update_last_version";
    private static final String KEY_AUTO_UPDATE_PENDING_APK = "auto_update_pending_apk";
    private static final String KEY_AUTO_UPDATE_PENDING_MESSAGE = "auto_update_pending_message";

    public LocalStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, active_notes_count INTEGER NOT NULL, active_cards_count INTEGER NOT NULL, suspended_cards_archived_count INTEGER NOT NULL, suspended_kanji_imported_count INTEGER NOT NULL, deleted_notes_count INTEGER NOT NULL, deleted_cards_count INTEGER NOT NULL, error_code TEXT, error_message TEXT, removal_message TEXT)");
        db.execSQL("CREATE TABLE source_notes (note_id INTEGER PRIMARY KEY, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, tags TEXT NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE source_cards (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, ord INTEGER NOT NULL, queue INTEGER NOT NULL, type INTEGER NOT NULL, due INTEGER NOT NULL, interval_days INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_archive (card_id INTEGER PRIMARY KEY, note_id INTEGER NOT NULL, deck_name TEXT NOT NULL, model_name TEXT NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, fields_json TEXT NOT NULL, archived_at INTEGER NOT NULL, archived_sync_id INTEGER NOT NULL, restored_at INTEGER)");
        db.execSQL("CREATE TABLE suspended_imports (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, rank_known INTEGER NOT NULL, cutoff_used INTEGER NOT NULL, first_imported_at INTEGER NOT NULL, last_seen_sync_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE suspended_sources (kanji TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, sync_id INTEGER NOT NULL, PRIMARY KEY (kanji, card_id))");
        db.execSQL("CREATE TABLE dashboard_rows (kanji TEXT PRIMARY KEY, jiten_rank INTEGER, primary_meaning TEXT NOT NULL, reading TEXT NOT NULL, browser_search TEXT NOT NULL, weakness_score INTEGER NOT NULL, reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, active_example_count INTEGER NOT NULL, suspended_example_count INTEGER NOT NULL, mature_support_count INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE kanji_examples (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL, card_id INTEGER NOT NULL, note_id INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL, sentence TEXT NOT NULL, mature INTEGER NOT NULL, lapses INTEGER NOT NULL, interval_days INTEGER NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, fsrs_stability REAL, fsrs_difficulty REAL, fsrs_retrievability REAL)");
        db.execSQL("CREATE TABLE study_items (kanji TEXT PRIMARY KEY, state TEXT NOT NULL, due_at INTEGER NOT NULL, stability REAL NOT NULL, difficulty REAL NOT NULL, total_reviews INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_step INTEGER NOT NULL, writing_level INTEGER NOT NULL, active_token TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, token TEXT NOT NULL UNIQUE, rating TEXT NOT NULL, writing_required INTEGER NOT NULL, writing_passed INTEGER NOT NULL, manual_override INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_examples_kanji ON kanji_examples(kanji)");
        db.execSQL("CREATE INDEX idx_study_due ON study_items(state, due_at)");
        createTimelineTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createTimelineTables(db);
            backfillTimelineEvents(db);
        }
        if (oldVersion < 3) {
            addNullableColumn(db, "source_cards", "fsrs_stability", "REAL");
            addNullableColumn(db, "source_cards", "fsrs_difficulty", "REAL");
            addNullableColumn(db, "source_cards", "fsrs_retrievability", "REAL");
            addNullableColumn(db, "kanji_examples", "interval_days", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "kanji_examples", "reps", "INTEGER NOT NULL DEFAULT 0");
            addNullableColumn(db, "kanji_examples", "fsrs_stability", "REAL");
            addNullableColumn(db, "kanji_examples", "fsrs_difficulty", "REAL");
            addNullableColumn(db, "kanji_examples", "fsrs_retrievability", "REAL");
        }
    }

    public long saveSuccessfulSync(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> imports,
            List<Records.DashboardRow> rows,
            Records.Settings settings,
            long startedAt,
            long finishedAt,
            AnkiDroidGateway.RemovalSummary removal
    ) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, RowSnapshot> previousRows = rowSnapshots(db);
            ActiveCardIndex activeIndex = activeCardIndex(snapshot.cards);
            int deletedNotes = countDeletedExisting(db, "source_notes", "note_id", activeIndex.noteIds);
            int deletedCards = countDeletedExisting(db, "source_cards", "card_id", activeIndex.cardIds);
            long syncId = insertSyncRun(db, startedAt, finishedAt, "success", activeIndex, imports.size(), null, null, removal == null ? "" : removal.message, deletedNotes, deletedCards);
            db.delete("source_cards", null, null);
            db.delete("source_notes", null, null);
            db.delete("dashboard_rows", null, null);
            db.delete("kanji_examples", null, null);

            Map<Long, Records.Note> notesById = snapshot.notesById();
            for (Records.Note note : snapshot.notes) {
                if (activeIndex.noteIds.contains(note.noteId)) {
                    ContentValues values = new ContentValues();
                    values.put("note_id", note.noteId);
                    values.put("model_name", note.modelName);
                    values.put("expression", TextUtil.normalizeJapanese(note.expression(settings)));
                    values.put("reading", TextUtil.normalizeJapanese(note.reading(settings)));
                    values.put("meaning", TextUtil.firstMeaningLine(note.meaning(settings)));
                    values.put("sentence", TextUtil.normalizeJapanese(note.sentence(settings)));
                    values.put("fields_json", fieldsJson(note.fields));
                    values.put("tags", String.join(" ", note.tags));
                    values.put("last_seen_sync_id", syncId);
                    db.insertWithOnConflict("source_notes", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            for (Records.Card card : snapshot.cards) {
                Records.Note note = notesById.get(card.noteId);
                if (note == null) {
                    continue;
                }
                if (card.suspended) {
                    ContentValues values = new ContentValues();
                    values.put("card_id", card.cardId);
                    values.put("note_id", card.noteId);
                    values.put("deck_name", card.deckName);
                    values.put("model_name", note.modelName);
                    values.put("expression", TextUtil.normalizeJapanese(note.expression(settings)));
                    values.put("reading", TextUtil.normalizeJapanese(note.reading(settings)));
                    values.put("meaning", TextUtil.firstMeaningLine(note.meaning(settings)));
                    values.put("sentence", TextUtil.normalizeJapanese(note.sentence(settings)));
                    values.put("fields_json", fieldsJson(note.fields));
                    values.put("archived_at", finishedAt);
                    values.put("archived_sync_id", syncId);
                    db.insertWithOnConflict("suspended_archive", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                } else {
                    ContentValues values = new ContentValues();
                    values.put("card_id", card.cardId);
                    values.put("note_id", card.noteId);
                    values.put("deck_name", card.deckName);
                    values.put("ord", card.ord);
                    values.put("queue", card.queue);
                    values.put("type", card.type);
                    values.put("due", card.due);
                    values.put("interval_days", card.intervalDays);
                    values.put("reps", card.reps);
                    values.put("lapses", card.lapses);
                    putNullableDouble(values, "fsrs_stability", card.fsrsStability);
                    putNullableDouble(values, "fsrs_difficulty", card.fsrsDifficulty);
                    putNullableDouble(values, "fsrs_retrievability", card.fsrsRetrievability);
                    values.put("last_seen_sync_id", syncId);
                    db.insertWithOnConflict("source_cards", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            for (Records.SuspendedImport imported : imports) {
                ContentValues values = new ContentValues();
                values.put("kanji", imported.kanji);
                if (imported.jitenRank != null) {
                    values.put("jiten_rank", imported.jitenRank);
                }
                values.put("rank_known", imported.rankKnown ? 1 : 0);
                values.put("cutoff_used", imported.cutoffUsed);
                values.put("first_imported_at", firstImportedAt(db, imported.kanji, finishedAt));
                values.put("last_seen_sync_id", syncId);
                db.insertWithOnConflict("suspended_imports", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                for (Records.SuspendedSource source : imported.sources) {
                    ContentValues sourceValues = new ContentValues();
                    sourceValues.put("kanji", imported.kanji);
                    sourceValues.put("card_id", source.cardId);
                    sourceValues.put("note_id", source.noteId);
                    sourceValues.put("expression", source.expression);
                    sourceValues.put("reading", source.reading);
                    sourceValues.put("meaning", source.meaning);
                    sourceValues.put("sentence", source.sentence);
                    sourceValues.put("sync_id", syncId);
                    db.insertWithOnConflict("suspended_sources", null, sourceValues, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }

            saveRows(db, rows, finishedAt);
            appendSyncTimelineEvents(db, previousRows, imports, rows, syncId, finishedAt, settings);
            db.setTransactionSuccessful();
            return syncId;
        } finally {
            db.endTransaction();
        }
    }

    public void saveFailedSync(long startedAt, long finishedAt, String status, String errorCode, String errorMessage) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("started_at", startedAt);
        values.put("finished_at", finishedAt);
        values.put("status", status);
        values.put("active_notes_count", 0);
        values.put("active_cards_count", 0);
        values.put("suspended_cards_archived_count", 0);
        values.put("suspended_kanji_imported_count", 0);
        values.put("deleted_notes_count", 0);
        values.put("deleted_cards_count", 0);
        values.put("error_code", errorCode);
        values.put("error_message", errorMessage);
        values.put("removal_message", "");
        db.insert("sync_runs", null, values);
    }

    public void updateSyncRemovalMessage(long syncId, String message) {
        ContentValues values = new ContentValues();
        values.put("removal_message", message == null ? "" : message);
        getWritableDatabase().update("sync_runs", values, "id=?", new String[]{Long.toString(syncId)});
    }

    public List<Records.DashboardRow> dashboardRows() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.DashboardRow> rows = new ArrayList<>();
        Cursor cursor = db.query("dashboard_rows", null, null, null, null, null, "weakness_score DESC, suspended_example_count DESC, kanji ASC", "120");
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, "kanji");
                rows.add(new Records.DashboardRow(
                        kanji,
                        nullableInt(cursor, "jiten_rank"),
                        string(cursor, "primary_meaning"),
                        string(cursor, "reading"),
                        string(cursor, "browser_search"),
                        integer(cursor, "weakness_score"),
                        string(cursor, "reason_code"),
                        string(cursor, "reason_text"),
                        integer(cursor, "active_example_count"),
                        integer(cursor, "suspended_example_count"),
                        integer(cursor, "mature_support_count"),
                        examplesForKanji(db, kanji)
                ));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    public Records.DashboardRow rowForKanji(String kanji) {
        return readDashboardRow(getReadableDatabase(), kanji);
    }

    public Records.KanjiRecoveryTimeline timelineForKanji(String kanji) {
        SQLiteDatabase db = getReadableDatabase();
        Records.DashboardRow row = readDashboardRow(db, kanji);
        Records.StudyItem item = studyItemForKanji(db, kanji);
        List<Records.KanjiTimelineEvent> events = new ArrayList<>();
        Cursor cursor = db.query(
                "kanji_timeline_events",
                null,
                "kanji=?",
                new String[]{kanji},
                null,
                null,
                "occurred_at DESC, id DESC",
                "50"
        );
        try {
            while (cursor.moveToNext()) {
                events.add(readTimelineEvent(cursor));
            }
        } finally {
            cursor.close();
        }
        Collections.reverse(events);
        return new Records.KanjiRecoveryTimeline(row, item, events);
    }

    public List<Records.StudyItem> studyItems() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.StudyItem> items = new ArrayList<>();
        Cursor cursor = db.query("study_items", null, null, null, null, null, "due_at ASC");
        try {
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<Records.SuspendedImport> suspendedImports() {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        Cursor cursor = db.query("suspended_imports", null, null, null, null, null, "jiten_rank ASC, kanji ASC");
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, "kanji");
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, "jiten_rank"),
                        integer(cursor, "rank_known") == 1,
                        integer(cursor, "cutoff_used")
                ));
            }
        } finally {
            cursor.close();
        }

        Cursor sources = db.query("suspended_sources", null, null, null, null, null, "kanji ASC, card_id ASC");
        try {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, "kanji"));
                if (imported == null) {
                    continue;
                }
                imported.sources.add(new Records.SuspendedSource(
                        imported.kanji,
                        longValue(sources, "card_id"),
                        longValue(sources, "note_id"),
                        string(sources, "expression"),
                        string(sources, "reading"),
                        string(sources, "meaning"),
                        string(sources, "sentence")
                ));
            }
        } finally {
            sources.close();
        }

        List<Records.SuspendedImport> out = new ArrayList<>();
        for (MutableSuspendedImport imported : imports.values()) {
            out.add(imported.build());
        }
        return out;
    }

    public void replaceStudyItems(List<Records.StudyItem> items) {
        replaceStudyItems(items, null, 0L, null);
    }

    public void replaceStudyItems(List<Records.StudyItem> items, Long syncId, long occurredAt, Records.Settings settings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, StudySnapshot> previous = syncId == null ? Collections.emptyMap() : studySnapshots(db);
            db.delete("study_items", null, null);
            for (Records.StudyItem item : items) {
                upsertStudyItem(db, item);
            }
            if (syncId != null) {
                appendStudyStateTimelineEvents(db, previous, items, syncId, occurredAt, settings);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveStudyItem(Records.StudyItem item) {
        upsertStudyItem(getWritableDatabase(), item);
    }

    public void saveReview(Records.ReviewRequest request, String appliedRating, long reviewedAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long inserted = insertReview(db, request, appliedRating, reviewedAt);
            if (inserted != -1L) {
                appendReviewTimelineEvent(db, request, appliedRating, reviewedAt, "review:" + request.token);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private long insertReview(SQLiteDatabase db, Records.ReviewRequest request, String appliedRating, long reviewedAt) {
        ContentValues values = new ContentValues();
        values.put("kanji", request.kanji);
        values.put("token", request.token);
        values.put("rating", appliedRating);
        values.put("writing_required", request.writingRequired ? 1 : 0);
        values.put("writing_passed", request.writingPassed ? 1 : 0);
        values.put("manual_override", request.manualOverride ? 1 : 0);
        values.put("reviewed_at", reviewedAt);
        return db.insertWithOnConflict("review_log", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<String> consumedTokens() {
        List<String> tokens = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("review_log", new String[]{"token"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                tokens.add(string(cursor, "token"));
            }
        } finally {
            cursor.close();
        }
        return tokens;
    }

    public SyncStatus latestSync() {
        Cursor cursor = getReadableDatabase().query("sync_runs", null, null, null, null, null, "id DESC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new SyncStatus(
                    string(cursor, "status"),
                    integer(cursor, "active_notes_count"),
                    integer(cursor, "active_cards_count"),
                    integer(cursor, "suspended_cards_archived_count"),
                    integer(cursor, "suspended_kanji_imported_count"),
                    longValue(cursor, "finished_at"),
                    string(cursor, "error_message"),
                    string(cursor, "removal_message")
            );
        } finally {
            cursor.close();
        }
    }

    public boolean hasSuccessfulSyncSince(long finishedAtMillis) {
        Cursor cursor = getReadableDatabase().query(
                "sync_runs",
                new String[]{"id"},
                "status=? AND finished_at>=?",
                new String[]{"success", Long.toString(finishedAtMillis)},
                null,
                null,
                "id DESC",
                "1"
        );
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public int getIntSetting(String key, int fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Integer.parseInt(string(cursor, "value"));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } finally {
            cursor.close();
        }
    }

    public long getLongSetting(String key, long fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Long.parseLong(string(cursor, "value"));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } finally {
            cursor.close();
        }
    }

    public String getStringSetting(String key, String fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            String value = string(cursor, "value");
            return value == null ? fallback : value;
        } finally {
            cursor.close();
        }
    }

    public double getDoubleSetting(String key, double fallback) {
        Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return fallback;
            }
            try {
                return Double.parseDouble(string(cursor, "value"));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } finally {
            cursor.close();
        }
    }

    public void putIntSetting(String key, int value) {
        putSetting(key, Integer.toString(value));
    }

    public void putLongSetting(String key, long value) {
        putSetting(key, Long.toString(value));
    }

    public void putStringSetting(String key, String value) {
        putSetting(key, value == null ? "" : value);
    }

    public void putDoubleSetting(String key, double value) {
        putSetting(key, String.format(Locale.ROOT, "%.4f", value));
    }

    public ReminderSettings reminderSettings() {
        return new ReminderSettings(
                getIntSetting("reminder_enabled", 0) == 1,
                getIntSetting("reminder_hour", DEFAULT_REMINDER_HOUR),
                getIntSetting("reminder_minute", DEFAULT_REMINDER_MINUTE)
        ).normalized();
    }

    public void saveReminderSettings(ReminderSettings settings) {
        ReminderSettings normalized = settings.normalized();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("reminder_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("reminder_hour", normalized.hour);
            putIntSetting("reminder_minute", normalized.minute);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public AutoSyncSettings autoSyncSettings() {
        return new AutoSyncSettings(
                getIntSetting("auto_sync_configured", 0) == 1,
                getIntSetting("auto_sync_enabled", 0) == 1,
                getIntSetting("auto_sync_hour", DEFAULT_AUTO_SYNC_HOUR),
                getIntSetting("auto_sync_minute", DEFAULT_AUTO_SYNC_MINUTE),
                getLongSetting("auto_sync_last_attempt_at", 0L),
                getLongSetting("auto_sync_last_success_at", 0L),
                getLongSetting("auto_sync_next_run_at", 0L)
        ).normalized();
    }

    public boolean activateAutoSyncAfterFirstSuccess() {
        AutoSyncSettings current = autoSyncSettings();
        if (current.configured) {
            return false;
        }
        saveAutoSyncSettings(new AutoSyncSettings(true, true, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
        return true;
    }

    public void setAutoSyncEnabled(boolean enabled) {
        AutoSyncSettings current = autoSyncSettings();
        saveAutoSyncSettings(new AutoSyncSettings(true, enabled, current.hour, current.minute, current.lastAttemptAt, current.lastSuccessAt, current.nextRunAt));
    }

    public void markAutoSyncScheduled(long nextRunAt) {
        putLongSetting("auto_sync_next_run_at", nextRunAt);
    }

    public void recordAutoSyncAttempt(long attemptedAt, boolean success) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting("auto_sync_last_attempt_at", attemptedAt);
            if (success) {
                putLongSetting("auto_sync_last_success_at", attemptedAt);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveAutoSyncSettings(AutoSyncSettings settings) {
        AutoSyncSettings normalized = settings.normalized();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putIntSetting("auto_sync_configured", normalized.configured ? 1 : 0);
            putIntSetting("auto_sync_enabled", normalized.enabled ? 1 : 0);
            putIntSetting("auto_sync_hour", normalized.hour);
            putIntSetting("auto_sync_minute", normalized.minute);
            putLongSetting("auto_sync_last_attempt_at", normalized.lastAttemptAt);
            putLongSetting("auto_sync_last_success_at", normalized.lastSuccessAt);
            putLongSetting("auto_sync_next_run_at", normalized.nextRunAt);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public AutoUpdateStatus autoUpdateStatus() {
        return new AutoUpdateStatus(
                getIntSetting(KEY_AUTO_UPDATE_ENABLED, 1) == 1,
                getLongSetting(KEY_AUTO_UPDATE_LAST_CHECK_AT, 0L),
                getStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, "No automatic update check has run yet."),
                getStringSetting(KEY_AUTO_UPDATE_LAST_VERSION, ""),
                getStringSetting(KEY_AUTO_UPDATE_PENDING_APK, ""),
                getStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, "")
        );
    }

    public void saveAutoUpdateEnabled(boolean enabled) {
        putIntSetting(KEY_AUTO_UPDATE_ENABLED, enabled ? 1 : 0);
    }

    public void recordAutoUpdateResult(long checkedAt, String result, String version, String pendingApkName, String pendingMessage) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putLongSetting(KEY_AUTO_UPDATE_LAST_CHECK_AT, checkedAt);
            putStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, result == null ? "" : result);
            putStringSetting(KEY_AUTO_UPDATE_LAST_VERSION, version == null ? "" : version);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_APK, pendingApkName == null ? "" : pendingApkName);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, pendingMessage == null ? "" : pendingMessage);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearPendingAutoUpdate(String result) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putStringSetting(KEY_AUTO_UPDATE_LAST_RESULT, result == null ? "" : result);
            putStringSetting(KEY_AUTO_UPDATE_PENDING_APK, "");
            putStringSetting(KEY_AUTO_UPDATE_PENDING_MESSAGE, "");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.SchedulerParameters schedulerParameters() {
        Records.SchedulerParameters defaults = Records.SchedulerParameters.defaults();
        return new Records.SchedulerParameters(
                getDoubleSetting("scheduler_target_retention", defaults.targetRetention),
                getDoubleSetting("scheduler_again_multiplier", defaults.againMultiplier),
                getDoubleSetting("scheduler_hard_multiplier", defaults.hardMultiplier),
                getDoubleSetting("scheduler_good_multiplier", defaults.goodMultiplier),
                getDoubleSetting("scheduler_easy_multiplier", defaults.easyMultiplier),
                getLongSetting("scheduler_last_adjusted_at", defaults.lastAdjustedAtMillis),
                getIntSetting("scheduler_last_adjustment_review_count", defaults.lastAdjustmentReviewCount)
        );
    }

    public void saveSchedulerParameters(Records.SchedulerParameters parameters) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            putDoubleSetting("scheduler_target_retention", parameters.targetRetention);
            putDoubleSetting("scheduler_again_multiplier", parameters.againMultiplier);
            putDoubleSetting("scheduler_hard_multiplier", parameters.hardMultiplier);
            putDoubleSetting("scheduler_good_multiplier", parameters.goodMultiplier);
            putDoubleSetting("scheduler_easy_multiplier", parameters.easyMultiplier);
            putLongSetting("scheduler_last_adjusted_at", parameters.lastAdjustedAtMillis);
            putIntSetting("scheduler_last_adjustment_review_count", parameters.lastAdjustmentReviewCount);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.ReviewStats reviewStatsSince(long sinceMillis) {
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"rating", "writing_required", "writing_passed", "manual_override"},
                "reviewed_at>=?",
                new String[]{Long.toString(sinceMillis)},
                null,
                null,
                null
        );
        int total = 0;
        int again = 0;
        int hard = 0;
        int good = 0;
        int easy = 0;
        int writingRequired = 0;
        int writingFailed = 0;
        try {
            while (cursor.moveToNext()) {
                total++;
                String rating = string(cursor, "rating");
                if ("again".equals(rating)) {
                    again++;
                } else if ("hard".equals(rating)) {
                    hard++;
                } else if ("easy".equals(rating)) {
                    easy++;
                } else {
                    good++;
                }
                boolean required = integer(cursor, "writing_required") == 1;
                boolean passed = integer(cursor, "writing_passed") == 1;
                boolean override = integer(cursor, "manual_override") == 1;
                if (required) {
                    writingRequired++;
                    if (!passed && !override) {
                        writingFailed++;
                    }
                }
            }
        } finally {
            cursor.close();
        }
        return new Records.ReviewStats(total, again, hard, good, easy, writingRequired, writingFailed);
    }

    public StudyStreak studyStreak(long nowMillis) {
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"reviewed_at"},
                null,
                null,
                null,
                null,
                "reviewed_at DESC"
        );
        List<Long> days = new ArrayList<>();
        int reviewsToday = 0;
        long today = localDayStart(nowMillis);
        long tomorrow = moveLocalDays(today, 1);
        long lastStudyAt = 0L;
        try {
            long lastAddedDay = Long.MIN_VALUE;
            while (cursor.moveToNext()) {
                long reviewedAt = cursor.getLong(cursor.getColumnIndexOrThrow("reviewed_at"));
                if (lastStudyAt == 0L) {
                    lastStudyAt = reviewedAt;
                }
                if (reviewedAt >= today && reviewedAt < tomorrow) {
                    reviewsToday++;
                }
                long day = localDayStart(reviewedAt);
                if (day != lastAddedDay) {
                    days.add(day);
                    lastAddedDay = day;
                }
            }
        } finally {
            cursor.close();
        }
        if (days.isEmpty()) {
            return new StudyStreak(0, 0, false, 0, 0L);
        }

        long yesterday = moveLocalDays(today, -1);
        boolean studiedToday = days.get(0) == today;
        int current = 0;
        if (studiedToday || days.get(0) == yesterday) {
            long expected = studiedToday ? today : yesterday;
            for (long day : days) {
                if (day != expected) {
                    break;
                }
                current++;
                expected = moveLocalDays(expected, -1);
            }
        }

        int best = 0;
        int run = 0;
        long expectedPrevious = Long.MIN_VALUE;
        for (int i = days.size() - 1; i >= 0; i--) {
            long day = days.get(i);
            if (run == 0 || day == moveLocalDays(expectedPrevious, 1)) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            expectedPrevious = day;
        }
        return new StudyStreak(current, best, studiedToday, reviewsToday, lastStudyAt);
    }

    public StudyImpactStats studyImpactStats() {
        Cursor cursor = getReadableDatabase().query(
                "review_log",
                new String[]{"kanji", "writing_required", "writing_passed", "manual_override"},
                null,
                null,
                null,
                null,
                null
        );
        Set<String> reviewedKanji = new HashSet<>();
        int total = 0;
        int writingRequired = 0;
        int writingPassed = 0;
        int writingFailed = 0;
        int manualOverrides = 0;
        try {
            while (cursor.moveToNext()) {
                total++;
                reviewedKanji.add(string(cursor, "kanji"));
                boolean required = integer(cursor, "writing_required") == 1;
                boolean passed = integer(cursor, "writing_passed") == 1;
                boolean override = integer(cursor, "manual_override") == 1;
                if (required) {
                    writingRequired++;
                    if (passed) {
                        writingPassed++;
                    } else if (!override) {
                        writingFailed++;
                    }
                }
                if (override) {
                    manualOverrides++;
                }
            }
        } finally {
            cursor.close();
        }
        return new StudyImpactStats(total, reviewedKanji.size(), writingRequired, writingPassed, writingFailed, manualOverrides);
    }

    private void createTimelineTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kanji_timeline_events (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, occurred_at INTEGER NOT NULL, event_type TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, source_expression TEXT NOT NULL, source_reading TEXT NOT NULL, rating TEXT NOT NULL, writing_required INTEGER NOT NULL DEFAULT 0, writing_passed INTEGER NOT NULL DEFAULT 0, manual_override INTEGER NOT NULL DEFAULT 0, weakness_score INTEGER, mature_support_count INTEGER, sync_id INTEGER, dedupe_key TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_timeline_dedupe ON kanji_timeline_events(dedupe_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timeline_kanji_time ON kanji_timeline_events(kanji, occurred_at, id)");
    }

    private void addNullableColumn(SQLiteDatabase db, String table, String column, String type) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (RuntimeException error) {
            if (error.getMessage() == null || !error.getMessage().contains("duplicate column")) {
                throw error;
            }
        }
    }

    private void backfillTimelineEvents(SQLiteDatabase db) {
        Map<String, RowSnapshot> rows = rowSnapshots(db);

        Cursor imports = db.query("suspended_imports", null, null, null, null, null, "first_imported_at ASC, kanji ASC");
        try {
            while (imports.moveToNext()) {
                String kanji = string(imports, "kanji");
                SourceSnapshot source = firstSuspendedSourceForKanji(db, kanji);
                long importedAt = longValue(imports, "first_imported_at");
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
                        longValue(imports, "last_seen_sync_id"),
                        "suspended_imported:" + kanji
                );
            }
        } finally {
            imports.close();
        }

        for (RowSnapshot row : rows.values()) {
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    "first_seen",
                    "Kani started watching",
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
                    "first_seen:" + row.kanji
            );
            insertTimelineEvent(
                    db,
                    row.kanji,
                    row.rebuiltAt == 0L ? System.currentTimeMillis() : row.rebuiltAt,
                    "weak_support_seen",
                    "Weak support seen",
                    supportDetail("Anki evidence still needs repair", row.matureSupportCount, Records.Settings.kikuDefaults().matureSupportThreshold),
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

        Cursor study = db.query("study_items", null, null, null, null, null, "created_at ASC, kanji ASC");
        try {
            while (study.moveToNext()) {
                String kanji = string(study, "kanji");
                long createdAt = longValue(study, "created_at");
                RowSnapshot row = rows.get(kanji);
                SourceSnapshot source = row == null ? firstExampleForKanji(db, kanji) : row.source;
                if (row == null) {
                    insertTimelineEvent(
                            db,
                            kanji,
                            createdAt == 0L ? System.currentTimeMillis() : createdAt,
                            "first_seen",
                            "Kani started watching",
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
                            "first_seen:" + kanji
                    );
                }
                if ("retired".equals(string(study, "state"))) {
                    Integer mature = row == null ? null : row.matureSupportCount;
                    insertTimelineEvent(
                            db,
                            kanji,
                            createdAt == 0L ? System.currentTimeMillis() : createdAt,
                            "retired",
                            "Retired by Anki support",
                            mature == null
                                    ? "Kani had already retired this repair before timeline tracking was added."
                                    : supportDetail("Mature Anki support met the target", mature, Records.Settings.kikuDefaults().matureSupportThreshold),
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
        } finally {
            study.close();
        }

        Cursor reviews = db.query("review_log", null, null, null, null, null, "reviewed_at ASC, id ASC");
        try {
            while (reviews.moveToNext()) {
                Records.ReviewRequest request = new Records.ReviewRequest(
                        string(reviews, "kanji"),
                        string(reviews, "token"),
                        string(reviews, "rating"),
                        integer(reviews, "writing_required") == 1,
                        integer(reviews, "writing_passed") == 1,
                        integer(reviews, "manual_override") == 1,
                        0
                );
                appendReviewTimelineEvent(db, request, string(reviews, "rating"), longValue(reviews, "reviewed_at"), "review:" + request.token);
            }
        } finally {
            reviews.close();
        }
    }

    private void appendSyncTimelineEvents(
            SQLiteDatabase db,
            Map<String, RowSnapshot> previousRows,
            List<Records.SuspendedImport> imports,
            List<Records.DashboardRow> rows,
            long syncId,
            long occurredAt,
            Records.Settings settings
    ) {
        int target = settings == null ? Records.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (Records.SuspendedImport imported : imports) {
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

        for (Records.DashboardRow row : rows) {
            RowSnapshot previous = previousRows.get(row.kanji);
            SourceSnapshot source = sourceForRow(row);
            insertTimelineEvent(
                    db,
                    row.kanji,
                    occurredAt,
                    "first_seen",
                    "Kani started watching",
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
                    "first_seen:" + row.kanji
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

    private void appendStudyStateTimelineEvents(
            SQLiteDatabase db,
            Map<String, StudySnapshot> previousItems,
            List<Records.StudyItem> currentItems,
            long syncId,
            long occurredAt,
            Records.Settings settings
    ) {
        int target = settings == null ? Records.Settings.kikuDefaults().matureSupportThreshold : settings.matureSupportThreshold;
        for (Records.StudyItem item : currentItems) {
            StudySnapshot previous = previousItems.get(item.kanji);
            if (previous == null) {
                continue;
            }
            RowSnapshot row = rowSnapshot(db, item.kanji);
            SourceSnapshot source = row == null ? firstExampleForKanji(db, item.kanji) : row.source;
            if (!"retired".equals(previous.state) && "retired".equals(item.state)) {
                Integer mature = row == null ? null : row.matureSupportCount;
                insertTimelineEvent(
                        db,
                        item.kanji,
                        occurredAt,
                        "retired",
                        "Retired by Anki support",
                        mature == null
                                ? "No weak Anki evidence remained after sync, so Kani retired this repair."
                                : supportDetail("Mature Anki support met the target", mature, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row == null ? null : row.weaknessScore,
                        mature,
                        syncId,
                        "retired:" + item.kanji + ":" + syncId
                );
            } else if ("retired".equals(previous.state) && !"retired".equals(item.state)) {
                Integer mature = row == null ? null : row.matureSupportCount;
                insertTimelineEvent(
                        db,
                        item.kanji,
                        occurredAt,
                        "reopened",
                        "Repair reopened",
                        mature == null
                                ? "Kani reopened this kanji after sync found weak evidence again."
                                : supportDetail("Mature Anki support fell below target", mature, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row == null ? null : row.weaknessScore,
                        mature,
                        syncId,
                        "reopened:" + item.kanji + ":" + syncId
                );
            }
        }
    }

    private void appendReviewTimelineEvent(SQLiteDatabase db, Records.ReviewRequest request, String appliedRating, long reviewedAt, String dedupeKey) {
        String eventType;
        String title;
        if (request.manualOverride) {
            eventType = "manual_override";
            title = "Manual override";
        } else if ("again".equals(appliedRating) || (request.writingRequired && !request.writingPassed)) {
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

    private String reviewDetail(Records.ReviewRequest request, String appliedRating) {
        if (request.manualOverride) {
            return "Saved as " + appliedRating + " after manual confirmation.";
        }
        if ("again".equals(appliedRating)) {
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

    private String supportDetail(String prefix, int matureSupportCount, int target) {
        return prefix + ": mature support " + matureSupportCount + " / target " + target + ".";
    }

    private Records.DashboardRow readDashboardRow(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("dashboard_rows", null, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readDashboardRow(db, cursor);
        } finally {
            cursor.close();
        }
    }

    private Records.DashboardRow readDashboardRow(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, "kanji");
        return new Records.DashboardRow(
                kanji,
                nullableInt(cursor, "jiten_rank"),
                string(cursor, "primary_meaning"),
                string(cursor, "reading"),
                string(cursor, "browser_search"),
                integer(cursor, "weakness_score"),
                string(cursor, "reason_code"),
                string(cursor, "reason_text"),
                integer(cursor, "active_example_count"),
                integer(cursor, "suspended_example_count"),
                integer(cursor, "mature_support_count"),
                examplesForKanji(db, kanji)
        );
    }

    private Records.StudyItem studyItemForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("study_items", null, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? readStudyItem(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private Records.KanjiTimelineEvent readTimelineEvent(Cursor cursor) {
        return new Records.KanjiTimelineEvent(
                longValue(cursor, "id"),
                string(cursor, "kanji"),
                longValue(cursor, "occurred_at"),
                string(cursor, "event_type"),
                string(cursor, "title"),
                string(cursor, "detail"),
                string(cursor, "source_expression"),
                string(cursor, "source_reading"),
                string(cursor, "rating"),
                integer(cursor, "writing_required") == 1,
                integer(cursor, "writing_passed") == 1,
                integer(cursor, "manual_override") == 1,
                nullableInt(cursor, "weakness_score"),
                nullableInt(cursor, "mature_support_count"),
                nullableLong(cursor, "sync_id"),
                string(cursor, "dedupe_key")
        );
    }

    private void insertTimelineEvent(
            SQLiteDatabase db,
            String kanji,
            long occurredAt,
            String eventType,
            String title,
            String detail,
            String sourceExpression,
            String sourceReading,
            String rating,
            boolean writingRequired,
            boolean writingPassed,
            boolean manualOverride,
            Integer weaknessScore,
            Integer matureSupportCount,
            Long syncId,
            String dedupeKey
    ) {
        ContentValues values = new ContentValues();
        values.put("kanji", kanji);
        values.put("occurred_at", occurredAt);
        values.put("event_type", eventType == null ? "" : eventType);
        values.put("title", title == null ? "" : title);
        values.put("detail", detail == null ? "" : detail);
        values.put("source_expression", sourceExpression == null ? "" : sourceExpression);
        values.put("source_reading", sourceReading == null ? "" : sourceReading);
        values.put("rating", rating == null ? "" : rating);
        values.put("writing_required", writingRequired ? 1 : 0);
        values.put("writing_passed", writingPassed ? 1 : 0);
        values.put("manual_override", manualOverride ? 1 : 0);
        if (weaknessScore == null) {
            values.putNull("weakness_score");
        } else {
            values.put("weakness_score", weaknessScore);
        }
        if (matureSupportCount == null) {
            values.putNull("mature_support_count");
        } else {
            values.put("mature_support_count", matureSupportCount);
        }
        if (syncId == null) {
            values.putNull("sync_id");
        } else {
            values.put("sync_id", syncId);
        }
        values.put("dedupe_key", dedupeKey);
        db.insertWithOnConflict("kanji_timeline_events", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private Map<String, RowSnapshot> rowSnapshots(SQLiteDatabase db) {
        Map<String, RowSnapshot> rows = new LinkedHashMap<>();
        Cursor cursor = db.query("dashboard_rows", null, null, null, null, null, "kanji ASC");
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

    private RowSnapshot rowSnapshot(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("dashboard_rows", null, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? rowSnapshotFromCursor(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private RowSnapshot rowSnapshotFromCursor(SQLiteDatabase db, Cursor cursor) {
        String kanji = string(cursor, "kanji");
        return new RowSnapshot(
                kanji,
                integer(cursor, "weakness_score"),
                integer(cursor, "mature_support_count"),
                longValue(cursor, "rebuilt_at"),
                firstExampleForKanji(db, kanji)
        );
    }

    private Map<String, StudySnapshot> studySnapshots(SQLiteDatabase db) {
        Map<String, StudySnapshot> items = new HashMap<>();
        Cursor cursor = db.query("study_items", new String[]{"kanji", "state"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                items.put(string(cursor, "kanji"), new StudySnapshot(string(cursor, "kanji"), string(cursor, "state")));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private SourceSnapshot firstExampleForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("kanji_examples", new String[]{"expression", "reading"}, "kanji=?", new String[]{kanji}, null, null, "source_type ASC, id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, "expression"), string(cursor, "reading"));
        } finally {
            cursor.close();
        }
    }

    private SourceSnapshot firstSuspendedSourceForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query("suspended_sources", new String[]{"expression", "reading"}, "kanji=?", new String[]{kanji}, null, null, "card_id ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return SourceSnapshot.EMPTY;
            }
            return new SourceSnapshot(string(cursor, "expression"), string(cursor, "reading"));
        } finally {
            cursor.close();
        }
    }

    private SourceSnapshot sourceFromImport(Records.SuspendedImport imported) {
        if (imported.sources.isEmpty()) {
            return SourceSnapshot.EMPTY;
        }
        Records.SuspendedSource source = imported.sources.get(0);
        return new SourceSnapshot(source.expression, source.reading);
    }

    private SourceSnapshot sourceForRow(Records.DashboardRow row) {
        Records.Example fallback = null;
        for (Records.Example example : row.examples) {
            if ("active".equals(example.sourceType)) {
                return new SourceSnapshot(example.expression, example.reading);
            }
            if (fallback == null) {
                fallback = example;
            }
        }
        return fallback == null ? SourceSnapshot.EMPTY : new SourceSnapshot(fallback.expression, fallback.reading);
    }

    private static long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long moveLocalDays(long localDayStart, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localDayStart);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void putSetting(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private long insertSyncRun(SQLiteDatabase db, long startedAt, long finishedAt, String status, ActiveCardIndex activeIndex, int importCount, String errorCode, String errorMessage, String removalMessage, int deletedNotes, int deletedCards) {
        ContentValues values = new ContentValues();
        values.put("started_at", startedAt);
        values.put("finished_at", finishedAt);
        values.put("status", status);
        values.put("active_notes_count", activeIndex.noteIds.size());
        values.put("active_cards_count", activeIndex.activeCardCount);
        values.put("suspended_cards_archived_count", activeIndex.suspendedCardCount);
        values.put("suspended_kanji_imported_count", importCount);
        values.put("deleted_notes_count", deletedNotes);
        values.put("deleted_cards_count", deletedCards);
        values.put("error_code", errorCode);
        values.put("error_message", errorMessage);
        values.put("removal_message", removalMessage);
        return db.insert("sync_runs", null, values);
    }

    private void saveRows(SQLiteDatabase db, List<Records.DashboardRow> rows, long rebuiltAt) {
        for (Records.DashboardRow row : rows) {
            ContentValues values = new ContentValues();
            values.put("kanji", row.kanji);
            if (row.jitenRank != null) {
                values.put("jiten_rank", row.jitenRank);
            }
            values.put("primary_meaning", row.primaryMeaning);
            values.put("reading", row.reading);
            values.put("browser_search", row.browserSearch);
            values.put("weakness_score", row.weaknessScore);
            values.put("reason_code", row.reasonCode);
            values.put("reason_text", row.reasonText);
            values.put("active_example_count", row.activeExampleCount);
            values.put("suspended_example_count", row.suspendedExampleCount);
            values.put("mature_support_count", row.matureSupportCount);
            values.put("rebuilt_at", rebuiltAt);
            db.insertWithOnConflict("dashboard_rows", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            for (Records.Example example : row.examples) {
                ContentValues ex = new ContentValues();
                ex.put("kanji", row.kanji);
                ex.put("source_type", example.sourceType);
                ex.put("card_id", example.cardId);
                ex.put("note_id", example.noteId);
                ex.put("expression", example.expression);
                ex.put("reading", example.reading);
                ex.put("meaning", example.meaning);
                ex.put("sentence", example.sentence);
                ex.put("mature", example.mature ? 1 : 0);
                ex.put("lapses", example.lapses);
                ex.put("interval_days", example.intervalDays);
                ex.put("reps", example.reps);
                putNullableDouble(ex, "fsrs_stability", example.fsrsStability);
                putNullableDouble(ex, "fsrs_difficulty", example.fsrsDifficulty);
                putNullableDouble(ex, "fsrs_retrievability", example.fsrsRetrievability);
                db.insert("kanji_examples", null, ex);
            }
        }
    }

    private List<Records.Example> examplesForKanji(SQLiteDatabase db, String kanji) {
        List<Records.Example> examples = new ArrayList<>();
        Cursor cursor = db.query("kanji_examples", null, "kanji=?", new String[]{kanji}, null, null, "source_type DESC, id ASC", "8");
        try {
            while (cursor.moveToNext()) {
                examples.add(new Records.Example(
                        string(cursor, "source_type"),
                        longValue(cursor, "card_id"),
                        longValue(cursor, "note_id"),
                        string(cursor, "expression"),
                        string(cursor, "reading"),
                        string(cursor, "meaning"),
                        string(cursor, "sentence"),
                        integer(cursor, "mature") == 1,
                        integer(cursor, "lapses"),
                        integer(cursor, "interval_days"),
                        integer(cursor, "reps"),
                        nullableDouble(cursor, "fsrs_stability"),
                        nullableDouble(cursor, "fsrs_difficulty"),
                        nullableDouble(cursor, "fsrs_retrievability")
                ));
            }
        } finally {
            cursor.close();
        }
        return examples;
    }

    private void upsertStudyItem(SQLiteDatabase db, Records.StudyItem item) {
        ContentValues values = new ContentValues();
        values.put("kanji", item.kanji);
        values.put("state", item.state);
        values.put("due_at", item.dueAtMillis);
        values.put("stability", item.stability);
        values.put("difficulty", item.difficulty);
        values.put("total_reviews", item.totalReviews);
        values.put("lapses", item.lapses);
        values.put("learning_step", item.learningStep);
        values.put("writing_level", item.writingLevel);
        values.put("active_token", item.activeToken);
        values.put("created_at", item.createdAtMillis);
        db.insertWithOnConflict("study_items", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private Records.StudyItem readStudyItem(Cursor cursor) {
        return new Records.StudyItem(
                string(cursor, "kanji"),
                string(cursor, "state"),
                longValue(cursor, "due_at"),
                cursor.getDouble(cursor.getColumnIndexOrThrow("stability")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("difficulty")),
                integer(cursor, "total_reviews"),
                integer(cursor, "lapses"),
                integer(cursor, "learning_step"),
                integer(cursor, "writing_level"),
                string(cursor, "active_token"),
                longValue(cursor, "created_at")
        );
    }

    private long firstImportedAt(SQLiteDatabase db, String kanji, long fallback) {
        Cursor cursor = db.query("suspended_imports", new String[]{"first_imported_at"}, "kanji=?", new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? longValue(cursor, "first_imported_at") : fallback;
        } finally {
            cursor.close();
        }
    }

    private ActiveCardIndex activeCardIndex(List<Records.Card> cards) {
        Set<Long> noteIds = new HashSet<>();
        Set<Long> cardIds = new HashSet<>();
        int activeCardCount = 0;
        int suspendedCardCount = 0;
        for (Records.Card card : cards) {
            if (card.suspended) {
                suspendedCardCount++;
            } else {
                activeCardCount++;
                noteIds.add(card.noteId);
                cardIds.add(card.cardId);
            }
        }
        return new ActiveCardIndex(noteIds, cardIds, activeCardCount, suspendedCardCount);
    }

    private int countDeletedExisting(SQLiteDatabase db, String table, String idColumn, Set<Long> currentIds) {
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

    private static final class MutableSuspendedImport {
        private final String kanji;
        private final Integer rank;
        private final boolean rankKnown;
        private final int cutoff;
        private final List<Records.SuspendedSource> sources = new ArrayList<>();

        private MutableSuspendedImport(String kanji, Integer rank, boolean rankKnown, int cutoff) {
            this.kanji = kanji;
            this.rank = rank;
            this.rankKnown = rankKnown;
            this.cutoff = cutoff;
        }

        private Records.SuspendedImport build() {
            return new Records.SuspendedImport(kanji, rank, rankKnown, cutoff, sources);
        }
    }

    private static final class ActiveCardIndex {
        private final Set<Long> noteIds;
        private final Set<Long> cardIds;
        private final int activeCardCount;
        private final int suspendedCardCount;

        private ActiveCardIndex(Set<Long> noteIds, Set<Long> cardIds, int activeCardCount, int suspendedCardCount) {
            this.noteIds = noteIds;
            this.cardIds = cardIds;
            this.activeCardCount = activeCardCount;
            this.suspendedCardCount = suspendedCardCount;
        }
    }

    private static final class SourceSnapshot {
        private static final SourceSnapshot EMPTY = new SourceSnapshot("", "");

        private final String expression;
        private final String reading;

        private SourceSnapshot(String expression, String reading) {
            this.expression = expression == null ? "" : expression;
            this.reading = reading == null ? "" : reading;
        }
    }

    private static final class RowSnapshot {
        private final String kanji;
        private final int weaknessScore;
        private final int matureSupportCount;
        private final long rebuiltAt;
        private final SourceSnapshot source;

        private RowSnapshot(String kanji, int weaknessScore, int matureSupportCount, long rebuiltAt, SourceSnapshot source) {
            this.kanji = kanji;
            this.weaknessScore = weaknessScore;
            this.matureSupportCount = matureSupportCount;
            this.rebuiltAt = rebuiltAt;
            this.source = source == null ? SourceSnapshot.EMPTY : source;
        }
    }

    private static final class StudySnapshot {
        private final String kanji;
        private final String state;

        private StudySnapshot(String kanji, String state) {
            this.kanji = kanji;
            this.state = state == null ? "" : state;
        }
    }

    private static String fieldsJson(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(TextUtil.jsonQuote(entry.getKey())).append(':').append(TextUtil.jsonQuote(entry.getValue()));
        }
        out.append('}');
        return out.toString();
    }

    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static int integer(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static Integer nullableInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getInt(index);
    }

    private static Long nullableLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getLong(index);
    }

    private static Double nullableDouble(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    private static long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private static void putNullableDouble(ContentValues values, String key, Double value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    public static final class SyncStatus {
        public final String status;
        public final int activeNotes;
        public final int activeCards;
        public final int suspendedCards;
        public final int importedKanji;
        public final long finishedAt;
        public final String errorMessage;
        public final String removalMessage;

        private SyncStatus(String status, int activeNotes, int activeCards, int suspendedCards, int importedKanji, long finishedAt, String errorMessage, String removalMessage) {
            this.status = status;
            this.activeNotes = activeNotes;
            this.activeCards = activeCards;
            this.suspendedCards = suspendedCards;
            this.importedKanji = importedKanji;
            this.finishedAt = finishedAt;
            this.errorMessage = errorMessage;
            this.removalMessage = removalMessage;
        }

        public String headline() {
            if (!"success".equals(status)) {
                return "Sync blocked: " + errorMessage;
            }
            return String.format(Locale.ROOT, "%d active cards checked, %d suspended cards archived, %d rare kanji added", activeCards, suspendedCards, importedKanji);
        }
    }

    public static final class ReminderSettings {
        public final boolean enabled;
        public final int hour;
        public final int minute;

        public ReminderSettings(boolean enabled, int hour, int minute) {
            this.enabled = enabled;
            this.hour = hour;
            this.minute = minute;
        }

        private ReminderSettings normalized() {
            int normalizedHour = Math.max(0, Math.min(23, hour));
            int normalizedMinute = Math.max(0, Math.min(59, minute));
            return new ReminderSettings(enabled, normalizedHour, normalizedMinute);
        }

        public String displayTime() {
            return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
    }

    public static final class AutoSyncSettings {
        public final boolean configured;
        public final boolean enabled;
        public final int hour;
        public final int minute;
        public final long lastAttemptAt;
        public final long lastSuccessAt;
        public final long nextRunAt;

        public AutoSyncSettings(boolean configured, boolean enabled, int hour, int minute, long lastAttemptAt, long lastSuccessAt, long nextRunAt) {
            this.configured = configured;
            this.enabled = enabled;
            this.hour = hour;
            this.minute = minute;
            this.lastAttemptAt = lastAttemptAt;
            this.lastSuccessAt = lastSuccessAt;
            this.nextRunAt = nextRunAt;
        }

        private AutoSyncSettings normalized() {
            int normalizedHour = Math.max(0, Math.min(23, hour));
            int normalizedMinute = Math.max(0, Math.min(59, minute));
            return new AutoSyncSettings(configured, configured && enabled, normalizedHour, normalizedMinute, Math.max(0L, lastAttemptAt), Math.max(0L, lastSuccessAt), Math.max(0L, nextRunAt));
        }

        public String displayTime() {
            return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
    }

    public static final class AutoUpdateStatus {
        public final boolean enabled;
        public final long lastCheckAtMillis;
        public final String lastResult;
        public final String lastVersion;
        public final String pendingApkName;
        public final String pendingMessage;

        private AutoUpdateStatus(boolean enabled, long lastCheckAtMillis, String lastResult, String lastVersion, String pendingApkName, String pendingMessage) {
            this.enabled = enabled;
            this.lastCheckAtMillis = lastCheckAtMillis;
            this.lastResult = lastResult == null ? "" : lastResult;
            this.lastVersion = lastVersion == null ? "" : lastVersion;
            this.pendingApkName = pendingApkName == null ? "" : pendingApkName;
            this.pendingMessage = pendingMessage == null ? "" : pendingMessage;
        }

        public boolean hasPendingUpdate() {
            return !pendingApkName.isEmpty();
        }
    }

    public static final class StudyStreak {
        public final int currentDays;
        public final int bestDays;
        public final boolean studiedToday;
        public final int reviewsToday;
        public final long lastStudyAtMillis;

        public StudyStreak(int currentDays, int bestDays, boolean studiedToday, int reviewsToday, long lastStudyAtMillis) {
            this.currentDays = currentDays;
            this.bestDays = bestDays;
            this.studiedToday = studiedToday;
            this.reviewsToday = reviewsToday;
            this.lastStudyAtMillis = lastStudyAtMillis;
        }
    }

    public static final class StudyImpactStats {
        public final int totalReviews;
        public final int distinctReviewedKanji;
        public final int writingRequired;
        public final int writingPassed;
        public final int writingFailed;
        public final int manualOverrides;

        public StudyImpactStats(int totalReviews, int distinctReviewedKanji, int writingRequired, int writingPassed, int writingFailed, int manualOverrides) {
            this.totalReviews = totalReviews;
            this.distinctReviewedKanji = distinctReviewedKanji;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            this.writingFailed = writingFailed;
            this.manualOverrides = manualOverrides;
        }
    }
}
