package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.sync.SyncRun

fun interface StudyQueueSeedBuilder {
    suspend fun seed(existingItems: List<StudyQueueItem>): List<StudyQueueItem>
}

interface SourceMirrorSyncRepository {
    suspend fun retainedSuspendedImportCandidates(settings: ImportSettings): List<ImportedKanjiCandidate> =
        emptyList()

    suspend fun recordSuccessfulSnapshot(
        syncRun: SyncRun,
        notes: List<SourceNote>,
        cards: List<SourceCard>,
        importCandidates: List<ImportedKanjiCandidate>,
        dashboardRows: List<StudyDashboardRow>,
        settings: ImportSettings,
        queueSeedBuilder: StudyQueueSeedBuilder? = null,
        similarKanjiIndex: SimilarKanjiIndex? = null,
    ): SyncRunId
}
