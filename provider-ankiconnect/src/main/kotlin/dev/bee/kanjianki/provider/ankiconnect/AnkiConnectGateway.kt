package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.ArchiveTagSummary
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionSourceStatus
import dev.bee.kanjianki.syncapi.NoteTypeDescriptor
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot

/**
 * The [CollectionGateway] AnkiConnect implementation: the seam where the desktop
 * provider becomes interchangeable with [dev.bee.kanjianki.syncapi.CollectionGateway]
 * consumers written for AnkiDroid. It owns no protocol logic — the handshake
 * lives in [AnkiConnectHandshake] and the reads in [AnkiConnectCollectionReader];
 * this class only translates their results into the shared contract.
 *
 * Two translation decisions are load-bearing:
 *
 * - **Availability is the handshake's verdict, not a ping.** A reachable Anki
 *   that has not granted permission, speaks a different wire version, lacks a
 *   required action, or has no collection open is *not* `READY`, and each of
 *   those maps to the availability the caller can actually act on
 *   (`AUTH_REQUIRED` vs `INVALID_CONFIGURATION`). That translation is
 *   [AnkiConnectStatusMapping]'s, shared with the inventory gateway so the two
 *   cannot classify the same Anki differently.
 * - **Capabilities are only what this class can actually do today.** Stock
 *   AnkiConnect advertises `addTags`/`addNotes`, so it would be easy to declare
 *   [CollectionCapability.NOTE_TAG_WRITE] and
 *   [CollectionCapability.MISSING_KANJI_WRITE] from the handshake's optional
 *   action set. Those write paths are not implemented yet (Goal 190), and
 *   [removeArchivedSuspendedCards] here is a no-op, so declaring them would
 *   promise a write that silently does nothing. Read capabilities only, until
 *   the writes exist.
 */
class AnkiConnectGateway(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
) : CollectionGateway {
    private val handshake = AnkiConnectHandshake(transport)
    private val reader = AnkiConnectCollectionReader(transport, keyProvider)

    /**
     * Runs the full handshake and reports what Kani may do with this Anki.
     *
     * This is four round trips over loopback, so it is a deliberate check rather
     * than something to poll; callers hold the result for the duration of a sync.
     */
    override fun status(): CollectionSourceStatus {
        val result = handshake.run(keyProvider())
        val capabilities = if (result is AnkiConnectHandshake.Status.Ready) {
            READ_CAPABILITIES
        } else {
            emptySet()
        }
        return CollectionSourceStatus(
            AnkiConnectStatusMapping.availabilityFor(result),
            capabilities,
            AnkiConnectStatusMapping.messageFor(result),
        )
    }

    @Throws(CollectionFailure::class)
    override fun noteTypes(): List<NoteTypeDescriptor> = reader.noteTypes()

    @Throws(CollectionFailure::class)
    override fun readCollection(
        settings: RecordsSyncModels.Settings,
    ): RecordsSyncModels.CollectionSnapshot = reader.read(settings).snapshot

    @Throws(CollectionFailure::class)
    override fun readCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener,
    ): RecordsSyncModels.CollectionSnapshot = reader.read(settings, progress).snapshot

    @Throws(CollectionFailure::class)
    override fun readProviderCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener,
        cancellation: CollectionCancellation,
    ): ProviderCollectionSnapshot = reader.readProviderCollection(settings, progress, cancellation)

    /**
     * Not yet supported. Archiving imported suspended notes is a `addTags` write,
     * which Goal 190 gates behind the same additive-write review as the Missing
     * Kanji flow, so this reports zero work rather than pretending to tag.
     */
    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
    ): ArchiveTagSummary = ArchiveTagSummary(
        snapshot.cards.size,
        0,
        0,
        "Kani cannot tag notes over AnkiConnect yet, so nothing was archived.",
    )

    /**
     * The reader's malformed-row diagnostic for the most recent read shape, for
     * callers that surface the shared warning copy. Returns null below the shared
     * threshold.
     */
    @Throws(CollectionFailure::class)
    fun readWithDiagnostics(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener = CollectionProgressListener.NONE,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): AnkiConnectCollectionReader.ReadResult = reader.read(settings, progress, cancellation)

    companion object {
        /**
         * What an AnkiConnect Kani can talk to is able to do. Notably absent:
         * [CollectionCapability.FSRS_MEMORY_STATE], because AnkiConnect exposes no
         * memory state at all — see [AnkiConnectCollectionReader].
         */
        @JvmField
        val READ_CAPABILITIES: Set<CollectionCapability> = setOf(
            CollectionCapability.READ_COLLECTION,
            CollectionCapability.LIST_NOTE_TYPES,
        )
    }
}
