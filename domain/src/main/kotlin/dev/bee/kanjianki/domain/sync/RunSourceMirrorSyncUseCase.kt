package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.importing.ImportCandidateSelector
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlan
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedRequest
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedSettings
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeeder

class RunSourceMirrorSyncUseCase(
    private val gateway: CollectionGateway,
    private val syncRuns: SyncRunRepository,
    private val sourceMirrorSync: SourceMirrorSyncRepository,
    private val importCandidateSelector: ImportCandidateSelector,
    private val dashboardBuilder: SyncDashboardBuilder,
    private val clock: AppClock,
    private val studyQueue: StudyQueueRepository? = null,
    private val queueSeeder: StudyQueueSeeder = StudyQueueSeeder(),
) {
    suspend operator fun invoke(settings: ImportSettings): SyncRunId =
        invoke(RunSourceMirrorSyncRequest(importSettings = settings))

    suspend operator fun invoke(request: RunSourceMirrorSyncRequest): SyncRunId {
        val startedAt = clock.nowMillis()
        return try {
            val settings = request.importSettings
            val snapshot = gateway.readCollection(settings)
            val importCandidates = importCandidateSelector.select(snapshot, settings)
            val dashboardRows = dashboardBuilder.build(importCandidates, settings)
            val finishedAt = clock.nowMillis()
            val seededQueueItems = seedQueueItems(request, dashboardRows, finishedAt)
            sourceMirrorSync.recordSuccessfulSnapshot(
                syncRun = successRun(startedAt, finishedAt, snapshot, importCandidates),
                notes = snapshot.notes,
                cards = snapshot.cards,
                importCandidates = importCandidates,
                dashboardRows = dashboardRows,
                settings = settings,
                seededQueueItems = seededQueueItems,
                similarKanjiIndex = request.similarKanjiIndex,
            )
        } catch (error: CollectionGatewayException) {
            val finishedAt = clock.nowMillis()
            syncRuns.insert(failureRun(startedAt, finishedAt, error))
        }
    }

    private suspend fun seedQueueItems(
        request: RunSourceMirrorSyncRequest,
        dashboardRows: List<StudyDashboardRow>,
        nowMillis: Long,
    ): List<StudyQueueItem>? {
        val queueSettings = request.queueSeedSettings ?: return null
        val repository = requireNotNull(studyQueue) {
            "StudyQueueRepository is required when queueSeedSettings are supplied."
        }
        return queueSeeder.seed(
            StudyQueueSeedRequest(
                rows = dashboardRows,
                existing = repository.listAllForSeeding(),
                settings = queueSettings,
                nowMillis = nowMillis,
                startOfDayMillis = request.startOfDayMillis,
                adaptivePlan = request.adaptivePlan,
                ladderSettings = request.ladderSettings,
            ),
        )
    }

    private fun successRun(
        startedAt: Long,
        finishedAt: Long,
        snapshot: CollectionSnapshot,
        importCandidates: List<ImportedKanjiCandidate>,
    ): SyncRun = SyncRun(
        id = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = SyncRunStatus.SUCCESS,
        activeNotesCount = snapshot.cards.filter(SourceCard::active).mapTo(mutableSetOf()) { it.noteId }.size,
        activeCardsCount = snapshot.cards.count(SourceCard::active),
        suspendedCardsArchivedCount = importCandidates.suspendedCardIds().size,
        suspendedKanjiImportedCount = importCandidates.count { it.hasSuspendedSource() },
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = null,
        errorMessage = null,
        removalMessage = "",
    )

    private fun List<ImportedKanjiCandidate>.suspendedCardIds() =
        flatMap { candidate ->
            candidate.sources.filter { it.suspended }.map { it.cardId }
        }.toSet()

    private fun ImportedKanjiCandidate.hasSuspendedSource(): Boolean =
        sources.any { it.suspended }

    private fun failureRun(
        startedAt: Long,
        finishedAt: Long,
        error: CollectionGatewayException,
    ): SyncRun = SyncRun(
        id = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = if (error.permanent) {
            SyncRunStatus.CONFIG_ERROR
        } else {
            SyncRunStatus.RETRYABLE_ERROR
        },
        activeNotesCount = 0,
        activeCardsCount = 0,
        suspendedCardsArchivedCount = 0,
        suspendedKanjiImportedCount = 0,
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = error.errorCode.wireName,
        errorMessage = error.message,
        removalMessage = "",
    )

}

data class RunSourceMirrorSyncRequest(
    val importSettings: ImportSettings,
    val queueSeedSettings: StudyQueueSeedSettings? = null,
    val startOfDayMillis: Long = 0L,
    val adaptivePlan: AdaptiveStudyPlan? = null,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
    val similarKanjiIndex: SimilarKanjiIndex? = null,
)
