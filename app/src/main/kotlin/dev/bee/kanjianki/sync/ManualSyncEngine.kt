package dev.bee.kanjianki.sync

import android.content.Context
import android.util.Log
import dev.bee.kanjianki.AppDebugLog
import dev.bee.kanjianki.R
import dev.bee.kanjianki.ReadingExposureMediaReader
import dev.bee.kanjianki.StudyNowCountCoordinator
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.KanjiAnalyzer
import dev.bee.kanjianki.core.KanjiImportSelector
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SuspendedImportPolicy
import dev.bee.kanjianki.core.StudyNowCountPolicy
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.data.DictionaryStore
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.recordRepairedWriteBack
import dev.bee.kanjianki.data.repairedWriteBackProposal
import dev.bee.kanjianki.time.AppClock
import dev.bee.kanjianki.widget.KaniWidgetUpdater
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

internal class ManualSyncEngine {
    private val context: Context
    private val store: LocalStore
    private val gateway: CollectionGateway
    private val settings: RecordsSyncModels.Settings
    private val progress: SyncProgress.Listener
    private val clock: AppClock
    private val repairedWriteBackAuthorized: Boolean
    private val confirmedRepairedNoteIds: Set<Long>?

    /**
     * Seam for the post-sync reminder re-arm (D4). Defaults to the real
     * scheduler; tests replace it to assert the success path re-arms and the
     * failure path does not.
     */
    @JvmField
    internal var reminderRescheduler: Runnable = Runnable {
        dev.bee.kanjianki.reminders.ReminderScheduler.schedule(context)
    }

    /** Refreshes any installed widget from the same committed queue snapshot. */
    @JvmField
    internal var widgetRefresher: Runnable = Runnable {
        KaniWidgetUpdater.requestUpdate(context)
    }

    /** Persists the best-effort provider cleanup summary after the sync commit. */
    internal var removalMessagePersister: (Long, String?) -> Unit = { syncId, message ->
        store.updateSyncRemovalMessage(syncId, message)
    }

    /** Re-reads the committed queue so the returned Study count includes mid-sync review merges. */
    internal var committedStudySummaryProvider:
        (List<RecordsImportModels.DashboardRow>, Long) -> CommittedStudySummary = { activeRows, countedAt ->
            committedStudySummary(activeRows, countedAt)
        }

    internal var repairedProposalProvider:
        (RecordsSyncModels.CollectionSnapshot, Int) -> RepairedWriteBackPolicy.Proposal =
        { snapshot, threshold -> store.repairedWriteBackProposal(snapshot, threshold) }

    internal var repairedWriteBackRecorder:
        (RepairedWriteBackPolicy.Proposal, Set<Long>, Long, Long) -> List<String> =
        { proposal, tagged, occurredAt, syncId ->
            store.recordRepairedWriteBack(proposal, tagged, occurredAt, syncId)
        }

    constructor(
        context: Context,
        store: LocalStore,
        gateway: CollectionGateway,
        settings: RecordsSyncModels.Settings,
    ) : this(context, store, gateway, settings, SyncProgress.NONE)

    constructor(
        context: Context,
        store: LocalStore,
        gateway: CollectionGateway,
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener?,
    ) : this(context, store, gateway, settings, progress, AppClock.systemClock())

    constructor(
        context: Context,
        store: LocalStore,
        gateway: CollectionGateway,
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener?,
        clock: AppClock?,
        repairedWriteBackAuthorized: Boolean = false,
        confirmedRepairedNoteIds: Set<Long>? = null,
    ) {
        this.context = context.applicationContext
        this.store = store
        this.gateway = gateway
        this.settings = settings
        this.progress = progress ?: SyncProgress.NONE
        this.clock = AppClock.orSystem(clock)
        this.repairedWriteBackAuthorized = repairedWriteBackAuthorized
        this.confirmedRepairedNoteIds = confirmedRepairedNoteIds?.toSet()
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
        AppDebugLog.log("sync start model=${settings.modelName}")
        try {
            val snapshot = gateway.readCollection(settings, progress)
            rejectTransientEmptySnapshot(snapshot)
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS))
            val ranks = loadRanks()
            val selectedImports = KanjiImportSelector(
                ranks,
                settings.suspendedRankMin,
                settings.suspendedRankMax,
            ).importFrom(snapshot, settings)
            val currentSuspendedImports = SuspendedImportPolicy.suspendedImportsOnly(selectedImports)
            val storedSuspendedImports = if (settings.importSuspendedCards) {
                storedImportsWithDurableProviderRoute(snapshot, store.suspendedImports())
            } else {
                emptyList()
            }
            val analysisImports = SuspendedImportPolicy.mergeSuspendedImports(
                storedSuspendedImports,
                selectedImports,
                settings,
            )
            val rows = KanjiAnalyzer().rebuildSelectedSources(snapshot, analysisImports, ranks, settings)
            val similarKanjiIndex = loadSimilarKanjiIndex()
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.SAVING_LOCAL_DATA))
            val finished = clock.nowMillis()
            var syncId = 0L
            lateinit var plan: RecordsSchedulerModels.AdaptiveLoadPlan
            var activeRows = emptyList<RecordsImportModels.DashboardRow>()
            store.publishSyncAtomically {
                // Keep the provider mirror, dashboard/inventory derivations, and
                // reconciled queue unpublished until every local phase succeeds.
                syncId = store.saveSuccessfulSync(
                    snapshot,
                    currentSuspendedImports,
                    rows,
                    settings,
                    LocalStoreBase.SyncTiming(started, finished),
                    null,
                    similarKanjiIndex,
                    selectedImports,
                    LocalStoreBase.STATUS_PENDING,
                    loadDictionary(),
                )

                progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE))
                val scheduler = BridgeScheduler.withWeights(store.schedulerFsrsWeights())
                activeRows = SuspendedImportPolicy.activeRows(rows, store.locallySuspendedKanji())
                // Seeding is a durable reconciliation, so it must see every persisted family.
                // Admission and planning stay scoped to rows that are not locally suspended.
                val currentItems = store.studyItems()
                val activeKanji = activeRows.mapTo(HashSet()) { it.kanji }
                val activeItems = currentItems.filter { it.kanji in activeKanji }
                plan = adaptivePlan(activeRows, activeItems, finished)
                val evidenceStatusByKanji = repairEvidenceStatusByKanji(rows, started)
                var seeded = scheduler.seedQueue(
                    rows,
                    activeRows,
                    currentItems,
                    settings,
                    finished,
                    startOfDay(finished),
                    plan,
                    store.studyLadderSettings(),
                    evidenceStatusByKanji,
                )
                seeded = store.annotateSimilarKanjiAvailability(seeded)
                // Pass the pre-seed baseline so queue publication preserves any review
                // saved before this outer write transaction acquired its lock.
                store.commitPendingSyncStudyItems(seeded, syncId, finished, settings, currentItems)
            }
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
                gateway.removeArchivedSuspendedCards(snapshot, currentSuspendedImports, progress)
            } catch (error: Exception) {
                logPostCommitFailure("Could not archive imported notes after committed sync", error)
                AnkiDroidGateway.RemovalSummary(
                    0,
                    0,
                    0,
                    "Archive tagging could not finish and will retry on the next sync.",
                )
            }
            var syncMessage = removal.message
            committedMessage = syncMessage
            try {
                if (repairedWriteBackAuthorized && SyncSettings.tagRepairedCards(store)) {
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
                            gateway.tagRepairedNotes(confirmedProposal.noteIdsToTag, progress)
                        } catch (error: Exception) {
                            logPostCommitFailure("Could not tag repaired notes; retrying next sync", error)
                            dev.bee.kanjianki.anki.RepairedTagSummary(
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
                AppDebugLog.log(
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
        } catch (error: AnkiDroidGateway.SyncFailure) {
            committedState?.let { committed ->
                return committedFailureResult(committed, committedMessage, error)
            }
            Log.e(TAG, "Sync failed (${if (error.permanentFailure) "permanent" else "retryable"}).", error)
            AppDebugLog.logError(
                "sync failed (${if (error.permanentFailure) "permanent" else "retryable"})",
                error,
            )
            val finished = clock.nowMillis()
            persistFailedSync(
                started,
                finished,
                if (error.permanentFailure) "config_error" else "retryable_error",
                if (error.permanentFailure) "permanent" else "retryable",
                error,
            )
            return SyncResult.create(
                false,
                false,
                0,
                0,
                error.message,
                "",
                retryable = !error.permanentFailure,
            )
        } catch (error: Exception) {
            committedState?.let { committed ->
                return committedFailureResult(committed, committedMessage, error)
            }
            // Only Exceptions are treated as recoverable sync failures. Errors
            // (OutOfMemoryError, StackOverflowError, ...) propagate instead of being
            // mislabeled as a retryable_error sync row.
            Log.e(TAG, "Unexpected sync failure.", error)
            AppDebugLog.logError("sync failed (unexpected)", error)
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
        val postSyncItems = if (activeRows.isEmpty()) {
            emptyList()
        } else {
            store.studyItemsForKanji(activeRows.map { it.kanji })
        }
        val postSyncPlan = if (activeRows.isEmpty()) {
            null
        } else {
            adaptivePlan(activeRows, postSyncItems, countedAt)
        }
        val ladder = store.studyLadderSettings()
        val studyNow = StudyNowCountCoordinator.count(
            StudyNowCountCoordinator.Request(
                queue = StudyNowCountCoordinator.QueueInput(activeRows, postSyncItems, settings, ladder),
                timing = StudyNowCountCoordinator.Timing(
                    countedAt,
                    startOfDay(countedAt),
                    store.studyAheadMinutes() * 60_000L,
                ),
                mode = StudyNowCountCoordinator.Mode(postSyncPlan, false),
                pipeline = StudyNowCountCoordinator.Pipeline(
                    scheduler = BridgeScheduler.withWeights(store.schedulerFsrsWeights()),
                    annotator = store::annotateSimilarKanjiAvailability,
                    replanner = { seeded -> adaptivePlan(activeRows, seeded, countedAt) },
                ),
            ),
        )
        val repairTaskKeys = if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            store.dueSimilarWritingRepairs(countedAt)
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
        try {
            Log.e(TAG, description, error)
        } catch (_: RuntimeException) {
            // Logging must not pierce the successful-sync commit boundary.
        }
        try {
            AppDebugLog.logError(description, error)
        } catch (_: RuntimeException) {
            // The diagnostic executor can reject work during process shutdown.
        }
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
            store.saveFailedSync(started, finished, status, errorCode, error.message)
        } catch (persistError: Exception) {
            persistError.addSuppressed(error)
            Log.e(TAG, "Failed to persist sync failure row.", persistError)
        }
    }

    internal fun repairEvidenceStatusByKanji(
        rows: List<RecordsImportModels.DashboardRow>,
        currentSyncAtMillis: Long? = null,
    ): Map<String, KanjiRepairEvidencePolicy.Status> {
        if (rows.isEmpty()) {
            return emptyMap()
        }
        val activeKanji = rows.mapTo(HashSet()) { it.kanji }
        if (currentSyncAtMillis != null) {
            val rowsByKanji = rows.associateBy { it.kanji }
            return store.kanjiRepairEvidenceInputs()
                .asSequence()
                .filter { it.kanji() in activeKanji }
                .associate { input ->
                    val row = rowsByKanji.getValue(input.kanji())
                    input.kanji() to currentEvidenceStatus(input, row, currentSyncAtMillis)
                }
        }
        val statusByKanji = HashMap<String, KanjiRepairEvidencePolicy.Status>()
        for (evidence in store.kanjiRepairEvidence()) {
            if (evidence.kanji in activeKanji) {
                statusByKanji[evidence.kanji] = evidence.status
            }
        }
        return statusByKanji
    }

    private fun currentEvidenceStatus(
        input: KanjiRepairEvidencePolicy.Input,
        row: RecordsImportModels.DashboardRow,
        currentSyncAtMillis: Long,
    ): KanjiRepairEvidencePolicy.Status {
        val isPostReviewSample = currentSyncAtMillis > input.lastReviewAtMillis()
        val currentSnapshot = KanjiRepairEvidencePolicy.Snapshot(
            row.weaknessScore,
            row.matureSupportCount,
            currentSyncAtMillis,
            row.activeExampleCount,
            row.suspendedExampleCount,
            row.reasonCode,
        )
        val updated = KanjiRepairEvidencePolicy.Input(
            input.kanji(),
            input.before(),
            if (isPostReviewSample) currentSnapshot else input.after(),
            input.kaniReviews(),
            input.postReviewSamples() + if (isPostReviewSample) 1 else 0,
            input.writingFailures(),
            input.lastMistakeAtMillis(),
            input.firstReviewAtMillis(),
            input.lastReviewAtMillis(),
            maxOf(input.lastSyncAtMillis(), currentSyncAtMillis),
            input.ladder(),
        )
        return KanjiRepairEvidencePolicy.summarize(updated).status()
    }

    private fun storedImportsWithDurableProviderRoute(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
    ): List<RecordsImportModels.SuspendedImport> {
        if (imports.isEmpty()) return emptyList()
        val durableCardIds = snapshot.cards.mapTo(HashSet<Long>()) { it.cardId }
        durableCardIds.addAll(store.unrestoredSuspendedArchiveCardIds())
        return imports.filter { imported ->
            imported.sources.any { source -> source.cardId in durableCardIds }
        }
    }

    private fun rejectTransientEmptySnapshot(snapshot: RecordsSyncModels.CollectionSnapshot) {
        val incomplete = snapshot.notes.isEmpty() || snapshot.cards.isEmpty()
        val hasDurableLocalState = store.studyItems().isNotEmpty() || store.hasPersistedCollectionMirror()
        if (incomplete && hasDurableLocalState) {
            throw AnkiDroidGateway.SyncFailure.retryable(
                "AnkiDroid returned an incomplete collection; existing study progress was preserved.",
            )
        }
    }

    private fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        val planner = AdaptiveLoadPlanner()
        val dayStart = startOfDay(nowMillis)
        val studiedToday = store.studiedKanjiSince(dayStart)
        return planner.plan(
            AdaptiveLoadPlanner.PlanRequest.builder(
                rows,
                items,
                store.reviewStatsSince(nowMillis - 7 * 86_400_000L),
                store.studyStreak(nowMillis).currentDays,
                studiedToday,
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                    store.adaptiveLoadWorkPercent(),
                    store.adaptiveLoadMode(),
                    store.adaptiveLoadMaxItems(),
                ),
                nowMillis,
            )
                .settings(settings)
                .readingExposure(ReadingExposureMediaReader().read())
                .build(),
        )
    }

    @Throws(IOException::class)
    fun loadRanks(): JitenKanjiRanks {
        return DictionaryStore.open(context).jitenRanks()
    }

    // The bundled KANJIDIC2 lookup feeds KanjiReadingAligner during the sync
    // save (Goal 77). Returns null if the dictionary cannot be opened so a
    // dictionary hiccup degrades to empty reading-usage tables rather than
    // failing the whole sync.
    fun loadDictionary(): DictionaryLookup? {
        return try {
            DictionaryStore.open(context)
        } catch (_: IOException) {
            null
        }
    }

    @Throws(IOException::class)
    fun loadSimilarKanjiIndex(): SimilarKanjiIndex {
        InputStreamReader(
            context.resources.openRawResource(R.raw.similar_kanji),
            StandardCharsets.UTF_8,
        ).use { reader ->
            return SimilarKanjiIndex.parseTsv(reader)
        }
    }

    private fun startOfDay(now: Long): Long {
        return LocalDayPolicy.localDayStart(now)
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

    internal data class CommittedStudySummary(
        val readyCount: Int,
        val focusPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    )

    private data class CommittedSyncState(
        val dashboardRows: Int,
        val importedSuspendedKanji: Int,
        val preCommitPlan: RecordsSchedulerModels.AdaptiveLoadPlan,
    )

    companion object {
        private const val TAG = "ManualSyncEngine"
        private val RUNNING = AtomicBoolean(false)

        @JvmStatic
        internal fun isRunning(): Boolean = RUNNING.get()

        private fun appendSyncMessage(current: String, addition: String): String =
            listOf(current, addition).filter(String::isNotBlank).joinToString(" ")
    }
}
