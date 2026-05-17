package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsImportModels;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

abstract class LocalStoreSimilarKanji extends LocalStoreStudy {
    LocalStoreSimilarKanji(Context context) {
        super(context);
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

    public List<RecordsImportModels.SimilarKanjiPair> allLocalSimilarPairs() {
        SQLiteDatabase db = getReadableDatabase();
        List<RecordsImportModels.SimilarKanjiPair> out = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_SIMILAR_KANJI_PAIRS, null, null, null, null, null, ORDER_SIMILAR_PAIR)) {
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor));
            }
        }
        return out;
    }

    public List<RecordsImportModels.SimilarKanjiPair> similarPairsForKanji(String kanji) {
        String normalized = normalizeSingleKanji(kanji);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        SQLiteDatabase db = getReadableDatabase();
        List<RecordsImportModels.SimilarKanjiPair> out = new ArrayList<>();
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

    public List<RecordsImportModels.SimilarKanjiChoiceCard> allSimilarChoiceCards() {
        SQLiteDatabase db = getReadableDatabase();
        List<RecordsImportModels.SimilarKanjiChoiceCard> out = new ArrayList<>();
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

    public RecordsImportModels.SimilarKanjiChoiceCard dueSimilarChoiceForActiveTarget(String kanji, long nowMillis) {
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
            RecordsImportModels.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
            return hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature) ? null : card;
        }
    }

    public RecordsImportModels.SimilarKanjiChoiceCard nextDueInventorySimilarChoice(Set<String> activeTargets, long nowMillis) {
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
                RecordsImportModels.SimilarKanjiChoiceCard card = readSimilarChoiceCard(cursor);
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

    public RecordsImportModels.SimilarKanjiChoiceResult submitSimilarChoice(
            RecordsImportModels.SimilarKanjiChoiceCard submitted,
            String selectedKanji,
            long nowMillis
    ) {
        return submitSimilarChoice(submitted, selectedKanji, nowMillis, true);
    }

    public RecordsImportModels.SimilarKanjiChoiceResult submitSimilarChoice(
            RecordsImportModels.SimilarKanjiChoiceCard submitted,
            String selectedKanji,
            long nowMillis,
            boolean enqueueRepairs
    ) {
        if (submitted == null) {
            return new RecordsImportModels.SimilarKanjiChoiceResult(null, selectedKanji, false, Collections.emptyList());
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            RecordsImportModels.SimilarKanjiChoiceCard card = similarChoiceCard(db, submitted.targetKanji, submitted.choiceSignature);
            if (card == null) {
                card = submitted;
            }
            SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
            RecordsImportModels.SimilarKanjiChoiceResult result = planner.evaluateSelection(card, normalizeSingleKanji(selectedKanji));

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

            if (!result.correct && enqueueRepairs) {
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

    public RecordsImportModels.SimilarKanjiWritingRepair nextDueSimilarWritingRepair(long nowMillis) {
        List<RecordsImportModels.SimilarKanjiWritingRepair> repairs = dueSimilarWritingRepairs(nowMillis);
        return repairs.isEmpty() ? null : repairs.get(0);
    }

    public List<RecordsImportModels.SimilarKanjiWritingRepair> dueSimilarWritingRepairs(long nowMillis) {
        SQLiteDatabase db = getReadableDatabase();
        List<RecordsImportModels.SimilarKanjiWritingRepair> out = new ArrayList<>();
        try (Cursor cursor = db.query(
                TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                null,
                "status=? AND due_at<=?",
                new String[]{STATUS_PENDING, Long.toString(nowMillis)},
                null,
                null,
                "created_at ASC, id ASC",
                null
        )) {
            while (cursor.moveToNext()) {
                out.add(readSimilarWritingRepair(cursor));
            }
        }
        return out;
    }

    public void saveSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair) {
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
            RecordsImportModels.SimilarKanjiWritingRepair current = similarWritingRepair(db, repairId);
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
}
