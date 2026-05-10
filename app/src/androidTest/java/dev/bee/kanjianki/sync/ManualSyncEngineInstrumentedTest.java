package dev.bee.kanjianki.sync;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.Records;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        Records.Settings settings = Records.Settings.kikuDefaults();
        ManualSyncEngine.SyncResult result = new ManualSyncEngine(
                context,
                store,
                new FakeGateway(snapshot(settings), new AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done")),
                settings
        ).run();

        assertTrue(result.success);
        assertEquals("cleanup done", result.message);
        List<Records.DashboardRow> rows = store.dashboardRows();
        List<Records.StudyItem> items = store.studyItems();
        assertFalse(rows.isEmpty());
        assertFalse(store.suspendedImports().isEmpty());
        assertFalse(items.isEmpty());
        assertEquals("success", store.latestSync().status);

        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        boolean activeStudyItem = false;
        for (Records.StudyItem item : items) {
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
    public void successfulSyncUsesAdaptiveWorkloadForNewAdmissions() {
        Records.Settings settings = Records.Settings.kikuDefaults();
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
    public void failedSyncPersistsConfigError() {
        Records.Settings settings = Records.Settings.kikuDefaults();
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
    public void manualSyncReceivesOrderedProgressEvents() {
        Records.Settings settings = Records.Settings.kikuDefaults();
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
                "BUILDING_PRACTICE_QUEUE:0/-1",
                "ARCHIVING_IMPORTED_CARDS:0/-1"
        ), events);
    }

    private Records.CollectionSnapshot snapshot(Records.Settings settings) {
        Records.Note active = note(1L, "確認", "かくにん", "confirmation", "確認した。");
        Records.Note suspended = note(2L, "笥箱", "しはこ", "rare box", "笥箱を見た。");
        Records.Card activeCard = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, settings.matureDays + 5, 12, 0, false);
        Records.Card suspendedCard = new Records.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true);
        return new Records.CollectionSnapshot(Arrays.asList(active, suspended), Arrays.asList(activeCard, suspendedCard));
    }

    private Records.CollectionSnapshot manyProblemSnapshot() {
        Records.Note first = note(1L, "拉麺", "らーめん", "ramen", "拉麺を食べた。");
        Records.Note second = note(2L, "謎", "なぞ", "mystery", "謎を見た。");
        Records.Note third = note(3L, "裂ける", "さける", "split", "裂ける音。");
        return new Records.CollectionSnapshot(
                Arrays.asList(first, second, third),
                Arrays.asList(
                        new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 12, 2, false),
                        new Records.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 3, 12, 1, false),
                        new Records.Card(30L, 3L, 0, "Kiku", 2, 2, 0, 3, 12, 0, false)
                )
        );
    }

    private int activeStudyItemCount(List<Records.StudyItem> items) {
        int count = 0;
        for (Records.StudyItem item : items) {
            if (!"retired".equals(item.state)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasSuspendedEvidence(List<Records.DashboardRow> rows) {
        for (Records.DashboardRow row : rows) {
            if (row.suspendedExampleCount > 0) {
                return true;
            }
        }
        return false;
    }

    private Records.Note note(long id, String expression, String reading, String meaning, String sentence) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new Records.Note(id, "Kiku", fields, Collections.emptyList());
    }

    private static final class FakeGateway implements CollectionGateway {
        private final Records.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;

        private FakeGateway(Records.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public Records.CollectionSnapshot readCollection(Records.Settings settings) {
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
            return removal;
        }
    }

    private static final class FailingGateway implements CollectionGateway {
        @Override
        public Records.CollectionSnapshot readCollection(Records.Settings settings) throws AnkiDroidGateway.SyncFailure {
            throw AnkiDroidGateway.SyncFailure.permanent("Kiku note type was not found in AnkiDroid.");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "");
        }
    }

    private static final class ProgressGateway implements CollectionGateway {
        private final Records.CollectionSnapshot snapshot;
        private final AnkiDroidGateway.RemovalSummary removal;

        private ProgressGateway(Records.CollectionSnapshot snapshot, AnkiDroidGateway.RemovalSummary removal) {
            this.snapshot = snapshot;
            this.removal = removal;
        }

        @Override
        public Records.CollectionSnapshot readCollection(Records.Settings settings) {
            return snapshot;
        }

        @Override
        public Records.CollectionSnapshot readCollection(Records.Settings settings, SyncProgress.Listener progress) {
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES));
            progress.onSyncProgress(SyncProgress.cardsScanned(0, snapshot.cards.size()));
            for (int i = 0; i < snapshot.cards.size(); i++) {
                progress.onSyncProgress(SyncProgress.cardsScanned(i + 1, snapshot.cards.size()));
            }
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
            return removal;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
            return removal;
        }
    }
}
