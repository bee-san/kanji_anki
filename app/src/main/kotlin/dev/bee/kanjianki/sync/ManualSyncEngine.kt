package dev.bee.kanjianki.sync

import android.content.Context
import dev.bee.kanjianki.R
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.KanjiAnalyzer
import dev.bee.kanjianki.core.KanjiImportSelector
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
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE))
            val finished = clock.nowMillis()
            val syncId = store.saveSuccessfulSync(
                snapshot,
                currentSuspendedImports,
                rows,
                settings,
                LocalStoreBase.SyncTiming(started, finished),
                null,
                similarKanjiIndex,
                selectedImports,
            )
            val removal = gateway.removeArchivedSuspendedCards(snapshot, currentSuspendedImports, progress)
            store.updateSyncRemovalMessage(syncId, removal.message)

            val scheduler = BridgeScheduler()
            val currentItems = store.studyItems()
            val activeRows = SuspendedImportPolicy.activeRows(rows, store.locallySuspendedKanji())
            val plan = adaptivePlan(activeRows, currentItems, finished)
            var seeded = scheduler.seedQueue(
                activeRows,
                currentItems,
                settings,
                finished,
                startOfDay(finished),
                plan,
                store.studyLadderSettings(),
            )
            seeded = store.annotateSimilarKanjiAvailability(seeded)
            store.replaceStudyItems(seeded, syncId, finished, settings)
            return SyncResult.create(true, false, rows.size, currentSuspendedImports.size, removal.message, plan.status)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            val finished = clock.nowMillis()
            store.saveFailedSync(
                started,
                finished,
                if (error.permanentFailure) "config_error" else "retryable_error",
                if (error.permanentFailure) "permanent" else "retryable",
                error.message,
            )
            return SyncResult.create(false, false, 0, 0, error.message, "")
        } catch (error: Throwable) {
            val finished = clock.nowMillis()
            store.saveFailedSync(started, finished, "retryable_error", "unexpected", error.message)
            return SyncResult.create(false, false, 0, 0, error.message, "")
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

        companion object {
            @JvmStatic
            internal fun create(
                success: Boolean,
                skipped: Boolean,
                dashboardRows: Int,
                importedSuspendedKanji: Int,
                message: String?,
                adaptiveSummary: String?,
            ): SyncResult {
                return SyncResult(
                    success,
                    skipped,
                    dashboardRows,
                    importedSuspendedKanji,
                    message,
                    adaptiveSummary,
                )
            }

            @JvmStatic
            internal fun skipped(message: String): SyncResult {
                return SyncResult(false, true, 0, 0, message, "")
            }
        }
    }

    companion object {
        private val RUNNING = AtomicBoolean(false)
    }
}
