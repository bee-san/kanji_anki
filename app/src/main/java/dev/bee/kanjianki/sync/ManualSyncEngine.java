package dev.bee.kanjianki.sync;

import android.content.Context;

import dev.bee.kanjianki.R;
import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.JitenKanjiRanks;
import dev.bee.kanjianki.core.KanjiAnalyzer;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.SuspendedKanjiImporter;
import dev.bee.kanjianki.data.DictionaryStore;
import dev.bee.kanjianki.data.LocalStore;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ManualSyncEngine {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final Context context;
    private final LocalStore store;
    private final CollectionGateway gateway;
    private final Records.Settings settings;
    private final SyncProgress.Listener progress;

    public ManualSyncEngine(Context context, LocalStore store, CollectionGateway gateway, Records.Settings settings) {
        this(context, store, gateway, settings, SyncProgress.NONE);
    }

    public ManualSyncEngine(Context context, LocalStore store, CollectionGateway gateway, Records.Settings settings, SyncProgress.Listener progress) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.gateway = gateway;
        this.settings = settings;
        this.progress = progress == null ? SyncProgress.NONE : progress;
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
        long started = System.currentTimeMillis();
        try {
            Records.CollectionSnapshot snapshot = gateway.readCollection(settings, progress);
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS));
            JitenKanjiRanks ranks = loadRanks();
            List<Records.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax)
                    .importFrom(snapshot, settings);
            List<Records.SuspendedImport> analysisImports = mergeSuspendedImports(store.suspendedImports(), imports);
            List<Records.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, analysisImports, ranks, settings);
            SimilarKanjiIndex similarKanjiIndex = loadSimilarKanjiIndex();
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE));
            long finished = System.currentTimeMillis();
            long syncId = store.saveSuccessfulSync(
                    snapshot,
                    imports,
                    rows,
                    settings,
                    new LocalStore.SyncTiming(started, finished),
                    null,
                    similarKanjiIndex
            );
            AnkiDroidGateway.RemovalSummary removal = gateway.removeArchivedSuspendedCards(snapshot, progress);
            store.updateSyncRemovalMessage(syncId, removal.message);

            BridgeScheduler scheduler = new BridgeScheduler();
            List<Records.StudyItem> currentItems = store.studyItems();
            List<Records.DashboardRow> activeRows = activeRows(rows, store.locallySuspendedKanji());
            Records.AdaptiveLoadPlan plan = adaptivePlan(activeRows, currentItems, finished);
            List<Records.StudyItem> seeded = scheduler.seedQueue(
                    activeRows,
                    currentItems,
                    settings,
                    finished,
                    startOfDay(finished),
                    plan
            );
            // Apply the hasSimilarKanji predicate using the similarity data
            // just written, so newly-admitted items immediately know whether
            // the similar_kanji rung is available for them.
            seeded = store.annotateSimilarKanjiAvailability(seeded);
            store.replaceStudyItems(seeded, syncId, finished, settings);
            return new SyncResult(true, false, rows.size(), imports.size(), removal.message, plan.status);
        } catch (AnkiDroidGateway.SyncFailure error) {
            long finished = System.currentTimeMillis();
            store.saveFailedSync(
                    started,
                    finished,
                    error.permanentFailure ? "config_error" : "retryable_error",
                    error.permanentFailure ? "permanent" : "retryable",
                    error.getMessage()
            );
            return new SyncResult(false, false, 0, 0, error.getMessage(), "");
        } catch (Throwable error) {
            long finished = System.currentTimeMillis();
            store.saveFailedSync(started, finished, "retryable_error", "unexpected", error.getMessage());
            return new SyncResult(false, false, 0, 0, error.getMessage(), "");
        }
    }

    private List<Records.DashboardRow> activeRows(List<Records.DashboardRow> rows, Set<String> suspendedKanji) {
        if (suspendedKanji.isEmpty()) {
            return rows;
        }
        List<Records.DashboardRow> out = new ArrayList<>();
        for (Records.DashboardRow row : rows) {
            if (!suspendedKanji.contains(row.kanji)) {
                out.add(row);
            }
        }
        return out;
    }

    private Records.AdaptiveLoadPlan adaptivePlan(List<Records.DashboardRow> rows, List<Records.StudyItem> items, long nowMillis) {
        AdaptiveLoadPlanner planner = new AdaptiveLoadPlanner();
        long dayStart = startOfDay(nowMillis);
        Set<String> studiedToday = store.studiedKanjiSince(dayStart);
        return planner.plan(
                rows,
                items,
                store.reviewStatsSince(nowMillis - 7 * 86_400_000L),
                store.studyStreak(nowMillis).currentDays,
                studiedToday,
                store.adaptiveLoadWorkPercent(),
                store.adaptiveLoadMode(),
                store.adaptiveLoadMaxItems(),
                nowMillis,
                settings
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
        return now - (now % 86_400_000L);
    }

    private List<Records.SuspendedImport> mergeSuspendedImports(List<Records.SuspendedImport> stored, List<Records.SuspendedImport> current) {
        Map<String, MutableImport> byKanji = new LinkedHashMap<>();
        addImports(byKanji, stored);
        addImports(byKanji, current);
        List<Records.SuspendedImport> out = new ArrayList<>();
        for (MutableImport item : byKanji.values()) {
            out.add(item.build());
        }
        return out;
    }

    private void addImports(Map<String, MutableImport> byKanji, List<Records.SuspendedImport> imports) {
        for (Records.SuspendedImport imported : imports) {
            if (!importInFrequencyRange(imported)) {
                continue;
            }
            MutableImport target = byKanji.computeIfAbsent(imported.kanji, ignored -> new MutableImport(imported));
            target.add(imported);
        }
    }

    private boolean importInFrequencyRange(Records.SuspendedImport imported) {
        return imported.jitenRank != null
                && imported.jitenRank >= settings.suspendedRankMin
                && imported.jitenRank <= settings.suspendedRankMax;
    }

    private static final class MutableImport {
        private final String kanji;
        private Integer rank;
        private boolean rankKnown;
        private int cutoffUsed;
        private final Map<Long, Records.SuspendedSource> sources = new LinkedHashMap<>();

        private MutableImport(Records.SuspendedImport imported) {
            this.kanji = imported.kanji;
            this.rank = imported.jitenRank;
            this.rankKnown = imported.rankKnown;
            this.cutoffUsed = imported.cutoffUsed;
        }

        private void add(Records.SuspendedImport imported) {
            if (rank == null && imported.jitenRank != null) {
                rank = imported.jitenRank;
                rankKnown = true;
            }
            cutoffUsed = Math.max(cutoffUsed, imported.cutoffUsed);
            for (Records.SuspendedSource source : imported.sources) {
                sources.put(source.cardId, source);
            }
        }

        private Records.SuspendedImport build() {
            return new Records.SuspendedImport(kanji, rank, rankKnown, cutoffUsed, new ArrayList<>(sources.values()));
        }
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
