package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.HistoricalKanjiAggregate
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TextUtil
import java.util.LinkedHashSet

internal class HistoricalSyncStore(private val localStore: LocalStoreHistory) {
    fun appendHistoricalSyncSnapshots(
        db: SQLiteDatabase,
        snapshot: RecordsSyncModels.CollectionSnapshot,
        notesById: Map<Long, RecordsSyncModels.Note>,
        rows: List<RecordsImportModels.DashboardRow>,
        settings: RecordsSyncModels.Settings,
        syncId: Long,
        timing: LocalStoreBase.SyncTiming,
    ) {
        localStore.createHistoricalSyncTables(db)
        val deckIdsByNote = deckIdsByNote(snapshot.cards)
        val deckNamesByNote = deckNamesByNote(snapshot.cards)
        val aggregates = LinkedHashMap<String, HistoricalKanjiAggregate>()

        for (card in snapshot.cards) {
            val note = notesById[card.noteId] ?: continue
            val cardValues = ContentValues()
            cardValues.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
            cardValues.put(LocalStoreBase.COLUMN_STARTED_AT, timing.startedAt)
            cardValues.put(LocalStoreBase.COLUMN_FINISHED_AT, timing.finishedAt)
            cardValues.put(LocalStoreBase.COLUMN_CARD_ID, card.cardId)
            cardValues.put(LocalStoreBase.COLUMN_NOTE_ID, card.noteId)
            cardValues.put(LocalStoreBase.COLUMN_DECK_ID, card.deckId)
            cardValues.put(LocalStoreBase.COLUMN_DECK_NAME, card.deckName)
            cardValues.put(LocalStoreBase.COLUMN_MODEL_ID, note.modelId)
            cardValues.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName)
            cardValues.put("ord", card.ord)
            cardValues.put(LocalStoreBase.COLUMN_QUEUE, card.queue)
            cardValues.put("type", card.type)
            cardValues.put("due", card.due)
            cardValues.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, card.intervalDays)
            cardValues.put(LocalStoreBase.COLUMN_REPS, card.reps)
            cardValues.put(LocalStoreBase.COLUMN_LAPSES, card.lapses)
            cardValues.put("suspended", if (card.suspended) 1 else 0)
            LocalStoreBase.putNullableDouble(cardValues, LocalStoreBase.COLUMN_FSRS_STABILITY, card.fsrsStability)
            LocalStoreBase.putNullableDouble(cardValues, LocalStoreBase.COLUMN_FSRS_DIFFICULTY, card.fsrsDifficulty)
            LocalStoreBase.putNullableDouble(
                cardValues,
                LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY,
                card.fsrsRetrievability
            )
            cardValues.put(LocalStoreBase.COLUMN_MATURE, if (card.mature(settings.matureDays)) 1 else 0)
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SYNC_CARD_SNAPSHOTS,
                null,
                cardValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            for (kanji in extractedKanji(note, settings)) {
                aggregateFor(aggregates, kanji).add(card, settings.matureDays)
            }
        }

        for (note in snapshot.notes) {
            val deckIds = deckIdsByNote[note.noteId]
            val decks = deckNamesByNote[note.noteId]
            if (decks.isNullOrEmpty()) {
                continue
            }
            val expression = TextUtil.normalizeJapanese(note.expression(settings))
            val reading = TextUtil.normalizeJapanese(note.reading(settings))
            val meaning = TextUtil.firstMeaningLine(note.meaning(settings))
            val sentence = TextUtil.normalizeJapanese(note.sentence(settings))
            val noteValues = ContentValues()
            noteValues.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
            noteValues.put(LocalStoreBase.COLUMN_FINISHED_AT, timing.finishedAt)
            noteValues.put(LocalStoreBase.COLUMN_NOTE_ID, note.noteId)
            noteValues.put(LocalStoreBase.COLUMN_MODEL_ID, note.modelId)
            noteValues.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName)
            noteValues.put(LocalStoreBase.COLUMN_DECK_IDS, deckIds?.joinToString(" ") ?: "")
            noteValues.put(LocalStoreBase.COLUMN_DECK_NAMES, decks.joinToString(" "))
            noteValues.put(LocalStoreBase.COLUMN_EXPRESSION, expression)
            noteValues.put(LocalStoreBase.COLUMN_READING, reading)
            noteValues.put(LocalStoreBase.COLUMN_MEANING, meaning)
            noteValues.put(LocalStoreBase.COLUMN_SENTENCE, sentence)
            noteValues.put(LocalStoreBase.COLUMN_TAGS, note.tags.joinToString(" "))
            noteValues.put(LocalStoreBase.COLUMN_FIELDS_JSON, LocalStoreBase.fieldsJson(note.fields))
            noteValues.put("extracted_kanji", TextUtil.extractKanji("$expression $sentence").joinToString(""))
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SYNC_NOTE_SNAPSHOTS,
                null,
                noteValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }

        overlayDashboardRows(aggregates, rows)
        insertHistoricalKanjiAggregates(db, syncId, timing.finishedAt, aggregates)
    }

    fun backfillLatestHistoricalSync(db: SQLiteDatabase) {
        if (tableHasRows(db, LocalStoreBase.TABLE_SYNC_KANJI_SNAPSHOTS)) {
            return
        }
        val sync = latestSuccessfulSyncRun(db) ?: return
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val notes = currentSourceNotes(db)
        if (notes.isEmpty()) {
            return
        }
        val deckIdsByNote = LinkedHashMap<Long, LinkedHashSet<String>>()
        val deckNamesByNote = LinkedHashMap<Long, LinkedHashSet<String>>()
        val aggregates = LinkedHashMap<String, HistoricalKanjiAggregate>()
        val context = LocalStoreBase.HistoricalBackfillContext(settings, deckIdsByNote, deckNamesByNote, aggregates)
        backfillHistoricalCards(db, sync, notes, context)
        backfillHistoricalNotes(db, sync, notes, deckIdsByNote, deckNamesByNote)
        overlayDashboardRows(aggregates, currentDashboardRows(db))
        insertHistoricalKanjiAggregates(db, sync.id, sync.finishedAt, aggregates)
    }

    private fun backfillHistoricalCards(
        db: SQLiteDatabase,
        sync: LocalStoreBase.HistoricalSyncRun,
        notes: Map<Long, LocalStoreBase.HistoricalNoteSnapshot>,
        context: LocalStoreBase.HistoricalBackfillContext,
    ) {
        db.query(LocalStoreBase.TABLE_SOURCE_CARDS, null, null, null, null, null, "card_id ASC").use { cards ->
            while (cards.moveToNext()) {
                val note = notes[LocalStoreBase.longValue(cards, LocalStoreBase.COLUMN_NOTE_ID)] ?: continue
                backfillHistoricalCard(db, cards, sync, note, context)
            }
        }
    }

    private fun backfillHistoricalCard(
        db: SQLiteDatabase,
        cards: Cursor,
        sync: LocalStoreBase.HistoricalSyncRun,
        note: LocalStoreBase.HistoricalNoteSnapshot,
        context: LocalStoreBase.HistoricalBackfillContext,
    ) {
        val deck = LocalStoreBase.string(cards, LocalStoreBase.COLUMN_DECK_NAME)
        linkedSetFor(context.deckIdsByNote(), note.noteId).add(deck)
        linkedSetFor(context.deckNamesByNote(), note.noteId).add(deck)
        val intervalDays = LocalStoreBase.integer(cards, LocalStoreBase.COLUMN_INTERVAL_DAYS)
        val reps = LocalStoreBase.integer(cards, LocalStoreBase.COLUMN_REPS)
        val lapses = LocalStoreBase.integer(cards, LocalStoreBase.COLUMN_LAPSES)
        val mature = intervalDays >= context.settings().matureDays
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_SYNC_CARD_SNAPSHOTS,
            null,
            historicalCardValues(
                cards,
                sync,
                note,
                deck,
                LocalStoreBase.HistoricalCardMetrics(intervalDays, reps, lapses, mature)
            ),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        for (kanji in TextUtil.extractKanji(note.expression + " " + note.sentence)) {
            aggregateFor(context.aggregates(), kanji).addCard(
                intervalDays,
                reps,
                lapses,
                false,
                mature,
                HistoricalKanjiAggregate.FsrsMemoryValues(
                    LocalStoreBase.nullableDouble(cards, LocalStoreBase.COLUMN_FSRS_STABILITY),
                    LocalStoreBase.nullableDouble(cards, LocalStoreBase.COLUMN_FSRS_DIFFICULTY),
                    LocalStoreBase.nullableDouble(cards, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY)
                )
            )
        }
    }

    private fun historicalCardValues(
        cards: Cursor,
        sync: LocalStoreBase.HistoricalSyncRun,
        note: LocalStoreBase.HistoricalNoteSnapshot,
        deck: String,
        metrics: LocalStoreBase.HistoricalCardMetrics,
    ): ContentValues {
        val cardValues = ContentValues()
        cardValues.put(LocalStoreBase.COLUMN_SYNC_ID, sync.id)
        cardValues.put(LocalStoreBase.COLUMN_STARTED_AT, sync.startedAt)
        cardValues.put(LocalStoreBase.COLUMN_FINISHED_AT, sync.finishedAt)
        cardValues.put(LocalStoreBase.COLUMN_CARD_ID, LocalStoreBase.longValue(cards, LocalStoreBase.COLUMN_CARD_ID))
        cardValues.put(LocalStoreBase.COLUMN_NOTE_ID, note.noteId)
        cardValues.put(LocalStoreBase.COLUMN_DECK_ID, deck)
        cardValues.put(LocalStoreBase.COLUMN_DECK_NAME, deck)
        cardValues.put(LocalStoreBase.COLUMN_MODEL_ID, note.modelId)
        cardValues.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName)
        cardValues.put("ord", LocalStoreBase.integer(cards, "ord"))
        cardValues.put(LocalStoreBase.COLUMN_QUEUE, LocalStoreBase.integer(cards, LocalStoreBase.COLUMN_QUEUE))
        cardValues.put("type", LocalStoreBase.integer(cards, "type"))
        cardValues.put("due", LocalStoreBase.integer(cards, "due"))
        cardValues.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, metrics.intervalDays())
        cardValues.put(LocalStoreBase.COLUMN_REPS, metrics.reps())
        cardValues.put(LocalStoreBase.COLUMN_LAPSES, metrics.lapses())
        cardValues.put("suspended", 0)
        LocalStoreBase.putNullableDouble(
            cardValues,
            LocalStoreBase.COLUMN_FSRS_STABILITY,
            LocalStoreBase.nullableDouble(cards, LocalStoreBase.COLUMN_FSRS_STABILITY)
        )
        LocalStoreBase.putNullableDouble(
            cardValues,
            LocalStoreBase.COLUMN_FSRS_DIFFICULTY,
            LocalStoreBase.nullableDouble(cards, LocalStoreBase.COLUMN_FSRS_DIFFICULTY)
        )
        LocalStoreBase.putNullableDouble(
            cardValues,
            LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY,
            LocalStoreBase.nullableDouble(cards, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY)
        )
        cardValues.put(LocalStoreBase.COLUMN_MATURE, if (metrics.mature()) 1 else 0)
        return cardValues
    }

    private fun backfillHistoricalNotes(
        db: SQLiteDatabase,
        sync: LocalStoreBase.HistoricalSyncRun,
        notes: Map<Long, LocalStoreBase.HistoricalNoteSnapshot>,
        deckIdsByNote: Map<Long, LinkedHashSet<String>>,
        deckNamesByNote: Map<Long, LinkedHashSet<String>>,
    ) {
        for (note in notes.values) {
            val deckIds = deckIdsByNote[note.noteId]
            val decks = deckNamesByNote[note.noteId]
            if (decks.isNullOrEmpty()) {
                continue
            }
            val noteValues = ContentValues()
            noteValues.put(LocalStoreBase.COLUMN_SYNC_ID, sync.id)
            noteValues.put(LocalStoreBase.COLUMN_FINISHED_AT, sync.finishedAt)
            noteValues.put(LocalStoreBase.COLUMN_NOTE_ID, note.noteId)
            noteValues.put(LocalStoreBase.COLUMN_MODEL_ID, note.modelId)
            noteValues.put(LocalStoreBase.COLUMN_MODEL_NAME, note.modelName)
            noteValues.put(LocalStoreBase.COLUMN_DECK_IDS, deckIds?.joinToString(" ") ?: "")
            noteValues.put(LocalStoreBase.COLUMN_DECK_NAMES, decks.joinToString(" "))
            noteValues.put(LocalStoreBase.COLUMN_EXPRESSION, note.expression)
            noteValues.put(LocalStoreBase.COLUMN_READING, note.reading)
            noteValues.put(LocalStoreBase.COLUMN_MEANING, note.meaning)
            noteValues.put(LocalStoreBase.COLUMN_SENTENCE, note.sentence)
            noteValues.put(LocalStoreBase.COLUMN_TAGS, note.tags)
            noteValues.put(LocalStoreBase.COLUMN_FIELDS_JSON, note.fieldsJson)
            noteValues.put("extracted_kanji", TextUtil.extractKanji(note.expression + " " + note.sentence).joinToString(""))
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SYNC_NOTE_SNAPSHOTS,
                null,
                noteValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    private fun deckNamesByNote(cards: List<RecordsSyncModels.Card>): Map<Long, LinkedHashSet<String>> {
        val out = LinkedHashMap<Long, LinkedHashSet<String>>()
        for (card in cards) {
            linkedSetFor(out, card.noteId).add(card.deckName)
        }
        return out
    }

    private fun deckIdsByNote(cards: List<RecordsSyncModels.Card>): Map<Long, LinkedHashSet<String>> {
        val out = LinkedHashMap<Long, LinkedHashSet<String>>()
        for (card in cards) {
            linkedSetFor(out, card.noteId).add(card.deckId)
        }
        return out
    }

    private fun linkedSetFor(map: MutableMap<Long, LinkedHashSet<String>>, key: Long): LinkedHashSet<String> {
        var values = map[key]
        if (values == null) {
            values = LinkedHashSet()
            map[key] = values
        }
        return values
    }

    private fun extractedKanji(note: RecordsSyncModels.Note, settings: RecordsSyncModels.Settings): List<String> {
        val expression = TextUtil.normalizeJapanese(note.expression(settings))
        val sentence = TextUtil.normalizeJapanese(note.sentence(settings))
        return TextUtil.extractKanji("$expression $sentence")
    }

    private fun aggregateFor(
        aggregates: MutableMap<String, HistoricalKanjiAggregate>,
        kanji: String,
    ): HistoricalKanjiAggregate {
        var aggregate = aggregates[kanji]
        if (aggregate == null) {
            aggregate = HistoricalKanjiAggregate(kanji)
            aggregates[kanji] = aggregate
        }
        return aggregate
    }

    private fun overlayDashboardRows(
        aggregates: MutableMap<String, HistoricalKanjiAggregate>,
        rows: List<RecordsImportModels.DashboardRow>,
    ) {
        for (row in rows) {
            val aggregate = aggregateFor(aggregates, row.kanji)
            aggregate.mergeDashboardEvidence(
                row.weaknessScore,
                row.reasonCode,
                row.activeExampleCount,
                row.suspendedExampleCount,
                row.matureSupportCount
            )
        }
    }

    fun insertHistoricalKanjiAggregates(
        db: SQLiteDatabase,
        syncId: Long,
        finishedAt: Long,
        aggregates: Map<String, HistoricalKanjiAggregate>,
    ) {
        for (aggregate in aggregates.values) {
            if (aggregate.kanji().isEmpty()) {
                continue
            }
            val values = ContentValues()
            values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
            values.put(LocalStoreBase.COLUMN_FINISHED_AT, finishedAt)
            values.put(LocalStoreBase.COLUMN_KANJI, aggregate.kanji())
            values.put("active_cards", aggregate.activeCards())
            values.put("suspended_cards", aggregate.suspendedCards())
            values.put(LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT, aggregate.matureSupportCount())
            values.put("average_interval_days", aggregate.averageIntervalDays())
            values.put("total_lapses", aggregate.totalLapses())
            values.put("total_reps", aggregate.totalReps())
            LocalStoreBase.putNullableDouble(values, "fsrs_stability_avg", aggregate.averageStability())
            LocalStoreBase.putNullableDouble(values, "fsrs_difficulty_avg", aggregate.averageDifficulty())
            LocalStoreBase.putNullableDouble(values, "fsrs_retrievability_avg", aggregate.averageRetrievability())
            values.put(LocalStoreBase.COLUMN_WEAKNESS_SCORE, aggregate.weaknessScore())
            values.put(LocalStoreBase.COLUMN_REASON_CODE, aggregate.reasonCode())
            values.put(LocalStoreBase.COLUMN_ACTIVE_EXAMPLE_COUNT, aggregate.activeExampleCount())
            values.put(LocalStoreBase.COLUMN_SUSPENDED_EXAMPLE_COUNT, aggregate.suspendedExampleCount())
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    private fun tableHasRows(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery("SELECT 1 FROM $table LIMIT 1", null).use {
            return it.moveToFirst()
        }
    }

    private fun latestSuccessfulSyncRun(db: SQLiteDatabase): LocalStoreBase.HistoricalSyncRun? {
        db.query(
            LocalStoreBase.TABLE_SYNC_RUNS,
            arrayOf("id", LocalStoreBase.COLUMN_STARTED_AT, LocalStoreBase.COLUMN_FINISHED_AT),
            "status=?",
            arrayOf(LocalStoreBase.STATUS_SUCCESS),
            null,
            null,
            LocalStoreBase.ORDER_ID_DESC,
            "1"
        ).use {
            if (!it.moveToFirst()) {
                return null
            }
            return LocalStoreBase.HistoricalSyncRun(
                LocalStoreBase.longValue(it, "id"),
                LocalStoreBase.longValue(it, LocalStoreBase.COLUMN_STARTED_AT),
                LocalStoreBase.longValue(it, LocalStoreBase.COLUMN_FINISHED_AT)
            )
        }
    }

    private fun currentSourceNotes(db: SQLiteDatabase): Map<Long, LocalStoreBase.HistoricalNoteSnapshot> {
        val notes = LinkedHashMap<Long, LocalStoreBase.HistoricalNoteSnapshot>()
        db.query(LocalStoreBase.TABLE_SOURCE_NOTES, null, null, null, null, null, "note_id ASC").use {
            while (it.moveToNext()) {
                val noteId = LocalStoreBase.longValue(it, LocalStoreBase.COLUMN_NOTE_ID)
                notes[noteId] = LocalStoreBase.HistoricalNoteSnapshot(
                    LocalStoreBase.HistoricalNoteFields(
                        noteId,
                        0L,
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_MODEL_NAME),
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_EXPRESSION),
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_READING),
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_MEANING),
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_SENTENCE),
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_TAGS),
                        LocalStoreBase.string(it, LocalStoreBase.COLUMN_FIELDS_JSON)
                    )
                )
            }
        }
        return notes
    }

    private fun currentDashboardRows(db: SQLiteDatabase): List<RecordsImportModels.DashboardRow> {
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        db.query(
            LocalStoreBase.TABLE_DASHBOARD_ROWS,
            arrayOf(LocalStoreBase.COLUMN_KANJI),
            null,
            null,
            null,
            null,
            LocalStoreBase.ORDER_KANJI_ASC
        ).use {
            while (it.moveToNext()) {
                val row = localStore.readDashboardRow(db, LocalStoreBase.string(it, LocalStoreBase.COLUMN_KANJI))
                if (row != null) {
                    rows.add(row)
                }
            }
        }
        return rows
    }
}
