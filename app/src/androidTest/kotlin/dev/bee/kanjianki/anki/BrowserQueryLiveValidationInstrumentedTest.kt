package dev.bee.kanjianki.anki

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.ManualSyncEngine
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

private const val DATABASE_NAME = "kanji_anki_simple.db"

@RunWith(AndroidJUnit4::class)
class BrowserQueryLiveValidationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        val arguments: Bundle = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "Live browser-query validation is opt-in.",
            arguments.getString("kanjiLiveBrowserQuery") == "true",
        )
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    @Test
    fun browserQueryImportsOnlyTaggedActiveCardAgainstRealAnkiDroidProvider() {
        val settings = liveSettings()
        val gateway = AnkiDroidGateway(context)

        assertProviderReady(gateway.status())

        val snapshot = gateway.readCollection(settings)
        assertLiveFixtureSnapshot(snapshot)

        val result = ManualSyncEngine(context, store, gateway, settings).run()
        assertManualSyncSucceeded(result)
        assertSuspendedArchiveCleanup()
        assertBrowserQueryDashboardRow()
        assertBrowserQueryAuditPrivacy()
    }

    private fun assertProviderReady(status: AnkiDroidGateway.ProviderStatus) {
        assertTrue(status.message, status.installed)
        assertTrue(status.message, status.permissionGranted)
        assertEquals("com.ichi2.anki.flashcards", status.authority)
    }

    private fun assertLiveFixtureSnapshot(snapshot: RecordsSyncModels.CollectionSnapshot) {
        assertEquals(2, snapshot.notes.size)
        assertEquals(2, snapshot.cards.size)
        assertTrue(
            "active query card should be marked by the real Browser query",
            cardForNote(snapshot, 1700000000002L).browserQueryMatched,
        )
        assertFalse(
            "suspended card must not match the Browser query",
            cardForNote(snapshot, 1700000000001L).browserQueryMatched,
        )
    }

    private fun assertManualSyncSucceeded(result: ManualSyncEngine.SyncResult) {
        assertTrue(result.message, result.success)
        assertEquals("success", store.latestSync()!!.status)
    }

    private fun assertSuspendedArchiveCleanup() {
        val suspendedImports = store.suspendedImports()
        assertEquals("The unrelated suspended card should still be archived safely.", 1, suspendedImports.size)
        assertEquals("箱", suspendedImports[0].kanji)
        assertEquals("suspended", suspendedImports[0].sources[0].sourceType)
    }

    private fun assertBrowserQueryDashboardRow() {
        val activeRow = rowFor(store.dashboardRows(), "橋")
        assertNotNull("Active card should import because Browser query is enabled.", activeRow)
        val row = activeRow!!
        assertEquals(1, row.activeExampleCount)
        assertEquals(0, row.suspendedExampleCount)
        assertEquals("browser_query", row.examples[0].sourceType)
    }

    private fun assertBrowserQueryAuditPrivacy() {
        assertEquals("suspended browser_query", scalar("import_rule_audits", "enabled_sources", "sync_id=?", arrayOf("1")))
        assertEquals("[redacted]", scalar("import_rule_audits", "browser_query", "sync_id=?", arrayOf("1")))
        val settingsJson = scalar("import_rule_audits", "settings_json", "sync_id=?", arrayOf("1"))
        assertFalse(
            "Raw query text must not appear in rule audit settings JSON.",
            settingsJson.contains("kani_query_test"),
        )

        assertEquals("browser_query_import", scalar("import_decisions", "reason_code", "sync_id=? AND kanji=?", arrayOf("1", "橋")))
        assertEquals("browser_query", scalar("import_decisions", "source_types", "sync_id=? AND kanji=?", arrayOf("1", "橋")))
        assertEquals("browser_query", scalar("import_decisions", "rule_types", "sync_id=? AND kanji=?", arrayOf("1", "橋")))
        val reasonText = scalar("import_decisions", "reason_text", "sync_id=? AND kanji=?", arrayOf("1", "橋"))
        assertFalse(
            "Raw query text must not appear in decision history.",
            reasonText.contains("kani_query_test"),
        )
    }

    private fun liveSettings(): RecordsSyncModels.Settings {
        return RecordsSyncModels.Settings(
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
            emptyList<String>(),
            false,
            7.0,
            2,
            1,
            true,
            "tag:kani_query_test",
            "balanced_priority",
            21,
            3,
        )
    }

    private fun cardForNote(snapshot: RecordsSyncModels.CollectionSnapshot, noteId: Long): RecordsSyncModels.Card {
        return snapshot.cards.firstOrNull { it.noteId == noteId }
            ?: error("Missing card for note $noteId")
    }

    private fun rowFor(rows: List<RecordsImportModels.DashboardRow>, kanji: String): RecordsImportModels.DashboardRow? {
        return rows.firstOrNull { it.kanji == kanji }
    }

    private fun scalar(table: String, column: String, where: String, args: Array<String>): String {
        val db = SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        val cursor: Cursor = db.query(table, arrayOf(column), where, args, null, null, null)
        try {
            assertTrue("No row for $table.$column", cursor.moveToFirst())
            return cursor.getString(0)
        } finally {
            cursor.close()
            db.close()
        }
    }
}
