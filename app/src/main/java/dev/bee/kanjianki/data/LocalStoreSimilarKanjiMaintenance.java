package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.SimilarKanjiStorageKeys;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

final class LocalStoreSimilarKanjiMaintenance {
    private final LocalStoreHistory activity;

    LocalStoreSimilarKanjiMaintenance(LocalStoreHistory activity) {
        this.activity = activity;
    }

    void rebuildSimilarKanjiPairs(SQLiteDatabase db, SimilarKanjiIndex similarIndex, long nowMillis) {
        Map<String, Long> firstSeenByPair = activity.similarPairFirstSeen(db);
        List<SimilarKanjiIndex.Pair> localPairs = similarIndex.pairsWithin(activity.localInventoryKanji(db));
        db.delete(LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS, null, null);
        for (SimilarKanjiIndex.Pair pair : localPairs) {
            ContentValues values = new ContentValues();
            values.put(LocalStoreBase.COLUMN_KANJI_A, pair.kanjiA);
            values.put(LocalStoreBase.COLUMN_KANJI_B, pair.kanjiB);
            values.put(LocalStoreBase.COLUMN_SOURCE, pair.source);
            values.put(LocalStoreBase.COLUMN_FIRST_SEEN_AT, firstSeenByPair.getOrDefault(activity.similarKey(pair.kanjiA, pair.kanjiB, pair.source), nowMillis));
            values.put(LocalStoreBase.COLUMN_LAST_SEEN_AT, nowMillis);
            db.insertWithOnConflict(LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    void rebuildSimilarKanjiChoiceStates(SQLiteDatabase db, long nowMillis) {
        activity.createSimilarKanjiPracticeTables(db);
        Map<String, LocalStoreBase.SimilarChoiceSnapshot> previous = activity.similarChoiceSnapshots(db);
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
        List<RecordsImportModels.SimilarKanjiChoiceCard> candidates = planner.buildCandidates(
                activity.allInventoryItems(db),
                activity.allSimilarPairs(db)
        );
        Set<String> currentKeys = new HashSet<>();
        for (RecordsImportModels.SimilarKanjiChoiceCard card : candidates) {
            String key = activity.similarChoiceKey(card.targetKanji, card.choiceSignature);
            currentKeys.add(key);
            upsertSimilarKanjiChoiceState(db, card, previous.get(key), nowMillis);
        }
        deleteStaleSimilarChoiceStates(db, previous.keySet(), currentKeys);
    }

    void upsertSimilarKanjiChoiceState(
            SQLiteDatabase db,
            RecordsImportModels.SimilarKanjiChoiceCard card,
            LocalStoreBase.SimilarChoiceSnapshot old,
            long nowMillis
    ) {
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_TARGET_KANJI, card.targetKanji);
        values.put(LocalStoreBase.COLUMN_CHOICE_SIGNATURE, card.choiceSignature);
        values.put(LocalStoreBase.COLUMN_PRIMARY_MEANING, card.primaryMeaning);
        values.put(LocalStoreBase.COLUMN_CHOICES, activity.serializeChoices(card.choices));
        values.put(LocalStoreBase.COLUMN_DUE_AT, old == null ? 0L : old.dueAtMillis);
        values.put(LocalStoreBase.COLUMN_PASSED_AT, old == null ? 0L : old.passedAtMillis);
        values.put(LocalStoreBase.COLUMN_LAST_REVIEWED_AT, old == null ? 0L : old.lastReviewedAtMillis);
        values.put(LocalStoreBase.COLUMN_CORRECT_COUNT, old == null ? 0 : old.correctCount);
        values.put(LocalStoreBase.COLUMN_WRONG_COUNT, old == null ? 0 : old.wrongCount);
        values.put(LocalStoreBase.COLUMN_ACTIVE_TOKEN, "");
        values.put(LocalStoreBase.COLUMN_FIRST_SEEN_AT, old == null ? nowMillis : old.firstSeenAtMillis);
        values.put(LocalStoreBase.COLUMN_LAST_SEEN_AT, nowMillis);
        db.insertWithOnConflict(LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteStaleSimilarChoiceStates(SQLiteDatabase db, Set<String> previousKeys, Set<String> currentKeys) {
        for (String key : previousKeys) {
            String[] parts = SimilarKanjiStorageKeys.splitChoiceKey(key);
            if (!currentKeys.contains(key) && parts.length == 2) {
                db.delete(
                        LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
                        LocalStoreBase.WHERE_SIMILAR_CHOICE,
                        new String[]{parts[0], parts[1]}
                );
                db.delete(
                        LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                        "status=? AND target_kanji=? AND choice_signature=?",
                        new String[]{LocalStoreBase.STATUS_PENDING, parts[0], parts[1]}
                );
            }
        }
    }
}
