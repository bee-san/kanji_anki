package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex

/** Atomic provider-mirror, queue-publication, history, and write-back storage. */
interface SyncRepository {
    suspend fun loadStoredState(): StoreResult<StoredSyncState>

    /**
     * Stages the provider mirror, invokes [SyncPublicationCommand.queuePlanner]
     * on an immutable staged snapshot, and publishes queue/history as one
     * transaction.
     */
    suspend fun publish(command: SyncPublicationCommand): StoreResult<SyncPublicationResult>

    suspend fun recordFailure(command: RecordSyncFailureCommand): StoreResult<Unit>

    suspend fun updateRemovalMessage(syncId: Long, message: String?): StoreResult<Unit>

    suspend fun repairedWriteBackProposal(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        matureSupportThreshold: Int,
    ): StoreResult<RepairedWriteBackPolicy.Proposal>

    suspend fun repairedWriteBackPreview(
        matureSupportThreshold: Int,
    ): StoreResult<RepairedWriteBackPolicy.Proposal>

    suspend fun recordRepairedWriteBack(
        command: RecordRepairedWriteBackCommand,
    ): StoreResult<List<String>>

    suspend fun loadRepairedHandoff(): StoreResult<List<String>>

    suspend fun dismissRepairedHandoff(): StoreResult<Unit>
}

data class StoredSyncState(
    val hasCollectionMirror: Boolean,
    val suspendedImports: List<RecordsImportModels.SuspendedImport>,
    val unrestoredSuspendedArchiveCardIds: Set<Long>,
    val studyItems: List<RecordsStudyModels.StudyItem>,
    val latestSuccessfulSyncAtMillis: Long?,
    val mirrorIdentityEvidence: CollectionMirrorIdentityEvidence =
        CollectionMirrorIdentityEvidence.EMPTY,
    val databaseIsEmpty: Boolean =
        !hasCollectionMirror &&
            suspendedImports.isEmpty() &&
            unrestoredSuspendedArchiveCardIds.isEmpty() &&
            studyItems.isEmpty() &&
            latestSuccessfulSyncAtMillis == null,
)

data class CollectionMirrorIdentityEvidence(
    val stableNoteIds: List<Long>,
    val stableCardIds: List<Long>,
) {
    companion object {
        @JvmField
        val EMPTY = CollectionMirrorIdentityEvidence(emptyList(), emptyList())
    }
}

data class SyncPublicationCommand(
    val snapshot: RecordsSyncModels.CollectionSnapshot,
    val imports: List<RecordsImportModels.SuspendedImport>,
    val auditImports: List<RecordsImportModels.SuspendedImport>,
    val rows: List<RecordsImportModels.DashboardRow>,
    val settings: RecordsSyncModels.Settings,
    val timing: SyncTimingSnapshot,
    val removalMessage: String?,
    val similarIndex: SimilarKanjiIndex?,
    val dictionary: DictionaryLookup?,
    val queuePlanner: SyncQueuePlanner,
)

data class SyncTimingSnapshot(
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
)

/**
 * Pure queue derivation executed while publication is open. Implementations
 * must not perform provider, repository, or other blocking I/O.
 */
fun interface SyncQueuePlanner {
    fun plan(snapshot: SyncQueuePlanningSnapshot): SyncQueuePlan
}

data class SyncQueuePlanningSnapshot(
    /** Provider-derived rows used for sync evidence classification. */
    val providerRows: List<RecordsImportModels.DashboardRow>,
    /** Provider rows plus durable manual dictionary sources used for queue reconciliation. */
    val rows: List<RecordsImportModels.DashboardRow>,
    val activeRows: List<RecordsImportModels.DashboardRow>,
    val currentItems: List<RecordsStudyModels.StudyItem>,
    val locallySuspendedKanji: Set<String>,
    val settings: RecordsSyncModels.Settings,
    val repairEvidenceInputs: List<KanjiRepairEvidencePolicy.Input>,
    val studyLadder: RecordsBase.StudyLadderSettings,
    val schedulerParameters: RecordsSchedulerModels.SchedulerParameters,
    val schedulerFsrsWeights: List<Double>?,
    val learningSteps: RecordsSchedulerModels.LearningStepSettings,
    val adaptiveWorkload: AdaptiveWorkloadSnapshot,
    val recentReviewStats: RecordsSchedulerModels.ReviewStats,
    val currentStudyStreakDays: Int,
    val studiedKanjiToday: Set<String>,
    val syncStartedAtMillis: Long,
    val nowMillis: Long,
)

data class SyncQueuePlan(
    val items: List<RecordsStudyModels.StudyItem>,
    val adaptiveLoadPlan: RecordsSchedulerModels.AdaptiveLoadPlan,
)

data class SyncPublicationResult(
    val syncId: Long,
    val activeRows: List<RecordsImportModels.DashboardRow>,
    val adaptiveLoadPlan: RecordsSchedulerModels.AdaptiveLoadPlan,
)

data class RecordSyncFailureCommand(
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val status: String,
    val errorCode: String,
    val errorMessage: String?,
)

data class RecordRepairedWriteBackCommand(
    val proposal: RepairedWriteBackPolicy.Proposal,
    val taggedNoteIds: Set<Long>,
    val occurredAtMillis: Long,
    val syncId: Long,
)
