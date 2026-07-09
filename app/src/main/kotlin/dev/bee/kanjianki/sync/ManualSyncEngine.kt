package dev.bee.kanjianki.sync

import android.content.Context
import android.util.Log
import dev.bee.kanjianki.AppDebugLog
import dev.bee.kanjianki.R
import dev.bee.kanjianki.ReadingExposureMediaReader
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.KanjiAnalyzer
import dev.bee.kanjianki.core.KanjiImportSelector
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.SuspendedImportPolicy
import dev.bee.kanjianki.data.DictionaryStore
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.time.AppClock
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

    /**
     * Seam for the post-sync reminder re-arm (D4). Defaults to the real
     * scheduler; tests replace it to assert the success path re-arms and the
     * failure path does not.
     */
    @JvmField
    internal var reminderRescheduler: Runnable = Runnable {
        dev.bee.kanjianki.reminders.ReminderScheduler.schedule(context)
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
    ) {
        this.context = context.applicationContext
        this.store = store
        this.gateway = gateway
        this.settings = settings
        this.progress = progress ?: SyncProgress.NONE
        this.clock = AppClock.orSystem(clock)
    }

    fun run(): SyncResult {
        if (!RUNNING.compareAndSet(false, true)) {
            return SyncResult.skipped("Sync already running.")
        }
        try {
            return runLocked()
        } finally {
            RUNNING.set(false)
        }
    }

    private fun runLocked(): SyncResult {
        val started = clock.nowMillis()
        AppDebugLog.log("sync start model=${settings.modelName}")
        try {
            val snapshot = gateway.readCollection(settings, progress)
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS))
            val ranks = loadRanks()
            val selectedImports = KanjiImportSelector(
                ranks,
                settings.suspendedRankMin,
                settings.suspendedRankMax,
            ).importFrom(snapshot, settings)
            val currentSuspendedImports = SuspendedImportPolicy.suspendedImportsOnly(selectedImports)
            val storedSuspendedImports = if (settings.importSuspendedCards) {
                store.suspendedImports()
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
            // Record the sync run as pending in transaction #1. It is flipped to
            // success only after study items commit in replaceStudyItems below, so a
            // crash between the two transactions leaves a pending row that
            // hasSuccessfulSyncSince ignores (auto-sync retries) instead of a committed
            // success sitting on stale study items.
            val syncId = store.saveSuccessfulSync(
                snapshot,
                currentSuspendedImports,
                rows,
                settings,
                LocalStoreBase.SyncTiming(started, finished),
                null,
                similarKanjiIndex,
                selectedImports,
                LocalStoreBase.STATUS_PENDING,
            )

            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE))
            val scheduler = BridgeScheduler()
            val activeRows = SuspendedImportPolicy.activeRows(rows, store.locallySuspendedKanji())
            val currentItems = store.studyItemsForKanji(activeRows.map { it.kanji })
            val plan = adaptivePlan(activeRows, currentItems, finished)
            val evidenceStatusByKanji = repairEvidenceStatusByKanji(activeRows)
            var seeded = scheduler.seedQueue(
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
            // Pass the pre-seed baseline so replaceStudyItems can preserve any review
            // the user saved between the studyItemsForKanji read above and this write
            // (auto-sync can run while the app is foregrounded and studyable).
            store.replaceStudyItems(seeded, syncId, finished, settings, currentItems)
            // Study items are committed; promote the pending sync run to success.
            store.markSyncSucceeded(syncId)
            // A sync replaces the whole study queue: cards can land newly overdue or
            // the queue can empty. Re-arm the reminder from fresh state so the alarm
            // timing tracks the new queue instead of a stale pre-sync schedule (D4).
            reminderRescheduler.run()

            // Provider tagging runs after all local persistence so a tagging
            // failure cannot strand a committed sync mirror alongside stale
            // study items. Tagging is re-attempted on the next sync, so a
            // failure here degrades to a warning instead of a failed sync.
            val removal = try {
                gateway.removeArchivedSuspendedCards(snapshot, currentSuspendedImports, progress)
            } catch (error: Exception) {
                AnkiDroidGateway.RemovalSummary(
                    0,
                    0,
                    0,
                    "Archive tagging failed and will be retried on the next sync: ${error.message}",
                )
            }
            store.updateSyncRemovalMessage(syncId, removal.message)
            val postSyncPlan = if (activeRows.isEmpty()) null else adaptivePlan(activeRows, seeded, finished)
            val readyCount = if (activeRows.isEmpty()) {
                0
            } else {
                FocusQueuePolicy.queuedEntries(
                    activeRows,
                    seeded,
                    finished,
                    store.studyAheadMinutes() * 60_000L,
                    postSyncPlan,
                    store.studyLadderSettings(),
                ).size
            }
            AppDebugLog.log(
                "sync success duration_ms=${clock.nowMillis() - started} rows=${rows.size} " +
                    "suspended_imports=${currentSuspendedImports.size} ready=$readyCount",
            )
            return SyncResult.create(
                true,
                false,
                rows.size,
                currentSuspendedImports.size,
                removal.message,
                plan.status,
                readyCount,
                AdaptiveFocusCopy.adaptiveFocusText(postSyncPlan),
            )
        } catch (error: AnkiDroidGateway.SyncFailure) {
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
            return SyncResult.create(false, false, 0, 0, error.message, "")
        } catch (error: Exception) {
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
    ): Map<String, KanjiRepairEvidencePolicy.Status> {
        if (rows.isEmpty()) {
            return emptyMap()
        }
        val activeKanji = rows.mapTo(HashSet()) { it.kanji }
        val statusByKanji = HashMap<String, KanjiRepairEvidencePolicy.Status>()
        for (evidence in store.kanjiRepairEvidence()) {
            if (evidence.kanji in activeKanji) {
                statusByKanji[evidence.kanji] = evidence.status
            }
        }
        return statusByKanji
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
                }
            }

            @JvmStatic
            internal fun skipped(message: String): SyncResult {
                return SyncResult(false, true, 0, 0, message, "")
            }
        }
    }

    companion object {
        private const val TAG = "ManualSyncEngine"
        private val RUNNING = AtomicBoolean(false)
    }
}
