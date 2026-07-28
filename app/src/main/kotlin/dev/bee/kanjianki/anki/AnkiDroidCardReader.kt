package dev.bee.kanjianki.anki

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.util.Log
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.sync.SyncCancellation
import dev.bee.kanjianki.sync.SyncProgress
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncdomain.ProviderCardPolicy
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy

internal class AnkiDroidCardReader(
    private val resolver: ContentResolver?,
    private val lifecycleCancellation: CollectionCancellation = SyncCancellation.NONE,
) {
    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun queryCardsByNote(
        authority: String,
        settings: RecordsSyncModels.Settings,
        noteIds: Set<Long>,
        progress: SyncProgress.Listener,
        operationCancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): List<RecordsSyncModels.Card> {
        val requestedNoteIds = noteIds.toList()
        val total = requestedNoteIds.size
        var scanned = 0
        progress.onSyncProgress(SyncProgress.cardsScanned(scanned, total))
        if (requestedNoteIds.isEmpty()) {
            return emptyList()
        }
        // Abort early if the job was already stopped before any provider work started.
        if (isCancelled(operationCancellation)) {
            throw AnkiDroidGateway.SyncFailure.cancelled("Sync cancelled before reading cards.")
        }

        val suspendedNoteIds = querySuspendedNoteIds(authority, settings)
        val cards = ArrayList<RecordsSyncModels.Card>()
        val projections = cardProjections()
        var projectionIndex = 0
        var batchCardsUriUnsupported = false
        for (noteBatch in requestedNoteIds.chunked(CARD_NOTE_BATCH_SIZE)) {
            // Cooperative cancellation: the provider/SQLite calls below ignore thread
            // interruption, so abort between batches with a retryable failure when the
            // job has been stopped.
            if (isCancelled(operationCancellation)) {
                throw AnkiDroidGateway.SyncFailure.cancelled("Sync cancelled before all cards were read.")
            }
            val result = if (batchCardsUriUnsupported) {
                readCardsPerNote(authority, noteBatch, suspendedNoteIds, projections, projectionIndex)
            } else {
                try {
                    readCardsForNotes(authority, noteBatch, suspendedNoteIds, projections, projectionIndex)
                } catch (unsupportedUri: BatchCardsUriUnsupportedException) {
                    // Older AnkiDroid releases (< 2.24.0) do not expose the
                    // top-level cards URI. Fall back to the per-note cards
                    // path, which every supported AnkiDroid release provides.
                    Log.d(TAG, "AnkiDroid bulk cards URI unsupported; falling back to per-note card reads.", unsupportedUri)
                    batchCardsUriUnsupported = true
                    readCardsPerNote(authority, noteBatch, suspendedNoteIds, projections, projectionIndex)
                }
            }
            projectionIndex = result.projectionIndex()
            cards.addAll(result.cards())
            scanned += noteBatch.size
            reportCardProgressIfNeeded(progress, scanned, total)
        }
        return cards
    }

    private fun isCancelled(operationCancellation: CollectionCancellation): Boolean =
        lifecycleCancellation.isCancelled() || operationCancellation.isCancelled()

    @Throws(AnkiDroidGateway.SyncFailure::class)
    private fun readCardsPerNote(
        authority: String,
        noteIds: List<Long>,
        suspendedNoteIds: Set<Long>,
        projections: Array<Array<String>>,
        startProjectionIndex: Int,
    ): ProjectionReadResult {
        var projectionIndex = startProjectionIndex
        val cards = ArrayList<RecordsSyncModels.Card>()
        for (noteId in noteIds) {
            val result = readCardsForNote(authority, noteId, suspendedNoteIds, projections, projectionIndex)
            projectionIndex = result.projectionIndex()
            cards.addAll(result.cards())
        }
        return ProjectionReadResult(cards, projectionIndex)
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun readCardsForNote(
        authority: String,
        noteId: Long,
        suspendedNoteIds: Set<Long>,
        projections: Array<Array<String>>,
        startProjectionIndex: Int,
    ): ProjectionReadResult {
        return readCardsWithProjectionFallback(projections, startProjectionIndex, false) { columns ->
            queryCardsForNote(authority, noteId, suspendedNoteIds, columns)
        }
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun readCardsForNotes(
        authority: String,
        noteIds: List<Long>,
        suspendedNoteIds: Set<Long>,
        projections: Array<Array<String>>,
        startProjectionIndex: Int,
    ): ProjectionReadResult {
        return readCardsWithProjectionFallback(projections, startProjectionIndex, true) { columns ->
            queryCardsForNotes(authority, noteIds, suspendedNoteIds, columns)
        }
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    private fun readCardsWithProjectionFallback(
        projections: Array<Array<String>>,
        startProjectionIndex: Int,
        detectUnsupportedUri: Boolean,
        query: (Array<String>) -> List<RecordsSyncModels.Card>,
    ): ProjectionReadResult {
        var projectionIndex = startProjectionIndex
        while (projectionIndex < projections.size) {
            try {
                return ProjectionReadResult(query(projections[projectionIndex]), projectionIndex)
            } catch (denied: SecurityException) {
                // Permission was revoked mid-sync. Degrading the projection
                // cannot help; surface the real permission failure instead.
                throw denied
            } catch (classified: AnkiDroidGateway.SyncFailure) {
                throw classified
            } catch (unsupportedColumns: Exception) {
                if (detectUnsupportedUri && isUnsupportedUriError(unsupportedColumns)) {
                    throw BatchCardsUriUnsupportedException(unsupportedColumns)
                }
                if (!isUnsupportedProjectionError(unsupportedColumns)) {
                    // Transient failures (locked database, remote process
                    // death) are not projection problems. Do not silently
                    // degrade the imported data quality; fail as retryable.
                    throw AnkiDroidGateway.SyncFailure.retryable(
                        "AnkiDroid card read failed: ${unsupportedColumns.message}",
                        unsupportedColumns,
                    )
                }
                projectionIndex++
                if (projectionIndex >= projections.size) {
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
        // Build the per-note map inside use{} so any failure here still closes the
        // cursor rather than leaking it.
        cursor.use { cardCursor ->
            for (noteId in requestedNoteIds) {
                cardsByNote[noteId] = ArrayList()
            }
            while (cardCursor.moveToNext()) {
                val noteId = longValue(cardCursor, COLUMN_NOTE_ID, Long.MIN_VALUE)
                val noteCards = cardsByNote[noteId] ?: continue
                noteCards.add(cardFromCursor(cardCursor, noteId, suspendedNoteIds))
            }
        }

        val cards = ArrayList<RecordsSyncModels.Card>()
        for (noteId in requestedNoteIds) {
            cards.addAll(cardsByNote[noteId].orEmpty())
        }
        return cards
    }

    @Throws(AnkiDroidGateway.SyncFailure::class)
    private fun queryCardsForNote(
        authority: String,
        noteId: Long,
        suspendedNoteIds: Set<Long>,
        columns: Array<String>,
    ): List<RecordsSyncModels.Card> {
        val cursor = resolver!!.query(
            uriFor(authority, URI_SEGMENT_NOTES, noteId.toString(), URI_SEGMENT_CARDS),
            columns,
            null,
            null,
            null,
        ) ?: throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no per-note card cursor.")
        val cards = ArrayList<RecordsSyncModels.Card>()
        cursor.use { cardCursor ->
            while (cardCursor.moveToNext()) {
                cards.add(cardFromCursor(cardCursor, noteId, suspendedNoteIds))
            }
        }
        return cards
    }

    private fun cardFromCursor(
        cardCursor: Cursor,
        noteId: Long,
        suspendedNoteIds: Set<Long>,
    ): RecordsSyncModels.Card {
        val ord = intValue(cardCursor, COLUMN_ORD, 0)
        val suspendedFromSearch = suspendedNoteIds.contains(noteId)
        val queue = intValue(cardCursor, COLUMN_QUEUE, if (suspendedFromSearch) -1 else 0)
        val suspended = suspendedFromSearch || queue < 0
        val fsrs = fsrsMemoryState(cardCursor)
        val deckId = value(cardCursor, COLUMN_DECK_ID)
        return RecordsSyncModels.Card(
            // Synthetic fallback card id when the provider omits a real _id: derived
            // from (noteId, ord) so it stays stable and unique per card without
            // querying the AnkiDroid `_id` column, which some provider projections
            // reject as unknown (see AGENTS.md "_id is unknown" fix).
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
        )
    }

    private fun querySuspendedNoteIds(authority: String, settings: RecordsSyncModels.Settings): Set<Long> {
        val ids = LinkedHashSet<Long>()
        try {
            val cursor = resolver!!.query(
                uriFor(authority, URI_SEGMENT_NOTES),
                null,
                ProviderNotePolicy.modelSearch(settings.modelName) + " is:suspended",
                null,
                null,
            ) ?: return ids
            // Keep the iteration inside this guard: AnkiDroid's search cursor
            // can defer SQLite failures to window-fill time during moveToNext,
            // and suspended-search unavailability must stay tolerated.
            cursor.use { suspendedCursor ->
                while (suspendedCursor.moveToNext()) {
                    ids.add(longValue(suspendedCursor, COLUMN_ID, 0))
                }
            }
        } catch (error: Exception) {
            Log.d(TAG, "AnkiDroid suspended-note search unavailable.", error)
            ids.clear()
        }
        return ids
    }

    private class BatchCardsUriUnsupportedException(cause: Throwable) : RuntimeException(cause)

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

        // Real AnkiDroid 2.24.0 supports fsrs_stability and fsrs_difficulty but
        // rejects the wider FSRS wishlist above, so a provider-supported FSRS
        // projection must sit between the wishlist and the scheduler fallback
        // or FSRS memory state is never imported from a real install.
        private val CARD_COLUMNS_WITH_PROVIDER_FSRS = arrayOf(
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
        internal fun cardProjections(): Array<Array<String>> {
            return arrayOf(
                CARD_COLUMNS_WITH_FSRS,
                CARD_COLUMNS_WITH_PROVIDER_FSRS,
                CARD_COLUMNS_WITH_SCHEDULER,
                CARD_COLUMNS_MINIMAL,
            )
        }

        @JvmStatic
        internal fun isUnsupportedUriError(error: Throwable): Boolean {
            if (error !is IllegalArgumentException) {
                return false
            }
            val message = error.message ?: return false
            return message.contains("is not supported") || message.contains("Unknown URI")
        }

        @JvmStatic
        internal fun isUnsupportedProjectionError(error: Throwable): Boolean {
            return error is IllegalArgumentException || error is UnsupportedOperationException
        }

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
