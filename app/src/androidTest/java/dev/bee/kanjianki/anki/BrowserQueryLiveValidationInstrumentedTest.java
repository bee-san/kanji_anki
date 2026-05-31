package dev.bee.kanjianki.anki;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.ManualSyncEngine;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class BrowserQueryLiveValidationInstrumentedTest {
    private Context context;
    private LocalStore store;

    @Before
    public void setUp() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Live browser-query validation is opt-in.", "true".equals(arguments.getString("kanjiLiveBrowserQuery")));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        if (context != null) {
            context.deleteDatabase("kanji_anki_simple.db");
        }
    }

    @Test
    public void browserQueryImportsOnlyTaggedActiveCardAgainstRealAnkiDroidProvider() throws Exception {
        RecordsSyncModels.Settings settings = liveSettings();
        AnkiDroidGateway gateway = new AnkiDroidGateway(context);

        assertProviderReady(gateway.status());

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(settings);
        assertLiveFixtureSnapshot(snapshot);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();
        assertManualSyncSucceeded(result);
        assertSuspendedArchiveCleanup();
        assertBrowserQueryDashboardRow();
        assertBrowserQueryAuditPrivacy();
    }

    private static void assertProviderReady(AnkiDroidGateway.ProviderStatus status) {
        assertTrue(status.message, status.installed);
        assertTrue(status.message, status.permissionGranted);
        assertEquals("com.ichi2.anki.flashcards", status.authority);
    }

    private static void assertLiveFixtureSnapshot(RecordsSyncModels.CollectionSnapshot snapshot) {
        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
        assertTrue("active query card should be marked by the real Browser query", cardForNote(snapshot, 1700000000002L).browserQueryMatched);
        assertFalse("suspended card must not match the Browser query", cardForNote(snapshot, 1700000000001L).browserQueryMatched);
    }

    private void assertManualSyncSucceeded(ManualSyncEngine.SyncResult result) {
        assertTrue(result.message, result.success);
        assertEquals("success", store.latestSync().status);
    }

    private void assertSuspendedArchiveCleanup() {
        List<RecordsImportModels.SuspendedImport> suspendedImports = store.suspendedImports();
        assertEquals("The unrelated suspended card should still be archived safely.", 1, suspendedImports.size());
        assertEquals("箱", suspendedImports.get(0).kanji);
        assertEquals("suspended", suspendedImports.get(0).sources.get(0).sourceType);
    }

    private void assertBrowserQueryDashboardRow() {
        RecordsImportModels.DashboardRow activeRow = rowFor(store.dashboardRows(), "橋");
        assertNotNull("Active card should import because Browser query is enabled.", activeRow);
        assertEquals(1, activeRow.activeExampleCount);
        assertEquals(0, activeRow.suspendedExampleCount);
        assertEquals("browser_query", activeRow.examples.get(0).sourceType);
    }

    private void assertBrowserQueryAuditPrivacy() {
        assertEquals("suspended browser_query", scalar("import_rule_audits", "enabled_sources", "sync_id=?", new String[]{"1"}));
        assertEquals("[redacted]", scalar("import_rule_audits", "browser_query", "sync_id=?", new String[]{"1"}));
        String settingsJson = scalar("import_rule_audits", "settings_json", "sync_id=?", new String[]{"1"});
        assertFalse("Raw query text must not appear in rule audit settings JSON.", settingsJson.contains("kani_query_test"));
        assertTrue(settingsJson.contains("[redacted]"));

        assertEquals("browser_query_import", scalar("import_decisions", "reason_code", "sync_id=? AND kanji=?", new String[]{"1", "橋"}));
        assertEquals("browser_query", scalar("import_decisions", "source_types", "sync_id=? AND kanji=?", new String[]{"1", "橋"}));
        assertEquals("browser_query", scalar("import_decisions", "rule_types", "sync_id=? AND kanji=?", new String[]{"1", "橋"}));
        String reasonText = scalar("import_decisions", "reason_text", "sync_id=? AND kanji=?", new String[]{"1", "橋"});
        assertFalse("Raw query text must not appear in decision history.", reasonText.contains("kani_query_test"));
    }

    private static RecordsSyncModels.Settings liveSettings() {
        return new RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                2,
                100,
                3000,
                24,
                3,
                28,
                3,
                1,
                false,
                true,
                false,
                Collections.emptyList(),
                false,
                7.0,
                2,
                1,
                true,
                "tag:kani_query_test",
                "balanced_priority",
                21,
                3
        );
    }

    private static RecordsSyncModels.Card cardForNote(RecordsSyncModels.CollectionSnapshot snapshot, long noteId) {
        for (RecordsSyncModels.Card card : snapshot.cards) {
            if (card.noteId == noteId) {
                return card;
            }
        }
        throw new AssertionError("Missing card for note " + noteId);
    }

    private static RecordsImportModels.DashboardRow rowFor(List<RecordsImportModels.DashboardRow> rows, String kanji) {
        for (RecordsImportModels.DashboardRow row : rows) {
            if (kanji.equals(row.kanji)) {
                return row;
            }
        }
        return null;
    }

    private String scalar(String table, String column, String where, String[] args) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("kanji_anki_simple.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor cursor = db.query(table, new String[]{column}, where, args, null, null, null)) {
            assertTrue("No row for " + table + "." + column, cursor.moveToFirst());
            return cursor.getString(0);
        } finally {
            db.close();
        }
    }
}
