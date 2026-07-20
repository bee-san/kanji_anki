package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.TimelineCopy

internal const val REPAIRED_HANDOFF_SETTING_KEY = "repaired_handoff_kanji"

internal fun LocalStore.repairedWriteBackProposal(
    snapshot: RecordsSyncModels.CollectionSnapshot,
    matureSupportThreshold: Int,
): RepairedWriteBackPolicy.Proposal {
    val sources = repairedSourceRows()
    if (sources.isEmpty()) {
        return RepairedWriteBackPolicy.plan(emptyList(), emptyList(), emptyList(), matureSupportThreshold)
    }
    val sourceKanji = sources.mapTo(linkedSetOf()) { it.kanji }
    val itemsByKanji = studyItems().groupBy { it.kanji }
    val matureSupportByKanji = linkedMapOf<String, Int>()
    readableDatabase.query(
        LocalStoreBase.TABLE_DASHBOARD_ROWS,
        arrayOf(LocalStoreBase.COLUMN_KANJI, LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT),
        null,
        null,
        null,
        null,
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) {
            matureSupportByKanji[LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI)] =
                LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT)
        }
    }
    val evidenceByKanji = kanjiRepairEvidence().associateBy { it.kanji }
    val states = sourceKanji.map { kanji ->
        val items = itemsByKanji[kanji].orEmpty()
        val state = if (items.any { it.state == StudyLadderRules.STATE_RETIRED }) {
            StudyLadderRules.STATE_RETIRED
        } else {
            items.firstOrNull()?.state.orEmpty()
        }
        val evidence = evidenceByKanji[kanji]
        RepairedWriteBackPolicy.RepairState(
            kanji = kanji,
            studyState = state,
            matureSupportCount = matureSupportByKanji[kanji] ?: 0,
            evidenceStatus = evidence?.status,
            evidenceConfidence = evidence?.confidence ?: 0.0,
        )
    }
    return RepairedWriteBackPolicy.plan(
        states,
        sources,
        repairedWriteBackCards(snapshot),
        matureSupportThreshold,
    )
}

/**
 * Archived notes are deliberately filtered from later provider snapshots, so
 * their original suspended cards must come from Kani's durable archive. A
 * live provider row wins when one is available (for example, after a manual
 * state change) so the every-card-of-note policy never ignores fresher state.
 */
private fun LocalStore.repairedWriteBackCards(
    snapshot: RecordsSyncModels.CollectionSnapshot,
): List<RepairedWriteBackPolicy.Card> {
    val cardsById = linkedMapOf<Long, RepairedWriteBackPolicy.Card>()
    readableDatabase.rawQuery(
        "SELECT card_id,note_id FROM suspended_archive ORDER BY card_id",
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val card = RepairedWriteBackPolicy.Card(cursor.getLong(0), cursor.getLong(1), true)
            cardsById[card.cardId] = card
        }
    }
    snapshot.cards.forEach { card ->
        cardsById[card.cardId] = RepairedWriteBackPolicy.Card(card.cardId, card.noteId, card.suspended)
    }
    return cardsById.values.toList()
}

internal fun LocalStore.repairedWriteBackPreview(
    matureSupportThreshold: Int,
): RepairedWriteBackPolicy.Proposal {
    val cards = mutableListOf<RecordsSyncModels.Card>()
    readableDatabase.rawQuery(
        "SELECT card_id,note_id,0 AS suspended FROM source_cards UNION ALL " +
            "SELECT card_id,note_id,1 AS suspended FROM suspended_archive",
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val suspended = cursor.getInt(2) == 1
            cards += RecordsSyncModels.Card(
                cursor.getLong(0),
                cursor.getLong(1),
                0,
                "",
                if (suspended) -1 else 0,
                0,
                0,
                0,
                0,
                0,
                suspended,
            )
        }
    }
    return repairedWriteBackProposal(
        RecordsSyncModels.CollectionSnapshot(emptyList(), cards.distinctBy { it.cardId }),
        matureSupportThreshold,
    )
}

private fun LocalStore.repairedSourceRows(): List<RepairedWriteBackPolicy.Source> {
    val rows = mutableListOf<RepairedWriteBackPolicy.Source>()
    readableDatabase.rawQuery(
        "SELECT s.kanji,s.card_id,s.note_id,a.restored_at " +
        "FROM suspended_sources s JOIN suspended_archive a ON a.card_id=s.card_id " +
            "ORDER BY s.kanji,s.card_id",
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) {
            rows += RepairedWriteBackPolicy.Source(
                kanji = cursor.getString(0).orEmpty(),
                cardId = cursor.getLong(1),
                noteId = cursor.getLong(2),
                restoredAtMillis = if (cursor.isNull(3)) null else cursor.getLong(3),
            )
        }
    }
    return rows
}

/** Stamps only provider-confirmed notes; failed notes remain eligible for retry. */
internal fun LocalStore.recordRepairedWriteBack(
    proposal: RepairedWriteBackPolicy.Proposal,
    taggedNoteIds: Set<Long>,
    occurredAtMillis: Long,
    syncId: Long,
): List<String> {
    val successfulNotes = proposal.noteIdsToTag.intersect(taggedNoteIds)
    if (successfulNotes.isEmpty()) return emptyList()
    val repairedKanji = successfulNotes
        .flatMap { proposal.kanjiByNote[it].orEmpty() }
        .distinct()
        .sorted()
    val alreadyPending = pendingRepairedHandoffKanji()
    val db = writableDatabase
    db.transaction {
        for (noteId in successfulNotes.sorted()) {
            for (cardId in proposal.cardIdsByNote[noteId].orEmpty()) {
                db.execSQL(
                    "UPDATE ${LocalStoreBase.TABLE_SUSPENDED_ARCHIVE} SET restored_at=? WHERE card_id=?",
                    arrayOf(occurredAtMillis, cardId),
                )
            }
            for (kanji in proposal.kanjiByNote[noteId].orEmpty().sorted()) {
                val source = firstSuspendedSourceForKanji(db, kanji)
                insertTimelineEvent(
                    db,
                    kanji,
                    occurredAtMillis,
                    TimelineCopy.EVENT_REPAIR_TAGGED,
                    TimelineCopy.repairTaggedTitle(),
                    TimelineCopy.repairTaggedDetail(),
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    null,
                    null,
                    syncId,
                    "repair_tagged:$kanji:$noteId",
                )
            }
        }
        val pending = (alreadyPending + repairedKanji).distinct().sorted()
        val setting = ContentValues().apply {
            put("key", REPAIRED_HANDOFF_SETTING_KEY)
            put(LocalStoreBase.COLUMN_VALUE, pending.joinToString("\n"))
            put(LocalStoreBase.COLUMN_UPDATED_AT, occurredAtMillis)
        }
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_SETTINGS,
            null,
            setting,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
    // This transaction intentionally writes the settings table directly so its timeline/archive
    // updates stay atomic. Publish that write to the bulk settings cache after commit.
    settingsStore().invalidate()
    return repairedKanji
}

internal fun LocalStore.pendingRepairedHandoffKanji(): List<String> =
    getStringSetting(REPAIRED_HANDOFF_SETTING_KEY, "")
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
        .toList()

internal fun LocalStore.dismissRepairedHandoff() {
    putStringSetting(REPAIRED_HANDOFF_SETTING_KEY, "")
}
