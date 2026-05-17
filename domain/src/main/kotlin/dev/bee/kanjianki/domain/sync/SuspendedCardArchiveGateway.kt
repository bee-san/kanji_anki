package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate

interface SuspendedCardArchiveGateway {
    suspend fun archiveSelectedSuspendedCards(
        snapshot: CollectionSnapshot,
        importCandidates: List<ImportedKanjiCandidate>,
    ): SuspendedCardArchiveSummary
}

data class SuspendedCardArchiveSummary(
    val sourceCards: Int,
    val taggedNotes: Int,
    val message: String,
) {
    init {
        require(sourceCards >= 0) { "sourceCards must be non-negative" }
        require(taggedNotes >= 0) { "taggedNotes must be non-negative" }
    }
}

object NoOpSuspendedCardArchiveGateway : SuspendedCardArchiveGateway {
    override suspend fun archiveSelectedSuspendedCards(
        snapshot: CollectionSnapshot,
        importCandidates: List<ImportedKanjiCandidate>,
    ): SuspendedCardArchiveSummary = SuspendedCardArchiveSummary(0, 0, "")
}
