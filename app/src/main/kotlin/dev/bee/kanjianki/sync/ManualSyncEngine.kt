package dev.bee.kanjianki.sync

import dev.bee.kanjianki.application.ManualSyncQueuePlanner
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.platform.AppClock
import dev.bee.kanjianki.platform.AppLogger
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.SourceBindingReason

/**
 * Android compatibility facade for the platform-neutral engine.
 *
 * Existing Activity and Robolectric callers keep their stable result and test
 * hook APIs while production orchestration executes in :sync-engine.
 */
internal class ManualSyncEngine(
    syncUseCases: SyncUseCases,
    gateway: CollectionGateway,
    settingsSnapshot: SettingsSnapshot,
    progress: SyncProgress.Listener,
    clock: AppClock,
    assetReaders: SyncAssetReaders,
    queuePlannerFactory:
        (ReadingExposureModels.ExposureIndex) -> ManualSyncQueuePlanner,
    postCommitEffects: SyncPostCommitEffects,
    repairedWriteBackAuthorized: Boolean,
    confirmedRepairedNoteIds: Set<Long>?,
    sourceBindingGate: SyncSourceBindingGate = SyncSourceBindingGate.ALLOW_ALL,
    cancellation: SyncCancellation = SyncCancellation.NONE,
    logger: AppLogger = AppLogger.NONE,
) {
    private val delegate = PlatformNeutralSyncEngine(
        syncUseCases = syncUseCases,
        gateway = gateway,
        settingsSnapshot = settingsSnapshot,
        progress = progress,
        clock = clock,
        assetReaders = assetReaders,
        queuePlannerFactory = queuePlannerFactory,
        postCommitEffects = postCommitEffects,
        repairedWriteBackAuthorized = repairedWriteBackAuthorized,
        confirmedRepairedNoteIds = confirmedRepairedNoteIds,
        sourceBindingGate = sourceBindingGate,
        cancellation = cancellation,
        logger = logger,
    )

    internal var reminderRescheduler: Runnable
        get() = delegate.reminderRescheduler
        set(value) {
            delegate.reminderRescheduler = value
        }

    internal var widgetRefresher: Runnable
        get() = delegate.widgetRefresher
        set(value) {
            delegate.widgetRefresher = value
        }

    internal var removalMessagePersister: (Long, String?) -> Unit
        get() = delegate.removalMessagePersister
        set(value) {
            delegate.removalMessagePersister = value
        }

    internal var committedStudySummaryProvider:
        (List<RecordsImportModels.DashboardRow>, Long) -> CommittedStudySummary
        get() = { rows, countedAt ->
            delegate.committedStudySummaryProvider(rows, countedAt).toCompatibilitySummary()
        }
        set(value) {
            delegate.committedStudySummaryProvider = { rows, countedAt ->
                value(rows, countedAt).toPortableSummary()
            }
        }

    internal var repairedProposalProvider:
        (RecordsSyncModels.CollectionSnapshot, Int) -> RepairedWriteBackPolicy.Proposal
        get() = delegate.repairedProposalProvider
        set(value) {
            delegate.repairedProposalProvider = value
        }

    internal var repairedWriteBackRecorder:
        (RepairedWriteBackPolicy.Proposal, Set<Long>, Long, Long) -> List<String>
        get() = delegate.repairedWriteBackRecorder
        set(value) {
            delegate.repairedWriteBackRecorder = value
        }

    fun run(): SyncResult = delegate.run().toCompatibilityResult()

    class SyncResult private constructor(
        @JvmField val success: Boolean,
        @JvmField val skipped: Boolean,
        @JvmField val dashboardRows: Int,
        @JvmField val importedSuspendedKanji: Int,
        @JvmField val message: String?,
        adaptiveSummary: String?,
    ) {
        @JvmField
        val adaptiveSummary: String = adaptiveSummary ?: ""

        @JvmField
        var studyReadyCount: Int = 0

        @JvmField
        var adaptiveFocusText: String = ""

        @JvmField
        var retryable: Boolean = false

        @JvmField
        var sourceBindingReason: SourceBindingReason? = null

        @JvmField
        var sourceBindingEvidence: SourceBindingEvidence? = null

        companion object {
            @JvmStatic
            internal fun create(
                success: Boolean,
                skipped: Boolean,
                dashboardRows: Int,
                importedSuspendedKanji: Int,
                message: String?,
                adaptiveSummary: String?,
                studyReadyCount: Int = 0,
                adaptiveFocusText: String = "",
                retryable: Boolean = false,
                sourceBindingReason: SourceBindingReason? = null,
                sourceBindingEvidence: SourceBindingEvidence? = null,
            ): SyncResult {
                return SyncResult(
                    success,
                    skipped,
                    dashboardRows,
                    importedSuspendedKanji,
                    message,
                    adaptiveSummary,
                ).apply {
                    this.studyReadyCount = studyReadyCount
                    this.adaptiveFocusText = adaptiveFocusText
                    this.retryable = retryable
                    this.sourceBindingReason = sourceBindingReason
                    this.sourceBindingEvidence = sourceBindingEvidence
                }
            }
        }
    }

    internal data class CommittedStudySummary(
        val readyCount: Int,
        val focusPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    )

    companion object {
        @JvmStatic
        internal fun isRunning(): Boolean = PlatformNeutralSyncEngine.isRunning()
    }

    private fun PlatformNeutralSyncEngine.SyncResult.toCompatibilityResult(): SyncResult =
        SyncResult.create(
            success = success,
            skipped = skipped,
            dashboardRows = dashboardRows,
            importedSuspendedKanji = importedSuspendedKanji,
            message = message,
            adaptiveSummary = adaptiveSummary,
            studyReadyCount = studyReadyCount,
            adaptiveFocusText = adaptiveFocusText,
            retryable = retryable,
            sourceBindingReason = sourceBindingReason,
            sourceBindingEvidence = sourceBindingEvidence,
        )

    private fun PlatformNeutralSyncEngine.CommittedStudySummary.toCompatibilitySummary() =
        CommittedStudySummary(readyCount, focusPlan)

    private fun CommittedStudySummary.toPortableSummary() =
        PlatformNeutralSyncEngine.CommittedStudySummary(readyCount, focusPlan)
}
