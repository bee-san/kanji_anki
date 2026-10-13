package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.ArchiveTagSummary
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionSourceStatus
import dev.bee.kanjianki.syncapi.NoteTypeDescriptor
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncapi.RepairedTagSummary
import dev.bee.kanjianki.syncdomain.ProviderArchiveCleanupPolicy
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy

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
 * - **Capabilities are only what this class can actually do today, against
 *   *this* Anki.** [CollectionCapability.NOTE_TAG_WRITE] is advertised only when
 *   the handshake's `apiReflect` actually reported `addTags`; an AnkiConnect
 *   build or configuration that withholds it gets the read capabilities alone,
 *   because a declared-but-absent write is worse than an undeclared one.
 *   [CollectionCapability.MISSING_KANJI_WRITE] stays unadvertised here: that flow
 *   is the Missing Kanji writer's, gated on its own capability check.
 */
class AnkiConnectGateway(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
) : CollectionGateway {
    private val handshake = AnkiConnectHandshake(transport)
    private val reader = AnkiConnectCollectionReader(transport, keyProvider)
    private val tagWriter = AnkiConnectTagWriter(transport, keyProvider)

    /**
     * Runs the full handshake and reports what Kani may do with this Anki.
     *
     * This is four round trips over loopback, so it is a deliberate check rather
     * than something to poll; callers hold the result for the duration of a sync.
     */
    override fun status(): CollectionSourceStatus {
        val result = handshake.run(keyProvider())
        val capabilities = if (result is AnkiConnectHandshake.Status.Ready) {
            capabilitiesFor(result)
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

    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
    ): ArchiveTagSummary =
        removeArchivedSuspendedCards(snapshot, null, CollectionProgressListener.NONE)

    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        progress: CollectionProgressListener,
    ): ArchiveTagSummary = removeArchivedSuspendedCards(snapshot, null, progress)

    /**
     * Tags fully-suspended imported notes `kani_archived` so later syncs skip them.
     *
     * Which notes qualify is [ProviderArchiveCleanupPolicy]'s decision, not this
     * class's — the same policy AnkiDroid uses, so a collection archived on one
     * host and the same collection archived on the other tag the same notes. Only
     * the transport differs.
     *
     * A tag failure is reported, never thrown: the caller has already committed
     * the sync, and the local archive keeps whatever the provider would not take.
     */
    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        selectedSuspendedImports: List<RecordsImportModels.SuspendedImport>?,
        progress: CollectionProgressListener,
    ): ArchiveTagSummary {
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.ARCHIVING_IMPORTED_CARDS))
        val cleanup = ProviderArchiveCleanupPolicy.plan(
            snapshot.cards.map { card ->
                ProviderArchiveCleanupPolicy.Card(card.cardId, card.noteId, card.suspended)
            },
            selectedSuspendedCardIds(selectedSuspendedImports),
        )
        if (!cleanup.hasSuspendedCards()) {
            return ArchiveTagSummary(0, 0, 0, "No suspended cards needed provider cleanup.")
        }
        val outcome = tagWriter.addTag(ProviderNotePolicy.ARCHIVED_TAG, cleanup.notesToTag)
        return ArchiveTagSummary(
            cleanup.sourceCards,
            // Kani never deletes a note; archiving is a tag write only.
            0,
            outcome.tagged.size,
            ProviderArchiveCleanupPolicy.removalMessage(
                outcome.tagged.size,
                // A partially-suspended note was never eligible, so it counts
                // against the write the same way AnkiDroid counts it.
                outcome.failed.size + cleanup.alreadyFailedCards,
                PROVIDER_NAME,
            ),
        )
    }

    /**
     * Tags repaired notes `kani_repaired`, so the user can find them in Anki and
     * unsuspend the cards themselves.
     *
     * This gateway performs the write it is asked for; it does not decide whether
     * to ask. Repaired tagging is manual-confirm-only, and that confirmation lives
     * in the sync runner that calls this — the automatic post-sync runner is not
     * authorized to reach this method.
     */
    override fun tagRepairedNotes(
        noteIds: Set<Long>,
        progress: CollectionProgressListener,
    ): RepairedTagSummary {
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.TAGGING_REPAIRED))
        if (noteIds.isEmpty()) return RepairedTagSummary.noOp()
        val outcome = tagWriter.addTag(ProviderNotePolicy.REPAIRED_TAG, noteIds)
        if (outcome.requested == 0) return RepairedTagSummary.noOp()
        return RepairedTagSummary(
            outcome.tagged + outcome.failed,
            outcome.tagged,
            outcome.failed,
            ProviderNotePolicy.repairedTagMessage(
                outcome.tagged.size,
                outcome.failed.size,
                PROVIDER_NAME,
            ),
        )
    }

    /**
     * The suspended card ids the user actually selected for import, or null when
     * every suspended card counts. Mirrors `AnkiDroidArchiveCleanup`'s flattening
     * so the policy sees the same input shape from both providers.
     */
    private fun selectedSuspendedCardIds(
        imports: List<RecordsImportModels.SuspendedImport>?,
    ): Set<Long>? {
        if (imports == null) return null
        return ProviderArchiveCleanupPolicy.selectedSuspendedCardIds(
            imports.flatMap { imported ->
                imported.sources.map { source ->
                    ProviderArchiveCleanupPolicy.SelectedSource(source.cardId, source.suspended)
                }
            },
        )
    }

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
        /** The provider name user-facing write copy names. */
        const val PROVIDER_NAME: String = "Anki"

        /**
         * What any ready AnkiConnect can do. Notably absent:
         * [CollectionCapability.FSRS_MEMORY_STATE], because AnkiConnect exposes no
         * memory state at all — see [AnkiConnectCollectionReader].
         */
        @JvmField
        val READ_CAPABILITIES: Set<CollectionCapability> = setOf(
            CollectionCapability.READ_COLLECTION,
            CollectionCapability.LIST_NOTE_TYPES,
        )

        /** The AnkiConnect action Kani's tag writes need. */
        const val TAG_WRITE_ACTION: String = "addTags"

        /**
         * Read capabilities plus tag write, if and only if this Anki reported the
         * action that makes the tag write possible.
         */
        private fun capabilitiesFor(
            ready: AnkiConnectHandshake.Status.Ready,
        ): Set<CollectionCapability> = if (TAG_WRITE_ACTION in ready.availableOptionalActions) {
            READ_CAPABILITIES + CollectionCapability.NOTE_TAG_WRITE
        } else {
            READ_CAPABILITIES
        }
    }
}
