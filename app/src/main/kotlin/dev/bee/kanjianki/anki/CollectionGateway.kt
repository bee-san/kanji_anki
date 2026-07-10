package dev.bee.kanjianki.anki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.sync.SyncProgress

interface CollectionGateway {
    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot

    @Throws(AnkiDroidGateway.SyncFailure::class)
    fun readCollection(
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener?,
    ): RecordsSyncModels.CollectionSnapshot {
        return readCollection(settings)
    }

    fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary

    fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        progress: SyncProgress.Listener?,
    ): AnkiDroidGateway.RemovalSummary {
        return removeArchivedSuspendedCards(snapshot)
    }

    fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        selectedSuspendedImports: List<RecordsImportModels.SuspendedImport>?,
        progress: SyncProgress.Listener?,
    ): AnkiDroidGateway.RemovalSummary {
        return removeArchivedSuspendedCards(snapshot, progress)
    }

    /**
     * Optional note-tag write-back. The default deliberately does nothing so test
     * gateways and non-Anki providers remain source-compatible.
     */
    fun tagRepairedNotes(
        noteIds: Set<Long>,
        progress: SyncProgress.Listener?,
    ): RepairedTagSummary = RepairedTagSummary.noOp()
}

data class RepairedTagSummary(
    val requestedNoteIds: Set<Long>,
    val taggedNoteIds: Set<Long>,
    val failedNoteIds: Set<Long>,
    val message: String,
) {
    companion object {
        @JvmStatic
        fun noOp(): RepairedTagSummary = RepairedTagSummary(
            emptySet(),
            emptySet(),
            emptySet(),
            "No repaired notes needed provider tagging.",
        )
    }
}
