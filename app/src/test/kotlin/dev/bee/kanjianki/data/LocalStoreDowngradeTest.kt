package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.KaniTestDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.bee.kanjianki.core.MissingKanjiExportReceipt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreDowngradeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KaniTestDatabase.delete(context)
    }

    @After
    fun tearDown() {
        KaniTestDatabase.delete(context)
    }

    @Test
    fun downgradePreservesDataAndSetsMarker() {
        val store = LocalStore(context)
        store.writableDatabase.execSQL(
            "INSERT INTO dashboard_rows (kanji, jiten_rank, primary_meaning, reading, browser_search, weakness_score, reason_code, reason_text, active_example_count, suspended_example_count, mature_support_count, rebuilt_at) VALUES ('痛', 100, 'pain', 'いたい', '痛い', 5, 'leech', 'leech', 1, 0, 0, 0)"
        )
        store.close()

        setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)

        val downgradedStore = LocalStore(context)
        downgradedStore.readableDatabase.rawQuery(
            "SELECT kanji, primary_meaning FROM dashboard_rows WHERE kanji = '痛'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("痛", cursor.getString(0))
            assertEquals("pain", cursor.getString(1))
        }

        val downgradeVersion = downgradedStore.consumeDowngradeNotice()
        assertNotNull(downgradeVersion)
        assertEquals(LocalStoreSchema.DB_VERSION + 1, downgradeVersion)

        val secondCheck = downgradedStore.consumeDowngradeNotice()
        assertNull(secondCheck)
        downgradedStore.close()
    }

    @Test
    fun cachedSettingsStillExposeDowngradeNoticeAfterReopen() {
        val store = LocalStore(context)
        store.writableDatabase
        assertNull(store.getStringSetting(LocalStoreBase.SETTING_DOWNGRADED_FROM_VERSION, null))
        store.close()

        setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)

        store.readableDatabase
        assertEquals(LocalStoreSchema.DB_VERSION + 1, store.consumeDowngradeNotice())
        store.close()
    }

    @Test
    fun cachedSettingsStillExposeDowngradeNoticeAcrossStores() {
        val observer = LocalStore(context)
        observer.writableDatabase
        assertNull(observer.getStringSetting(LocalStoreBase.SETTING_DOWNGRADED_FROM_VERSION, null))

        setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)

        LocalStore(context).use { it.readableDatabase }
        assertEquals(
            LocalStoreSchema.DB_VERSION + 1,
            observer.getStringSetting(LocalStoreBase.SETTING_DOWNGRADED_FROM_VERSION, null)?.toInt(),
        )
        observer.close()
    }

    @Test
    fun downgradeDoesNotCrashOnOpen() {
        val store = LocalStore(context)
        store.writableDatabase
        store.close()

        setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)

        val downgradedStore = LocalStore(context)
        val db = downgradedStore.readableDatabase
        assertNotNull(db)
        assertEquals(LocalStoreSchema.DB_VERSION, db.version)
        downgradedStore.close()
    }

    @Test
    fun coreQueriesWorkAfterDowngrade() {
        val store = LocalStore(context)
        store.writableDatabase.execSQL(
            "INSERT INTO dashboard_rows (kanji, jiten_rank, primary_meaning, reading, browser_search, weakness_score, reason_code, reason_text, active_example_count, suspended_example_count, mature_support_count, rebuilt_at) VALUES ('水', 50, 'water', 'みず', '水', 3, 'leech', 'leech', 1, 0, 0, 0)"
        )
        store.close()

        setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)

        val downgradedStore = LocalStore(context)
        val rows = downgradedStore.activeDashboardRows()
        assertEquals(1, rows.size)
        assertEquals("水", rows[0].kanji)

        val inventory = downgradedStore.searchKanjiInventory("水")
        assertNotNull(inventory)
        downgradedStore.close()
    }

    @Test
    fun downgradePreservesMissingKanjiSourcesAndReceipts() {
        val store = LocalStore(context)
        store.missingKanjiStore().addManualSources(
            listOf(
                MissingKanjiCandidate(
                    "水",
                    meanings = listOf("water"),
                    kunReadings = listOf("みず"),
                    jitenRank = 12,
                ),
            ),
            nowMillis = 100,
        )
        store.missingKanjiStore().recordExportReceipts(
            listOf(MissingKanjiExportReceipt("水", "anki:test", 200, 300)),
        )
        store.close()

        setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)

        val downgradedStore = LocalStore(context)
        assertEquals("水", downgradedStore.missingKanjiStore().manualSources().single().candidate.literal)
        assertEquals(
            300L,
            downgradedStore.missingKanjiStore().exportReceipts("anki:test").getValue("水").externalNoteId,
        )
        downgradedStore.close()
    }

    private fun setDatabaseVersion(version: Int) {
        val dbPath = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        val db = SQLiteDatabase.openDatabase(
            dbPath.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        db.version = version
        db.close()
    }
}
