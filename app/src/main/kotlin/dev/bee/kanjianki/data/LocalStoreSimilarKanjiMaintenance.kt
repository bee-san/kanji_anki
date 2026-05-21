package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SimilarKanjiStorageKeys

internal class LocalStoreSimilarKanjiMaintenance(
    private val activity: LocalStoreHistory,
) {
    fun rebuildSimilarKanjiPairs(
        db: SQLiteDatabase,
        similarIndex: SimilarKanjiIndex,
        nowMillis: Long,
    ) {
        val firstSeenByPair = activity.similarPairFirstSeen(db)
        val localPairs = similarIndex.pairsWithin(activity.localInventoryKanji(db))
        db.delete(LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS, null, null)
        for (pair in localPairs) {
            val values = ContentValues()
            values.put(LocalStoreBase.COLUMN_KANJI_A, pair.kanjiA)
            values.put(LocalStoreBase.COLUMN_KANJI_B, pair.kanjiB)
            values.put(LocalStoreBase.COLUMN_SOURCE, pair.source)
            values.put(
                LocalStoreBase.COLUMN_FIRST_SEEN_AT,
                firstSeenByPair.getOrDefault(
                    LocalStoreHistory.similarKey(pair.kanjiA, pair.kanjiB, pair.source),
                    nowMillis,
                ),
            )
            values.put(LocalStoreBase.COLUMN_LAST_SEEN_AT, nowMillis)
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun rebuildSimilarKanjiChoiceStates(db: SQLiteDatabase, nowMillis: Long) {
        activity.createSimilarKanjiPracticeTables(db)
        val previous = activity.similarChoiceSnapshots(db)
        val planner = SimilarKanjiChoicePlanner()
        val candidates = planner.buildCandidates(
            activity.allInventoryItems(db),
            activity.allSimilarPairs(db),
        )
        val currentKeys = HashSet<String>()
        for (card in candidates) {
            val key = LocalStoreHistory.similarChoiceKey(card.targetKanji, card.choiceSignature)
            currentKeys.add(key)
            upsertSimilarKanjiChoiceState(db, card, previous[key], nowMillis)
        }
        deleteStaleSimilarChoiceStates(db, previous.keys, currentKeys)
    }

    fun upsertSimilarKanjiChoiceState(
        db: SQLiteDatabase,
        card: RecordsImportModels.SimilarKanjiChoiceCard,
        old: LocalStoreBase.SimilarChoiceSnapshot?,
        nowMillis: Long,
    ) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_TARGET_KANJI, card.targetKanji)
        values.put(LocalStoreBase.COLUMN_CHOICE_SIGNATURE, card.choiceSignature)
        values.put(LocalStoreBase.COLUMN_PRIMARY_MEANING, card.primaryMeaning)
        values.put(LocalStoreBase.COLUMN_CHOICES, LocalStoreHistory.serializeChoices(card.choices))
        values.put(LocalStoreBase.COLUMN_DUE_AT, old?.dueAtMillis ?: 0L)
        values.put(LocalStoreBase.COLUMN_PASSED_AT, old?.passedAtMillis ?: 0L)
        values.put(LocalStoreBase.COLUMN_LAST_REVIEWED_AT, old?.lastReviewedAtMillis ?: 0L)
        values.put(LocalStoreBase.COLUMN_CORRECT_COUNT, old?.correctCount ?: 0)
        values.put(LocalStoreBase.COLUMN_WRONG_COUNT, old?.wrongCount ?: 0)
        values.put(LocalStoreBase.COLUMN_ACTIVE_TOKEN, "")
        values.put(LocalStoreBase.COLUMN_FIRST_SEEN_AT, old?.firstSeenAtMillis ?: nowMillis)
        values.put(LocalStoreBase.COLUMN_LAST_SEEN_AT, nowMillis)
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteStaleSimilarChoiceStates(
        db: SQLiteDatabase,
        previousKeys: Set<String>,
        currentKeys: Set<String>,
    ) {
        for (key in previousKeys) {
            val parts = SimilarKanjiStorageKeys.splitChoiceKey(key)
            if (!currentKeys.contains(key) && parts.size == 2) {
                db.delete(
                    LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
                    LocalStoreBase.WHERE_SIMILAR_CHOICE,
                    arrayOf(parts[0], parts[1]),
                )
                db.delete(
                    LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
                    "status=? AND target_kanji=? AND choice_signature=?",
                    arrayOf(LocalStoreBase.STATUS_PENDING, parts[0], parts[1]),
                )
            }
        }
    }
}
