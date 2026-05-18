package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.Context;

import dev.bee.kanjianki.R;
import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.JitenKanjiRanks;
import dev.bee.kanjianki.core.KanjiAnalyzer;
import dev.bee.kanjianki.core.KanjiImportSelector;
import dev.bee.kanjianki.core.LocalDayPolicy;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.SuspendedImportPolicy;
import dev.bee.kanjianki.data.DictionaryStore;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.time.AppClock;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ManualSyncEngine {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final Context context;
    private final LocalStore store;
    private final CollectionGateway gateway;
    private final RecordsSyncModels.Settings settings;
    private final SyncProgress.Listener progress;
    private final AppClock clock;

    public ManualSyncEngine(Context context, LocalStore store, CollectionGateway gateway, RecordsSyncModels.Settings settings) {
        this(context, store, gateway, settings, SyncProgress.NONE);
    }

    public ManualSyncEngine(Context context, LocalStore store, CollectionGateway gateway, RecordsSyncModels.Settings settings, SyncProgress.Listener progress) {
        this(context, store, gateway, settings, progress, AppClock.systemClock());
    }

    ManualSyncEngine(Context context, LocalStore store, CollectionGateway gateway, RecordsSyncModels.Settings settings, SyncProgress.Listener progress, AppClock clock) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.gateway = gateway;
        this.settings = settings;
        this.progress = progress == null ? SyncProgress.NONE : progress;
        this.clock = AppClock.orSystem(clock);
    }

    public SyncResult run() {
        if (!RUNNING.compareAndSet(false, true)) {
            return SyncResult.skipped("Sync already running.");
        }
        try {
            return runLocked();
        } finally {
            RUNNING.set(false);
        }
    }

    private SyncResult runLocked() {
        long started = clock.nowMillis();
        try {
            RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(settings, progress);
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS));
            JitenKanjiRanks ranks = loadRanks();
            List<RecordsImportModels.SuspendedImport> selectedImports = new KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax)
                    .importFrom(snapshot, settings);
            List<RecordsImportModels.SuspendedImport> currentSuspendedImports = SuspendedImportPolicy.suspendedImportsOnly(selectedImports);
            List<RecordsImportModels.SuspendedImport> storedSuspendedImports = settings.importSuspendedCards ? store.suspendedImports() : Collections.emptyList();
            List<RecordsImportModels.SuspendedImport> analysisImports = SuspendedImportPolicy.mergeSuspendedImports(storedSuspendedImports, selectedImports, settings);
            List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuildSelectedSources(snapshot, analysisImports, ranks, settings);
            SimilarKanjiIndex similarKanjiIndex = loadSimilarKanjiIndex();
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE));
            long finished = clock.nowMillis();
            long syncId = store.saveSuccessfulSync(
                    snapshot,
                    currentSuspendedImports,
                    rows,
                    settings,
                    new LocalStore.SyncTiming(started, finished),
                    null,
                    similarKanjiIndex,
                    selectedImports
            );
            AnkiDroidGateway.RemovalSummary removal = gateway.removeArchivedSuspendedCards(snapshot, currentSuspendedImports, progress);
            store.updateSyncRemovalMessage(syncId, removal.message);

            BridgeScheduler scheduler = new BridgeScheduler();
            List<RecordsStudyModels.StudyItem> currentItems = store.studyItems();
            List<RecordsImportModels.DashboardRow> activeRows = SuspendedImportPolicy.activeRows(rows, store.locallySuspendedKanji());
            RecordsSchedulerModels.AdaptiveLoadPlan plan = adaptivePlan(activeRows, currentItems, finished);
            List<RecordsStudyModels.StudyItem> seeded = scheduler.seedQueue(
                    activeRows,
                    currentItems,
                    settings,
                    finished,
                    startOfDay(finished),
                    plan,
                    store.studyLadderSettings()
            );
            // Apply the hasSimilarKanji predicate using the similarity data
            // just written, so newly-admitted items immediately know whether
            // the similar_kanji rung is available for them.
            seeded = store.annotateSimilarKanjiAvailability(seeded);
            store.replaceStudyItems(seeded, syncId, finished, settings);
            return new SyncResult(true, false, rows.size(), currentSuspendedImports.size(), removal.message, plan.status);
        } catch (AnkiDroidGateway.SyncFailure error) {
            long finished = clock.nowMillis();
            store.saveFailedSync(
                    started,
                    finished,
                    error.permanentFailure ? "config_error" : "retryable_error",
                    error.permanentFailure ? "permanent" : "retryable",
                    error.getMessage()
            );
            return new SyncResult(false, false, 0, 0, error.getMessage(), "");
        } catch (Throwable error) {
            long finished = clock.nowMillis();
            store.saveFailedSync(started, finished, "retryable_error", "unexpected", error.getMessage());
            return new SyncResult(false, false, 0, 0, error.getMessage(), "");
        }
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan adaptivePlan(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> items, long nowMillis) {
        AdaptiveLoadPlanner planner = new AdaptiveLoadPlanner();
        long dayStart = startOfDay(nowMillis);
        Set<String> studiedToday = store.studiedKanjiSince(dayStart);
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
                                        store.adaptiveLoadMaxItems()
                                ),
                                nowMillis
                        )
                        .settings(settings)
                        .build()
        );
    }

    public JitenKanjiRanks loadRanks() throws IOException {
        return DictionaryStore.open(context).jitenRanks();
    }

    public SimilarKanjiIndex loadSimilarKanjiIndex() throws IOException {
        try (InputStreamReader reader = new InputStreamReader(context.getResources().openRawResource(R.raw.similar_kanji), StandardCharsets.UTF_8)) {
            return SimilarKanjiIndex.parseTsv(reader);
        }
    }

    private long startOfDay(long now) {
        return LocalDayPolicy.localDayStart(now);
    }

    public static final class SyncResult {
        public final boolean success;
        public final boolean skipped;
        public final int dashboardRows;
        public final int importedSuspendedKanji;
        public final String message;
        public final String adaptiveSummary;

        private SyncResult(boolean success, boolean skipped, int dashboardRows, int importedSuspendedKanji, String message, String adaptiveSummary) {
            this.success = success;
            this.skipped = skipped;
            this.dashboardRows = dashboardRows;
            this.importedSuspendedKanji = importedSuspendedKanji;
            this.message = message;
            this.adaptiveSummary = adaptiveSummary == null ? "" : adaptiveSummary;
        }

        private static SyncResult skipped(String message) {
            return new SyncResult(false, true, 0, 0, message, "");
        }
    }
}
