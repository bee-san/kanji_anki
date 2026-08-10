package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.TimelineCopy

/**
 * Driver-neutral repaired write-back proposal, preview, and receipt
 * persistence. Ported from the legacy `LocalStoreRepairedWriteBack`. The
 * proposal is pure planning over durable state; recording stamps only
 * provider-confirmed notes and is an idempotent post-commit transaction.
 */
internal class SqlRepairedWriteBackData(
    private val session: SqlSession,
) {
    fun proposal(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        matureSupportThreshold: Int,
    ): RepairedWriteBackPolicy.Proposal {
        val sources = repairedSourceRows()
        if (sources.isEmpty()) {
            return RepairedWriteBackPolicy.plan(emptyList(), emptyList(), emptyList(), matureSupportThreshold)
        }
        val sourceKanji = sources.mapTo(linkedSetOf()) { it.kanji }
        val itemsByKanji = SqlStudyData(session).studyItemsForKanji(sourceKanji.toList())
            .groupBy { it.kanji }
        val matureSupportByKanji = HashMap<String, Int>()
        session.queryList(
            "SELECT kanji, mature_support_count FROM dashboard_rows",
        ) { row -> NamedSqlRow(row).let { it.text("kanji") to it.int("mature_support_count") } }
            .forEach { (kanji, count) -> matureSupportByKanji[kanji] = count }
        val evidenceByKanji = SqlRepairEvidenceReader(session).inputs()
            .map(KanjiRepairEvidencePolicy::summarize)
            .associateBy { it.kanji() }
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
                evidenceStatus = evidence?.status(),
                evidenceConfidence = evidence?.confidence() ?: 0.0,
            )
        }
        return RepairedWriteBackPolicy.plan(
            states,
            sources,
            repairedWriteBackCards(snapshot),
            matureSupportThreshold,
        )
    }

    fun preview(matureSupportThreshold: Int): RepairedWriteBackPolicy.Proposal {
        val cards = session.queryList(
            """
            SELECT card_id, note_id, 0 AS suspended FROM source_cards
            UNION ALL
            SELECT card_id, note_id, 1 AS suspended FROM suspended_archive
            """.trimIndent(),
        ) { row ->
            val suspended = row.long(2) == 1L
            RecordsSyncModels.Card(
                row.long(0), row.long(1), 0, "", if (suspended) -1 else 0, 0, 0, 0, 0, 0, suspended,
            )
        }.distinctBy { it.cardId }
        return proposal(RecordsSyncModels.CollectionSnapshot(emptyList(), cards), matureSupportThreshold)
    }

    fun record(
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
        for (noteId in successfulNotes.sorted()) {
            for (cardId in proposal.cardIdsByNote[noteId].orEmpty()) {
                session.executeBound(
                    "UPDATE suspended_archive SET restored_at = ? WHERE card_id = ?",
                ) {
                    bindLong(1, occurredAtMillis)
                    bindLong(2, cardId)
                }
            }
            for (kanji in proposal.kanjiByNote[noteId].orEmpty().sorted()) {
                val source = firstSuspendedSource(kanji)
                session.insertRow(
                    "kanji_timeline_events",
                    "IGNORE",
                    linkedMapOf(
                        "kanji" to kanji,
                        "occurred_at" to occurredAtMillis,
                        "event_type" to TimelineCopy.EVENT_REPAIR_TAGGED,
                        "title" to TimelineCopy.repairTaggedTitle(),
                        "detail" to TimelineCopy.repairTaggedDetail(),
                        "source_expression" to source.first,
                        "source_reading" to source.second,
                        "rating" to "",
                        "writing_required" to false,
                        "writing_passed" to false,
                        "manual_override" to false,
                        "weakness_score" to null,
                        "mature_support_count" to null,
                        "sync_id" to syncId,
                        "dedupe_key" to "repair_tagged:$kanji:$noteId",
                    ),
                )
            }
        }
        val pending = (alreadyPending + repairedKanji).distinct().sorted()
        session.executeBound(
            """
            INSERT INTO settings(key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
            """.trimIndent(),
        ) {
            bindText(1, SqlSyncData.REPAIRED_HANDOFF_SETTING_KEY)
            bindText(2, pending.joinToString("\n"))
            bindLong(3, occurredAtMillis)
        }
        return repairedKanji
    }

    private fun pendingRepairedHandoffKanji(): List<String> =
        session.queryOneOrNull(
            "SELECT value FROM settings WHERE key = ? LIMIT 1",
            bind = { bindText(1, SqlSyncData.REPAIRED_HANDOFF_SETTING_KEY) },
        ) { row -> row.textOrEmpty(0) }
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .toList()

    private fun repairedSourceRows(): List<RepairedWriteBackPolicy.Source> =
        session.queryList(
            """
            SELECT s.kanji, s.card_id, s.note_id, a.restored_at
            FROM suspended_sources s
            JOIN suspended_archive a ON a.card_id = s.card_id
            ORDER BY s.kanji, s.card_id
            """.trimIndent(),
        ) { row ->
            RepairedWriteBackPolicy.Source(
                kanji = row.textOrEmpty(0),
                cardId = row.long(1),
                noteId = row.long(2),
                restoredAtMillis = if (row.isNull(3)) null else row.long(3),
            )
        }

    private fun repairedWriteBackCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
    ): List<RepairedWriteBackPolicy.Card> {
        val cardsById = LinkedHashMap<Long, RepairedWriteBackPolicy.Card>()
        session.queryList(
            "SELECT card_id, note_id FROM suspended_archive ORDER BY card_id",
        ) { row -> RepairedWriteBackPolicy.Card(row.long(0), row.long(1), true) }
            .forEach { cardsById[it.cardId] = it }
        snapshot.cards.forEach { card ->
            cardsById[card.cardId] = RepairedWriteBackPolicy.Card(card.cardId, card.noteId, card.suspended)
        }
        return cardsById.values.toList()
    }

    private fun firstSuspendedSource(kanji: String): Pair<String, String> =
        session.queryOneOrNull(
            "SELECT expression, reading FROM suspended_sources WHERE kanji = ? ORDER BY card_id ASC LIMIT 1",
            bind = { bindText(1, kanji) },
        ) { row -> row.textOrEmpty(0) to row.textOrEmpty(1) } ?: ("" to "")
}
