package dev.bee.kanjianki.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.OperationCanceledException
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Additive, idempotent writer for Kani's dedicated Missing Kanji note type.
 */
class AnkiMissingKanjiWriter private constructor(
    context: Context,
    private val statusProvider: () -> AnkiDroidCollectionInventoryGateway.CapabilityStatus,
    private val cancellation: Cancellation,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    constructor(context: Context) : this(
        context = context,
        statusProvider = { AnkiDroidCollectionInventoryGateway(context).status() },
        cancellation = Cancellation.NONE,
    )

    constructor(context: Context, cancellation: Cancellation) : this(
        context = context,
        statusProvider = { AnkiDroidCollectionInventoryGateway(context).status() },
        cancellation = cancellation,
    )

    fun status(): AnkiDroidCollectionInventoryGateway.CapabilityStatus = statusProvider()

    fun export(
        candidates: Iterable<MissingKanjiCandidate>,
        deckName: String = MissingKanjiExportPlanner.DEFAULT_DECK_NAME,
        progress: ProgressListener = ProgressListener.NONE,
        receiptSink: ReceiptSink = ReceiptSink.NONE,
    ): ExportResult {
        val plan = MissingKanjiExportPlanner.plan(candidates)
        val state = ExportState(plan)
        return synchronized(EXPORT_LOCK) {
            try {
                exportLocked(plan, state, deckName.trim(), progress, receiptSink)
            } catch (abort: WriteAbort) {
                state.result(abort.kind)
            } catch (_: SecurityException) {
                state.result(FailureKind.PERMISSION_REQUIRED)
            } catch (_: OperationCanceledException) {
                state.result(FailureKind.PROVIDER_UNAVAILABLE)
            } catch (_: RuntimeException) {
                state.result(FailureKind.PROVIDER_UNAVAILABLE)
            }
        }
    }

    private fun exportLocked(
        plan: MissingKanjiExportPlanner.Plan,
        state: ExportState,
        deckName: String,
        progress: ProgressListener,
        receiptSink: ReceiptSink,
    ): ExportResult {
        if (deckName.isEmpty()) {
            return state.result(FailureKind.INVALID_DECK_NAME)
        }
        if (isCancelled()) {
            return state.result(FailureKind.CANCELLED)
        }
        val status = statusProvider()
        val authority = when {
            !status.installed -> return state.result(FailureKind.NOT_INSTALLED)
            !status.permissionGranted -> return state.result(FailureKind.PERMISSION_REQUIRED)
            !status.canWriteCollection || status.providerSpecVersion < MINIMUM_PROVIDER_SPEC ->
                return state.result(FailureKind.UNSUPPORTED_PROVIDER)
            status.authority.isNullOrBlank() -> return state.result(FailureKind.PROVIDER_UNAVAILABLE)
            else -> status.authority
        }
        if (plan.notes.isEmpty()) {
            progress.onProgress(state.progress())
            return state.result()
        }
        progress.onProgress(state.progress())

        var deckId: Long? = null
        val modelId = compatibleModelId(authority) ?: run {
            val createdDeckId = ensureDeck(authority, deckName)
            deckId = createdDeckId
            checkCancellation()
            createModel(authority, createdDeckId)
        }
        val destinationKey = destinationKey(authority, modelId)
        state.destinationKey = destinationKey
        checkCancellation()

        val existing = queryExportedNotes(authority, modelId)
        val selectedBySource = plan.notes.associateBy(MissingKanjiExportPlanner.ExportNote::sourceId)
        val existingSelected = ArrayList<ConfirmedNote>()
        for ((sourceId, noteId) in existing) {
            val note = selectedBySource[sourceId] ?: continue
            state.alreadyPresent[note.literal] = noteId
            state.unfinished.remove(note.literal)
            existingSelected.add(ConfirmedNote(note.literal, noteId))
        }
        if (!recordReceipts(receiptSink, destinationKey, existingSelected)) {
            return state.result(FailureKind.RECEIPT_PERSISTENCE)
        }
        progress.onProgress(state.progress())

        val pending = plan.notes.filter { note -> note.literal in state.unfinished }
        if (pending.isEmpty()) {
            return state.result()
        }
        checkCancellation()
        val targetDeckId = deckId ?: ensureDeck(authority, deckName)
        checkCancellation()
        for (batch in pending.chunked(MAX_BATCH_SIZE)) {
            if (isCancelled()) {
                return state.result(FailureKind.CANCELLED)
            }
            val failure = insertAndReconcileBatch(
                authority = authority,
                deckId = targetDeckId,
                modelId = modelId,
                batch = batch,
                previouslyExisting = existing,
                destinationKey = destinationKey,
                state = state,
                receiptSink = receiptSink,
            )
            progress.onProgress(state.progress())
            if (failure != null) {
                return state.result(failure)
            }
        }
        return state.result()
    }

    private fun insertAndReconcileBatch(
        authority: String,
        deckId: Long,
        modelId: Long,
        batch: List<MissingKanjiExportPlanner.ExportNote>,
        previouslyExisting: MutableMap<String, Long>,
        destinationKey: String,
        state: ExportState,
        receiptSink: ReceiptSink,
    ): FailureKind? {
        var providerFailure: RuntimeException? = null
        val insertedCount = try {
            resolver.bulkInsert(
                uri(authority, NOTES_PATH).buildUpon()
                    .appendQueryParameter(DECK_ID_QUERY_PARAMETER, deckId.toString())
                    .build(),
                batch.map { note ->
                    ContentValues().apply {
                        put(COLUMN_MODEL_ID, modelId)
                        put(COLUMN_FIELDS, note.fields.joinToString(FIELD_SEPARATOR))
                        put(COLUMN_TAGS, MissingKanjiExportPlanner.TAG)
                    }
                }.toTypedArray(),
            )
        } catch (error: RuntimeException) {
            providerFailure = error
            -1
        }

        val reconciled = try {
            queryExportedNotes(authority, modelId)
        } catch (error: RuntimeException) {
            if (providerFailure != null) {
                throw providerFailure
            }
            throw error
        }
        val confirmed = ArrayList<ConfirmedNote>()
        for (note in batch) {
            val noteId = reconciled[note.sourceId] ?: continue
            if (!previouslyExisting.containsKey(note.sourceId)) {
                state.created[note.literal] = noteId
                confirmed.add(ConfirmedNote(note.literal, noteId))
            }
            state.unfinished.remove(note.literal)
        }
        previouslyExisting.putAll(reconciled)
        if (!recordReceipts(receiptSink, destinationKey, confirmed)) {
            return FailureKind.RECEIPT_PERSISTENCE
        }
        if (providerFailure != null) {
            throw providerFailure
        }
        return if (insertedCount != batch.size || confirmed.size != batch.size) {
            FailureKind.INCOMPLETE_WRITE
        } else {
            null
        }
    }

    private fun ensureDeck(authority: String, deckName: String): Long {
        findDeck(authority, deckName)?.let { deck ->
            if (deck.filtered) {
                throw WriteAbort(FailureKind.DECK_COLLISION)
            }
            return deck.id
        }
        val inserted = resolver.insert(
            uri(authority, DECKS_PATH),
            ContentValues().apply { put(COLUMN_DECK_NAME, deckName) },
        ) ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        val insertedId = inserted.lastPathSegment?.toLongOrNull()
            ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        val confirmed = findDeck(authority, deckName)
            ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        if (confirmed.filtered || confirmed.id != insertedId) {
            throw WriteAbort(FailureKind.DECK_COLLISION)
        }
        return confirmed.id
    }

    private fun findDeck(authority: String, deckName: String): DeckInfo? {
        val cursor = resolver.query(uri(authority, DECKS_PATH), null, null, null, null)
            ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        var match: DeckInfo? = null
        cursor.use {
            while (it.moveToNext()) {
                checkCancellation()
                if (stringValue(it, COLUMN_DECK_NAME) != deckName) {
                    continue
                }
                val candidate = DeckInfo(
                    id = longValue(it, COLUMN_DECK_ID),
                    filtered = booleanValue(it, COLUMN_DECK_FILTERED),
                )
                if (candidate.id <= 0L || match != null) {
                    throw WriteAbort(FailureKind.DECK_COLLISION)
                }
                match = candidate
            }
        }
        return match
    }

    private fun compatibleModelId(authority: String): Long? {
        val model = findModel(authority) ?: return null
        if (!modelCompatible(authority, model)) {
            throw WriteAbort(FailureKind.MODEL_COLLISION)
        }
        return model.id
    }

    private fun createModel(authority: String, deckId: Long): Long {
        val modelUri = resolver.insert(
            uri(authority, MODELS_PATH),
            ContentValues().apply {
                put(COLUMN_NAME, MissingKanjiExportPlanner.MODEL_NAME)
                put(COLUMN_FIELD_NAMES, MissingKanjiExportPlanner.FIELD_NAMES.joinToString(FIELD_SEPARATOR))
                put(COLUMN_CARD_COUNT, 1)
                put(COLUMN_CSS, MissingKanjiExportPlanner.CSS)
                put(COLUMN_DECK_ID, deckId)
                put(COLUMN_SORT_FIELD_INDEX, 0)
                put(COLUMN_MODEL_TYPE, 0)
            },
        ) ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        val modelId = modelUri.lastPathSegment?.toLongOrNull()
            ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        resolver.update(
            uri(authority, "$MODELS_PATH/$modelId/$TEMPLATES_PATH/0"),
            ContentValues().apply {
                put(COLUMN_TEMPLATE_NAME, MissingKanjiExportPlanner.TEMPLATE_NAME)
                put(COLUMN_QUESTION_FORMAT, MissingKanjiExportPlanner.QUESTION_FORMAT)
                put(COLUMN_ANSWER_FORMAT, MissingKanjiExportPlanner.ANSWER_FORMAT)
            },
            null,
            null,
        )
        val confirmed = findModel(authority)
        if (confirmed == null || confirmed.id != modelId || !modelCompatible(authority, confirmed)) {
            throw WriteAbort(FailureKind.MODEL_COLLISION)
        }
        return modelId
    }

    private fun findModel(authority: String): ModelInfo? {
        val cursor = resolver.query(uri(authority, MODELS_PATH), null, null, null, null)
            ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        var match: ModelInfo? = null
        cursor.use {
            while (it.moveToNext()) {
                checkCancellation()
                if (stringValue(it, COLUMN_NAME) != MissingKanjiExportPlanner.MODEL_NAME) {
                    continue
                }
                val candidate = ModelInfo(
                    id = longValue(it, COLUMN_ID),
                    fieldNames = splitFields(stringValue(it, COLUMN_FIELD_NAMES)),
                    cardCount = intValue(it, COLUMN_CARD_COUNT),
                    css = stringValue(it, COLUMN_CSS),
                    sortFieldIndex = intValue(it, COLUMN_SORT_FIELD_INDEX),
                    type = intValue(it, COLUMN_MODEL_TYPE),
                )
                if (candidate.id <= 0L || match != null) {
                    throw WriteAbort(FailureKind.MODEL_COLLISION)
                }
                match = candidate
            }
        }
        return match
    }

    private fun modelCompatible(authority: String, model: ModelInfo): Boolean {
        if (model.fieldNames != MissingKanjiExportPlanner.FIELD_NAMES ||
            model.cardCount != 1 ||
            model.css != MissingKanjiExportPlanner.CSS ||
            model.sortFieldIndex != 0 ||
            model.type != 0
        ) {
            return false
        }
        val cursor = resolver.query(
            uri(authority, "$MODELS_PATH/${model.id}/$TEMPLATES_PATH/0"),
            null,
            null,
            null,
            null,
        ) ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        cursor.use {
            if (!it.moveToFirst()) {
                return false
            }
            checkCancellation()
            val compatible = stringValue(it, COLUMN_TEMPLATE_NAME) ==
                MissingKanjiExportPlanner.TEMPLATE_NAME &&
                stringValue(it, COLUMN_QUESTION_FORMAT) ==
                MissingKanjiExportPlanner.QUESTION_FORMAT &&
                stringValue(it, COLUMN_ANSWER_FORMAT) ==
                MissingKanjiExportPlanner.ANSWER_FORMAT
            return compatible && !it.moveToNext()
        }
    }

    private fun queryExportedNotes(authority: String, modelId: Long): MutableMap<String, Long> {
        val cursor = resolver.query(
            uri(authority, NOTES_V2_PATH),
            null,
            "$COLUMN_MODEL_ID=?",
            arrayOf(modelId.toString()),
            "$DATABASE_ID ASC",
        ) ?: throw WriteAbort(FailureKind.PROVIDER_UNAVAILABLE)
        val notes = LinkedHashMap<String, Long>()
        cursor.use {
            while (it.moveToNext()) {
                checkCancellation()
                val noteId = longValue(it, COLUMN_ID)
                val fields = splitFields(stringValue(it, COLUMN_FIELDS))
                if (noteId <= 0L || fields.size != MissingKanjiExportPlanner.FIELD_NAMES.size) {
                    throw WriteAbort(FailureKind.MODEL_COLLISION)
                }
                val sourceId = fields[SOURCE_ID_FIELD_INDEX]
                if (sourceId.startsWith(MissingKanjiExportPlanner.SOURCE_ID_PREFIX)) {
                    notes.putIfAbsent(sourceId, noteId)
                }
            }
        }
        return notes
    }

    private fun recordReceipts(
        receiptSink: ReceiptSink,
        destinationKey: String,
        notes: List<ConfirmedNote>,
    ): Boolean {
        if (notes.isEmpty()) {
            return true
        }
        return try {
            receiptSink.record(destinationKey, Collections.unmodifiableList(notes))
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun checkCancellation() {
        if (isCancelled()) {
            throw WriteAbort(FailureKind.CANCELLED)
        }
    }

    private fun isCancelled(): Boolean = cancellation.isCancelled()

    fun interface Cancellation {
        fun isCancelled(): Boolean

        companion object {
            val NONE = Cancellation { false }
        }
    }

    fun interface ProgressListener {
        fun onProgress(progress: ExportProgress)

        companion object {
            val NONE = ProgressListener { }
        }
    }

    fun interface ReceiptSink {
        fun record(destinationKey: String, notes: List<ConfirmedNote>): Boolean

        companion object {
            val NONE = ReceiptSink { _, _ -> true }
        }
    }

    data class ConfirmedNote(
        val literal: String,
        val noteId: Long,
    )

    data class ExportProgress(
        val totalCount: Int,
        val processedCount: Int,
        val createdCount: Int,
        val alreadyPresentCount: Int,
    )

    data class ExportResult(
        val requestedCount: Int,
        val validCount: Int,
        val createdNotes: Map<String, Long>,
        val alreadyPresentNotes: Map<String, Long>,
        val invalidLiterals: Set<String>,
        val invalidCount: Int,
        val duplicateRequestCount: Int,
        val unfinishedLiterals: Set<String>,
        val destinationKey: String?,
        val failureKind: FailureKind?,
    ) {
        val createdCount: Int
            get() = createdNotes.size

        val alreadyPresentCount: Int
            get() = alreadyPresentNotes.size

        val skippedCount: Int
            get() = invalidCount + duplicateRequestCount

        val completed: Boolean
            get() = failureKind == null && unfinishedLiterals.isEmpty()
    }

    enum class FailureKind {
        NOT_INSTALLED,
        PERMISSION_REQUIRED,
        UNSUPPORTED_PROVIDER,
        PROVIDER_UNAVAILABLE,
        INVALID_DECK_NAME,
        DECK_COLLISION,
        MODEL_COLLISION,
        INCOMPLETE_WRITE,
        RECEIPT_PERSISTENCE,
        CANCELLED,
    }

    private class ExportState(private val plan: MissingKanjiExportPlanner.Plan) {
        val created = LinkedHashMap<String, Long>()
        val alreadyPresent = LinkedHashMap<String, Long>()
        val unfinished = LinkedHashSet(plan.notes.map(MissingKanjiExportPlanner.ExportNote::literal))
        var destinationKey: String? = null

        fun progress(): ExportProgress = ExportProgress(
            totalCount = plan.notes.size,
            processedCount = plan.notes.size - unfinished.size,
            createdCount = created.size,
            alreadyPresentCount = alreadyPresent.size,
        )

        fun result(failureKind: FailureKind? = null): ExportResult = ExportResult(
            requestedCount = plan.requestedCount,
            validCount = plan.notes.size,
            createdNotes = Collections.unmodifiableMap(LinkedHashMap(created)),
            alreadyPresentNotes = Collections.unmodifiableMap(LinkedHashMap(alreadyPresent)),
            invalidLiterals = plan.invalidLiterals,
            invalidCount = plan.invalidCount,
            duplicateRequestCount = plan.duplicateCount,
            unfinishedLiterals = Collections.unmodifiableSet(LinkedHashSet(unfinished)),
            destinationKey = destinationKey,
            failureKind = failureKind,
        )
    }

    private data class DeckInfo(
        val id: Long,
        val filtered: Boolean,
    )

    private data class ModelInfo(
        val id: Long,
        val fieldNames: List<String>,
        val cardCount: Int,
        val css: String,
        val sortFieldIndex: Int,
        val type: Int,
    )

    private class WriteAbort(val kind: FailureKind) : RuntimeException()

    companion object {
        private const val MINIMUM_PROVIDER_SPEC = 2
        private const val MAX_BATCH_SIZE = 100
        private const val SOURCE_ID_FIELD_INDEX = 5
        private const val FIELD_SEPARATOR = "\u001f"
        private const val NOTES_PATH = "notes"
        private const val NOTES_V2_PATH = "notes_v2"
        private const val MODELS_PATH = "models"
        private const val TEMPLATES_PATH = "templates"
        private const val DECKS_PATH = "decks"
        private const val DATABASE_ID = "id"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_FIELD_NAMES = "field_names"
        private const val COLUMN_CARD_COUNT = "num_cards"
        private const val COLUMN_CSS = "css"
        private const val COLUMN_SORT_FIELD_INDEX = "sort_field_index"
        private const val COLUMN_MODEL_TYPE = "type"
        private const val COLUMN_MODEL_ID = "mid"
        private const val COLUMN_FIELDS = "flds"
        private const val COLUMN_TAGS = "tags"
        private const val COLUMN_DECK_NAME = "deck_name"
        private const val COLUMN_DECK_ID = "deck_id"
        private const val COLUMN_DECK_FILTERED = "deck_dyn"
        private const val COLUMN_TEMPLATE_NAME = "card_template_name"
        private const val COLUMN_QUESTION_FORMAT = "question_format"
        private const val COLUMN_ANSWER_FORMAT = "answer_format"
        private const val DECK_ID_QUERY_PARAMETER = "deckId"
        private const val CONTENT_SCHEME = "content"
        private val EXPORT_LOCK = Any()

        fun testProvider(
            context: Context,
            authority: String,
            providerSpecVersion: Int = MINIMUM_PROVIDER_SPEC,
            cancellation: Cancellation = Cancellation.NONE,
        ): AnkiMissingKanjiWriter {
            return AnkiMissingKanjiWriter(
                context = context,
                statusProvider = {
                    val installed = AnkiDroidCollectionInventoryGateway.providerInstalled(
                        context.packageManager,
                        authority,
                    )
                    AnkiDroidCollectionInventoryGateway.CapabilityStatus(
                        installed = installed,
                        permissionGranted = true,
                        canReadCollection = installed,
                        canWriteCollection = installed && providerSpecVersion >= MINIMUM_PROVIDER_SPEC,
                        authority = authority,
                        permission = null,
                        providerSpecVersion = providerSpecVersion,
                    )
                },
                cancellation = cancellation,
            )
        }

        fun destinationKey(authority: String, modelId: Long): String =
            "ankidroid:$authority:$modelId"

        private fun uri(authority: String, path: String): Uri {
            val builder = Uri.Builder()
                .scheme(CONTENT_SCHEME)
                .authority(authority)
            for (segment in path.split('/')) {
                builder.appendPath(segment)
            }
            return builder.build()
        }

        private fun splitFields(value: String): List<String> =
            value.split(FIELD_SEPARATOR, ignoreCase = false, limit = Int.MAX_VALUE)

        private fun stringValue(cursor: Cursor, column: String): String {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index)
        }

        private fun longValue(cursor: Cursor, column: String): Long {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) -1L else cursor.getLong(index)
        }

        private fun intValue(cursor: Cursor, column: String): Int {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) Int.MIN_VALUE else cursor.getInt(index)
        }

        private fun booleanValue(cursor: Cursor, column: String): Boolean {
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) {
                return false
            }
            return when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(index) != 0
                else -> cursor.getString(index).toBooleanStrictOrNull() ?: false
            }
        }
    }
}
