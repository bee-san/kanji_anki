package dev.bee.kanjianki.sync

import dev.bee.kanjianki.StudyNowCountCoordinator
import dev.bee.kanjianki.application.ManualSyncQueuePlanner
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.KanjiAnalyzer
import dev.bee.kanjianki.core.KanjiImportSelector
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.SuspendedImportPolicy
import dev.bee.kanjianki.core.StudyNowCountPolicy
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.data.RecordRepairedWriteBackCommand
import dev.bee.kanjianki.data.RecordSyncFailureCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncTimingSnapshot
import dev.bee.kanjianki.syncapi.ArchiveTagSummary
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.RepairedTagSummary
import dev.bee.kanjianki.syncapi.SourceBindingReason
import dev.bee.kanjianki.platform.AppClock
import dev.bee.kanjianki.platform.AppLogger
import dev.bee.kanjianki.platform.error
import dev.bee.kanjianki.platform.info
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class PlatformNeutralSyncEngine(
    private val syncUseCases: SyncUseCases,
    private val gateway: CollectionGateway,
    private val settingsSnapshot: SettingsSnapshot,
    private val progress: SyncProgress.Listener,
    private val clock: AppClock,
    private val assetReaders: SyncAssetReaders,
    private val queuePlannerFactory:
        (ReadingExposureModels.ExposureIndex) -> ManualSyncQueuePlanner,
    postCommitEffects: SyncPostCommitEffects,
    private val repairedWriteBackAuthorized: Boolean,
    confirmedRepairedNoteIds: Set<Long>?,
    private val sourceBindingGate: SyncSourceBindingGate = SyncSourceBindingGate.ALLOW_ALL,
    private val cancellation: SyncCancellation = SyncCancellation.NONE,
    private val logger: AppLogger = AppLogger.NONE,
) {
    private val settings: RecordsSyncModels.Settings = settingsSnapshot.sync
    private val confirmedRepairedNoteIds: Set<Long>? = confirmedRepairedNoteIds?.toSet()
    private val providerProgress = CollectionProgressListener { providerProgress ->
        progress.onSyncProgress(SyncProgress.fromCollection(providerProgress))
    }

    /**
     * Seam for the post-sync reminder re-arm (D4). Defaults to the real
     * scheduler; tests replace it to assert the success path re-arms and the
     * failure path does not.
     */
    @JvmField
    var reminderRescheduler: Runnable = postCommitEffects.reminderRescheduler

    /** Refreshes any installed widget from the same committed queue snapshot. */
    @JvmField
    var widgetRefresher: Runnable = postCommitEffects.widgetRefresher

    /** Persists the best-effort provider cleanup summary after the sync commit. */
    var removalMessagePersister: (Long, String?) -> Unit = { syncId, message ->
        runBlocking { syncUseCases.updateRemovalMessage(syncId, message) }
    }

    /** Re-reads the committed queue so the returned Study count includes mid-sync review merges. */
    var committedStudySummaryProvider:
        (List<RecordsImportModels.DashboardRow>, Long) -> CommittedStudySummary = { activeRows, countedAt ->
            committedStudySummary(activeRows, countedAt)
        }

    var repairedProposalProvider:
        (RecordsSyncModels.CollectionSnapshot, Int) -> RepairedWriteBackPolicy.Proposal =
        { snapshot, threshold ->
            runBlocking { syncUseCases.repairedWriteBackProposal(snapshot, threshold) }
        }

    var repairedWriteBackRecorder:
        (RepairedWriteBackPolicy.Proposal, Set<Long>, Long, Long) -> List<String> =
        { proposal, tagged, occurredAt, syncId ->
            runBlocking {
                syncUseCases.recordRepairedWriteBack(
                    RecordRepairedWriteBackCommand(
                        proposal,
                        tagged,
                        occurredAt,
                        syncId,
                    ),
                )
            }
        }

    fun run(): SyncResult {
        if (!RUNNING.compareAndSet(false, true)) {
            return SyncResult.skipped("Sync already running.", retryable = true)
        }
        try {
            return runLocked()
        } finally {
            RUNNING.set(false)
        }
    }

    private fun runLocked(): SyncResult {
        val started = clock.nowMillis()
        var committedState: CommittedSyncState? = null
        var committedMessage: String? = null
        safeLogInfo("sync start model=${settings.modelName}")
        try {
            ensureActive()
            val provider = gateway.readProviderCollection(settings, providerProgress, cancellation)
            ensureActive()
            val snapshot = ProviderCapabilityPolicy.normalize(provider).snapshot
            val storedState = runBlocking { syncUseCases.loadStoredState() }
            ensureActive()
            rejectTransientEmptySnapshot(snapshot, storedState)
            sourceBindingGate.requireAccess(provider, storedState, clock.nowMillis())
            ensureActive()
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS))
            val ranks = assetReaders.loadRanks()
            ensureActive()
            val selectedImports = KanjiImportSelector(
                ranks,
                settings.suspendedRankMin,
                settings.suspendedRankMax,
            ).importFrom(snapshot, settings)
            val currentSuspendedImports = SuspendedImportPolicy.suspendedImportsOnly(selectedImports)
            val storedSuspendedImports = if (settings.importSuspendedCards) {
                storedImportsWithDurableProviderRoute(snapshot, storedState)
            } else {
                emptyList()
            }
            val analysisImports = SuspendedImportPolicy.mergeSuspendedImports(
                storedSuspendedImports,
                selectedImports,
                settings,
            )
            val rows = KanjiAnalyzer().rebuildSelectedSources(snapshot, analysisImports, ranks, settings)
            ensureActive()
            val similarKanjiIndex = assetReaders.loadSimilarKanjiIndex()
            ensureActive()
            val dictionary = assetReaders.loadDictionary()
            ensureActive()
            val queuePlanner = queuePlannerFactory(assetReaders.loadReadingExposure())
            ensureActive()
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.SAVING_LOCAL_DATA))
            val finished = clock.nowMillis()
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE))
            ensureActive()
            val publication = runBlocking {
                syncUseCases.publish(
                    SyncPublicationCommand(
                        snapshot = snapshot,
                        imports = currentSuspendedImports,
                        auditImports = selectedImports,
                        rows = rows,
                        settings = settings,
                        timing = SyncTimingSnapshot(started, finished),
                        removalMessage = null,
                        similarIndex = similarKanjiIndex,
                        dictionary = dictionary,
                        queuePlanner = queuePlanner,
                    ),
                )
            }
            val syncId = publication.syncId
            val activeRows = publication.activeRows
            val plan = publication.adaptiveLoadPlan
            committedState = CommittedSyncState(rows.size, currentSuspendedImports.size, plan)
            // A sync replaces the whole study queue: cards can land newly overdue or
            // the queue can empty. Re-arm the reminder from fresh state so the alarm
            // timing tracks the new queue instead of a stale pre-sync schedule (D4).
            runPostCommitSideEffect("Could not re-arm reminders after committed sync") {
                reminderRescheduler.run()
            }
            runPostCommitSideEffect("Could not refresh widget after committed sync") {
                widgetRefresher.run()
            }

            // Provider tagging runs after all local persistence so a tagging
            // failure cannot strand a committed sync mirror alongside stale
            // study items. Tagging is re-attempted on the next sync, so a
            // failure here degrades to a warning instead of a failed sync.
            val removal = try {
                ensureActive()
                sourceBindingGate.requireAccess(provider, storedState, clock.nowMillis())
                ensureActive()
                gateway.removeArchivedSuspendedCards(
                    snapshot,
                    currentSuspendedImports,
                    providerProgress,
                )
            } catch (error: Exception) {
                logPostCommitFailure("Could not archive imported notes after committed sync", error)
                ArchiveTagSummary(
                    0,
                    0,
                    0,
                    "Archive tagging could not finish and will retry on the next sync.",
                )
            }
            var syncMessage = removal.message
            committedMessage = syncMessage
            try {
                if (repairedWriteBackAuthorized && settingsSnapshot.tagRepairedCards) {
                    val proposal = try {
                        repairedProposalProvider(snapshot, settings.matureSupportThreshold)
                    } catch (error: Exception) {
                        logPostCommitFailure("Could not prepare repaired-note write-back; retrying next sync", error)
                        syncMessage = appendSyncMessage(
                            syncMessage,
                            "Repaired-note tagging could not be prepared and will retry on the next sync.",
                        )
                        null
                    }
                    val confirmedProposal = proposal?.let(::confirmedProposal)
                    if (confirmedProposal != null && !confirmedProposal.isEmpty()) {
                        val tagging = try {
                            ensureActive()
                            sourceBindingGate.requireAccess(provider, storedState, clock.nowMillis())
                            ensureActive()
                            gateway.tagRepairedNotes(
                                confirmedProposal.noteIdsToTag,
                                providerProgress,
                            )
                        } catch (error: Exception) {
                            logPostCommitFailure("Could not tag repaired notes; retrying next sync", error)
                            RepairedTagSummary(
                                confirmedProposal.noteIdsToTag,
                                emptySet(),
                                confirmedProposal.noteIdsToTag,
                                "Repaired-note tagging could not finish and will retry on the next sync.",
                            )
                        }
                        var stampFailureMessage = ""
                        try {
                            repairedWriteBackRecorder(
                                confirmedProposal,
                                tagging.taggedNoteIds,
                                clock.nowMillis(),
                                syncId,
                            )
                        } catch (error: Exception) {
                            logPostCommitFailure("Could not stamp repaired-note write-back; retrying next sync", error)
                            stampFailureMessage =
                                "Repaired-note confirmation could not be saved and will retry on the next sync."
                        }
                        syncMessage = appendSyncMessage(syncMessage, tagging.message)
                        syncMessage = appendSyncMessage(syncMessage, stampFailureMessage)
                    }
                }
            } catch (error: Exception) {
                logPostCommitFailure("Could not finish repaired-note write-back after committed sync", error)
                syncMessage = appendSyncMessage(
                    syncMessage,
                    "Repaired-note tagging could not finish and will retry on the next sync.",
                )
            }
            committedMessage = syncMessage
            runPostCommitSideEffect("Could not save provider cleanup summary after committed sync") {
                removalMessagePersister(syncId, syncMessage)
            }

            val committedSummary = try {
                committedStudySummaryProvider(activeRows, clock.nowMillis())
            } catch (error: Exception) {
                logPostCommitFailure("Could not build exact Study summary after committed sync", error)
                CommittedStudySummary(0, plan)
            }
            runPostCommitSideEffect("Could not log committed sync completion") {
                safeLogInfo(
                    "sync success duration_ms=${clock.nowMillis() - started} rows=${rows.size} " +
                        "suspended_imports=${currentSuspendedImports.size} ready=${committedSummary.readyCount}",
                )
            }
            return SyncResult.create(
                true,
                false,
                rows.size,
                currentSuspendedImports.size,
                syncMessage,
                committedSummary.focusPlan?.status ?: plan.status,
                committedSummary.readyCount,
                AdaptiveFocusCopy.adaptiveFocusText(committedSummary.focusPlan),
            )
        } catch (error: CollectionFailure) {
            committedState?.let { committed ->
                return committedFailureResult(committed, committedMessage, error)
            }
            safeLogError(
                "sync failed (${if (error.retryable) "retryable" else "permanent"})",
                error,
            )
            val finished = clock.nowMillis()
            val sourceBindingReason = (error as? SourceBindingFailure)?.reason
            val sourceBindingEvidence = (error as? SourceBindingFailure)?.evidence
            persistFailedSync(
                started,
                finished,
                if (error.retryable) "retryable_error" else "config_error",
                when {
                    sourceBindingReason != null ->
                        "source_binding_${sourceBindingReason.name.lowercase()}"
                    error.retryable -> "retryable"
                    else -> "permanent"
                },
                error,
            )
            return SyncResult.create(
                false,
                false,
                0,
                0,
                error.message,
                "",
                retryable = error.retryable,
                sourceBindingReason = sourceBindingReason,
                sourceBindingEvidence = sourceBindingEvidence,
            )
        } catch (error: Exception) {
            committedState?.let { committed ->
                return committedFailureResult(committed, committedMessage, error)
            }
            // Only Exceptions are treated as recoverable sync failures. Errors
            // (OutOfMemoryError, StackOverflowError, ...) propagate instead of being
            // mislabeled as a retryable_error sync row.
            safeLogError("sync failed (unexpected)", error)
            val finished = clock.nowMillis()
            persistFailedSync(started, finished, "retryable_error", "unexpected", error)
            return SyncResult.create(false, false, 0, 0, error.message, "")
        }
    }

    private fun committedFailureResult(
        committed: CommittedSyncState,
        message: String?,
        error: Exception,
    ): SyncResult {
        logPostCommitFailure("Post-commit sync follow-up failed; retaining successful sync", error)
        return SyncResult.create(
            true,
            false,
            committed.dashboardRows,
            committed.importedSuspendedKanji,
            message,
            committed.preCommitPlan.status,
            0,
            AdaptiveFocusCopy.adaptiveFocusText(committed.preCommitPlan),
        )
    }

    private fun committedStudySummary(
        activeRows: List<RecordsImportModels.DashboardRow>,
        countedAt: Long,
    ): CommittedStudySummary {
        // The atomic queue commit can merge a review saved while sync was in flight;
        // derive the post-sync plan/count from the committed queue, not the stale
        // pre-merge seeded snapshot.
        val queue = runBlocking { syncUseCases.loadCommittedStudyQueue(countedAt) }
        val activeKanji = activeRows.mapTo(HashSet()) { it.kanji }
        val postSyncItems = if (activeRows.isEmpty()) {
            emptyList()
        } else {
            runBlocking { syncUseCases.loadCommittedStudyItems(activeKanji) }
        }
        val planner = queuePlannerFactory(assetReaders.loadReadingExposure())
        val replan = { items: List<RecordsStudyModels.StudyItem> ->
            planner.adaptivePlan(
                rows = activeRows,
                items = items,
                settings = settings,
                workload = queue.adaptiveWorkload,
                recentReviewStats = queue.recentReviewStats,
                currentStudyStreakDays = queue.studyStreak.currentDays,
                studiedKanjiToday = queue.studiedKanjiToday,
                nowMillis = countedAt,
            )
        }
        val postSyncPlan = if (activeRows.isEmpty()) {
            null
        } else {
            replan(postSyncItems)
        }
        val ladder = queue.studyLadder
        val studyNow = StudyNowCountCoordinator.count(
            StudyNowCountCoordinator.Request(
                queue = StudyNowCountCoordinator.QueueInput(activeRows, postSyncItems, settings, ladder),
                timing = StudyNowCountCoordinator.Timing(
                    countedAt,
                    startOfDay(countedAt),
                    queue.studyAheadMinutes * 60_000L,
                ),
                mode = StudyNowCountCoordinator.Mode(postSyncPlan, false),
                pipeline = StudyNowCountCoordinator.Pipeline(
                    scheduler = BridgeScheduler.withWeights(
                        queue.schedulerFsrsWeights?.toDoubleArray(),
                    ),
                    annotator = { items ->
                        runBlocking { syncUseCases.annotateCapabilities(items) }
                    },
                    replanner = replan,
                ),
            ),
        )
        val repairTaskKeys = if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            queue.dueLegacyWritingRepairs
                .map(StudySessionProgressTracker::similarRepairProgressKey)
        } else {
            emptyList()
        }
        return CommittedStudySummary(
            StudyNowCountPolicy.includingAdditionalTaskKeys(studyNow.studyItemCount, repairTaskKeys),
            studyNow.effectivePlan,
        )
    }

    private fun runPostCommitSideEffect(description: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            logPostCommitFailure(description, error)
        }
    }

    private fun logPostCommitFailure(description: String, error: Exception) {
        safeLogError(description, error)
    }

    private fun confirmedProposal(
        proposal: RepairedWriteBackPolicy.Proposal,
    ): RepairedWriteBackPolicy.Proposal {
        val confirmed = confirmedRepairedNoteIds ?: return proposal
        val noteIds = proposal.noteIdsToTag.intersect(confirmed)
        if (noteIds.isEmpty()) {
            return proposal.copy(
                noteIdsToTag = emptySet(),
                cardIdsByNote = emptyMap(),
                kanjiByNote = emptyMap(),
                repairedKanji = emptyList(),
                candidateSourceCount = 0,
            )
        }
        val cards = proposal.cardIdsByNote.filterKeys { it in noteIds }
        val kanji = proposal.kanjiByNote.filterKeys { it in noteIds }
        return proposal.copy(
            noteIdsToTag = noteIds,
            cardIdsByNote = cards,
            kanjiByNote = kanji,
            repairedKanji = kanji.values.flatten().distinct().sorted(),
            candidateSourceCount = cards.values.sumOf { it.size },
        )
    }

    /**
     * Persist a failed-sync row, guarding the write itself: if saveFailedSync throws
     * (e.g. disk full) the persistence failure is logged with the original error
     * attached as suppressed so the root cause is not masked.
     */
    private fun persistFailedSync(
        started: Long,
        finished: Long,
        status: String,
        errorCode: String,
        error: Throwable,
    ) {
        try {
            runBlocking {
                syncUseCases.recordFailure(
                    RecordSyncFailureCommand(
                        startedAtMillis = started,
                        finishedAtMillis = finished,
                        status = status,
                        errorCode = errorCode,
                        errorMessage = error.message,
                    ),
                )
            }
        } catch (persistError: Exception) {
            persistError.addSuppressed(error)
            safeLogError("Failed to persist sync failure row.", persistError)
        }
    }

    private fun storedImportsWithDurableProviderRoute(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        storedState: StoredSyncState,
    ): List<RecordsImportModels.SuspendedImport> {
        val imports = storedState.suspendedImports
        if (imports.isEmpty()) return emptyList()
        val durableCardIds = snapshot.cards.mapTo(HashSet<Long>()) { it.cardId }
        durableCardIds.addAll(storedState.unrestoredSuspendedArchiveCardIds)
        return imports.filter { imported ->
            imported.sources.any { source -> source.cardId in durableCardIds }
        }
    }

    private fun rejectTransientEmptySnapshot(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        storedState: StoredSyncState,
    ) {
        val incomplete = snapshot.notes.isEmpty() || snapshot.cards.isEmpty()
        val hasDurableLocalState =
            storedState.studyItems.isNotEmpty() || storedState.hasCollectionMirror
        if (incomplete && hasDurableLocalState) {
            throw CollectionFailure(
                dev.bee.kanjianki.syncapi.CollectionFailureKind.TRANSIENT,
                "The provider returned an incomplete collection; existing study progress was preserved.",
            )
        }
    }

    private fun startOfDay(now: Long): Long {
        return LocalDayPolicy.localDayStart(now)
    }

    private fun ensureActive() {
        if (cancellation.isStopped()) {
            throw CollectionFailure.cancelled("Sync cancelled.")
        }
    }

    private fun safeLogInfo(message: String) {
        try {
            logger.info(message)
        } catch (_: RuntimeException) {
            // Diagnostics must never change sync outcomes.
        }
    }

    private fun safeLogError(message: String, error: Throwable) {
        try {
            logger.error(message, error)
        } catch (_: RuntimeException) {
            // Diagnostics must never change sync outcomes.
        }
    }

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

        /**
         * True only when another automatic attempt can reasonably succeed without
         * user intervention. Unexpected runtime failures stay terminal even though
         * their historical sync-run row retains the legacy `retryable_error` label.
         */
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

            @JvmStatic
            internal fun skipped(message: String, retryable: Boolean = false): SyncResult {
                return SyncResult(false, true, 0, 0, message, "").apply {
                    this.retryable = retryable
                }
            }
        }
    }

    data class CommittedStudySummary(
        val readyCount: Int,
        val focusPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    )

    private data class CommittedSyncState(
        val dashboardRows: Int,
        val importedSuspendedKanji: Int,
        val preCommitPlan: RecordsSchedulerModels.AdaptiveLoadPlan,
    )

    companion object {
        private val RUNNING = AtomicBoolean(false)

        @JvmStatic
        fun isRunning(): Boolean = RUNNING.get()

        private fun appendSyncMessage(current: String, addition: String): String =
            listOf(current, addition).filter(String::isNotBlank).joinToString(" ")
    }
}
