package dev.bee.kanjianki.sync;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        assertFalse(store.dashboardRows().isEmpty());
        assertFalse(store.suspendedImports().isEmpty());
        assertFalse(store.studyItems().isEmpty());
        assertEquals("success", store.latestSync().status);
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

    private Records.CollectionSnapshot snapshot(Records.Settings settings) {
        Records.Note active = note(1L, "確認", "かくにん", "confirmation", "確認した。");
        Records.Note suspended = note(2L, "笥箱", "しはこ", "rare box", "笥箱を見た。");
        Records.Card activeCard = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, settings.matureDays + 5, 12, 0, false);
        Records.Card suspendedCard = new Records.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true);
        return new Records.CollectionSnapshot(Arrays.asList(active, suspended), Arrays.asList(activeCard, suspendedCard));
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
        public Records.CollectionSnapshot readCollection(Records.Settings settings) throws AnkiDroidGateway.SyncException {
            throw AnkiDroidGateway.SyncException.permanent("Kiku note type was not found in AnkiDroid.");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "");
        }
    }
}
