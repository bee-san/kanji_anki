package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
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

abstract class LocalStoreSync extends LocalStoreInventory {
    LocalStoreSync(Context context) {
        super(context);
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
            long syncId = insertSyncRun(db, new SyncRunInsert(
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
            saveSourceNotes(db, snapshot.notes, activeIndex, settings, syncId);
            saveSourceCardsAndArchive(db, snapshot.cards, notesById, selectedSuspendedCardIds, settings, timing.finishedAt, syncId);
            saveSuspendedImports(db, imports, timing.finishedAt, syncId);
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
        saveImportRuleAudit(db, settings, finishedAt, syncId);
        for (RecordsImportModels.SuspendedImport imported : imports) {
            saveImportDecision(db, imported, settings, finishedAt, syncId);
        }
    }

    void saveImportRuleAudit(SQLiteDatabase db, RecordsSyncModels.Settings settings, long finishedAt, long syncId) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_ID, syncId);
        values.put(COLUMN_CREATED_AT, finishedAt);
        values.put(COLUMN_MODEL_NAME, settings.modelName);
        values.put(COLUMN_ENABLED_SOURCES, String.join(" ", enabledImportSources(settings)));
        values.put("rank_min", settings.suspendedRankMin);
        values.put("rank_max", settings.suspendedRankMax);
        values.put("min_matching_cards", settings.importMinMatchingCardsPerKanji);
        values.put("import_tags", settings.importTagsText());
        values.put("weak_fsrs_difficulty", settings.importWeakFsrsDifficultyThreshold);
        values.put("weak_lapses", settings.importWeakLapsesThreshold);
        values.put("browser_query", settings.normalizedBrowserQuery());
        values.put(COLUMN_SETTINGS_JSON, importSettingsJson(settings));
        db.insertWithOnConflict(TABLE_IMPORT_RULE_AUDITS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void saveImportDecision(
            SQLiteDatabase db,
            RecordsImportModels.SuspendedImport imported,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        Set<String> sourceTypes = new LinkedHashSet<>();
        Set<String> ruleTypes = new LinkedHashSet<>();
        Set<Long> cardIds = new LinkedHashSet<>();
        Set<Long> noteIds = new LinkedHashSet<>();
        for (RecordsImportModels.SuspendedSource source : imported.sources) {
            sourceTypes.add(source.sourceType);
            ruleTypes.addAll(source.ruleTypes);
            cardIds.add(source.cardId);
            noteIds.add(source.noteId);
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_SYNC_ID, syncId);
        values.put(COLUMN_KANJI, imported.kanji);
        values.put(COLUMN_DECISION, "imported");
        values.put(COLUMN_REASON_CODE, importReasonCode(ruleTypes));
        values.put(COLUMN_REASON_TEXT, importReasonText(imported, settings, ruleTypes, cardIds.size()));
        if (imported.jitenRank != null) {
            values.put(COLUMN_JITEN_RANK, imported.jitenRank);
        }
        values.put(COLUMN_RANK_KNOWN, imported.rankKnown ? 1 : 0);
        values.put("rank_min", settings.suspendedRankMin);
        values.put("rank_max", settings.suspendedRankMax);
        values.put("min_matching_cards", settings.importMinMatchingCardsPerKanji);
        values.put(COLUMN_SOURCE_COUNT, cardIds.size());
        values.put(COLUMN_SOURCE_TYPES, String.join(" ", sourceTypes));
        values.put(COLUMN_RULE_TYPES, String.join(" ", ruleTypes));
        values.put(COLUMN_SOURCE_CARD_IDS, joinLongs(cardIds));
        values.put(COLUMN_SOURCE_NOTE_IDS, joinLongs(noteIds));
        values.put(COLUMN_CREATED_AT, finishedAt);
        db.insertWithOnConflict(TABLE_IMPORT_DECISIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void clearSyncMirrorTables(SQLiteDatabase db) {
        db.delete(TABLE_SOURCE_CARDS, null, null);
        db.delete(TABLE_SOURCE_NOTES, null, null);
        db.delete(TABLE_DASHBOARD_ROWS, null, null);
        db.delete(TABLE_KANJI_EXAMPLES, null, null);
    }

    void saveSourceNotes(
            SQLiteDatabase db,
            List<RecordsSyncModels.Note> notes,
            ActiveCardIndex activeIndex,
            RecordsSyncModels.Settings settings,
            long syncId
    ) {
        for (RecordsSyncModels.Note note : notes) {
            if (!activeIndex.noteIds.contains(note.noteId)) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_NOTE_ID, note.noteId);
            values.put(COLUMN_MODEL_NAME, note.modelName);
            values.put(COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)));
            values.put(COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)));
            values.put(COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)));
            values.put(COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)));
            values.put(COLUMN_FIELDS_JSON, fieldsJson(note.fields));
            values.put(COLUMN_TAGS, String.join(" ", note.tags));
            values.put(COLUMN_LAST_SEEN_SYNC_ID, syncId);
            db.insertWithOnConflict(TABLE_SOURCE_NOTES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void saveSourceCardsAndArchive(
            SQLiteDatabase db,
            List<RecordsSyncModels.Card> cards,
            Map<Long, RecordsSyncModels.Note> notesById,
            Set<Long> selectedSuspendedCardIds,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        for (RecordsSyncModels.Card card : cards) {
            RecordsSyncModels.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            if (card.suspended) {
                if (selectedSuspendedCardIds.contains(card.cardId)) {
                    saveSuspendedArchiveCard(db, card, note, settings, finishedAt, syncId);
                }
            } else {
                saveSourceCard(db, card, syncId);
            }
        }
    }

    void saveSuspendedArchiveCard(
            SQLiteDatabase db,
            RecordsSyncModels.Card card,
            RecordsSyncModels.Note note,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CARD_ID, card.cardId);
        values.put(COLUMN_NOTE_ID, card.noteId);
        values.put(COLUMN_DECK_NAME, card.deckName);
        values.put(COLUMN_MODEL_NAME, note.modelName);
        values.put(COLUMN_EXPRESSION, TextUtil.normalizeJapanese(note.expression(settings)));
        values.put(COLUMN_READING, TextUtil.normalizeJapanese(note.reading(settings)));
        values.put(COLUMN_MEANING, TextUtil.firstMeaningLine(note.meaning(settings)));
        values.put(COLUMN_SENTENCE, TextUtil.normalizeJapanese(note.sentence(settings)));
        values.put(COLUMN_FIELDS_JSON, fieldsJson(note.fields));
        values.put("archived_at", finishedAt);
        values.put("archived_sync_id", syncId);
        db.insertWithOnConflict(TABLE_SUSPENDED_ARCHIVE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    void saveSourceCard(SQLiteDatabase db, RecordsSyncModels.Card card, long syncId) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CARD_ID, card.cardId);
        values.put(COLUMN_NOTE_ID, card.noteId);
        values.put(COLUMN_DECK_NAME, card.deckName);
        values.put("ord", card.ord);
        values.put(COLUMN_QUEUE, card.queue);
        values.put("type", card.type);
        values.put("due", card.due);
        values.put(COLUMN_INTERVAL_DAYS, card.intervalDays);
        values.put(COLUMN_REPS, card.reps);
        values.put(COLUMN_LAPSES, card.lapses);
        putNullableDouble(values, COLUMN_FSRS_STABILITY, card.fsrsStability);
        putNullableDouble(values, COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty);
        putNullableDouble(values, COLUMN_FSRS_RETRIEVABILITY, card.fsrsRetrievability);
        values.put(COLUMN_LAST_SEEN_SYNC_ID, syncId);
        db.insertWithOnConflict(TABLE_SOURCE_CARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
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
        values.put(COLUMN_KANJI, imported.kanji);
        if (imported.jitenRank != null) {
            values.put(COLUMN_JITEN_RANK, imported.jitenRank);
        }
        values.put(COLUMN_RANK_KNOWN, imported.rankKnown ? 1 : 0);
        values.put(COLUMN_CUTOFF_USED, imported.cutoffUsed);
        values.put(COLUMN_FIRST_IMPORTED_AT, firstImportedAt(db, imported.kanji, finishedAt));
        values.put(COLUMN_LAST_SEEN_SYNC_ID, syncId);
        db.insertWithOnConflict(TABLE_SUSPENDED_IMPORTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        for (RecordsImportModels.SuspendedSource source : imported.sources) {
            ContentValues sourceValues = new ContentValues();
            sourceValues.put(COLUMN_KANJI, imported.kanji);
            sourceValues.put(COLUMN_CARD_ID, source.cardId);
            sourceValues.put(COLUMN_NOTE_ID, source.noteId);
            sourceValues.put(COLUMN_EXPRESSION, source.expression);
            sourceValues.put(COLUMN_READING, source.reading);
            sourceValues.put(COLUMN_MEANING, source.meaning);
            sourceValues.put(COLUMN_SENTENCE, source.sentence);
            sourceValues.put(COLUMN_SYNC_ID, syncId);
            db.insertWithOnConflict(TABLE_SUSPENDED_SOURCES, null, sourceValues, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private List<String> enabledImportSources(RecordsSyncModels.Settings settings) {
        List<String> sources = new ArrayList<>();
        if (settings.importActiveCards) {
            sources.add(RecordsBase.SOURCE_ACTIVE);
        }
        if (settings.importSuspendedCards) {
            sources.add(RecordsBase.SOURCE_SUSPENDED);
        }
        if (settings.importTaggedCardsEnabled()) {
            sources.add(RecordsBase.SOURCE_TAGGED);
        }
        if (settings.importWeakCards) {
            sources.add(RecordsBase.SOURCE_WEAK);
        }
        if (settings.browserQueryImportEnabled()) {
            sources.add(RecordsBase.SOURCE_BROWSER_QUERY);
        }
        return sources;
    }

    private String importReasonCode(Set<String> ruleTypes) {
        if (ruleTypes.size() > 1) {
            return "multiple_import_rules";
        }
        if (ruleTypes.contains(RecordsBase.SOURCE_BROWSER_QUERY)) {
            return "browser_query_import";
        }
        if (ruleTypes.contains(RecordsBase.SOURCE_SUSPENDED)) {
            return "suspended_import";
        }
        if (ruleTypes.contains(RecordsBase.SOURCE_TAGGED)) {
            return "tagged_import";
        }
        if (ruleTypes.contains(RecordsBase.SOURCE_WEAK)) {
            return "weak_card_import";
        }
        if (ruleTypes.contains(RecordsBase.SOURCE_ACTIVE)) {
            return "active_import";
        }
        return "imported";
    }

    private String importReasonText(
            RecordsImportModels.SuspendedImport imported,
            RecordsSyncModels.Settings settings,
            Set<String> ruleTypes,
            int sourceCount
    ) {
        String rank = imported.jitenRank == null ? "unknown" : Integer.toString(imported.jitenRank);
        String rules = ruleTypes.isEmpty() ? "unknown rule" : String.join(" + ", ruleTypes);
        return "Imported by " + rules
                + "; " + sourceCount + " source card" + (sourceCount == 1 ? "" : "s")
                + "; Jiten rank " + rank
                + "; rank range " + settings.suspendedRankMin + "-" + settings.suspendedRankMax
                + "; minimum matching cards " + settings.importMinMatchingCardsPerKanji + ".";
    }

    private String importSettingsJson(RecordsSyncModels.Settings settings) {
        return "{"
                + "\"model_name\":" + TextUtil.jsonQuote(settings.modelName)
                + ",\"import_active_cards\":" + settings.importActiveCards
                + ",\"import_suspended_cards\":" + settings.importSuspendedCards
                + ",\"import_tagged_cards\":" + settings.importTaggedCardsEnabled()
                + ",\"import_tags\":" + jsonArray(settings.importTags)
                + ",\"import_weak_cards\":" + settings.importWeakCards
                + ",\"import_weak_fsrs_difficulty\":" + settings.importWeakFsrsDifficultyThreshold
                + ",\"import_weak_lapses\":" + settings.importWeakLapsesThreshold
                + ",\"import_browser_query_cards\":" + settings.importBrowserQueryCards
                + ",\"import_browser_query\":" + TextUtil.jsonQuote(settings.normalizedBrowserQuery())
                + ",\"rank_min\":" + settings.suspendedRankMin
                + ",\"rank_max\":" + settings.suspendedRankMax
                + ",\"min_matching_cards\":" + settings.importMinMatchingCardsPerKanji
                + "}";
    }

    private String jsonArray(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(TextUtil.jsonQuote(value));
        }
        out.append(']');
        return out.toString();
    }

    private String joinLongs(Set<Long> values) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Long value : values) {
            if (!first) {
                out.append(' ');
            }
            first = false;
            out.append(value);
        }
        return out.toString();
    }

    public void saveFailedSync(long startedAt, long finishedAt, String status, String errorCode, String errorMessage) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STARTED_AT, startedAt);
        values.put(COLUMN_FINISHED_AT, finishedAt);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_ACTIVE_NOTES_COUNT, 0);
        values.put(COLUMN_ACTIVE_CARDS_COUNT, 0);
        values.put(COLUMN_SUSPENDED_CARDS_ARCHIVED_COUNT, 0);
        values.put(COLUMN_SUSPENDED_KANJI_IMPORTED_COUNT, 0);
        values.put("deleted_notes_count", 0);
        values.put("deleted_cards_count", 0);
        values.put("error_code", errorCode);
        values.put(COLUMN_ERROR_MESSAGE, errorMessage);
        values.put(COLUMN_REMOVAL_MESSAGE, "");
        db.insert(TABLE_SYNC_RUNS, null, values);
    }

    public void updateSyncRemovalMessage(long syncId, String message) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_REMOVAL_MESSAGE, message == null ? "" : message);
        getWritableDatabase().update(TABLE_SYNC_RUNS, values, "id=?", new String[]{Long.toString(syncId)});
    }
}
