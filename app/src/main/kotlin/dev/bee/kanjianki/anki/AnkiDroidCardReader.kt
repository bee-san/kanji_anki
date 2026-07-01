package dev.bee.kanjianki.anki

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.util.Log
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.sync.SyncProgress
import dev.bee.kanjianki.syncdomain.ProviderCardPolicy
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy

internal class AnkiDroidCardReader(
    private val resolver: ContentResolver?,
) {
    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun queryCardsByNote(
        authority: String,
        settings: RecordsSyncModels.Settings,
        noteIds: Set<Long>,
        progress: SyncProgress.Listener,
    ): List<RecordsSyncModels.Card> {
        val requestedNoteIds = noteIds.toList()
        val total = requestedNoteIds.size
        var scanned = 0
        progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total))
        if (requestedNoteIds.isEmpty()) {
            return emptyList()
        }

        val suspendedNoteIds = querySuspendedNoteIds(authority, settings)
        val cards = ArrayList<RecordsSyncModels.Card>()
        val projections = arrayOf(
            CARD_COLUMNS_WITH_FSRS,
            CARD_COLUMNS_WITH_SCHEDULER,
            CARD_COLUMNS_MINIMAL,
        )
        var projectionIndex = 0
        for (noteBatch in requestedNoteIds.chunked(CARD_NOTE_BATCH_SIZE)) {
            val result = readCardsForNotes(authority, noteBatch, suspendedNoteIds, projections, projectionIndex)
            projectionIndex = result.projectionIndex()
            cards.addAll(result.cards())
            scanned += noteBatch.size
            reportCardProgressIfNeeded(progress, scanned, total)
        }
        return cards
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun readCardsForNote(
        authority: String,
        noteId: Long,
        suspendedNoteIds: Set<Long>,
        projections: Array<Array<String>>,
        startProjectionIndex: Int,
    ): ProjectionReadResult {
        return readCardsForNotes(authority, listOf(noteId), suspendedNoteIds, projections, startProjectionIndex)
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun readCardsForNotes(
        authority: String,
        noteIds: List<Long>,
        suspendedNoteIds: Set<Long>,
        projections: Array<Array<String>>,
        startProjectionIndex: Int,
    ): ProjectionReadResult {
        var projectionIndex = startProjectionIndex
        while (projectionIndex < projections.size) {
            try {
                return ProjectionReadResult(
                    queryCardsForNotes(authority, noteIds, suspendedNoteIds, projections[projectionIndex]),
                    projectionIndex,
                )
            } catch (unsupportedColumns: Exception) {
                projectionIndex++
                if (projectionIndex >= projections.size) {
                    if (unsupportedColumns is AnkiDroidGateway.SyncFailure) {
                        throw unsupportedColumns
                    }
                    throw AnkiDroidGateway.SyncFailure.retryable(
                        "AnkiDroid card projection failed: ${unsupportedColumns.message}",
                        unsupportedColumns,
                    )
                }
            }
        }
        return ProjectionReadResult(emptyList(), projectionIndex)
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    private fun queryCardsForNotes(
        authority: String,
        noteIds: List<Long>,
        suspendedNoteIds: Set<Long>,
        columns: Array<String>,
    ): List<RecordsSyncModels.Card> {
        val requestedNoteIds = LinkedHashSet(noteIds)
        if (requestedNoteIds.isEmpty()) {
            return emptyList()
        }

        val cursor = resolver!!.query(
            uriFor(authority, URI_SEGMENT_CARDS),
            columns,
            noteIdSelection(requestedNoteIds),
            null,
            null,
        ) ?: throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no bulk card cursor.")

        val cardsByNote = LinkedHashMap<Long, MutableList<RecordsSyncModels.Card>>(requestedNoteIds.size)
        for (noteId in requestedNoteIds) {
            cardsByNote[noteId] = ArrayList()
        }

        cursor.use { cardCursor ->
            while (cardCursor.moveToNext()) {
                val noteId = longValue(cardCursor, COLUMN_NOTE_ID, Long.MIN_VALUE)
                val noteCards = cardsByNote[noteId] ?: continue
                val ord = intValue(cardCursor, COLUMN_ORD, 0)
                val suspendedFromSearch = suspendedNoteIds.contains(noteId)
                val queue = intValue(cardCursor, COLUMN_QUEUE, if (suspendedFromSearch) -1 else 0)
                val suspended = suspendedFromSearch || queue < 0
                val fsrs = fsrsMemoryState(cardCursor)
                val deckId = value(cardCursor, COLUMN_DECK_ID)
                noteCards.add(
                    RecordsSyncModels.Card(
                        longValue(cardCursor, COLUMN_ID, noteId * 1000L + ord),
                        longValue(cardCursor, COLUMN_NOTE_ID, noteId),
                        ord,
                        deckId,
                        deckId,
                        queue,
                        intValue(cardCursor, COLUMN_TYPE, if (suspended) 3 else 0),
                        intValue(cardCursor, COLUMN_DUE, 0),
                        intValue(cardCursor, COLUMN_INTERVAL, 0),
                        intValue(cardCursor, COLUMN_REPS, 0),
                        intValue(cardCursor, COLUMN_LAPSES, 0),
                        suspended,
                        fsrs.stability,
                        fsrs.difficulty,
                        fsrs.retrievability,
                    ),
                )
            }
        }

        val cards = ArrayList<RecordsSyncModels.Card>()
        for (noteId in requestedNoteIds) {
            cards.addAll(cardsByNote[noteId].orEmpty())
        }
        return cards
    }

    private fun querySuspendedNoteIds(authority: String, settings: RecordsSyncModels.Settings): Set<Long> {
        val ids = LinkedHashSet<Long>()
        val cursor = try {
            resolver!!.query(
                uriFor(authority, URI_SEGMENT_NOTES),
                null,
                ProviderNotePolicy.modelSearch(settings.modelName) + " is:suspended",
                null,
                null,
            )
        } catch (error: Exception) {
            Log.d(TAG, "AnkiDroid suspended-note search unavailable.", error)
            return ids
        } ?: return ids

        cursor.use { suspendedCursor ->
            while (suspendedCursor.moveToNext()) {
                ids.add(longValue(suspendedCursor, COLUMN_ID, 0))
            }
        }
        return ids
    }

    class ProjectionReadResult(
        private val cards: List<RecordsSyncModels.Card>,
        private val projectionIndex: Int,
    ) {
        fun cards(): List<RecordsSyncModels.Card> = cards

        fun projectionIndex(): Int = projectionIndex
    }

    companion object {
        private const val TAG = "AnkiDroidGateway"
        private const val CONTENT_SCHEME = "content"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_NOTE_ID = "note_id"
        private const val COLUMN_ORD = "ord"
        private const val COLUMN_DECK_ID = "deck_id"
        private const val COLUMN_QUEUE = "queue"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_DUE = "due"
        private const val COLUMN_INTERVAL = "interval"
        private const val COLUMN_REPS = "reps"
        private const val COLUMN_LAPSES = "lapses"
        private const val COLUMN_FSRS_STABILITY = "fsrs_stability"
        private const val COLUMN_FSRS_DIFFICULTY = "fsrs_difficulty"
        private const val COLUMN_FSRS_RETRIEVABILITY = "fsrs_retrievability"
        private const val COLUMN_STABILITY = "stability"
        private const val COLUMN_DIFFICULTY = "difficulty"
        private const val COLUMN_RETRIEVABILITY = "retrievability"
        private const val COLUMN_DATA = "data"
        private const val URI_SEGMENT_NOTES = "notes"
        private const val URI_SEGMENT_CARDS = "cards"
        private const val CARD_NOTE_BATCH_SIZE = 512

        private val CARD_COLUMNS_WITH_FSRS = arrayOf(
            COLUMN_NOTE_ID,
            COLUMN_ORD,
            COLUMN_DECK_ID,
            COLUMN_QUEUE,
            COLUMN_TYPE,
            COLUMN_DUE,
            COLUMN_INTERVAL,
            COLUMN_REPS,
            COLUMN_LAPSES,
            COLUMN_FSRS_STABILITY,
            COLUMN_FSRS_DIFFICULTY,
            COLUMN_FSRS_RETRIEVABILITY,
            COLUMN_STABILITY,
            COLUMN_DIFFICULTY,
            COLUMN_RETRIEVABILITY,
            COLUMN_DATA,
        )
        private val CARD_COLUMNS_WITH_SCHEDULER = arrayOf(
            COLUMN_NOTE_ID,
            COLUMN_ORD,
            COLUMN_DECK_ID,
            COLUMN_QUEUE,
            COLUMN_TYPE,
            COLUMN_DUE,
            COLUMN_INTERVAL,
            COLUMN_REPS,
            COLUMN_LAPSES,
        )
        private val CARD_COLUMNS_MINIMAL = arrayOf(COLUMN_NOTE_ID, COLUMN_ORD, COLUMN_DECK_ID)

        @JvmStatic
        fun reportCardProgressIfNeeded(progress: SyncProgress.Listener, scanned: Int, total: Int) {
            if (shouldReportCardProgress(scanned, total)) {
                progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total))
            }
        }

        @JvmStatic
        fun shouldReportCardProgress(scanned: Int, total: Int): Boolean {
            return ProviderCardPolicy.shouldReportCardProgress(scanned, total)
        }

        @JvmStatic
        private fun uriFor(authority: String, vararg segments: String): Uri {
            val builder = Uri.Builder().scheme(CONTENT_SCHEME).authority(authority)
            for (segment in segments) {
                builder.appendPath(segment)
            }
            return builder.build()
        }

        @JvmStatic
        private fun noteIdSelection(noteIds: Collection<Long>): String {
            return buildString {
                append("nid:")
                append(noteIds.joinToString(","))
            }
        }

        @JvmStatic
        private fun value(cursor: Cursor, column: String): String {
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) {
                return ""
            }
            return cursor.getString(index)
        }

        @JvmStatic
        private fun nullableValue(cursor: Cursor, column: String): String? {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
        }

        @JvmStatic
        private fun longValue(cursor: Cursor, column: String, fallback: Long): Long {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) fallback else cursor.getLong(index)
        }

        @JvmStatic
        private fun intValue(cursor: Cursor, column: String, fallback: Int): Int {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) fallback else cursor.getInt(index)
        }

        @JvmStatic
        private fun fsrsMemoryState(cursor: Cursor): ProviderCardPolicy.FsrsMemoryState {
            return ProviderCardPolicy.fsrsMemoryState(
                nullableValue(cursor, COLUMN_FSRS_STABILITY),
                nullableValue(cursor, COLUMN_STABILITY),
                nullableValue(cursor, COLUMN_FSRS_DIFFICULTY),
                nullableValue(cursor, COLUMN_DIFFICULTY),
                nullableValue(cursor, COLUMN_FSRS_RETRIEVABILITY),
                nullableValue(cursor, COLUMN_RETRIEVABILITY),
                nullableValue(cursor, COLUMN_DATA),
            )
        }
    }
}
