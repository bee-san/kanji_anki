package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.SimilarKanjiRepairPolicy

internal class LocalStoreSimilarKanjiData(
    private val activity: LocalStoreHistory,
) {
    fun similarChoiceSnapshots(db: SQLiteDatabase): Map<String, LocalStoreBase.SimilarChoiceSnapshot> {
        val out = HashMap<String, LocalStoreBase.SimilarChoiceSnapshot>()
        db.query(LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE, null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val target = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TARGET_KANJI)
                val signature = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICE_SIGNATURE)
                out[LocalStoreHistory.similarChoiceKey(target, signature)] = LocalStoreBase.SimilarChoiceSnapshot(
                    LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
                    LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_PASSED_AT),
                    LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_REVIEWED_AT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_CORRECT_COUNT),
                    LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRONG_COUNT),
                    LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FIRST_SEEN_AT),
                )
            }
        }
        return out
    }

    fun similarPairFirstSeen(db: SQLiteDatabase): Map<String, Long> {
        val out = HashMap<String, Long>()
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS,
            arrayOf(
                LocalStoreBase.COLUMN_KANJI_A,
                LocalStoreBase.COLUMN_KANJI_B,
                LocalStoreBase.COLUMN_SOURCE,
                LocalStoreBase.COLUMN_FIRST_SEEN_AT,
            ),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out[
                    LocalStoreHistory.similarKey(
                        LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI_A),
                        LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI_B),
                        LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_SOURCE),
                    )
                ] = LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FIRST_SEEN_AT)
            }
        }
        return out
    }

    fun localInventoryKanji(db: SQLiteDatabase): Set<String> {
        val out = HashSet<String>()
        db.query(
            LocalStoreBase.TABLE_KANJI_INVENTORY,
            arrayOf(LocalStoreBase.COLUMN_KANJI),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = LocalStoreHistory.normalizeSingleKanji(
                    LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI),
                )
                if (kanji.isNotEmpty()) {
                    out.add(kanji)
                }
            }
        }
        return out
    }

    fun choiceWrongPickCounts(db: SQLiteDatabase, sinceMillis: Long): Map<String, Map<String, Int>> {
        val out = HashMap<String, MutableMap<String, Int>>()
        db.rawQuery(
            "SELECT target_kanji, selected_kanji, COUNT(*) " +
                "FROM ${LocalStoreBase.TABLE_SIMILAR_KANJI_REVIEW_LOG} " +
                "WHERE correct=0 AND reviewed_at>=? AND selected_kanji<>'' AND selected_kanji<>target_kanji " +
                "GROUP BY target_kanji, selected_kanji",
            arrayOf(sinceMillis.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val target = cursor.getString(0)
                val selected = cursor.getString(1)
                if (target.isNullOrEmpty() || selected.isNullOrEmpty()) {
                    continue
                }
                out.getOrPut(target) { HashMap() }[selected] = cursor.getInt(2)
            }
        }
        return out
    }

    fun readSimilarPair(cursor: Cursor): RecordsImportModels.SimilarKanjiPair {
        return RecordsImportModels.SimilarKanjiPair(
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI_A),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI_B),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_SOURCE),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FIRST_SEEN_AT),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_SEEN_AT),
        )
    }

    fun allSimilarPairs(db: SQLiteDatabase): List<RecordsImportModels.SimilarKanjiPair> {
        val out = ArrayList<RecordsImportModels.SimilarKanjiPair>()
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS,
            null,
            null,
            null,
            null,
            null,
            LocalStoreBase.ORDER_SIMILAR_PAIR,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(readSimilarPair(cursor))
            }
        }
        return out
    }

    fun allInventoryItems(db: SQLiteDatabase): List<RecordsImportModels.KanjiInventoryItem> {
        val out = ArrayList<RecordsImportModels.KanjiInventoryItem>()
        db.query(
            LocalStoreBase.TABLE_KANJI_INVENTORY,
            null,
            null,
            null,
            null,
            null,
            LocalStoreBase.ORDER_KANJI_ASC,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(activity.readInventoryItem(db, cursor))
            }
        }
        return out
    }

    fun readSimilarChoiceCard(cursor: Cursor): RecordsImportModels.SimilarKanjiChoiceCard {
        return RecordsImportModels.SimilarKanjiChoiceCard(
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TARGET_KANJI),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_PRIMARY_MEANING),
            LocalStoreHistory.deserializeChoices(LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICES)),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_CHOICE_SIGNATURE),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_PASSED_AT),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_REVIEWED_AT),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_CORRECT_COUNT),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRONG_COUNT),
        )
    }

    fun similarChoiceCard(
        db: SQLiteDatabase,
        targetKanji: String?,
        choiceSignature: String?,
    ): RecordsImportModels.SimilarKanjiChoiceCard? {
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
            null,
            LocalStoreBase.WHERE_SIMILAR_CHOICE,
            arrayOf(targetKanji, choiceSignature),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) readSimilarChoiceCard(cursor) else null
        }
    }

    fun hasPendingSimilarRepairs(
        db: SQLiteDatabase,
        targetKanji: String?,
        choiceSignature: String?,
    ): Boolean {
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            arrayOf("id"),
            "status=? AND target_kanji=? AND choice_signature=?",
            arrayOf(LocalStoreBase.STATUS_PENDING, targetKanji, choiceSignature),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun enqueueSimilarWritingRepair(
        db: SQLiteDatabase,
        card: RecordsImportModels.SimilarKanjiChoiceCard?,
        repairKanji: String?,
        wrongSelection: String?,
        nowMillis: Long,
    ) {
        val draft = SimilarKanjiRepairPolicy.newRepair(card, repairKanji, wrongSelection, nowMillis) ?: return
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            arrayOf("id"),
            "status=? AND target_kanji=? AND choice_signature=? AND repair_kanji=?",
            arrayOf(
                LocalStoreBase.STATUS_PENDING,
                draft.targetKanji(),
                draft.choiceSignature(),
                draft.repairKanji(),
            ),
            null,
            null,
            null,
            "1",
        ).use { pending ->
            if (pending.moveToFirst()) {
                return
            }
        }
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_TARGET_KANJI, draft.targetKanji())
        values.put("repair_kanji", draft.repairKanji())
        values.put(LocalStoreBase.COLUMN_CHOICE_SIGNATURE, draft.choiceSignature())
        values.put("wrong_selection", draft.wrongSelection())
        values.put("prompt_meaning", draft.promptMeaning())
        values.put(LocalStoreBase.COLUMN_STATUS, draft.status())
        values.put(LocalStoreBase.COLUMN_DUE_AT, draft.dueAtMillis())
        values.put(LocalStoreBase.COLUMN_ACTIVE_TOKEN, draft.activeToken())
        values.put(LocalStoreBase.COLUMN_ATTEMPTS, draft.attempts())
        values.put(LocalStoreBase.COLUMN_CREATED_AT, draft.createdAtMillis())
        values.put(LocalStoreBase.COLUMN_UPDATED_AT, draft.updatedAtMillis())
        values.put(LocalStoreBase.COLUMN_COMPLETED_AT, draft.completedAtMillis())
        db.insert(LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE, null, values)
    }

    fun similarWritingRepair(
        db: SQLiteDatabase,
        repairId: Long,
    ): RecordsImportModels.SimilarKanjiWritingRepair? {
        db.query(
            LocalStoreBase.TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            null,
            "id=?",
            arrayOf(repairId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) readSimilarWritingRepair(cursor) else null
        }
    }

    fun readSimilarWritingRepair(cursor: Cursor): RecordsImportModels.SimilarKanjiWritingRepair {
        return RecordsImportModels.SimilarKanjiWritingRepair(
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
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_COMPLETED_AT),
        )
    }
}
