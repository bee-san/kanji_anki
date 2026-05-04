package dev.bee.kanjianki.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.Records;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class LocalStoreInstrumentedTest {
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
    public void testSyncPersistsActiveMirrorSuspendedArchiveAndDerivedRows() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note active = note(1L, "確認", "かくにん", "confirmation", "確認した。");
        Records.Note suspended = note(2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。");
        Records.Card activeCard = new Records.Card(10L, 1L, 0, "例文マイニング", 2, 2, 0, 45, 12, 1, false);
        Records.Card suspendedCard = new Records.Card(20L, 2L, 0, "例文マイニング", -1, 0, 0, 0, 0, 0, true);
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(active, suspended),
                Arrays.asList(activeCard, suspendedCard)
        );
        Records.SuspendedSource source = new Records.SuspendedSource("拉", 20L, 2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。");
        Records.SuspendedImport imported = new Records.SuspendedImport("拉", 3401, true, 3000, Collections.singletonList(source));
        Records.Example example = new Records.Example("suspended", 20L, 2L, "拉麺", "らーめん", "ramen", "拉麺を食べた。", false, 0);
        Records.DashboardRow row = new Records.DashboardRow(
                "拉",
                3401,
                "ramen",
                "らーめん",
                "deck:例文マイニング 拉",
                93,
                "suspended_archive",
                "Imported from the local suspended archive.",
                0,
                1,
                0,
                Collections.singletonList(example)
        );

        long syncId = store.saveSuccessfulSync(
                snapshot,
                Collections.singletonList(imported),
                Collections.singletonList(row),
                settings,
                1000L,
                2000L,
                null
        );
        store.updateSyncRemovalMessage(syncId, "Archived locally before provider cleanup.");

        LocalStore.SyncStatus status = store.latestSync();
        assertNotNull(status);
        assertEquals("success", status.status);
        assertEquals(1, status.activeCards);
        assertEquals(1, status.suspendedCards);
        assertEquals(1, status.importedKanji);
        assertEquals("Archived locally before provider cleanup.", status.removalMessage);
        assertEquals(1, count("source_cards"));
        assertEquals(1, count("source_notes"));
        assertEquals(1, count("suspended_archive"));
        assertEquals(1, count("suspended_imports"));
        assertEquals(1, count("suspended_sources"));
        assertEquals(1, count("dashboard_rows"));
        assertEquals(1, count("kanji_examples"));
        assertEquals("拉", store.dashboardRows().get(0).kanji);
        List<Records.SuspendedImport> storedImports = store.suspendedImports();
        assertEquals(1, storedImports.size());
        assertEquals("拉", storedImports.get(0).kanji);
        assertEquals(1, storedImports.get(0).sources.size());
    }

    @Test
    public void testSettingsAndReviewTokensPersistAcrossStoreInstances() {
        store.putIntSetting("suspended_rank_cutoff", 3000);
        Records.ReviewRequest request = new Records.ReviewRequest("拉", "token-1", "good", true, true, false, 0);
        store.saveReview(request, "good", 3000L);
        store.close();

        store = new LocalStore(context);
        assertEquals(3000, store.getIntSetting("suspended_rank_cutoff", 1000));
        List<String> tokens = store.consumedTokens();
        assertEquals(1, tokens.size());
        assertEquals("token-1", tokens.get(0));

        store.saveReview(request, "easy", 4000L);
        assertEquals(1, store.consumedTokens().size());
    }

    @Test
    public void testReviewStatsAndSchedulerParametersPersist() {
        store.saveReview(new Records.ReviewRequest("拉", "token-a", "again", true, false, false, 0), "again", 1000L);
        store.saveReview(new Records.ReviewRequest("麺", "token-b", "good", true, true, false, 0), "good", 2000L);
        Records.ReviewStats stats = store.reviewStatsSince(0L);
        assertEquals(2, stats.total);
        assertEquals(1, stats.again);
        assertEquals(1, stats.good);
        assertEquals(2, stats.writingRequired);
        assertEquals(1, stats.writingFailed);

        Records.SchedulerParameters tuned = Records.SchedulerParameters.defaults()
                .withAdjustment(0.40, 1.10, 1.80, 2.80, 5000L, 30);
        store.saveSchedulerParameters(tuned);
        Records.SchedulerParameters loaded = store.schedulerParameters();
        assertEquals(0.40, loaded.againMultiplier, 0.001);
        assertEquals(1.80, loaded.goodMultiplier, 0.001);
        assertEquals(5000L, loaded.lastAdjustedAtMillis);
        assertEquals(30, loaded.lastAdjustmentReviewCount);
    }

    private int count(String table) {
        SQLiteDatabase db = store.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
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
}
