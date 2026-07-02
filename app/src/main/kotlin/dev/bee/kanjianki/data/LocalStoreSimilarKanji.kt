package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner
import dev.bee.kanjianki.core.SimilarKanjiChoiceReviewPolicy
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SimilarKanjiRepairPolicy

internal abstract class LocalStoreSimilarKanji(context: Context?) : LocalStoreStudy(context) {
    fun rebuildSimilarKanjiPairs(similarIndex: SimilarKanjiIndex?, nowMillis: Long) {
        if (similarIndex == null) {
            return
        }
        writableDatabase.transaction {
            rebuildSimilarKanjiPairs(this, similarIndex, nowMillis)
            rebuildSimilarKanjiChoiceStates(this, nowMillis)
        }
        clearStudyItemsCache()
    }

    fun allLocalSimilarPairs(): List<RecordsImportModels.SimilarKanjiPair> {
        val out = ArrayList<RecordsImportModels.SimilarKanjiPair>()
        readableDatabase.query(TABLE_SIMILAR_KANJI_PAIRS, null, null, null, null, null, ORDER_SIMILAR_PAIR).use {
            while (it.moveToNext()) {
                out.add(readSimilarPair(it))
            }
        }
        return out
    }

    fun similarPairsForKanji(kanji: String?): List<RecordsImportModels.SimilarKanjiPair> {
        val normalized = normalizeSingleKanji(kanji)
        if (normalized.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<RecordsImportModels.SimilarKanjiPair>()
        readableDatabase.query(
            TABLE_SIMILAR_KANJI_PAIRS,
            null,
            "kanji_a=? OR kanji_b=?",
            arrayOf(normalized, normalized),
            null,
            null,
            ORDER_SIMILAR_PAIR
        ).use {
            while (it.moveToNext()) {
                out.add(readSimilarPair(it))
            }
        }
        return out
    }

    fun hasSimilarLocalPair(first: String?, second: String?): Boolean {
        val kanjiA = normalizeSingleKanji(first)
        val kanjiB = normalizeSingleKanji(second)
        if (kanjiA.isEmpty() || kanjiB.isEmpty() || kanjiA == kanjiB) {
            return false
        }
        val pair = canonicalSimilarPair(kanjiA, kanjiB)
        readableDatabase.query(
            TABLE_SIMILAR_KANJI_PAIRS,
            arrayOf(COLUMN_KANJI_A),
            "kanji_a=? AND kanji_b=?",
            pair,
            null,
            null,
            null,
            "1"
        ).use {
            return it.moveToFirst()
        }
    }

    /**
     * Aggregated wrong-pick counts (target -> selected -> count) from the
     * choice review log, limited to the confusion-mining window. Used to
     * weight multiple-choice distractors toward historically confused kanji.
     */
    fun choiceWrongPickCounts(nowMillis: Long): Map<String, Map<String, Int>> {
        return choiceWrongPickCounts(readableDatabase, ConfusionPairMiner.windowStartMillis(nowMillis))
    }

    fun allSimilarChoiceCards(): List<RecordsImportModels.SimilarKanjiChoiceCard> {
        val out = ArrayList<RecordsImportModels.SimilarKanjiChoiceCard>()
        readableDatabase.query(
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            null,
            null,
            null,
            null,
            null,
            "target_kanji ASC, choice_signature ASC"
        ).use {
            while (it.moveToNext()) {
                out.add(readSimilarChoiceCard(it))
            }
        }
        return out
    }

    fun dueSimilarChoiceForActiveTarget(
        kanji: String?,
        nowMillis: Long,
    ): RecordsImportModels.SimilarKanjiChoiceCard? {
        val target = normalizeSingleKanji(kanji)
        if (target.isEmpty()) {
            return null
        }
        val db = readableDatabase
        db.query(
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            null,
            "target_kanji=? AND passed_at=0 AND due_at<=?",
            arrayOf(target, nowMillis.toString()),
            null,
            null,
            "due_at ASC, first_seen_at ASC",
            "1"
        ).use {
            if (!it.moveToFirst()) {
                return null
            }
            val card = readSimilarChoiceCard(it)
            return if (hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature)) null else card
        }
    }

    fun nextDueInventorySimilarChoice(
        activeTargets: Set<String>?,
        nowMillis: Long,
    ): RecordsImportModels.SimilarKanjiChoiceCard? {
        val db = readableDatabase
        db.query(
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            null,
            "passed_at=0 AND due_at<=?",
            arrayOf(nowMillis.toString()),
            null,
            null,
            "due_at ASC, last_reviewed_at ASC, target_kanji ASC"
        ).use {
            while (it.moveToNext()) {
                val card = readSimilarChoiceCard(it)
                if (activeTargets != null && activeTargets.contains(card.targetKanji)) {
                    continue
                }
                if (!hasPendingSimilarRepairs(db, card.targetKanji, card.choiceSignature)) {
                    return card
                }
            }
            return null
        }
    }

    fun dueSimilarStudyTaskCount(nowMillis: Long): Int {
        return dueSimilarChoiceTaskCount(nowMillis) + dueSimilarWritingRepairTaskCount(nowMillis)
    }

    fun dueSimilarChoiceTaskCount(nowMillis: Long): Int {
        val db = readableDatabase
        var count = 0
        db.query(
            TABLE_SIMILAR_KANJI_CHOICE_STATE,
            arrayOf(COLUMN_TARGET_KANJI, COLUMN_CHOICE_SIGNATURE),
            "passed_at=0 AND due_at<=?",
            arrayOf(nowMillis.toString()),
            null,
            null,
            null
        ).use {
            while (it.moveToNext()) {
                val targetKanji = string(it, COLUMN_TARGET_KANJI)
                val choiceSignature = string(it, COLUMN_CHOICE_SIGNATURE)
                if (!hasPendingSimilarRepairs(db, targetKanji, choiceSignature)) {
                    count++
                }
            }
        }
        return count
    }

    fun dueSimilarWritingRepairTaskCount(nowMillis: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SIMILAR_KANJI_REPAIR_QUEUE WHERE status=? AND due_at<=?",
            arrayOf(STATUS_PENDING, nowMillis.toString())
        ).use {
            it.moveToFirst()
            return it.getInt(0)
        }
    }

    fun submitSimilarChoice(
        submitted: RecordsImportModels.SimilarKanjiChoiceCard?,
        selectedKanji: String?,
        nowMillis: Long,
    ): RecordsImportModels.SimilarKanjiChoiceResult {
        return submitSimilarChoice(submitted, selectedKanji, nowMillis, true)
    }

    /**
     * Records a multiple-choice pick for confusion-pair mining. Used by rungs
     * that render a choice grid outside the similar-kanji practice tables
     * (currently meaning_kanji); the similar_kanji path logs inline during
     * submitSimilarChoice.
     */
    fun recordChoiceReviewLog(
        targetKanji: String?,
        choiceSignature: String?,
        selectedKanji: String?,
        correct: Boolean,
        rung: String?,
        nowMillis: Long,
    ) {
        val target = normalizeSingleKanji(targetKanji)
        val selected = normalizeSingleKanji(selectedKanji)
        if (target.isEmpty() || selected.isEmpty()) {
            return
        }
        val log = ContentValues()
        log.put(COLUMN_TARGET_KANJI, target)
        log.put(COLUMN_CHOICE_SIGNATURE, choiceSignature ?: "")
        log.put("selected_kanji", selected)
        log.put("correct", if (correct) 1 else 0)
        log.put(COLUMN_REVIEWED_AT, nowMillis)
        log.put(COLUMN_RUNG, rung?.trim().takeUnless { it.isNullOrEmpty() } ?: RecordsBase.LadderRung.SIMILAR_KANJI.wireName())
        writableDatabase.insert(TABLE_SIMILAR_KANJI_REVIEW_LOG, null, log)
    }

    fun submitSimilarChoice(
        submitted: RecordsImportModels.SimilarKanjiChoiceCard?,
        selectedKanji: String?,
        nowMillis: Long,
        enqueueRepairs: Boolean,
    ): RecordsImportModels.SimilarKanjiChoiceResult {
        if (submitted == null) {
            return RecordsImportModels.SimilarKanjiChoiceResult(null, selectedKanji, false, emptyList())
        }
        var result: RecordsImportModels.SimilarKanjiChoiceResult? = null
        writableDatabase.transaction {
            var card = similarChoiceCard(this, submitted.targetKanji, submitted.choiceSignature)
            if (card == null) {
                card = submitted
            }
            val planner = SimilarKanjiChoicePlanner()
            val evaluated = planner.evaluateSelection(card, normalizeSingleKanji(selectedKanji))
            val reviewUpdate = SimilarKanjiChoiceReviewPolicy.reviewUpdate(card, evaluated, nowMillis)

            val values = ContentValues()
            values.put(COLUMN_LAST_REVIEWED_AT, reviewUpdate.lastReviewedAtMillis())
            values.put(COLUMN_PASSED_AT, reviewUpdate.passedAtMillis())
            reviewUpdate.dueAtMillis()?.let { values.put(COLUMN_DUE_AT, it) }
            reviewUpdate.correctCount()?.let { values.put(COLUMN_CORRECT_COUNT, it) }
            reviewUpdate.wrongCount()?.let { values.put(COLUMN_WRONG_COUNT, it) }
            update(
                TABLE_SIMILAR_KANJI_CHOICE_STATE,
                values,
                WHERE_SIMILAR_CHOICE,
                arrayOf(card.targetKanji, card.choiceSignature)
            )

            val log = ContentValues()
            log.put(COLUMN_TARGET_KANJI, card.targetKanji)
            log.put(COLUMN_CHOICE_SIGNATURE, card.choiceSignature)
            log.put("selected_kanji", evaluated.selectedKanji)
            log.put("correct", if (evaluated.correct) 1 else 0)
            log.put(COLUMN_REVIEWED_AT, nowMillis)
            log.put(COLUMN_RUNG, RecordsBase.LadderRung.SIMILAR_KANJI.wireName())
            insert(TABLE_SIMILAR_KANJI_REVIEW_LOG, null, log)

            if (!evaluated.correct && enqueueRepairs) {
                for (repairKanji in evaluated.repairKanji) {
                    enqueueSimilarWritingRepair(this, card, repairKanji, evaluated.selectedKanji, nowMillis)
                }
            }
            result = evaluated
        }
        return result ?: RecordsImportModels.SimilarKanjiChoiceResult(null, selectedKanji, false, emptyList())
    }

    fun nextDueSimilarWritingRepair(nowMillis: Long): RecordsImportModels.SimilarKanjiWritingRepair? {
        val repairs = dueSimilarWritingRepairs(nowMillis)
        return if (repairs.isEmpty()) null else repairs[0]
    }

    fun dueSimilarWritingRepairs(nowMillis: Long): List<RecordsImportModels.SimilarKanjiWritingRepair> {
        val out = ArrayList<RecordsImportModels.SimilarKanjiWritingRepair>()
        readableDatabase.query(
            TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            null,
            "status=? AND due_at<=?",
            arrayOf(STATUS_PENDING, nowMillis.toString()),
            null,
            null,
            "created_at ASC, id ASC",
            null
        ).use {
            while (it.moveToNext()) {
                out.add(readSimilarWritingRepair(it))
            }
        }
        return out
    }

    fun saveSimilarWritingRepair(repair: RecordsImportModels.SimilarKanjiWritingRepair?) {
        if (repair == null || repair.id <= 0L) {
            return
        }
        val values = ContentValues()
        values.put(COLUMN_ACTIVE_TOKEN, repair.activeToken)
        values.put(COLUMN_UPDATED_AT, repair.updatedAtMillis)
        writableDatabase.update(
            TABLE_SIMILAR_KANJI_REPAIR_QUEUE,
            values,
            "id=? AND status=?",
            arrayOf(repair.id.toString(), STATUS_PENDING)
        )
    }

    fun finishSimilarWritingRepair(
        repairId: Long,
        token: String?,
        passed: Boolean,
        nowMillis: Long,
    ): Boolean {
        var finished = false
        writableDatabase.transaction {
            val current = similarWritingRepair(this, repairId)
            if (current == null || current.status != STATUS_PENDING) {
                return@transaction
            }
            if (current.activeToken.isNotEmpty() && current.activeToken != (token ?: "")) {
                return@transaction
            }
            val finishUpdate = SimilarKanjiRepairPolicy.finishUpdate(current, passed, nowMillis)
            val values = ContentValues()
            values.put(COLUMN_ACTIVE_TOKEN, finishUpdate.activeToken())
            values.put(COLUMN_UPDATED_AT, finishUpdate.updatedAtMillis())
            finishUpdate.status()?.let { values.put(COLUMN_STATUS, it) }
            finishUpdate.completedAtMillis()?.let { values.put(COLUMN_COMPLETED_AT, it) }
            finishUpdate.attempts()?.let { values.put(COLUMN_ATTEMPTS, it) }
            finishUpdate.dueAtMillis()?.let { values.put(COLUMN_DUE_AT, it) }
            update(TABLE_SIMILAR_KANJI_REPAIR_QUEUE, values, "id=?", arrayOf(repairId.toString()))
            finished = true
        }
        return finished
    }

    fun skipSimilarWritingRepair(
        repairId: Long,
        token: String?,
        nowMillis: Long,
    ): Boolean {
        var finished = false
        writableDatabase.transaction {
            val current = similarWritingRepair(this, repairId)
            if (current == null || current.status != STATUS_PENDING) {
                return@transaction
            }
            if (current.activeToken.isNotEmpty() && current.activeToken != (token ?: "")) {
                return@transaction
            }
            val finishUpdate = SimilarKanjiRepairPolicy.skipUpdate(current, nowMillis)
            val values = ContentValues()
            values.put(COLUMN_ACTIVE_TOKEN, finishUpdate.activeToken())
            values.put(COLUMN_UPDATED_AT, finishUpdate.updatedAtMillis())
            finishUpdate.status()?.let { values.put(COLUMN_STATUS, it) }
            finishUpdate.completedAtMillis()?.let { values.put(COLUMN_COMPLETED_AT, it) }
            finishUpdate.attempts()?.let { values.put(COLUMN_ATTEMPTS, it) }
            finishUpdate.dueAtMillis()?.let { values.put(COLUMN_DUE_AT, it) }
            update(TABLE_SIMILAR_KANJI_REPAIR_QUEUE, values, "id=?", arrayOf(repairId.toString()))
            finished = true
        }
        return finished
    }
}
