package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.ConfusionPairMiner
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
        val inventoryKanji = activity.localInventoryKanji(db)
        val localPairs = similarIndex.pairsWithin(inventoryKanji)
        db.delete(LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS, null, null)
        for (pair in localPairs) {
            insertSimilarPair(db, pair.kanjiA, pair.kanjiB, pair.source, firstSeenByPair, nowMillis)
        }
        for (pair in minedConfusionPairs(db, inventoryKanji, nowMillis)) {
            insertSimilarPair(db, pair.kanjiA, pair.kanjiB, pair.source, firstSeenByPair, nowMillis)
        }
    }

    private fun insertSimilarPair(
        db: SQLiteDatabase,
        kanjiA: String,
        kanjiB: String,
        source: String,
        firstSeenByPair: Map<String, Long>,
        nowMillis: Long,
    ) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_KANJI_A, kanjiA)
        values.put(LocalStoreBase.COLUMN_KANJI_B, kanjiB)
        values.put(LocalStoreBase.COLUMN_SOURCE, source)
        values.put(
            LocalStoreBase.COLUMN_FIRST_SEEN_AT,
            firstSeenByPair.getOrDefault(
                LocalStoreHistory.similarKey(kanjiA, kanjiB, source),
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

    /**
     * The review log is the durable source: pairs regenerate from it on
     * every sync, so no preservation logic is needed across the
     * delete-and-reinsert rebuild above.
     */
    private fun minedConfusionPairs(
        db: SQLiteDatabase,
        inventoryKanji: Collection<String>,
        nowMillis: Long,
    ): List<RecordsImportModels.SimilarKanjiPair> {
        activity.createSimilarKanjiPracticeTables(db)
        val windowStartMillis = ConfusionPairMiner.windowStartMillis(nowMillis)
        // Drop wrong-pick rows older than the mining window so the append-only log
        // stops growing without bound; the miner discards them anyway, so this only
        // removes rows that can never contribute a pair again.
        db.delete(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REVIEW_LOG,
            "reviewed_at<?",
            arrayOf(windowStartMillis.toString()),
        )
        val rows = ArrayList<ConfusionPairMiner.WrongPickRow>()
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TARGET_KANJI, "selected_kanji", "correct", "reviewed_at"),
            "correct=0 AND reviewed_at>=?",
            arrayOf(windowStartMillis.toString()),
            null,
            null,
            null,
        ).use {
            while (it.moveToNext()) {
                rows.add(
                    ConfusionPairMiner.WrongPickRow(
                        it.getString(0),
                        it.getString(1),
                        it.getInt(2) != 0,
                        it.getLong(3),
                    ),
                )
            }
        }
        val local = HashSet(inventoryKanji)
        return ConfusionPairMiner().minePairs(rows, nowMillis)
            .filter { it.kanjiA in local && it.kanjiB in local }
    }

    fun rebuildSimilarKanjiChoiceStates(db: SQLiteDatabase, nowMillis: Long) {
        activity.createSimilarKanjiPracticeTables(db)
        val previous = activity.similarChoiceSnapshots(db)
        val planner = SimilarKanjiChoicePlanner()
        val candidates = planner.buildCandidates(
            activity.allInventoryItems(db),
            activity.allSimilarPairs(db),
            activity.choiceWrongPickCounts(db, ConfusionPairMiner.windowStartMillis(nowMillis)),
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
