package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RwkvFedAnswer
import dev.bee.kanjianki.core.RwkvHistoryBackfill
import dev.bee.rwkv.RwkvAlgorithmInfo
import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvArchitecture
import dev.bee.rwkv.RwkvCardFeatures
import dev.bee.rwkv.RwkvCardHistory
import dev.bee.rwkv.RwkvCollectionState
import dev.bee.rwkv.RwkvEncodingCodec
import dev.bee.rwkv.RwkvGlobalFeatures
import dev.bee.rwkv.RwkvIdKey
import dev.bee.rwkv.RwkvIdKind
import dev.bee.rwkv.RwkvModuleState
import dev.bee.rwkv.RwkvSnapshotView
import dev.bee.rwkv.RwkvStateCodec
import dev.bee.rwkv.RwkvStateDelta
import dev.bee.rwkv.RwkvStateKey
import dev.bee.rwkv.RwkvStatePrecision
import dev.bee.rwkv.RwkvStateUndo
import dev.bee.rwkv.RwkvStreamIds
import dev.bee.rwkv.RwkvUndoCodec
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Persistence of the RWKV memory model's state (database v34).
 *
 * The state is a derived cache of the ordered history: every `review_log`
 * row RWKV was fed (`mm_seq > 0`, in `mm_seq` order) merged with the
 * synthetic seed bursts in `rwkv_burst` (by `seq`). [RwkvStateStore] keeps the
 * cache consistent with that history inside the review transaction, and
 * `rwkv_meta` records whether it is ([META_NEEDS_REBUILD]) and which model and
 * layout produced it ([META_IDENTITY]). A `state_version` compare-and-swap
 * orders every writer: reviews, undo and rebuilds.
 *
 * `review_log.mm_seq` is NULL for rows written before v34 that the rebuild
 * has not classified yet, 0 for rows RWKV was not fed (adaptive repairs,
 * tests' direct inserts) and the replay position otherwise.
 */
internal class RwkvStateStore(private val precisionFor: (Int) -> RwkvStatePrecision = ::defaultPrecision) {
    class Meta(
        val identity: String,
        val stateVersion: Long,
        val nextSeq: Long,
        val needsRebuild: Boolean,
    ) {
        fun matchesCurrentModel(): Boolean = identity == RwkvAlgorithmInfo.stateIdentity()
    }

    /** A loaded slice of state plus the version and sequence it was loaded at. */
    class Snapshot(val view: RwkvSnapshotView, val stateVersion: Long, val nextSeq: Long)

    /** One event of the ordered history: a fed review row or a seed burst. */
    sealed class HistoryEvent(val seq: Long) {
        class Review(seq: Long, val ids: RwkvStreamIds, val answer: RwkvAnswer) : HistoryEvent(seq)

        class Warm(seq: Long, val burst: RwkvSeedBurst) : HistoryEvent(seq)
    }

    fun meta(db: SQLiteDatabase): Meta {
        val values = HashMap<String, String>()
        db.rawQuery("SELECT key, value FROM $TABLE_META", null).use { cursor ->
            while (cursor.moveToNext()) {
                values[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return Meta(
            identity = values[META_IDENTITY].orEmpty(),
            stateVersion = values[META_STATE_VERSION]?.toLongOrNull() ?: 0L,
            nextSeq = values[META_NEXT_SEQ]?.toLongOrNull() ?: 1L,
            needsRebuild = values[META_NEEDS_REBUILD] != "0",
        )
    }

    /** True when the persisted state is the current model's and covers the whole history. */
    fun isReady(db: SQLiteDatabase): Boolean {
        val meta = meta(db)
        return meta.matchesCurrentModel() && !meta.needsRebuild && unclassifiedRowCount(db) == 0
    }

    fun markNeedsRebuild(db: SQLiteDatabase) {
        val meta = meta(db)
        putMeta(db, META_NEEDS_REBUILD, "1")
        putMeta(db, META_STATE_VERSION, (meta.stateVersion + 1).toString())
    }

    /** Loads everything answering [ids] (and previewing [extraCards]) reads. */
    fun loadSnapshot(db: SQLiteDatabase, ids: List<RwkvStreamIds>): Snapshot {
        val meta = meta(db)
        val stateKeys = ids.flatMap(RwkvSnapshotView::footprint).distinct()
        val encodingKeys = ids.flatMap(RwkvSnapshotView::encodingFootprint).distinct()
        val cardIds = ids.map { it.card }.distinct()
        val states = LinkedHashMap<RwkvStateKey, RwkvModuleState?>()
        for (key in stateKeys) {
            states[key] = blob(db, "SELECT state FROM $TABLE_STATE WHERE stream=? AND id=?", key.stream.toString(), key.id.toString())
                ?.let(RwkvStateCodec::decode)
        }
        val encodings = LinkedHashMap<RwkvIdKey, FloatArray?>()
        for (key in encodingKeys) {
            encodings[key] = blob(db, "SELECT encoding FROM $TABLE_ENCODING WHERE kind=? AND id=?", key.kind.code.toString(), key.id.toString())
                ?.let { RwkvEncodingCodec.decode(key.kind, it) }
        }
        val cards = cardIds.associateWith { id ->
            blob(db, "SELECT features FROM $TABLE_CARD_FEATURES WHERE card_id=?", id.toString())?.let(RwkvCardFeatures::decode)
        }
        val histories = cardIds.associateWith { id ->
            blob(db, "SELECT history FROM $TABLE_CARD_HISTORY WHERE card_id=?", id.toString())?.let(RwkvCardHistory::decode)
        }
        val view = RwkvSnapshotView(globals(db), states, cards, encodings, histories)
        return Snapshot(view, meta.stateVersion, meta.nextSeq)
    }

    /**
     * Persists a review's model writes. Must run inside the review
     * transaction, after the review row. A concurrent state change since the
     * snapshot was loaded throws [StaleReviewCommitException], rolling the
     * whole review back.
     */
    fun applyReviewCommit(db: SQLiteDatabase, commit: RwkvReviewCommit, token: String, nowMillis: Long) {
        val meta = meta(db)
        if (meta.stateVersion != commit.expectedStateVersion || !meta.matchesCurrentModel() || meta.needsRebuild) {
            throw StaleReviewCommitException()
        }
        if (!commit.writesState()) {
            return
        }
        commit.burst?.let { insertBurst(db, commit.burstSeq!!, it) }
        applyDelta(db, commit.delta)
        val nextVersion = meta.stateVersion + 1
        val lastSeq = listOfNotNull(commit.burstSeq, commit.fedSeq).maxOrNull() ?: (meta.nextSeq - 1)
        putMeta(db, META_STATE_VERSION, nextVersion.toString())
        putMeta(db, META_NEXT_SEQ, maxOf(meta.nextSeq, lastSeq + 1).toString())
        db.insertWithOnConflict(
            TABLE_JOURNAL,
            null,
            ContentValues().apply {
                put("token", token)
                put("state_version", nextVersion)
                if (commit.burstSeq != null) put("burst_seq", commit.burstSeq) else putNull("burst_seq")
                put("undo", RwkvUndoCodec.encode(commit.undo, precisionFor))
                put("created_at", nowMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        db.execSQL(
            "DELETE FROM $TABLE_JOURNAL WHERE rowid NOT IN " +
                "(SELECT rowid FROM $TABLE_JOURNAL ORDER BY created_at DESC, rowid DESC LIMIT $JOURNAL_LIMIT)",
        )
    }

    /**
     * Rolls the model state back for an undone review whose log row (with
     * `mm_seq` [deletedSeq]) was just deleted in the same transaction. The
     * journal restores exactly when that review was the last state change;
     * otherwise a fed review forces a rebuild from history.
     */
    fun undoReview(db: SQLiteDatabase, token: String, deletedSeq: Long) {
        val meta = meta(db)
        var journalVersion: Long? = null
        var burstSeq: Long? = null
        var undo: ByteArray? = null
        db.rawQuery("SELECT state_version, burst_seq, undo FROM $TABLE_JOURNAL WHERE token=?", arrayOf(token)).use { cursor ->
            if (cursor.moveToFirst()) {
                journalVersion = cursor.getLong(0)
                burstSeq = if (cursor.isNull(1)) null else cursor.getLong(1)
                undo = cursor.getBlob(2)
            }
        }
        db.delete(TABLE_JOURNAL, "token=?", arrayOf(token))
        val restorable = journalVersion != null && journalVersion == meta.stateVersion && !meta.needsRebuild
        if (restorable) {
            val decoded = try {
                RwkvUndoCodec.decode(undo!!)
            } catch (_: IOException) {
                null
            }
            if (decoded != null) {
                applyUndo(db, decoded)
                burstSeq?.let { db.delete(TABLE_BURST, "seq=?", arrayOf(it.toString())) }
                putMeta(db, META_STATE_VERSION, (meta.stateVersion + 1).toString())
                return
            }
        }
        if (deletedSeq > 0L || burstSeq != null) {
            markNeedsRebuild(db)
        }
    }

    // --- full state (rebuilds and simulations) ---------------------------------

    fun loadCollection(db: SQLiteDatabase): RwkvCollectionState {
        val state = RwkvCollectionState(globals(db))
        db.rawQuery("SELECT stream, id, state FROM $TABLE_STATE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val stream = cursor.getInt(0)
                if (stream in 0 until RwkvArchitecture.STREAMS) {
                    state.streams[stream][cursor.getLong(1)] = RwkvStateCodec.decode(cursor.getBlob(2))
                }
            }
        }
        db.rawQuery("SELECT card_id, features FROM $TABLE_CARD_FEATURES", null).use { cursor ->
            while (cursor.moveToNext()) {
                state.cards[cursor.getLong(0)] = RwkvCardFeatures.decode(cursor.getBlob(1))
            }
        }
        db.rawQuery("SELECT card_id, history FROM $TABLE_CARD_HISTORY", null).use { cursor ->
            while (cursor.moveToNext()) {
                state.histories[cursor.getLong(0)] = RwkvCardHistory.decode(cursor.getBlob(1))
            }
        }
        db.rawQuery("SELECT kind, id, encoding FROM $TABLE_ENCODING", null).use { cursor ->
            while (cursor.moveToNext()) {
                val kind = RwkvIdKind.fromCode(cursor.getInt(0))
                state.encodings[RwkvIdKey(kind, cursor.getLong(1))] = RwkvEncodingCodec.decode(kind, cursor.getBlob(2))
            }
        }
        return state
    }

    /**
     * Replaces the persisted state with a rebuilt [state] if nothing changed
     * since [expectedStateVersion]. Must run in a transaction. Returns false
     * when another writer won (the caller rebuilds again).
     */
    fun replaceCollection(db: SQLiteDatabase, state: RwkvCollectionState, expectedStateVersion: Long): Boolean {
        val meta = meta(db)
        if (meta.stateVersion != expectedStateVersion || unclassifiedRowCount(db) != 0) {
            return false
        }
        for (table in listOf(TABLE_STATE, TABLE_CARD_FEATURES, TABLE_CARD_HISTORY, TABLE_ENCODING, TABLE_GLOBALS, TABLE_JOURNAL)) {
            db.delete(table, null, null)
        }
        putGlobals(db, state.globals)
        state.streams.forEachIndexed { stream, map ->
            map.forEach { (id, value) -> putState(db, RwkvStateKey(stream, id), value) }
        }
        state.cards.forEach { (id, value) -> putBlob(db, TABLE_CARD_FEATURES, "card_id", id, "features", value.encode()) }
        state.histories.forEach { (id, value) -> putBlob(db, TABLE_CARD_HISTORY, "card_id", id, "history", value.encode()) }
        state.encodings.forEach { (key, value) -> putEncoding(db, key, value) }
        putMeta(db, META_IDENTITY, RwkvAlgorithmInfo.stateIdentity())
        putMeta(db, META_NEEDS_REBUILD, "0")
        putMeta(db, META_STATE_VERSION, (meta.stateVersion + 1).toString())
        return true
    }

    // --- history ---------------------------------------------------------------

    fun unclassifiedRowCount(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT COUNT(*) FROM ${LocalStoreBase.TABLE_REVIEW_LOG} WHERE $COLUMN_MM_SEQ IS NULL", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    /** Card ids that already have fed history (so a resumed backfill does not warm them twice). */
    fun fedCardIds(db: SQLiteDatabase): Set<Long> {
        val out = HashSet<Long>()
        db.rawQuery(
            "SELECT DISTINCT $COLUMN_MM_CARD FROM ${LocalStoreBase.TABLE_REVIEW_LOG} WHERE $COLUMN_MM_SEQ > 0",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out += cursor.getLong(0)
            }
        }
        db.rawQuery("SELECT card_id FROM $TABLE_BURST", null).use { cursor ->
            while (cursor.moveToNext()) {
                out += cursor.getLong(0)
            }
        }
        return out
    }

    /**
     * Classifies up to [limit] pre-v34 rows in commit order. Historical rows get
     * `mm_seq = 2 * id` (bursts `2 * id - 1`), which sorts before every live
     * sequence number (`next_seq` starts above them at migration). Returns the
     * number of rows classified.
     */
    fun classifyBatch(db: SQLiteDatabase, backfill: RwkvHistoryBackfill, limit: Int): Int {
        val rows = ArrayList<Pair<Long, RwkvHistoryBackfill.Row>>()
        db.rawQuery(
            "SELECT r.id, r.kanji, r.answer_signature, r.task_type, r.rating, r.reviewed_at, r.review_day_start, " +
                "r.core_skill, r.scheduler_state_before_json, r.memory_before, t.active_elapsed_ms " +
                "FROM ${LocalStoreBase.TABLE_REVIEW_LOG} r LEFT JOIN ${LocalStoreBase.TABLE_STUDY_TASK_LOG} t " +
                "ON t.task_key = 'session:' || r.task_type || ':' || r.kanji || ':' || r.token " +
                "WHERE r.$COLUMN_MM_SEQ IS NULL ORDER BY r.id LIMIT $limit",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += cursor.getLong(0) to RwkvHistoryBackfill.Row(
                    kanji = cursor.getString(1).orEmpty(),
                    answerSignature = cursor.getString(2).orEmpty(),
                    taskType = cursor.getString(3).orEmpty(),
                    rating = cursor.getString(4).orEmpty(),
                    reviewedAtMillis = cursor.getLong(5),
                    reviewDayStartMillis = cursor.getLong(6),
                    coreSkill = cursor.getString(7).orEmpty(),
                    schedulerStateBeforeJson = cursor.getString(8).orEmpty(),
                    memoryBefore = cursor.getString(9).orEmpty(),
                    activeElapsedMillis = if (cursor.isNull(10)) null else cursor.getLong(10),
                )
            }
        }
        for ((id, row) in rows) {
            val fed = backfill.classify(row)
            if (fed == null) {
                db.execSQL("UPDATE ${LocalStoreBase.TABLE_REVIEW_LOG} SET $COLUMN_MM_SEQ=0 WHERE id=?", arrayOf<Any>(id))
                continue
            }
            if (fed.warmFirst.isNotEmpty()) {
                insertBurst(db, 2 * id - 1, RwkvSeedBurst(fed.ids, fed.warmFirst))
            }
            db.update(
                LocalStoreBase.TABLE_REVIEW_LOG,
                fedValues(RwkvFedAnswer(fed.ids, fed.answer), 2 * id),
                "id=?",
                arrayOf(id.toString()),
            )
        }
        return rows.size
    }

    fun historyEventCount(db: SQLiteDatabase): Int {
        val reviews = db.rawQuery("SELECT COUNT(*) FROM ${LocalStoreBase.TABLE_REVIEW_LOG} WHERE $COLUMN_MM_SEQ > 0", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val bursts = db.rawQuery("SELECT COUNT(*) FROM $TABLE_BURST", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return reviews + bursts
    }

    /** Streams the ordered history (fed rows merged with bursts by sequence; a burst sorts first on a tie). */
    fun forEachHistoryEvent(db: SQLiteDatabase, consumer: (HistoryEvent) -> Unit) {
        db.rawQuery(
            "SELECT $COLUMN_MM_SEQ, $COLUMN_MM_CARD, $COLUMN_MM_NOTE, $COLUMN_MM_DECK, $COLUMN_MM_PRESET, " +
                "$COLUMN_MM_RATING, $COLUMN_MM_DURATION, reviewed_at, $COLUMN_MM_DAY, $COLUMN_MM_KIND " +
                "FROM ${LocalStoreBase.TABLE_REVIEW_LOG} WHERE $COLUMN_MM_SEQ > 0 ORDER BY $COLUMN_MM_SEQ",
            null,
        ).use { reviews ->
            db.rawQuery(
                "SELECT seq, card_id, note_id, deck_id, preset_id, answers FROM $TABLE_BURST ORDER BY seq",
                null,
            ).use { bursts ->
                var review = nextReview(reviews)
                var burst = nextBurst(bursts)
                while (review != null || burst != null) {
                    if (burst != null && (review == null || burst.seq <= review.seq)) {
                        consumer(burst)
                        burst = nextBurst(bursts)
                    } else {
                        consumer(review!!)
                        review = nextReview(reviews)
                    }
                }
            }
        }
    }

    /** `review_log` values for a fed answer at sequence [seq]. */
    fun fedValues(fed: RwkvFedAnswer, seq: Long): ContentValues =
        ContentValues().apply {
            put(COLUMN_MM_SEQ, seq)
            put(COLUMN_MM_CARD, fed.ids.card)
            put(COLUMN_MM_NOTE, fed.ids.note)
            put(COLUMN_MM_DECK, fed.ids.deck)
            put(COLUMN_MM_PRESET, fed.ids.preset)
            put(COLUMN_MM_RATING, fed.answer.rating)
            if (fed.answer.durationMillis != null) put(COLUMN_MM_DURATION, fed.answer.durationMillis) else putNull(COLUMN_MM_DURATION)
            put(COLUMN_MM_DAY, fed.answer.dayOffset)
            put(COLUMN_MM_KIND, fed.answer.reviewKind)
        }

    // --- internals ---------------------------------------------------------------

    private fun nextReview(cursor: Cursor): HistoryEvent.Review? {
        if (!cursor.moveToNext()) {
            return null
        }
        val ids = RwkvStreamIds(card = cursor.getLong(1), deck = cursor.getLong(3), note = cursor.getLong(2), preset = cursor.getLong(4))
        val answer = RwkvAnswer(
            rating = cursor.getInt(5).coerceIn(1, 4),
            durationMillis = if (cursor.isNull(6)) null else cursor.getLong(6),
            reviewedAtMillis = cursor.getLong(7),
            dayOffset = cursor.getLong(8),
            reviewKind = cursor.getInt(9),
        )
        return HistoryEvent.Review(cursor.getLong(0), ids, answer)
    }

    private fun nextBurst(cursor: Cursor): HistoryEvent.Warm? {
        if (!cursor.moveToNext()) {
            return null
        }
        val ids = RwkvStreamIds(card = cursor.getLong(1), deck = cursor.getLong(3), note = cursor.getLong(2), preset = cursor.getLong(4))
        return HistoryEvent.Warm(cursor.getLong(0), RwkvSeedBurst(ids, decodeAnswers(cursor.getBlob(5))))
    }

    private fun insertBurst(db: SQLiteDatabase, seq: Long, burst: RwkvSeedBurst) {
        db.insertWithOnConflict(
            TABLE_BURST,
            null,
            ContentValues().apply {
                put("seq", seq)
                put("card_id", burst.ids.card)
                put("note_id", burst.ids.note)
                put("deck_id", burst.ids.deck)
                put("preset_id", burst.ids.preset)
                put("answers", encodeAnswers(burst.answers))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun applyDelta(db: SQLiteDatabase, delta: RwkvStateDelta) {
        putGlobals(db, delta.globals)
        delta.states.forEach { (key, value) -> putState(db, key, value) }
        delta.cards.forEach { (id, value) -> putBlob(db, TABLE_CARD_FEATURES, "card_id", id, "features", value.encode()) }
        delta.histories.forEach { (id, value) -> putBlob(db, TABLE_CARD_HISTORY, "card_id", id, "history", value.encode()) }
        delta.encodings.forEach { (key, value) -> putEncoding(db, key, value) }
    }

    private fun applyUndo(db: SQLiteDatabase, undo: RwkvStateUndo) {
        putGlobals(db, undo.globals)
        undo.states.forEach { (key, value) ->
            if (value == null) {
                db.delete(TABLE_STATE, "stream=? AND id=?", arrayOf(key.stream.toString(), key.id.toString()))
            } else {
                putState(db, key, value)
            }
        }
        undo.cards.forEach { (id, value) ->
            if (value == null) {
                db.delete(TABLE_CARD_FEATURES, "card_id=?", arrayOf(id.toString()))
            } else {
                putBlob(db, TABLE_CARD_FEATURES, "card_id", id, "features", value.encode())
            }
        }
        undo.histories.forEach { (id, value) ->
            if (value == null) {
                db.delete(TABLE_CARD_HISTORY, "card_id=?", arrayOf(id.toString()))
            } else {
                putBlob(db, TABLE_CARD_HISTORY, "card_id", id, "history", value.encode())
            }
        }
        undo.encodings.forEach { (key, value) ->
            if (value == null) {
                db.delete(TABLE_ENCODING, "kind=? AND id=?", arrayOf(key.kind.code.toString(), key.id.toString()))
            } else {
                putEncoding(db, key, value)
            }
        }
    }

    private fun globals(db: SQLiteDatabase): RwkvGlobalFeatures =
        blob(db, "SELECT globals FROM $TABLE_GLOBALS WHERE id=0")?.let(RwkvGlobalFeatures::decode)
            ?: RwkvGlobalFeatures.initial()

    private fun putGlobals(db: SQLiteDatabase, globals: RwkvGlobalFeatures) {
        db.insertWithOnConflict(
            TABLE_GLOBALS,
            null,
            ContentValues().apply {
                put("id", 0)
                put("globals", globals.encode())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putState(db: SQLiteDatabase, key: RwkvStateKey, state: RwkvModuleState) {
        db.insertWithOnConflict(
            TABLE_STATE,
            null,
            ContentValues().apply {
                put("stream", key.stream)
                put("id", key.id)
                put("state", RwkvStateCodec.encode(state, precisionFor(key.stream)))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putEncoding(db: SQLiteDatabase, key: RwkvIdKey, values: FloatArray) {
        db.insertWithOnConflict(
            TABLE_ENCODING,
            null,
            ContentValues().apply {
                put("kind", key.kind.code)
                put("id", key.id)
                put("encoding", RwkvEncodingCodec.encode(values))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putBlob(db: SQLiteDatabase, table: String, idColumn: String, id: Long, column: String, bytes: ByteArray) {
        db.insertWithOnConflict(
            table,
            null,
            ContentValues().apply {
                put(idColumn, id)
                put(column, bytes)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun blob(db: SQLiteDatabase, sql: String, vararg args: String): ByteArray? =
        db.rawQuery(sql, args).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getBlob(0) else null }

    companion object {
        const val TABLE_STATE = "rwkv_state"
        const val TABLE_CARD_FEATURES = "rwkv_card_features"
        const val TABLE_CARD_HISTORY = "rwkv_card_history"
        const val TABLE_ENCODING = "rwkv_encoding"
        const val TABLE_GLOBALS = "rwkv_globals"
        const val TABLE_META = "rwkv_meta"
        const val TABLE_JOURNAL = "rwkv_undo_journal"
        const val TABLE_BURST = "rwkv_burst"

        const val COLUMN_MM_SEQ = "mm_seq"
        const val COLUMN_MM_CARD = "mm_card_id"
        const val COLUMN_MM_NOTE = "mm_note_id"
        const val COLUMN_MM_DECK = "mm_deck_id"
        const val COLUMN_MM_PRESET = "mm_preset_id"
        const val COLUMN_MM_RATING = "mm_rating"
        const val COLUMN_MM_DURATION = "mm_duration_ms"
        const val COLUMN_MM_DAY = "mm_day"
        const val COLUMN_MM_KIND = "mm_kind"

        /** The `review_log` columns v34 adds (all nullable INTEGER). */
        val REVIEW_LOG_COLUMNS: List<String> = listOf(
            COLUMN_MM_SEQ,
            COLUMN_MM_CARD,
            COLUMN_MM_NOTE,
            COLUMN_MM_DECK,
            COLUMN_MM_PRESET,
            COLUMN_MM_RATING,
            COLUMN_MM_DURATION,
            COLUMN_MM_DAY,
            COLUMN_MM_KIND,
        )

        const val META_IDENTITY = "identity"
        const val META_STATE_VERSION = "state_version"
        const val META_NEXT_SEQ = "next_seq"
        const val META_NEEDS_REBUILD = "needs_rebuild"

        private const val JOURNAL_LIMIT = 100

        fun defaultPrecision(stream: Int): RwkvStatePrecision =
            when (stream) {
                RwkvArchitecture.CARD, RwkvArchitecture.NOTE -> RwkvStatePrecision.INT8
                else -> RwkvStatePrecision.F32
            }

        @JvmStatic
        fun createTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_STATE (stream INTEGER NOT NULL, id INTEGER NOT NULL, state BLOB NOT NULL, PRIMARY KEY (stream, id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_CARD_FEATURES (card_id INTEGER PRIMARY KEY, features BLOB NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_CARD_HISTORY (card_id INTEGER PRIMARY KEY, history BLOB NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_ENCODING (kind INTEGER NOT NULL, id INTEGER NOT NULL, encoding BLOB NOT NULL, PRIMARY KEY (kind, id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_GLOBALS (id INTEGER PRIMARY KEY CHECK (id = 0), globals BLOB NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_META (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_JOURNAL (token TEXT PRIMARY KEY, state_version INTEGER NOT NULL, " +
                    "burst_seq INTEGER, undo BLOB NOT NULL, created_at INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $TABLE_BURST (seq INTEGER PRIMARY KEY, card_id INTEGER NOT NULL, " +
                    "note_id INTEGER NOT NULL, deck_id INTEGER NOT NULL, preset_id INTEGER NOT NULL, answers BLOB NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_log_mm_seq ON ${LocalStoreBase.TABLE_REVIEW_LOG}($COLUMN_MM_SEQ)")
        }

        /** A new database: no history, so the empty state is complete. */
        @JvmStatic
        fun initializeFresh(db: SQLiteDatabase) {
            putMeta(db, META_IDENTITY, RwkvAlgorithmInfo.stateIdentity())
            putMeta(db, META_STATE_VERSION, "0")
            putMeta(db, META_NEXT_SEQ, "1")
            putMeta(db, META_NEEDS_REBUILD, "0")
        }

        /**
         * A database upgraded to v34: existing review rows await classification
         * (their `mm_seq` stays NULL) and live sequence numbers start above the
         * historical ones the classification assigns.
         */
        @JvmStatic
        fun initializeAfterMigration(db: SQLiteDatabase) {
            val maxId = db.rawQuery("SELECT COALESCE(MAX(id), 0) FROM ${LocalStoreBase.TABLE_REVIEW_LOG}", null)
                .use { if (it.moveToFirst()) it.getLong(0) else 0L }
            putMeta(db, META_IDENTITY, RwkvAlgorithmInfo.stateIdentity())
            putMeta(db, META_STATE_VERSION, "0")
            putMeta(db, META_NEXT_SEQ, (2 * maxId + 2).toString())
            putMeta(db, META_NEEDS_REBUILD, "1")
        }

        private fun putMeta(db: SQLiteDatabase, key: String, value: String) {
            db.insertWithOnConflict(
                TABLE_META,
                null,
                ContentValues().apply {
                    put("key", key)
                    put("value", value)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

        @JvmStatic
        fun encodeAnswers(answers: List<RwkvAnswer>): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(answers.size)
                for (answer in answers) {
                    out.writeByte(answer.rating)
                    out.writeLong(answer.durationMillis ?: -1L)
                    out.writeLong(answer.reviewedAtMillis)
                    out.writeLong(answer.dayOffset)
                    out.writeByte(answer.reviewKind)
                }
            }
            return bytes.toByteArray()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun decodeAnswers(bytes: ByteArray): List<RwkvAnswer> =
            DataInputStream(bytes.inputStream()).use { input ->
                val count = input.readInt()
                if (count !in 0..64) {
                    throw IOException("invalid RWKV burst length $count")
                }
                List(count) {
                    val rating = input.readUnsignedByte()
                    val duration = input.readLong()
                    RwkvAnswer(
                        rating = rating.coerceIn(1, 4),
                        durationMillis = duration.takeIf { it >= 0L },
                        reviewedAtMillis = input.readLong(),
                        dayOffset = input.readLong(),
                        reviewKind = input.readUnsignedByte(),
                    )
                }
            }
    }
}
