package dev.bee.kanjianki.syncapi

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import java.util.Collections

enum class CollectionCapability {
    READ_COLLECTION,
    LIST_NOTE_TYPES,
    COLLECTION_INVENTORY,
    NOTE_TAG_WRITE,
    MISSING_KANJI_WRITE,
    FSRS_MEMORY_STATE,
    SOURCE_IDENTITY,
}

enum class CollectionAvailability {
    READY,
    NOT_AVAILABLE,
    AUTH_REQUIRED,
    INVALID_CONFIGURATION,
}

open class CollectionSourceStatus(
    @JvmField val availability: CollectionAvailability,
    capabilities: Set<CollectionCapability>,
    message: String?,
) {
    @JvmField
    val capabilities: Set<CollectionCapability> =
        Collections.unmodifiableSet(capabilities.toSet())

    @JvmField
    val message: String = message.orEmpty()

    fun isReady(): Boolean = availability == CollectionAvailability.READY

    fun supports(capability: CollectionCapability): Boolean = capability in capabilities

    override fun toString(): String =
        "CollectionSourceStatus(availability=$availability, capabilities=$capabilities)"

    companion object {
        @JvmStatic
        fun ready(capabilities: Set<CollectionCapability>): CollectionSourceStatus =
            CollectionSourceStatus(CollectionAvailability.READY, capabilities, "")
    }
}

open class NoteTypeDescriptor(
    @JvmField val modelId: Long,
    name: String?,
    fields: List<String>?,
) {
    @JvmField
    val name: String = name.orEmpty()

    @JvmField
    val fields: List<String> =
        Collections.unmodifiableList(ArrayList(fields.orEmpty()))
}

enum class CollectionFailureKind(val retryableByDefault: Boolean) {
    NOT_AVAILABLE(true),
    AUTH_REQUIRED(false),
    INVALID_CONFIGURATION(false),
    UNSUPPORTED_CAPABILITY(false),
    TRANSIENT(true),
    CANCELLED(true),
}

open class CollectionFailure(
    @JvmField val kind: CollectionFailureKind,
    message: String?,
    @JvmField val retryable: Boolean = kind.retryableByDefault,
    cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun cancelled(message: String? = "Collection operation cancelled."): CollectionFailure =
            CollectionFailure(CollectionFailureKind.CANCELLED, message)
    }
}

fun interface CollectionCancellation {
    fun isCancelled(): Boolean

    companion object {
        @JvmField
        val NONE: CollectionCancellation = CollectionCancellation { false }
    }
}

data class CollectionProgress(
    val stage: Stage,
    val completed: Int = 0,
    val total: Int? = null,
) {
    init {
        require(completed >= 0) { "completed must not be negative" }
        require(total == null || total >= 0) { "total must not be negative" }
    }

    enum class Stage {
        FINDING_NOTE_TYPE,
        READING_NOTES,
        SCANNING_CARDS,
        ARCHIVING_IMPORTED_CARDS,
        TAGGING_REPAIRED,
        READING_INVENTORY,
        WRITING_MISSING_KANJI,
    }
}

fun interface CollectionProgressListener {
    fun onProgress(progress: CollectionProgress)

    companion object {
        @JvmField
        val NONE: CollectionProgressListener = CollectionProgressListener { }
    }
}

class ProviderCollectionSnapshot(
    val snapshot: RecordsSyncModels.CollectionSnapshot,
    capabilities: Set<CollectionCapability>,
    val sourceIdentity: CollectionSourceIdentity?,
) {
    val capabilities: Set<CollectionCapability> =
        Collections.unmodifiableSet(capabilities.toSet())

    init {
        require(
            (sourceIdentity != null) ==
                (CollectionCapability.SOURCE_IDENTITY in capabilities),
        ) {
            "SOURCE_IDENTITY capability and evidence must be supplied together"
        }
    }
}

open class ArchiveTagSummary(
    @JvmField val sourceCards: Int,
    @JvmField val deletedNotes: Int,
    @JvmField val taggedNotes: Int,
    message: String?,
) {
    @JvmField
    val message: String = message.orEmpty()
}

open class RepairedTagSummary(
    requestedNoteIds: Set<Long>,
    taggedNoteIds: Set<Long>,
    failedNoteIds: Set<Long>,
    message: String?,
) {
    @JvmField
    val requestedNoteIds: Set<Long> =
        Collections.unmodifiableSet(requestedNoteIds.toSet())

    @JvmField
    val taggedNoteIds: Set<Long> =
        Collections.unmodifiableSet(taggedNoteIds.toSet())

    @JvmField
    val failedNoteIds: Set<Long> =
        Collections.unmodifiableSet(failedNoteIds.toSet())

    @JvmField
    val message: String = message.orEmpty()

    companion object {
        @JvmStatic
        fun noOp(): RepairedTagSummary =
            RepairedTagSummary(
                emptySet(),
                emptySet(),
                emptySet(),
                "No repaired notes needed provider tagging.",
            )
    }
}

interface CollectionGateway {
    fun status(): CollectionSourceStatus =
        CollectionSourceStatus.ready(setOf(CollectionCapability.READ_COLLECTION))

    @Throws(CollectionFailure::class)
    fun noteTypes(): List<NoteTypeDescriptor> = emptyList()

    @Throws(CollectionFailure::class)
    fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot

    @Throws(CollectionFailure::class)
    fun readCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener,
    ): RecordsSyncModels.CollectionSnapshot = readCollection(settings)

    @Throws(CollectionFailure::class)
    fun readProviderCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener = CollectionProgressListener.NONE,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): ProviderCollectionSnapshot {
        if (cancellation.isCancelled()) throw CollectionFailure.cancelled()
        val snapshot = readCollection(settings, progress)
        if (cancellation.isCancelled()) throw CollectionFailure.cancelled()
        return ProviderCollectionSnapshot(snapshot, status().capabilities, null)
    }

    fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
    ): ArchiveTagSummary

    fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        progress: CollectionProgressListener,
    ): ArchiveTagSummary = removeArchivedSuspendedCards(snapshot)

    fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        selectedSuspendedImports: List<RecordsImportModels.SuspendedImport>?,
        progress: CollectionProgressListener,
    ): ArchiveTagSummary = removeArchivedSuspendedCards(snapshot, progress)

    fun tagRepairedNotes(
        noteIds: Set<Long>,
        progress: CollectionProgressListener,
    ): RepairedTagSummary = RepairedTagSummary.noOp()
}

data class CollectionInventoryNote(
    val noteId: Long,
    val modelId: Long,
    val modelName: String,
    val fieldNames: List<String>,
    val fields: List<String>,
)

fun interface CollectionInventoryConsumer {
    fun onNote(note: CollectionInventoryNote)
}

data class CollectionInventoryResult(
    val notesRead: Int,
    val skippedNotes: Int,
    val modelCount: Int,
    val queryMode: QueryMode,
) {
    enum class QueryMode {
        DIRECT_PAGED,
        PROVIDER_SEARCH,
    }
}

interface CollectionInventoryGateway {
    fun status(): CollectionSourceStatus

    @Throws(CollectionFailure::class)
    fun scan(
        consumer: CollectionInventoryConsumer,
        progress: CollectionProgressListener = CollectionProgressListener.NONE,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): CollectionInventoryResult
}

fun interface MissingKanjiReceiptSink {
    fun record(destinationKey: String, notes: List<ConfirmedMissingKanjiNote>): Boolean

    companion object {
        @JvmField
        val NONE: MissingKanjiReceiptSink = MissingKanjiReceiptSink { _, _ -> true }
    }
}

data class ConfirmedMissingKanjiNote(
    val literal: String,
    val noteId: Long,
)

data class MissingKanjiWriteProgress(
    val totalCount: Int,
    val processedCount: Int,
    val createdCount: Int,
    val alreadyPresentCount: Int,
)

fun interface MissingKanjiProgressListener {
    fun onProgress(progress: MissingKanjiWriteProgress)

    companion object {
        @JvmField
        val NONE: MissingKanjiProgressListener = MissingKanjiProgressListener { }
    }
}

enum class MissingKanjiWriteFailureKind {
    NOT_AVAILABLE,
    AUTH_REQUIRED,
    UNSUPPORTED_CAPABILITY,
    INVALID_DECK_NAME,
    DECK_COLLISION,
    MODEL_COLLISION,
    INCOMPLETE_WRITE,
    RECEIPT_PERSISTENCE,
    TRANSIENT,
    CANCELLED,
}

data class MissingKanjiWriteResult(
    val requestedCount: Int,
    val validCount: Int,
    val createdNotes: Map<String, Long>,
    val alreadyPresentNotes: Map<String, Long>,
    val invalidLiterals: Set<String>,
    val invalidCount: Int,
    val duplicateRequestCount: Int,
    val unfinishedLiterals: Set<String>,
    val destinationKey: String?,
    val failureKind: MissingKanjiWriteFailureKind?,
) {
    val completed: Boolean
        get() = failureKind == null && unfinishedLiterals.isEmpty()
}

interface MissingKanjiWriter {
    fun status(): CollectionSourceStatus

    fun export(
        candidates: Iterable<MissingKanjiCandidate>,
        deckName: String = MissingKanjiExportPlanner.DEFAULT_DECK_NAME,
        progress: MissingKanjiProgressListener = MissingKanjiProgressListener.NONE,
        receiptSink: MissingKanjiReceiptSink = MissingKanjiReceiptSink.NONE,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): MissingKanjiWriteResult
}
