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
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import dev.bee.kanjianki.domain.repository.SyncRunRepository
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlan
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanRequest
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudySettings
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedRequest
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeedSettings
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeeder
import kotlin.coroutines.cancellation.CancellationException

class RunSourceMirrorSyncUseCase(
    private val gateway: CollectionGateway,
    private val syncRuns: SyncRunRepository,
    private val sourceMirrorSync: SourceMirrorSyncRepository,
    private val importCandidateSelector: ImportCandidateSelector,
    private val dashboardBuilder: SyncDashboardBuilder,
    private val clock: AppClock,
    private val studyQueue: StudyQueueRepository? = null,
    private val queueSeeder: StudyQueueSeeder = StudyQueueSeeder(),
    private val adaptiveStudyPlanner: AdaptiveStudyPlanner = AdaptiveStudyPlanner(),
    private val executionGate: SyncExecutionGate = SyncExecutionGate(),
    private val archiveGateway: SuspendedCardArchiveGateway = NoOpSuspendedCardArchiveGateway,
) {
    suspend operator fun invoke(settings: ImportSettings): SyncRunId =
        invoke(RunSourceMirrorSyncRequest(importSettings = settings))

    suspend operator fun invoke(request: RunSourceMirrorSyncRequest): SyncRunId =
        executionGate.run { runUnlocked(request) }

    private suspend fun runUnlocked(request: RunSourceMirrorSyncRequest): SyncRunId {
        val startedAt = clock.nowMillis()
        return try {
            val settings = request.importSettings
            val progress = request.progress
            val snapshot = gateway.readCollection(settings, progress)
            progress.onSyncProgress(SyncProgressSnapshot.atStage(SyncProgressStage.PROCESSING_IMPORTED_CARDS))
            val importCandidates = importCandidateSelector.select(snapshot, settings)
            val dashboardRows = dashboardBuilder.build(importCandidates, settings)
            val finishedAt = clock.nowMillis()
            progress.onSyncProgress(SyncProgressSnapshot.atStage(SyncProgressStage.BUILDING_PRACTICE_QUEUE))
            val seededQueueItems = seedQueueItems(request, dashboardRows, finishedAt)
            val syncRunId = sourceMirrorSync.recordSuccessfulSnapshot(
                syncRun = successRun(startedAt, finishedAt, snapshot, importCandidates),
                notes = snapshot.notes,
                cards = snapshot.cards,
                importCandidates = importCandidates,
                dashboardRows = dashboardRows,
                settings = settings,
                seededQueueItems = seededQueueItems,
                similarKanjiIndex = request.similarKanjiIndex,
            )
            recordArchiveCleanup(syncRunId, snapshot, importCandidates, progress)
            syncRunId
        } catch (error: CollectionGatewayException) {
            val finishedAt = clock.nowMillis()
            syncRuns.insert(failureRun(startedAt, finishedAt, error))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val finishedAt = clock.nowMillis()
            syncRuns.insert(unexpectedFailureRun(startedAt, finishedAt, error))
        }
    }

    private suspend fun recordArchiveCleanup(
        syncRunId: SyncRunId,
        snapshot: CollectionSnapshot,
        importCandidates: List<ImportedKanjiCandidate>,
        progress: SyncProgressListener,
    ) {
        progress.onSyncProgress(SyncProgressSnapshot.atStage(SyncProgressStage.ARCHIVING_IMPORTED_CARDS))
        val message = try {
            archiveGateway.archiveSelectedSuspendedCards(snapshot, importCandidates).message
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            "Archived suspended cards were kept in the local archive; provider cleanup failed."
        }
        if (message.isBlank()) {
            return
        }
        try {
            val syncRun = syncRuns.get(syncRunId) ?: return
            syncRuns.update(syncRun.copy(removalMessage = message))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Local sync data is already committed; a cleanup-status update should not create a second failed run.
        }
    }

    private suspend fun seedQueueItems(
        request: RunSourceMirrorSyncRequest,
        dashboardRows: List<StudyDashboardRow>,
        nowMillis: Long,
    ): List<StudyQueueItem>? {
        val queueContext = request.queueSeedContext ?: return null
        val repository = requireNotNull(studyQueue) {
            "StudyQueueRepository is required when queueSeedContext is supplied."
        }
        val existingItems = repository.listAllForSeeding()
        val seedingRows = dashboardRows.filterNot { it.kanji in queueContext.locallySuspendedKanji }
        return queueSeeder.seed(
            StudyQueueSeedRequest(
                rows = seedingRows,
                existing = existingItems,
                settings = queueContext.settings,
                nowMillis = nowMillis,
                startOfDayMillis = queueContext.startOfDayMillis,
                adaptivePlan = adaptivePlan(request, queueContext, seedingRows, existingItems, nowMillis),
                ladderSettings = queueContext.ladderSettings,
            ),
        )
    }

    private fun adaptivePlan(
        request: RunSourceMirrorSyncRequest,
        queueContext: SyncStudyQueueSeedContext,
        seedingRows: List<StudyDashboardRow>,
        existingItems: List<StudyQueueItem>,
        nowMillis: Long,
    ): AdaptiveStudyPlan? {
        val adaptiveContext = queueContext.adaptiveContext ?: return null
        return adaptiveStudyPlanner.plan(
            AdaptiveStudyPlanRequest(
                rows = seedingRows,
                items = existingItems,
                recentStats = adaptiveContext.recentStats,
                currentStreakDays = adaptiveContext.currentStreakDays,
                studiedToday = adaptiveContext.studiedToday,
                workloadPolicy = adaptiveContext.workloadPolicy,
                nowMillis = nowMillis,
                settings = AdaptiveStudySettings(
                    matureDays = request.importSettings.matureDays,
                    matureSupportThreshold = request.importSettings.matureSupportThreshold,
                ),
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
    ): SyncRun = failureRun(
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = if (error.permanent) {
            SyncRunStatus.CONFIG_ERROR
        } else {
            SyncRunStatus.RETRYABLE_ERROR
        },
        errorCode = error.errorCode.wireName,
        errorMessage = error.message,
    )

    private fun unexpectedFailureRun(
        startedAt: Long,
        finishedAt: Long,
        error: Exception,
    ): SyncRun = failureRun(
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = SyncRunStatus.RETRYABLE_ERROR,
        errorCode = SyncErrorCode.UNEXPECTED.wireName,
        errorMessage = error.message ?: error::class.simpleName ?: "Unexpected sync failure.",
    )

    private fun failureRun(
        startedAt: Long,
        finishedAt: Long,
        status: SyncRunStatus,
        errorCode: String,
        errorMessage: String?,
    ): SyncRun = SyncRun(
        id = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        activeNotesCount = 0,
        activeCardsCount = 0,
        suspendedCardsArchivedCount = 0,
        suspendedKanjiImportedCount = 0,
        deletedNotesCount = 0,
        deletedCardsCount = 0,
        errorCode = errorCode,
        errorMessage = errorMessage,
        removalMessage = "",
    )

}

data class RunSourceMirrorSyncRequest(
    val importSettings: ImportSettings,
    val queueSeedContext: SyncStudyQueueSeedContext? = null,
    val similarKanjiIndex: SimilarKanjiIndex? = null,
    val progress: SyncProgressListener = NoOpSyncProgressListener,
)

data class SyncStudyQueueSeedContext(
    val settings: StudyQueueSeedSettings,
    val startOfDayMillis: Long,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
    val locallySuspendedKanji: Set<String> = emptySet(),
    val adaptiveContext: SyncAdaptivePlanContext? = null,
)

data class SyncAdaptivePlanContext(
    val recentStats: AdaptiveReviewStats = AdaptiveReviewStats(),
    val currentStreakDays: Int = 0,
    val studiedToday: Set<String> = emptySet(),
    val workloadPolicy: AdaptiveWorkloadPolicy =
        AdaptiveWorkloadPolicy.fromSettings(
            AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT,
            AdaptiveStudyPlanner.DEFAULT_WORKLOAD_MODE,
            AdaptiveStudyPlanner.DEFAULT_MAX_ITEMS,
        ),
)
