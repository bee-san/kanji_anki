package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncRun

interface SourceMirrorSyncRepository {
    suspend fun recordSuccessfulSnapshot(
        syncRun: SyncRun,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        importCandidates: List<ImportedKanjiCandidate>,
        settings: ImportSettings,
    ): SyncRunId
}
