package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class ManualSyncEngineInstrumentedTest {
    private Context context;
    private LocalStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void successfulSyncArchivesSuspendedCardsBuildsRowsAndSeedsStudy() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new FakeGateway(snapshot(settings), new AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done")),
                settings
        ).run();

        assertTrue(result.success);
        assertEquals("cleanup done", result.message);
        List<RecordsImportModels.DashboardRow> rows = store.dashboardRows();
        List<RecordsStudyModels.StudyItem> items = store.studyItems();
        assertFalse(rows.isEmpty());
        assertFalse(store.suspendedImports().isEmpty());
        assertFalse(items.isEmpty());
        assertEquals("success", store.latestSync().status);

        Map<String, RecordsImportModels.DashboardRow> rowByKanji = new HashMap<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        boolean activeStudyItem = false;
        for (RecordsStudyModels.StudyItem item : items) {
            if ("retired".equals(item.state)) {
                continue;
            }
            activeStudyItem = true;
            assertTrue("Active study item must still have current Anki evidence: " + item.kanji, rowByKanji.containsKey(item.kanji));
        }
        assertTrue(activeStudyItem);
        assertTrue("The fake suspended problem card should create at least one suspended-evidence row.", hasSuspendedEvidence(rows));
    }

    @Test
    public void nullProgressListenerUsesNoopProgressAndStillSyncs() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new FakeGateway(snapshot(settings), new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
                settings,
                null
        ).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
    }

    @Test
    public void successfulSyncUsesAdaptiveWorkloadForNewAdmissions() {
        RecordsSyncModels.Settings settings = importSettings(true, false, false, "", false, 1);
        store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
        store.saveAdaptiveLoadWorkPercent(0);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new FakeGateway(manyProblemSnapshot(), new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
                settings
        ).run();

        assertTrue(result.success);
        assertTrue(result.adaptiveSummary.contains("Very little"));
        assertEquals(1, activeStudyItemCount(store.studyItems()));
    }

    @Test
    public void importFiltersCanCreateRowsFromTaggedActiveCardsWithoutArchivingExcludedSuspendedCards() {
        RecordsSyncModels.Settings settings = importSettings(false, false, true, "focus", false, 1);
        RecordsSyncModels.Note taggedActive = note(1L, "裂ける", "さける", "split", "裂ける音。", "focus");
        RecordsSyncModels.Note excludedSuspended = note(2L, "謎", "なぞ", "mystery", "謎を見た。");
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(taggedActive, excludedSuspended),
                Arrays.asList(
                        new RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 30, 12, 0, false),
                        new RecordsSyncModels.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true)
                )
        );
        RecordingGateway gateway = new RecordingGateway(snapshot, new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done"));

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertTrue(store.suspendedImports().isEmpty());
        assertEquals(0, store.latestSync().suspendedCards);
        assertTrue(gateway.selectedSuspendedImports.isEmpty());
        List<RecordsImportModels.DashboardRow> rows = store.dashboardRows();
        assertEquals(1, rows.size());
        assertEquals("裂", rows.get(0).kanji);
    }

    @Test
    public void failedSyncPersistsConfigError() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new FailingGateway(),
                settings
        ).run();

        assertFalse(result.success);
        assertEquals("Kiku note type was not found in AnkiDroid.", result.message);
        LocalStore.SyncStatus status = store.latestSync();
        assertEquals("config_error", status.status);
        assertEquals("Kiku note type was not found in AnkiDroid.", status.errorMessage);
    }

    @Test
    public void retryableSyncFailurePersistsRetryableError() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new RetryableGateway(),
                settings
        ).run();

        assertFalse(result.success);
        assertEquals("AnkiDroid returned no configured note cursor.", result.message);
        LocalStore.SyncStatus status = store.latestSync();
        assertEquals("retryable_error", status.status);
        assertEquals("AnkiDroid returned no configured note cursor.", status.errorMessage);
    }

    @Test
    public void unexpectedRuntimeExceptionPersistsRetryableError() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new RuntimeFailingGateway(),
                settings
        ).run();

        assertFalse(result.success);
        assertEquals("sync crashed", result.message);
        LocalStore.SyncStatus status = store.latestSync();
        assertEquals("retryable_error", status.status);
        assertEquals("sync crashed", status.errorMessage);
    }

    @Test
    public void concurrentManualSyncSkipsWithoutRecordingSync() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        BlockingGateway blockingGateway = new BlockingGateway(
                snapshot(settings),
                new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")
        );
        AtomicReference<ManualSyncEngine.SyncResult> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread firstSync = new Thread(() -> {
            try {
                firstResult.set(new ManualSyncEngine(context, store, blockingGateway, settings).run());
            } catch (Throwable error) {
                threadFailure.set(error);
            }
        });

        firstSync.start();
        assertTrue(blockingGateway.awaitStarted());

        ManualSyncEngine.SyncResult skipped = new ManualSyncEngine(
                context,
                store,
                new FakeGateway(snapshot(settings), new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
                settings
        ).run();

        assertTrue(skipped.skipped);
        assertFalse(skipped.success);
        assertEquals("Sync already running.", skipped.message);
        assertNull(store.latestSync());

        blockingGateway.release();
        firstSync.join(10_000L);
        assertFalse(firstSync.isAlive());
        assertNull(threadFailure.get());
        assertTrue(firstResult.get().success);
    }

    @Test
    public void manualSyncReceivesOrderedProgressEvents() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        List<String> events = new ArrayList<>();

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new ProgressGateway(snapshot(settings), new AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done")),
                settings,
                progress -> events.add(progress.stage.name() + ":" + progress.scannedCards + "/" + progress.totalCards)
        ).run();

        assertTrue(result.success);
        assertEquals(Arrays.asList(
                "FINDING_NOTE_TYPE:0/-1",
                "READING_NOTES:0/-1",
                "SCANNING_CARDS:0/2",
                "SCANNING_CARDS:1/2",
                "SCANNING_CARDS:2/2",
                "PROCESSING_IMPORTED_CARDS:0/-1",
                "BUILDING_PRACTICE_QUEUE:0/-1",
                "ARCHIVING_IMPORTED_CARDS:0/-1"
        ), events);
    }

    private RecordsSyncModels.CollectionSnapshot snapshot(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Note active = note(1L, "確認", "かくにん", "confirmation", "確認した。");
        RecordsSyncModels.Note suspended = note(2L, "笥箱", "しはこ", "rare box", "笥箱を見た。");
        RecordsSyncModels.Card activeCard = new RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, settings.matureDays + 5, 12, 0, false);
        RecordsSyncModels.Card suspendedCard = new RecordsSyncModels.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true);
        return new RecordsSyncModels.CollectionSnapshot(Arrays.asList(active, suspended), Arrays.asList(activeCard, suspendedCard));
    }

    private RecordsSyncModels.CollectionSnapshot manyProblemSnapshot() {
        RecordsSyncModels.Note first = note(1L, "拉麺", "らーめん", "ramen", "拉麺を食べた。");
        RecordsSyncModels.Note second = note(2L, "謎", "なぞ", "mystery", "謎を見た。");
        RecordsSyncModels.Note third = note(3L, "裂ける", "さける", "split", "裂ける音。");
        return new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(first, second, third),
                Arrays.asList(
                        new RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 12, 2, false),
                        new RecordsSyncModels.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 3, 12, 1, false),
                        new RecordsSyncModels.Card(30L, 3L, 0, "Kiku", 2, 2, 0, 3, 12, 0, false)
                )
        );
    }

    private int activeStudyItemCount(List<RecordsStudyModels.StudyItem> items) {
        int count = 0;
        for (RecordsStudyModels.StudyItem item : items) {
            if (!"retired".equals(item.state)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasSuspendedEvidence(List<RecordsImportModels.DashboardRow> rows) {
        for (RecordsImportModels.DashboardRow row : rows) {
            if (row.suspendedExampleCount > 0) {
                return true;
            }
        }
        return false;
    }

    private RecordsSyncModels.Note note(long id, String expression, String reading, String meaning, String sentence, String... tags) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new RecordsSyncModels.Note(id, "Kiku", fields, Arrays.asList(tags));
    }

    private RecordsSyncModels.Settings importSettings(
            boolean active,
            boolean suspended,
            boolean tagged,
            String tags,
            boolean weak,
            int minMatching
    ) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                active,
                suspended,
                tagged,
                RecordsBase.parseImportTags(tags),
                weak,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                minMatching
        );
    }

    private static final class FakeGateway implements CollectionGateway {
        private final RecordsSyncModels.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;

        private FakeGateway(RecordsSyncModels.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) {
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return removal;
        }
    }

    private static final class FailingGateway implements CollectionGateway {
        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws AnkiDroidGateway.SyncFailure {
            throw AnkiDroidGateway.SyncFailure.permanent("Kiku note type was not found in AnkiDroid.");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "");
        }
    }

    private static final class RetryableGateway implements CollectionGateway {
        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws AnkiDroidGateway.SyncFailure {
            throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no configured note cursor.");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "");
        }
    }

    private static final class RuntimeFailingGateway implements CollectionGateway {
        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) {
            throw new IllegalStateException("sync crashed");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "");
        }
    }

    private static final class BlockingGateway implements CollectionGateway {
        private final RecordsSyncModels.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingGateway(RecordsSyncModels.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) throws AnkiDroidGateway.SyncFailure {
            started.countDown();
            try {
                if (!release.await(10L, TimeUnit.SECONDS)) {
                    throw AnkiDroidGateway.SyncFailure.retryable("Timed out waiting for test release.");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw AnkiDroidGateway.SyncFailure.retryable("Interrupted while waiting for test release.", error);
            }
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return removal;
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(10L, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class ProgressGateway implements CollectionGateway {
        private final RecordsSyncModels.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;

        private ProgressGateway(RecordsSyncModels.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) {
            return snapshot;
        }

        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings, SyncProgress.Listener progress) {
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES));
            progress.onSyncProgress(SyncProgress.cardsScanned(0, snapshot.cards.size()));
            for (int i = 0; i < snapshot.cards.size(); i++) {
                progress.onSyncProgress(SyncProgress.cardsScanned(i + 1, snapshot.cards.size()));
            }
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return removal;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
            return removal;
        }
    }

    private static final class RecordingGateway implements CollectionGateway {
        private final RecordsSyncModels.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;
        private List<RecordsImportModels.SuspendedImport> selectedSuspendedImports = Collections.emptyList();

        private RecordingGateway(RecordsSyncModels.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public RecordsSyncModels.CollectionSnapshot readCollection(RecordsSyncModels.Settings settings) {
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(RecordsSyncModels.CollectionSnapshot snapshot) {
            return removal;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(
                RecordsSyncModels.CollectionSnapshot snapshot,
                List<RecordsImportModels.SuspendedImport> selectedSuspendedImports,
                SyncProgress.Listener progress
        ) {
            this.selectedSuspendedImports = selectedSuspendedImports;
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
            return removal;
        }
    }
}
