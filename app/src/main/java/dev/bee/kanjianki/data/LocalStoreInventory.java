package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.Records;
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

abstract class LocalStoreInventory extends LocalStoreStudy {
    LocalStoreInventory(Context context) {
        super(context);
    }

    public List<Records.DashboardRow> dashboardRows() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.DashboardRow> rows = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, "weakness_score DESC, suspended_example_count DESC, kanji ASC", "120")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                rows.add(new Records.DashboardRow(
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
                ));
            }
        }
        return rows;
    }

    public List<Records.DashboardRow> activeDashboardRows() {
        Set<String> suspended = locallySuspendedKanji();
        if (suspended.isEmpty()) {
            return dashboardRows();
        }
        List<Records.DashboardRow> out = new ArrayList<>();
        for (Records.DashboardRow row : dashboardRows()) {
            if (!suspended.contains(row.kanji)) {
                out.add(row);
            }
        }
        return out;
    }

    public Records.DashboardRow rowForKanji(String kanji) {
        return readDashboardRow(getReadableDatabase(), kanji);
    }

    public Records.KanjiInventoryItem inventoryItemForKanji(String kanji) {
        return readInventoryItem(getReadableDatabase(), kanji);
    }

    public List<Records.KanjiInventoryItem> searchKanjiInventory(String query) {
        SQLiteDatabase db = getReadableDatabase();
        String normalized = TextUtil.normalizeJapanese(query == null ? "" : query).toLowerCase(Locale.ROOT);
        List<Records.KanjiInventoryItem> out = new ArrayList<>();
        String selection = null;
        String[] args = null;
        if (!normalized.isEmpty()) {
            selection = "search_text LIKE ?";
            args = new String[]{"%" + normalized + "%"};
        }
        try (Cursor cursor = db.query(
                TABLE_KANJI_INVENTORY,
                null,
                selection,
                args,
                null,
                null,
                ORDER_KANJI_ASC,
                "300"
        )) {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
        }
        return out;
    }

    public void rebuildSimilarKanjiPairs(SimilarKanjiIndex similarIndex, long nowMillis) {
        if (similarIndex == null) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            rebuildSimilarKanjiPairs(db, similarIndex, nowMillis);
            rebuildSimilarKanjiChoiceStates(db, nowMillis);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Records.SimilarKanjiPair> allLocalSimilarPairs() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiPair> out = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, null, null, null, null, null, ORDER_SIMILAR_PAIR)) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        }
        return out;
    }

    public List<Records.SimilarKanjiPair> similarPairsForKanji(String kanji) {
        String normalized = normalizeSingleKanji(kanji);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiPair> out = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_PAIRS,
                null,
                "kanji_a=? OR kanji_b=?",
                new String[]{normalized, normalized},
                null,
                null,
                ORDER_SIMILAR_PAIR
        )) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        }
        return out;
    }

    public boolean hasSimilarLocalPair(String first, String second) {
        String kanjiA = normalizeSingleKanji(first);
        String kanjiB = normalizeSingleKanji(second);
        if (kanjiA.isEmpty() || kanjiB.isEmpty() || kanjiA.equals(kanjiB)) {
            return false;
        }
        String[] pair = canonicalSimilarPair(kanjiA, kanjiB);
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_SIMILAR_KANJI_PAIRS,
                new String[]{COLUMN_KANJI_A},
                "kanji_a=? AND kanji_b=?",
                pair,
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    public List<Records.SimilarKanjiChoiceCard> allSimilarChoiceCards() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.SimilarKanjiChoiceCard> out = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                null,
                null,
                null,
                null,
                "target_kanji ASC, choice_signature ASC"
        )) {
            while (cursor.moveToNext()) {
                out.add(readSimilarChoiceCard(cursor));
            }
        }
        return out;
    }

    public Records.SimilarKanjiChoiceCard dueSimilarChoiceForActiveTarget(String kanji, long nowMillis) {
        String target = normalizeSingleKanji(kanji);
        if (target.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                "target_kanji=? AND passed_at=0 AND due_at<=?",
                new String[]{target, Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, first_seen_at ASC",
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Records.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
            return hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature) ? null : card;
        }
    }

    public Records.SimilarKanjiChoiceCard nextDueInventorySimilarChoice(Set<String> activeTargets, long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                null,
                "passed_at=0 AND due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, last_reviewed_at ASC, target_kanji ASC"
        )) {
            while (cursor.moveToNext()) {
                Records.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
                if (activeTargets != null && activeTargets.contains(card.targetKanji)) {
                    continue;
                }
                if (!hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature)) {
                    return card;
                }
            }
            return null;
        }
    }

    public int dueSimilarStudyTaskCount(long nowMillis) {
        return dueSimilarChoiceTaskCount(nowMillis) + dueSimilarWritingRepairTaskCount(nowMillis);
    }

    public int dueSimilarChoiceTaskCount(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        int count = 0;
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                new String[]{COLUMN_TARGET_KANJI, COLUMN_CHOICE_SIGNATURE},
                "passed_at=0 AND due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                String targetKanji = string(cursor, COLUMN_TARGET_KANJI);
                String choiceSignature = string(cursor, COLUMN_CHOICE_SIGNATURE);
                if (!hasPendingSimilarRepairs(db, targetKanji, choiceSignature)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int dueSimilarWritingRepairTaskCount(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_SIMILAR_KANJI_REPAIR_QUEUE + " WHERE status=? AND due_at<=?",
                new String[]{STATUS_PENDING, Long.toString(nowMillis)}
        )) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    public Records.SimilarKanjiChoiceResult submitSimilarChoice(
            Records.SimilarKanjiChoiceCard submitted,
            String selectedKanji,
            long nowMillis
    ) {
        if (submitted == null) {
            return new Records.SimilarKanjiChoiceResult(null, selectedKanji, false, Collections.emptyList());
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Records.SimilarKanjiChoiceCard card = similarChoiceCard(db, submitted.targetKanji, submitted.choiceSignature);
            if (card == null) {
                card = submitted;
            }
            SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
            Records.SimilarKanjiChoiceResult result = planner.evaluateSelection(card, normalizeSingleKanji(selectedKanji));

            ContentValues values = new ContentValues();
            values.put(COLUMN_LAST_REVIEWED_AT, nowMillis);
            if (result.correct) {
                values.put(COLUMN_PASSED_AT, nowMillis);
                values.put(COLUMN_CORRECT_COUNT, card.correctCount + 1);
            } else {
                values.put(COLUMN_PASSED_AT, 0L);
                values.put(COLUMN_DUE_AT, nowMillis);
                values.put(COLUMN_WRONG_COUNT, card.wrongCount + 1);
            }
            db.update(
                    TABLE_SIMILAR_KANJI_CHOICE_STATE,
                    values,
                    WHERE_SIMILAR_CHOICE,
                    new String[]{card.targetKanji, card.choiceSignature}
            );

            ContentValues log = new ContentValues();
            log.put(COLUMN_TARGET_KANJI, card.targetKanji);
            log.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
            log.put("selected_kanji", result.selectedKanji);
            log.put("correct", result.correct ? 1 : 0);
            log.put(COLUMN_REVIEWED_AT, nowMillis);
            db.insert(TABLE_SIMILAR_KANJI_REVIEW_LOG, null, log);

            if (!result.correct) {
                for (String repairKanji : result.repairKanji) {
                    enqueueSimilarWritingRepair(db, card, repairKanji, result.selectedKanji, nowMillis);
                }
            }
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    public Records.SimilarKanjiWritingRepair nextDueSimilarWritingRepair(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                null,
                "status=? AND due_at<=?",
                new String[]{STATUS_PENDING, Long.toString(nowMillis)},
                null,
                null,
                "created_at ASC, id ASC",
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readSimilarWritingRepair(cursor);
        }
    }

    public void saveSimilarWritingRepair(Records.SimilarKanjiWritingRepair repair) {
        if (repair == null || repair.id <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(COLUMN_ACTIVE_TOKEN, repair.activeToken);
        values.put(COLUMN_UPDATED_AT, repair.updatedAtMillis);
        getWritableDatabase().update(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                values,
                "id=? AND status=?",
                new String[]{Long.toString(repair.id), STATUS_PENDING}
        );
    }

    public boolean finishSimilarWritingRepair(long repairId, String token, boolean passed, long nowMillis) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Records.SimilarKanjiWritingRepair current = similarWritingRepair(db, repairId);
            if (current == null || !STATUS_PENDING.equals(current.status)) {
                return false;
            }
            if (!current.activeToken.isEmpty() && !current.activeToken.equals(token == null ? "" : token)) {
                return false;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_ACTIVE_TOKEN, "");
            values.put(COLUMN_UPDATED_AT, nowMillis);
            if (passed) {
                values.put(COLUMN_STATUS, STATUS_COMPLETE);
                values.put(COLUMN_COMPLETED_AT, nowMillis);
            } else {
                values.put(COLUMN_ATTEMPTS, current.attempts + 1);
                values.put(COLUMN_DUE_AT, nowMillis);
            }
            db.update(TABLE_SIMILAR_KANJI_REPAIR_QUEUE, values, "id=?", new String[]{Long.toString(repairId)});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public Set<String> locallySuspendedKanji() {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                out.add(string(cursor, COLUMN_KANJI));
            }
        }
        return out;
    }

    public boolean isKanjiLocallySuspended(String kanji) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    public void setKanjiLocallySuspended(String kanji, boolean suspended, long nowMillis) {
        if (kanji == null || kanji.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (suspended) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_KANJI, kanji);
                values.put("suspended_at", nowMillis);
                db.insertWithOnConflict(TABLE_LOCAL_KANJI_SUSPENSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                db.delete(TABLE_LEARNING_REPEATS, WHERE_KANJI, new String[]{kanji});
            } else {
                db.delete(TABLE_LOCAL_KANJI_SUSPENSIONS, WHERE_KANJI, new String[]{kanji});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Records.KanjiRecoveryTimeline timelineForKanji(String kanji) {
        SQLiteDatabase db = getReadableDatabase();
        Records.KanjiInventoryItem inventoryItem = readInventoryItem(db, kanji);
        Records.DashboardRow row = readDashboardRow(db, kanji);
        Records.StudyItem item = studyItemForKanji(db, kanji);
        List<Records.KanjiTimelineEvent> events = new ArrayList<>();
        Cursor cursor = db.query(
                TABLE_KANJI_TIMELINE_EVENTS,
                null,
                WHERE_KANJI,
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
        return new Records.KanjiRecoveryTimeline(inventoryItem, row, item, events);
    }

    public List<Records.StudyItem> studyItems() {
        SQLiteDatabase db = getReadableDatabase();
        List<Records.StudyItem> items = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "due_at ASC")) {
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor));
            }
        }
        Set<String> withSimilar = kanjiWithSimilarNeighbors(db);
        for (int i = 0; i < items.size(); i++) {
            Records.StudyItem current = items.get(i);
            boolean hasSimilar = withSimilar.contains(current.kanji);
            if (hasSimilar != current.hasSimilarKanji) {
                items.set(i, current.withHasSimilarKanji(hasSimilar));
            }
        }
        return items;
    }

    /**
     * Returns the set of kanji that have at least one entry in the
     * {@code similar_kanji_pairs} table, either as kanji_a or kanji_b.
     * This set is the data source for
     * {@link Records.StudyItem#hasSimilarKanji}: when a study item's kanji
     * is present here, the {@code similar_kanji} rung is included in the
     * ladder for that card.
     */
    Set<String> kanjiWithSimilarNeighbors(SQLiteDatabase db) {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT kanji_a FROM " + TABLE_SIMILAR_KANJI_PAIRS
                        + " UNION SELECT kanji_b FROM " + TABLE_SIMILAR_KANJI_PAIRS,
                null
        )) {
            while (cursor.moveToNext()) {
                String k = cursor.getString(0);
                if (k != null && !k.isEmpty()) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /**
     * Re-applies the {@link Records.StudyItem#hasSimilarKanji} predicate to
     * each item in the given list using the current
     * {@code similar_kanji_pairs} contents. Call this after
     * {@link BridgeScheduler#seedQueue} produces new items but before
     * persisting them so the ladder scheduler's {@code similar_kanji} rung
     * inclusion decision is consistent with the just-rebuilt similarity
     * data, without waiting for a follow-up DB reload.
     */
    public List<Records.StudyItem> annotateSimilarKanjiAvailability(List<Records.StudyItem> items) {
        if (items == null || items.isEmpty()) {
            return items == null ? Collections.emptyList() : items;
        }
        Set<String> withSimilar = kanjiWithSimilarNeighbors(getReadableDatabase());
        List<Records.StudyItem> out = new ArrayList<>(items.size());
        for (Records.StudyItem item : items) {
            boolean hasSimilar = withSimilar.contains(item.kanji);
            out.add(hasSimilar == item.hasSimilarKanji ? item : item.withHasSimilarKanji(hasSimilar));
        }
        return out;
    }

    public List<Records.SuspendedImport> suspendedImports() {
        SQLiteDatabase db = getReadableDatabase();
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
                if (imported == null) {
                    continue;
                }
                imported.sources.add(new Records.SuspendedSource(
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

        List<Records.SuspendedImport> out = new ArrayList<>();
        for (MutableSuspendedImport imported : imports.values()) {
            out.add(imported.build());
        }
        return out;
    }

}
