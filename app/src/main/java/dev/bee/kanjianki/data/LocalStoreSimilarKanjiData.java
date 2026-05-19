package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.SimilarKanjiRepairPolicy;

import java.util.HashMap;
import java.util.Map;

final class LocalStoreSimilarKanjiData {
    private final LocalStoreHistory activity;

    LocalStoreSimilarKanjiData(LocalStoreHistory activity) {
        this.activity = activity;
    }

    Map<String, LocalStoreBase.SimilarChoiceSnapshot> similarChoiceSnapshots(SQLiteDatabase db) {
        Map<String, LocalStoreBase.SimilarChoiceSnapshot> out = new HashMap<>();
        try (Cursor cursor = db.query(LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                String target = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TARGET_KANJI);
                String signature = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICE_SIGNATURE);
                out.put(
                        activity.similarChoiceKey(target, signature),
                        new LocalStoreBase.SimilarChoiceSnapshot(
                                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
                                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_PASSED_AT),
                                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_REVIEWED_AT),
                                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_CORRECT_COUNT),
                                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRONG_COUNT),
                                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FIRST_SEEN_AT)
                        )
                );
            }
        }
        return out;
    }

    RecordsImportModels.SimilarKanjiChoiceCard readSimilarChoiceCard(Cursor cursor) {
        return new RecordsImportModels.SimilarKanjiChoiceCard(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TARGET_KANJI),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_PRIMARY_MEANING),
                activity.deserializeChoices(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICES)),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICE_SIGNATURE),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_PASSED_AT),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_REVIEWED_AT),
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_CORRECT_COUNT),
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRONG_COUNT)
        );
    }

    boolean hasPendingSimilarRepairs(SQLiteDatabase db, String targetKanji, String choiceSignature) {
        try (Cursor cursor = db.query(
                LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=?",
                new String[]{LocalStoreBase.STATUS_PENDING, targetKanji, choiceSignature},
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
        SimilarKanjiRepairPolicy.RepairDraft draft =
                SimilarKanjiRepairPolicy.newRepair(card, repairKanji, wrongSelection, nowMillis);
        if (draft == null) {
            return;
        }
        try (Cursor pending = db.query(
                LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                new String[]{"id"},
                "status=? AND target_kanji=? AND choice_signature=? AND repair_kanji=?",
                new String[]{LocalStoreBase.STATUS_PENDING, draft.targetKanji(), draft.choiceSignature(), draft.repairKanji()},
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
        values.put(LocalStoreBase.COLUMN_TARGET_KANJI, draft.targetKanji());
        values.put("repair_kanji", draft.repairKanji());
        values.put(LocalStoreBase.COLUMN_CHOICE_SIGNATURE, draft.choiceSignature());
        values.put("wrong_selection", draft.wrongSelection());
        values.put("prompt_meaning", draft.promptMeaning());
        values.put(LocalStoreBase.COLUMN_STATUS, draft.status());
        values.put(LocalStoreBase.COLUMN_DUE_AT, draft.dueAtMillis());
        values.put(LocalStoreBase.COLUMN_ACTIVE_TOKEN, draft.activeToken());
        values.put(LocalStoreBase.COLUMN_ATTEMPTS, draft.attempts());
        values.put(LocalStoreBase.COLUMN_CREATED_AT, draft.createdAtMillis());
        values.put(LocalStoreBase.COLUMN_UPDATED_AT, draft.updatedAtMillis());
        values.put(LocalStoreBase.COLUMN_COMPLETED_AT, draft.completedAtMillis());
        db.insert(LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE, null, values);
    }

    RecordsImportModels.SimilarKanjiWritingRepair similarWritingRepair(SQLiteDatabase db, long repairId) {
        try (Cursor cursor = db.query(
                LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
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
                LocalStoreBase.longValue(cursor, "id"),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TARGET_KANJI),
                LocalStoreBase.string(cursor, "repair_kanji"),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICE_SIGNATURE),
                LocalStoreBase.string(cursor, "wrong_selection"),
                LocalStoreBase.string(cursor, "prompt_meaning"),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_STATUS),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ACTIVE_TOKEN),
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_ATTEMPTS),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_CREATED_AT),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_UPDATED_AT),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_COMPLETED_AT)
        );
    }
}
