package dev.bee.kanjianki.syncapi.testing

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.ArchiveTagSummary
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.CollectionSourceIdentity
import dev.bee.kanjianki.syncapi.CollectionSourceStatus
import dev.bee.kanjianki.syncapi.NoteTypeDescriptor
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot

object CanonicalCollectionFixture {
    @JvmField
    val settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults()

    @JvmField
    val snapshot: RecordsSyncModels.CollectionSnapshot =
        RecordsSyncModels.CollectionSnapshot(
            listOf(
                RecordsSyncModels.Note(
                    101L,
                    7L,
                    "Kiku",
                    mapOf(
                        "Expression" to "橋",
                        "Reading" to "はし",
                        "Meaning" to "bridge",
                        "Sentence" to "橋を渡る。",
                    ),
                    listOf("fixture"),
                ),
            ),
            listOf(
                RecordsSyncModels.Card(
                    201L,
                    101L,
                    0,
                    "Kiku",
                    2,
                    2,
                    0,
                    30,
                    10,
                    1,
                    false,
                    12.5,
                    7.0,
                    0.9,
                ),
            ),
        )

    @JvmField
    val identity: CollectionSourceIdentity =
        CollectionSourceIdentity.create(
            CollectionProviderKind.TEST,
            "canonical-fixture",
            listOf(101L),
            listOf(201L),
        )

    @JvmField
    val capabilities: Set<CollectionCapability> =
        setOf(
            CollectionCapability.READ_COLLECTION,
            CollectionCapability.LIST_NOTE_TYPES,
            CollectionCapability.FSRS_MEMORY_STATE,
            CollectionCapability.SOURCE_IDENTITY,
        )
}

class CanonicalCollectionGateway : CollectionGateway {
    override fun status(): CollectionSourceStatus =
        CollectionSourceStatus.ready(CanonicalCollectionFixture.capabilities)

    override fun noteTypes(): List<NoteTypeDescriptor> =
        listOf(
            NoteTypeDescriptor(
                7L,
                "Kiku",
                listOf("Expression", "Reading", "Meaning", "Sentence"),
            ),
        )

    override fun readCollection(
        settings: RecordsSyncModels.Settings,
    ): RecordsSyncModels.CollectionSnapshot = CanonicalCollectionFixture.snapshot

    override fun readCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener,
    ): RecordsSyncModels.CollectionSnapshot {
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.FINDING_NOTE_TYPE))
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.READING_NOTES))
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.SCANNING_CARDS, 1, 1))
        return CanonicalCollectionFixture.snapshot
    }

    override fun readProviderCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener,
        cancellation: dev.bee.kanjianki.syncapi.CollectionCancellation,
    ): ProviderCollectionSnapshot {
        if (cancellation.isCancelled()) {
            throw dev.bee.kanjianki.syncapi.CollectionFailure.cancelled()
        }
        return ProviderCollectionSnapshot(
            readCollection(settings, progress),
            CanonicalCollectionFixture.capabilities,
            CanonicalCollectionFixture.identity,
        )
    }

    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
    ): ArchiveTagSummary = ArchiveTagSummary(0, 0, 0, "No fixture write needed.")
}
